import type {Platform} from './device'

export type LogSessionStatus = 'running' | 'stopped' | 'failed'
export type LogLineSource = 'stdout' | 'stderr'

export interface UnifiedLogEntry {
  seq: number
  ts: string
  platform: Platform
  udid: string
  source: LogLineSource
  level?: string
  tag?: string
  process?: string
  pid?: number
  message: string
  raw: string
}

export interface CreateLogSessionRequest {
  udid: string
  maxBufferEntries?: number
  maxSessionBytes?: number
  ttlMs?: number
}

export interface ReadLogSessionRequest {
  sessionId: string
  cursor?: number
  limit?: number
}

export interface DeleteLogSessionRequest {
  sessionId: string
}

export interface LogSessionSnapshot {
  sessionId: string
  udid: string
  platform: Platform
  status: LogSessionStatus
  command: string
  args: string[]
  startedAt: string
  endedAt?: string
  lastActivityAt: string
  ttlMs: number
  nextSeq: number
  minSeq: number
  droppedCount: number
  maxBufferEntries: number
  maxSessionBytes: number
  error?: string
}

export interface CreateLogSessionResult {
  session: LogSessionSnapshot
}

export interface ReadLogSessionResult {
  session: LogSessionSnapshot
  cursor: number
  nextCursor: number
  cursorAdjusted: boolean
  entries: UnifiedLogEntry[]
}

export interface DeleteLogSessionResult {
  sessionId: string
  removed: true
}
