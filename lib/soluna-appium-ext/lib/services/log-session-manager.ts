import {spawn} from 'node:child_process'
import {randomUUID} from 'node:crypto'
import {appendFile, mkdir, readFile, rm, unlink, writeFile} from 'node:fs/promises'
import {tmpdir} from 'node:os'
import path from 'node:path'
import {resolveIosCommand as resolveIosCommandDefault} from '../cli/preflight'
import {lookupDeviceByUdid as lookupDeviceByUdidDefault} from './device-service'
import {parseUnifiedLogLine} from './log-parser'
import type {Platform} from '../types/device'
import type {
  CreateLogSessionRequest,
  CreateLogSessionResult,
  DeleteLogSessionRequest,
  DeleteLogSessionResult,
  LogLineSource,
  LogSessionSnapshot,
  LogSessionStatus,
  ReadLogSessionRequest,
  ReadLogSessionResult,
  UnifiedLogEntry,
} from '../types/log'

const DEFAULT_BUFFER_ENTRIES = 1000
const MIN_BUFFER_ENTRIES = 100
const MAX_BUFFER_ENTRIES = 10000
const DEFAULT_SESSION_BYTES = 100 * 1024 * 1024
const MIN_SESSION_BYTES = 1024 * 1024
const MAX_SESSION_BYTES = 500 * 1024 * 1024
const DEFAULT_TTL_MS = 10 * 60 * 1000
const MIN_TTL_MS = 60 * 1000
const MAX_TTL_MS = 24 * 60 * 60 * 1000
const DEFAULT_READ_LIMIT = 200
const MIN_READ_LIMIT = 1
const MAX_READ_LIMIT = 2000
const DEFAULT_MAX_SESSIONS = 20
const CLEANUP_INTERVAL_MS = 5000
const FORCE_KILL_DELAY_MS = 500

interface LogSessionProcessEvents {
  on(event: 'close', listener: (code: number | null) => void): this
  once(event: 'error', listener: (err: NodeJS.ErrnoException) => void): this
}

interface LogSessionProcess extends LogSessionProcessEvents {
  stdout: NodeJS.ReadableStream | null
  stderr: NodeJS.ReadableStream | null
  kill(signal?: NodeJS.Signals): boolean
}

export interface LogSessionService {
  createSession(input: unknown): Promise<CreateLogSessionResult>
  readSession(input: unknown): Promise<ReadLogSessionResult>
  deleteSession(input: unknown): Promise<DeleteLogSessionResult>
}

interface ParsedCreateLogSessionRequest {
  udid: string
  maxBufferEntries: number
  maxSessionBytes: number
  ttlMs: number
}

interface ParsedReadLogSessionRequest {
  sessionId: string
  cursor: number
  limit: number
}

interface ParsedDeleteLogSessionRequest {
  sessionId: string
}

interface InternalLogSession {
  sessionId: string
  udid: string
  platform: Platform
  status: LogSessionStatus
  command: string
  args: string[]
  startedAtMs: number
  endedAtMs?: number
  lastActivityAtMs: number
  ttlMs: number
  nextSeq: number
  minSeq: number
  droppedCount: number
  maxBufferEntries: number
  maxSessionBytes: number
  filePath: string
  fileBytes: number
  error?: string
  stdoutRemainder: string
  stderrRemainder: string
  ringBuffer: UnifiedLogEntry[]
  persistQueue: Promise<void>
  child: LogSessionProcess
}

type SpawnLogProcess = (command: string, args: string[]) => LogSessionProcess
type LookupDeviceByUdid = typeof lookupDeviceByUdidDefault
type ResolveIosCommand = typeof resolveIosCommandDefault

export class LogSessionHttpError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

export interface LogSessionManagerOptions {
  baseDir?: string
  maxSessions?: number
  cleanupIntervalMs?: number
  now?: () => number
  spawnProcess?: SpawnLogProcess
  lookupDeviceByUdid?: LookupDeviceByUdid
  resolveIosCommand?: ResolveIosCommand
  sessionIdFactory?: () => string
}

