import appiumLog, {type Logger as AppiumRawLogger} from '@appium/logger'

export interface SolunaLogger {
  info(...args: unknown[]): void
  debug(...args: unknown[]): void
  warn(...args: unknown[]): void
  error(...args: unknown[]): void
}

type LogLevel = 'info' | 'debug' | 'warn' | 'error'
type RawLogMethod = (prefix: string, message: unknown, ...args: unknown[]) => void

const LOG_PREFIX = 'soluna-ext'
const rawLogger: AppiumRawLogger = appiumLog

function write(level: LogLevel, args: unknown[]): void {
  const method = rawLogger[level] as RawLogMethod
  if (args.length === 0) {
    method.call(rawLogger, LOG_PREFIX, '')
    return
  }
  method.call(rawLogger, LOG_PREFIX, args[0], ...args.slice(1))
}

export const log: SolunaLogger = {
  info: (...args: unknown[]) => write('info', args),
  debug: (...args: unknown[]) => write('debug', args),
  warn: (...args: unknown[]) => write('warn', args),
  error: (...args: unknown[]) => write('error', args),
}
