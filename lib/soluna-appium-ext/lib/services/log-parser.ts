import type {Platform} from '../types/device'
import type {LogLineSource, UnifiedLogEntry} from '../types/log'

interface ParseLineContext {
  platform: Platform
  udid: string
  seq: number
  source: LogLineSource
  line: string
}

function parseAndroidLine(context: ParseLineContext): UnifiedLogEntry {
  const ts = new Date().toISOString()
  const match = context.line.match(
    /^([A-Z])\/([^(\s]+)\(\s*(\d+)\):\s?(.*)$/
  )
  if (!match) {
    return {
      seq: context.seq,
      ts,
      platform: context.platform,
      udid: context.udid,
      source: context.source,
      message: context.line,
      raw: context.line,
    }
  }

  const levelMap: Record<string, string> = {
    V: 'verbose',
    D: 'debug',
    I: 'info',
    W: 'warn',
    E: 'error',
    F: 'fatal',
  }

  return {
    seq: context.seq,
    ts,
    platform: context.platform,
    udid: context.udid,
    source: context.source,
    level: levelMap[match[1]] ?? match[1].toLowerCase(),
    tag: match[2],
    pid: Number.parseInt(match[3], 10),
    message: match[4] ?? '',
    raw: context.line,
  }
}

function parseIosLine(context: ParseLineContext): UnifiedLogEntry {
  const ts = new Date().toISOString()
  const line = context.line
  const match = line.match(
    /^[A-Za-z]{3}\s+\d+\s+\d{2}:\d{2}:\d{2}\s+([^\s]+)\s+([^[]+)\[(\d+)]\s+<([^>]+)>:\s?(.*)$/
  )
  if (!match) {
    return {
      seq: context.seq,
      ts,
      platform: context.platform,
      udid: context.udid,
      source: context.source,
      message: line,
      raw: line,
    }
  }

  return {
    seq: context.seq,
    ts,
    platform: context.platform,
    udid: context.udid,
    source: context.source,
    process: match[2].trim(),
    pid: Number.parseInt(match[3], 10),
    level: match[4].trim().toLowerCase(),
    message: match[5] ?? '',
    raw: line,
  }
}

export function parseUnifiedLogLine(context: ParseLineContext): UnifiedLogEntry {
  if (context.platform === 'android') {
    return parseAndroidLine(context)
  }
  return parseIosLine(context)
}