function defaultSpawnProcess(command: string, args: string[]): LogSessionProcess {
  return spawn(command, args, {
    stdio: ['ignore', 'pipe', 'pipe'],
  })
}

function toIso(ms: number): string {
  return new Date(ms).toISOString()
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

function parseFiniteNumber(value: unknown, fieldName: string): number {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new LogSessionHttpError(400, 'invalid_argument', `'${fieldName}' must be a finite number`)
  }
  return value
}

function parseOptionalNumber(value: unknown, fieldName: string): number | undefined {
  if (value === undefined) {
    return undefined
  }
  return parseFiniteNumber(value, fieldName)
}

function parseCreateLogSessionRequest(input: unknown): ParsedCreateLogSessionRequest {
  if (!input || typeof input !== 'object') {
    throw new LogSessionHttpError(400, 'invalid_argument', 'Request body must be a JSON object')
  }

  const body = input as CreateLogSessionRequest
  if (body.udid.trim().length === 0) {
    throw new LogSessionHttpError(400, 'invalid_argument', "'udid' must be a non-empty string")
  }

  const maxBufferEntriesRaw = parseOptionalNumber(body.maxBufferEntries, 'maxBufferEntries')
  const maxSessionBytesRaw = parseOptionalNumber(body.maxSessionBytes, 'maxSessionBytes')
  const ttlMsRaw = parseOptionalNumber(body.ttlMs, 'ttlMs')

  return {
    udid: body.udid.trim(),
    maxBufferEntries: clamp(
      Math.floor(maxBufferEntriesRaw ?? DEFAULT_BUFFER_ENTRIES),
      MIN_BUFFER_ENTRIES,
      MAX_BUFFER_ENTRIES
    ),
    maxSessionBytes: clamp(
      Math.floor(maxSessionBytesRaw ?? DEFAULT_SESSION_BYTES),
      MIN_SESSION_BYTES,
      MAX_SESSION_BYTES
    ),
    ttlMs: clamp(Math.floor(ttlMsRaw ?? DEFAULT_TTL_MS), MIN_TTL_MS, MAX_TTL_MS),
  }
}

function parseCursor(queryValue: unknown): number {
  if (queryValue === undefined) {
    return 0
  }

  let numericValue: number
  if (typeof queryValue === 'string') {
    numericValue = Number.parseInt(queryValue, 10)
  } else if (typeof queryValue === 'number') {
    numericValue = queryValue
  } else {
    throw new LogSessionHttpError(400, 'invalid_argument', "'cursor' must be a number")
  }

  if (!Number.isFinite(numericValue) || numericValue < 0) {
    throw new LogSessionHttpError(400, 'invalid_argument', "'cursor' must be a non-negative number")
  }

  return Math.floor(numericValue)
}

function parseLimit(queryValue: unknown): number {
  if (queryValue === undefined) {
    return DEFAULT_READ_LIMIT
  }

  let numericValue: number
  if (typeof queryValue === 'string') {
    numericValue = Number.parseInt(queryValue, 10)
  } else if (typeof queryValue === 'number') {
    numericValue = queryValue
  } else {
    throw new LogSessionHttpError(400, 'invalid_argument', "'limit' must be a number")
  }

  if (!Number.isFinite(numericValue)) {
    throw new LogSessionHttpError(400, 'invalid_argument', "'limit' must be a finite number")
  }

  return clamp(Math.floor(numericValue), MIN_READ_LIMIT, MAX_READ_LIMIT)
}

function parseReadLogSessionRequest(input: unknown): ParsedReadLogSessionRequest {
  if (!input || typeof input !== 'object') {
    throw new LogSessionHttpError(400, 'invalid_argument', 'Read request must be an object')
  }

  const body = input as ReadLogSessionRequest
  if (body.sessionId.trim().length === 0) {
    throw new LogSessionHttpError(400, 'invalid_argument', "'sessionId' must be a non-empty string")
  }

  return {
    sessionId: body.sessionId.trim(),
    cursor: parseCursor(body.cursor),
    limit: parseLimit(body.limit),
  }
}

