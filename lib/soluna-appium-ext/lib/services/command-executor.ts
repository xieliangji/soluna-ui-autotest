import {spawn} from 'node:child_process'
import type {CommandExecuteRequest, CommandExecuteResult, SupportedTool} from '../types/command'

const DEFAULT_TIMEOUT_MS = 5000
const MAX_TIMEOUT_MS = 60000
const DEFAULT_MAX_OUTPUT_BYTES = 64 * 1024
const MAX_OUTPUT_BYTES = 2 * 1024 * 1024
const KILL_GRACE_MS = 500
const HARD_STOP_EXTRA_MS = 2000
const ALLOWED_TOOLS = new Set<SupportedTool>(['adb', 'go-ios', 'ios'])

interface CommandProcessEvents {
  on(event: 'close', listener: (code: number | null) => void): this
  once(event: 'error', listener: (err: NodeJS.ErrnoException) => void): this
}

export interface SpawnedCommandProcess extends CommandProcessEvents {
  stdout: NodeJS.ReadableStream | null
  stderr: NodeJS.ReadableStream | null
  kill(signal?: NodeJS.Signals): boolean
}

export type SpawnCommandProcess = (command: string, args: string[]) => SpawnedCommandProcess

export interface CommandExecutorOptions {
  spawnProcess?: SpawnCommandProcess
  now?: () => number
}

function defaultSpawnProcess(command: string, args: string[]): SpawnedCommandProcess {
  return spawn(command, args, {
    stdio: ['ignore', 'pipe', 'pipe'],
  })
}