function parseDeleteLogSessionRequest(input: unknown): ParsedDeleteLogSessionRequest {
  if (!input || typeof input !== 'object') {
    throw new LogSessionHttpError(400, 'invalid_argument', 'Delete request must be an object')
  }

  const body = input as DeleteLogSessionRequest
  if (body.sessionId.trim().length === 0) {
    throw new LogSessionHttpError(400, 'invalid_argument', "'sessionId' must be a non-empty string")
  }

  return {
    sessionId: body.sessionId.trim(),
  }
}

function serializeSnapshot(session: InternalLogSession): LogSessionSnapshot {
  return {
    sessionId: session.sessionId,
    udid: session.udid,
    platform: session.platform,
    status: session.status,
    command: session.command,
    args: session.args,
    startedAt: toIso(session.startedAtMs),
    endedAt: session.endedAtMs ? toIso(session.endedAtMs) : undefined,
    lastActivityAt: toIso(session.lastActivityAtMs),
    ttlMs: session.ttlMs,
    nextSeq: session.nextSeq,
    minSeq: session.minSeq,
    droppedCount: session.droppedCount,
    maxBufferEntries: session.maxBufferEntries,
    maxSessionBytes: session.maxSessionBytes,
    error: session.error,
  }
}

function countLines(text: string): number {
  if (!text) {
    return 0
  }
  let lines = 0
  for (const ch of text) {
    if (ch === '\n') {
      lines += 1
    }
  }
  return lines
}

export class LogSessionManager implements LogSessionService {
  private readonly baseDir: string
  private readonly maxSessions: number
  private readonly now: () => number
  private readonly spawnProcess: SpawnLogProcess
  private readonly lookupDeviceByUdid: LookupDeviceByUdid
  private readonly resolveIosCommand: ResolveIosCommand
  private readonly sessionIdFactory: () => string
  private readonly sessions = new Map<string, InternalLogSession>()
  private readonly cleanupTimer: NodeJS.Timeout

  constructor(options: LogSessionManagerOptions = {}) {
    this.baseDir = options.baseDir ?? path.join(tmpdir(), 'soluna-appium-ext', 'log-sessions')
    this.maxSessions = options.maxSessions ?? DEFAULT_MAX_SESSIONS
    this.now = options.now ?? Date.now
    this.spawnProcess = options.spawnProcess ?? defaultSpawnProcess
    this.lookupDeviceByUdid = options.lookupDeviceByUdid ?? lookupDeviceByUdidDefault
    this.resolveIosCommand = options.resolveIosCommand ?? resolveIosCommandDefault
    this.sessionIdFactory = options.sessionIdFactory ?? randomUUID

    const cleanupIntervalMs = options.cleanupIntervalMs ?? CLEANUP_INTERVAL_MS
    this.cleanupTimer = setInterval(() => {
      void this.cleanupExpiredSessions()
    }, cleanupIntervalMs)
    this.cleanupTimer.unref?.()
  }

  private ensureCapacity(): void {
    if (this.sessions.size >= this.maxSessions) {
      throw new LogSessionHttpError(
        409,
        'session_limit_reached',
        `Active session count has reached the limit (${this.maxSessions})`
      )
    }
  }

  private assertNoRunningSessionForUdid(udid: string): void {
    const existing = [...this.sessions.values()].find((session) => {
      return session.udid === udid && session.status === 'running'
    })
    if (existing) {
      throw new LogSessionHttpError(
        409,
        'session_conflict',
        `A running log session already exists for udid '${udid}'`
      )
    }
  }

  private async resolveLogCommand(
    platform: Platform,
    udid: string
  ): Promise<{command: string; args: string[]}> {
    if (platform === 'android') {
      return {
        command: 'adb',
        args: ['-s', udid, 'logcat', '-v', 'threadtime'],
      }
    }

    const iosCmd = await this.resolveIosCommand()
    if (!iosCmd) {
      throw new LogSessionHttpError(
        500,
        'dependency_missing',
        "Neither 'go-ios' nor 'ios' is currently available on this host"
      )
    }
    return {
      command: iosCmd,
      args: ['syslog', '--udid', udid],
    }
  }