function clampNumber(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

function normalizeBuffer(data: string | Buffer): Buffer<ArrayBufferLike> {
  return Buffer.isBuffer(data) ? data : Buffer.from(data)
}

function appendWithLimit(
  current: Buffer<ArrayBufferLike>,
  chunk: Buffer<ArrayBufferLike>,
  maxBytes: number
): {next: Buffer<ArrayBufferLike>; overflowed: boolean} {
  const combined = Buffer.concat([current, chunk])
  if (combined.length <= maxBytes) {
    return {next: combined, overflowed: false}
  }
  return {next: combined.subarray(0, maxBytes), overflowed: true}
}

function isCommandNotFound(err: unknown): boolean {
  if (!err || typeof err !== 'object') {
    return false
  }
  return (err as NodeJS.ErrnoException).code === 'ENOENT'
}

function getToolCandidates(tool: SupportedTool): string[] {
  if (tool === 'adb') {
    return ['adb']
  }
  if (tool === 'go-ios') {
    return ['go-ios', 'ios']
  }
  return ['ios', 'go-ios']
}

function normalizeRequest(input: CommandExecuteRequest): Required<CommandExecuteRequest> {
  return {
    tool: input.tool,
    args: input.args ?? [],
    timeoutMs: input.timeoutMs ?? DEFAULT_TIMEOUT_MS,
    maxOutputBytes: input.maxOutputBytes ?? DEFAULT_MAX_OUTPUT_BYTES,
  }
}

async function runSingleCommand(
  command: string,
  args: string[],
  timeoutMs: number,
  maxOutputBytes: number,
  options: CommandExecutorOptions
): Promise<CommandExecuteResult> {
  const spawnProcess = options.spawnProcess ?? defaultSpawnProcess
  const now = options.now ?? Date.now

  return await new Promise<CommandExecuteResult>((resolve, reject) => {
    const startedAt = now()
    let stdoutBuffer: Buffer<ArrayBufferLike> = Buffer.alloc(0)
    let stderrBuffer: Buffer<ArrayBufferLike> = Buffer.alloc(0)
    let truncated = false
    let timedOut = false
    let exitCode: number | null = null

    let closed = false
    let finished = false

    let softTimeout: NodeJS.Timeout | undefined
    let killTimeout: NodeJS.Timeout | undefined
    let hardTimeout: NodeJS.Timeout | undefined

    const child = spawnProcess(command, args)

    const cleanupTimers = (): void => {
      if (softTimeout) {
        clearTimeout(softTimeout)
      }
      if (killTimeout) {
        clearTimeout(killTimeout)
      }
      if (hardTimeout) {
        clearTimeout(hardTimeout)
      }
    }

    const complete = (): void => {
      if (finished) {
        return
      }
      finished = true
      cleanupTimers()
      resolve({
        command,
        args,
        exitCode,
        timedOut,
        truncated,
        durationMs: now() - startedAt,
        stdout: stdoutBuffer.toString('utf8'),
        stderr: stderrBuffer.toString('utf8'),
      })
    }

    const fail = (err: NodeJS.ErrnoException): void => {
      if (finished) {
        return
      }
      finished = true
      cleanupTimers()
      reject(err)
    }

    child.once('error', fail)

    child.on('close', (code) => {
      closed = true
      exitCode = code
      complete()
    })

    child.stdout?.on('data', (chunk: string | Buffer) => {
      const appended = appendWithLimit(stdoutBuffer, normalizeBuffer(chunk), maxOutputBytes)
      stdoutBuffer = appended.next
      truncated = truncated || appended.overflowed
    })

    child.stderr?.on('data', (chunk: string | Buffer) => {
      const appended = appendWithLimit(stderrBuffer, normalizeBuffer(chunk), maxOutputBytes)
      stderrBuffer = appended.next
      truncated = truncated || appended.overflowed
    })

    softTimeout = setTimeout(() => {
      if (closed || finished) {
        return
      }
      timedOut = true
      child.kill('SIGTERM')

      killTimeout = setTimeout(() => {
        if (!closed && !finished) {
          child.kill('SIGKILL')
        }
      }, KILL_GRACE_MS)
    }, timeoutMs)

    hardTimeout = setTimeout(() => {
      if (closed || finished) {
        return
      }
      timedOut = true
      child.kill('SIGKILL')
      complete()
    }, timeoutMs + KILL_GRACE_MS + HARD_STOP_EXTRA_MS)
  })
}

export function validateCommandRequest(input: unknown): Required<CommandExecuteRequest> {
  if (!input || typeof input !== 'object') {
    throw new Error('Request body must be a JSON object')
  }

  const body = input as Partial<CommandExecuteRequest>
  if (!body.tool || !ALLOWED_TOOLS.has(body.tool)) {
    throw new Error("'tool' must be one of: adb, go-ios, ios")
  }

  const args = body.args ?? []
  if (!Array.isArray(args) || args.some((item) => typeof item !== 'string')) {
    throw new Error("'args' must be an array of strings")
  }

  const timeoutMsRaw = body.timeoutMs ?? DEFAULT_TIMEOUT_MS
  if (!Number.isFinite(timeoutMsRaw)) {
    throw new Error("'timeoutMs' must be a finite number")
  }

  const maxOutputBytesRaw = body.maxOutputBytes ?? DEFAULT_MAX_OUTPUT_BYTES
  if (!Number.isFinite(maxOutputBytesRaw)) {
    throw new Error("'maxOutputBytes' must be a finite number")
  }

  return normalizeRequest({
    tool: body.tool,
    args,
    timeoutMs: clampNumber(Math.floor(timeoutMsRaw), 100, MAX_TIMEOUT_MS),
    maxOutputBytes: clampNumber(Math.floor(maxOutputBytesRaw), 1024, MAX_OUTPUT_BYTES),
  })
}

export async function executeSupportedCommand(
  input: CommandExecuteRequest,
  options: CommandExecutorOptions = {}
): Promise<CommandExecuteResult> {
  const request = normalizeRequest(input)
  const candidates = getToolCandidates(request.tool)

  let lastNotFoundError: unknown

  for (const candidate of candidates) {
    try {
      return await runSingleCommand(
        candidate,
        request.args,
        request.timeoutMs,
        request.maxOutputBytes,
        options
      )
    } catch (err) {
      if (isCommandNotFound(err) && candidates.length > 1) {
        lastNotFoundError = err
        continue
      }
      throw err
    }
  }

  if (request.tool === 'adb') {
    throw new Error("'adb' command is not available on this host")
  }

  if (lastNotFoundError) {
    throw new Error("Neither 'go-ios' nor 'ios' command is available on this host")
  }

  throw new Error('Failed to execute requested command')
}