  private handleClose(session: InternalLogSession, code: number | null): void {
    if (session.status !== 'running') {
      return
    }

    this.flushRemainderLine(session, 'stdout')
    this.flushRemainderLine(session, 'stderr')

    if (code === null || code === 0 || code === 143) {
      session.status = 'stopped'
    } else {
      session.status = 'failed'
      session.error = `Log process exited with code ${code}`
    }
    session.endedAtMs = this.now()
    session.lastActivityAtMs = this.now()
  }

  private failSession(session: InternalLogSession, message: string): void {
    if (session.status === 'failed') {
      return
    }

    session.status = 'failed'
    session.error = message
    session.endedAtMs = this.now()
    session.lastActivityAtMs = this.now()
    this.stopProcess(session)
  }

  private stopProcess(session: InternalLogSession): void {
    const terminated = session.child.kill('SIGTERM')
    if (!terminated) {
      return
    }

    const timer = setTimeout(() => {
      session.child.kill('SIGKILL')
    }, FORCE_KILL_DELAY_MS)
    timer.unref?.()
  }

  private enqueuePersist(session: InternalLogSession, entry: UnifiedLogEntry): void {
    const payload = `${JSON.stringify(entry)}\n`
    const payloadBytes = Buffer.byteLength(payload)
    const task = async (): Promise<void> => {
      try {
        await appendFile(session.filePath, payload, 'utf8')
        session.fileBytes += payloadBytes
        if (session.fileBytes > session.maxSessionBytes) {
          await this.trimFile(session)
        }
      } catch (err) {
        this.failSession(
          session,
          `Failed to persist log entry: ${err instanceof Error ? err.message : String(err)}`
        )
      }
    }

    session.persistQueue = session.persistQueue.then(task, task)
  }

  private async trimFile(session: InternalLogSession): Promise<void> {
    const current = await readFile(session.filePath, 'utf8')
    if (Buffer.byteLength(current) <= session.maxSessionBytes) {
      session.fileBytes = Buffer.byteLength(current)
      return
    }

    const currentBuffer = Buffer.from(current, 'utf8')
    let tail = currentBuffer.subarray(currentBuffer.length - session.maxSessionBytes)
    const firstLineBreak = tail.indexOf(0x0a)
    if (firstLineBreak >= 0) {
      tail = tail.subarray(firstLineBreak + 1)
    } else {
      tail = Buffer.alloc(0)
    }

    await writeFile(session.filePath, tail)
    session.fileBytes = tail.length

    const kept = tail.toString('utf8')
    const firstLine = kept.split('\n').find((line) => line.trim().length > 0)
    if (!firstLine) {
      const dropped = session.nextSeq - session.minSeq
      if (dropped > 0) {
        session.droppedCount += dropped
      }
      session.minSeq = session.nextSeq
      session.ringBuffer = []
      return
    }

    let nextMin = session.minSeq
    try {
      const parsed = JSON.parse(firstLine) as {seq?: unknown}
      if (typeof parsed.seq === 'number' && Number.isFinite(parsed.seq)) {
        nextMin = parsed.seq
      }
    } catch {
      nextMin = session.minSeq + countLines(current) - countLines(kept)
    }

    if (nextMin > session.minSeq) {
      session.droppedCount += nextMin - session.minSeq
      session.minSeq = nextMin
      session.ringBuffer = session.ringBuffer.filter((entry) => entry.seq >= session.minSeq)
    }
  }

  private consumeChunk(session: InternalLogSession, source: LogLineSource, chunk: string | Buffer): void {
    if (session.status !== 'running') {
      return
    }

    session.lastActivityAtMs = this.now()
    const chunkText = Buffer.isBuffer(chunk) ? chunk.toString('utf8') : chunk
    const carry = source === 'stdout' ? session.stdoutRemainder : session.stderrRemainder
    const merged = `${carry}${chunkText}`
    const lines = merged.split(/\r?\n/)
    const remainder = lines.pop() ?? ''
    if (source === 'stdout') {
      session.stdoutRemainder = remainder
    } else {
      session.stderrRemainder = remainder
    }

    for (const line of lines) {
      if (!line) {
        continue
      }
      const entry = parseUnifiedLogLine({
        platform: session.platform,
        udid: session.udid,
        seq: session.nextSeq,
        source,
        line,
      })
      session.nextSeq += 1
      session.ringBuffer.push(entry)
      if (session.ringBuffer.length > session.maxBufferEntries) {
        session.ringBuffer.shift()
      }
      this.enqueuePersist(session, entry)
    }
  }

  private flushRemainderLine(session: InternalLogSession, source: LogLineSource): void {
    const value = source === 'stdout' ? session.stdoutRemainder : session.stderrRemainder
    if (!value) {
      return
    }

    if (source === 'stdout') {
      session.stdoutRemainder = ''
    } else {
      session.stderrRemainder = ''
    }

    const entry = parseUnifiedLogLine({
      platform: session.platform,
      udid: session.udid,
      seq: session.nextSeq,
      source,
      line: value,
    })
    session.nextSeq += 1
    session.ringBuffer.push(entry)
    if (session.ringBuffer.length > session.maxBufferEntries) {
      session.ringBuffer.shift()
    }
    this.enqueuePersist(session, entry)
  }

  private async readEntriesFromFile(
    session: InternalLogSession,
    startSeq: number,
    limit: number
  ): Promise<UnifiedLogEntry[]> {
    await session.persistQueue

    let content: string
    try {
      content = await readFile(session.filePath, 'utf8')
    } catch (err) {
      if ((err as NodeJS.ErrnoException).code === 'ENOENT') {
        return []
      }
      throw err
    }

    if (!content) {
      return []
    }

    const entries: UnifiedLogEntry[] = []
    for (const line of content.split('\n')) {
      if (!line.trim()) {
        continue
      }

      try {
        const parsed = JSON.parse(line) as UnifiedLogEntry

        if (parsed.seq < startSeq) {
          continue
        }
        entries.push(parsed)
      } catch {
        continue
      }

      if (entries.length >= limit) {
        break
      }
    }
    return entries
  }

  private async cleanupExpiredSessions(): Promise<void> {
    const nowMs = this.now()
    const expired = [...this.sessions.values()].filter((session) => {
      return nowMs - session.lastActivityAtMs > session.ttlMs
    })

    for (const session of expired) {
      await this.removeSessionById(session.sessionId, false)
    }
  }

  private async removeSessionById(
    sessionId: string,
    throwIfNotFound: boolean
  ): Promise<InternalLogSession | undefined> {
    const session = this.sessions.get(sessionId)
    if (!session) {
      if (throwIfNotFound) {
        throw new LogSessionHttpError(404, 'session_not_found', `Session '${sessionId}' not found`)
      }
      return undefined
    }

    if (session.status === 'running') {
      session.status = 'stopped'
      session.endedAtMs = this.now()
      session.lastActivityAtMs = this.now()
      this.stopProcess(session)
    }

    await session.persistQueue

    this.sessions.delete(sessionId)
    try {
      await unlink(session.filePath)
    } catch (err) {
      if ((err as NodeJS.ErrnoException).code !== 'ENOENT') {
        throw err
      }
    }

    return session
  }

  async createSession(input: unknown): Promise<CreateLogSessionResult> {
    const request = parseCreateLogSessionRequest(input)
    this.ensureCapacity()
    this.assertNoRunningSessionForUdid(request.udid)

    const lookup = await this.lookupDeviceByUdid(request.udid)
    if (!lookup.found || !lookup.device) {
      throw new LogSessionHttpError(
        404,
        'device_not_found',
        `Device '${request.udid}' not found on this host`
      )
    }

    const commandSpec = await this.resolveLogCommand(lookup.device.platform, request.udid)
    await mkdir(this.baseDir, {recursive: true})

    const sessionId = this.sessionIdFactory()
    const filePath = path.join(this.baseDir, `${sessionId}.jsonl`)
    await writeFile(filePath, '', 'utf8')

    let child: LogSessionProcess
    try {
      child = this.spawnProcess(commandSpec.command, commandSpec.args)
    } catch (err) {
      throw new LogSessionHttpError(
        500,
        'spawn_failed',
        `Failed to spawn log command: ${err instanceof Error ? err.message : String(err)}`
      )
    }

    const nowMs = this.now()
    const session: InternalLogSession = {
      sessionId,
      udid: request.udid,
      platform: lookup.device.platform,
      status: 'running',
      command: commandSpec.command,
      args: commandSpec.args,
      startedAtMs: nowMs,
      lastActivityAtMs: nowMs,
      ttlMs: request.ttlMs,
      nextSeq: 0,
      minSeq: 0,
      droppedCount: 0,
      maxBufferEntries: request.maxBufferEntries,
      maxSessionBytes: request.maxSessionBytes,
      filePath,
      fileBytes: 0,
      stdoutRemainder: '',
      stderrRemainder: '',
      ringBuffer: [],
      persistQueue: Promise.resolve(),
      child,
    }

    child.stdout?.on('data', (chunk: string | Buffer) => {
      this.consumeChunk(session, 'stdout', chunk)
    })
    child.stderr?.on('data', (chunk: string | Buffer) => {
      this.consumeChunk(session, 'stderr', chunk)
    })
    child.on('close', (code) => {
      this.handleClose(session, code)
    })
    child.once('error', (err) => {
      this.failSession(
        session,
        `Log process error: ${(err.message)}`
      )
    })

    this.sessions.set(sessionId, session)
    return {
      session: serializeSnapshot(session),
    }
  }

  async readSession(input: unknown): Promise<ReadLogSessionResult> {
    const request = parseReadLogSessionRequest(input)
    const session = this.sessions.get(request.sessionId)
    if (!session) {
      throw new LogSessionHttpError(
        404,
        'session_not_found',
        `Session '${request.sessionId}' not found`
      )
    }

    session.lastActivityAtMs = this.now()

    const effectiveCursor = Math.min(
      Math.max(request.cursor, session.minSeq),
      session.nextSeq
    )
    const cursorAdjusted = effectiveCursor !== request.cursor

    const ringStartSeq = session.ringBuffer.length > 0 ? session.ringBuffer[0].seq : session.nextSeq
    const entries =
      effectiveCursor >= ringStartSeq
        ? session.ringBuffer
            .filter((entry) => entry.seq >= effectiveCursor)
            .slice(0, request.limit)
        : await this.readEntriesFromFile(session, effectiveCursor, request.limit)

    const nextCursor =
      entries.length > 0 ? entries[entries.length - 1].seq + 1 : effectiveCursor

    return {
      session: serializeSnapshot(session),
      cursor: effectiveCursor,
      nextCursor,
      cursorAdjusted,
      entries,
    }
  }

  async deleteSession(input: unknown): Promise<DeleteLogSessionResult> {
    const request = parseDeleteLogSessionRequest(input)
    await this.removeSessionById(request.sessionId, true)
    return {
      sessionId: request.sessionId,
      removed: true,
    }
  }

  async dispose(): Promise<void> {
    clearInterval(this.cleanupTimer)
    const sessionIds = [...this.sessions.keys()]
    for (const sessionId of sessionIds) {
      await this.removeSessionById(sessionId, false)
    }
    await rm(this.baseDir, {recursive: true, force: true})
  }
}

let singleton: LogSessionManager | null = null
let cleanupHookInstalled = false

export function getLogSessionManager(): LogSessionManager {
  if (!singleton) {
    singleton = new LogSessionManager()
    if (!cleanupHookInstalled) {
      cleanupHookInstalled = true
      process.once('beforeExit', () => {
        void singleton?.dispose()
      })
    }
  }
  return singleton
}

export const __internal = {
  parseCreateLogSessionRequest,
  parseReadLogSessionRequest,
  parseDeleteLogSessionRequest,
}
