import type {Request, Response} from 'express'
import {executeSupportedCommand, validateCommandRequest} from '../services/command-executor'
import {log, type SolunaLogger} from '../logger'

interface CommandRouteDependencies {
  logger?: SolunaLogger
  executeCommand?: typeof executeSupportedCommand
  validateRequest?: typeof validateCommandRequest
}

const DEBUG_LOG_PREVIEW_MAX = 1000

function buildCommandSummary(tool: string, args: string[], timeoutMs: number, maxOutputBytes: number): string {
  return `tool=${tool}, args=${JSON.stringify(args)}, timeoutMs=${timeoutMs}, maxOutputBytes=${maxOutputBytes}`
}

function buildResultSummary(
  command: string,
  exitCode: number | null,
  durationMs: number,
  timedOut: boolean,
  truncated: boolean
): string {
  return `command=${command}, exitCode=${String(exitCode)}, durationMs=${durationMs}, timedOut=${timedOut}, truncated=${truncated}`
}

function toPreview(value: string): string {
  if (value.length <= DEBUG_LOG_PREVIEW_MAX) {
    return value
  }
  return `${value.slice(0, DEBUG_LOG_PREVIEW_MAX)}\n...<truncated for debug log>`
}

export async function handleExecuteCommand(
  req: Request,
  res: Response,
  dependencies: CommandRouteDependencies = {}
): Promise<void> {
  const logger = dependencies.logger ?? log
  const executeCommand = dependencies.executeCommand ?? executeSupportedCommand
  const validateRequest = dependencies.validateRequest ?? validateCommandRequest

  let normalized
  try {
    normalized = validateRequest(req.body)
  } catch (err) {
    logger.warn(
      `Command request validation failed: ${err instanceof Error ? err.message : String(err)}`
    )
    res.status(400).json({
      value: {
        error: 'invalid_argument',
        message: err instanceof Error ? err.message : String(err),
      },
    })
    return
  }

  try {
    logger.info(
      `Executing command request: ${buildCommandSummary(normalized.tool, normalized.args, normalized.timeoutMs, normalized.maxOutputBytes)}`
    )
    const result = await executeCommand(normalized)
    const status = result.exitCode === 0 ? 200 : 422
    logger.info(
      `Command execution finished: ${buildResultSummary(result.command, result.exitCode, result.durationMs, result.timedOut, result.truncated)}`
    )
    if (result.stdout) {
      logger.debug(
        `Command stdout (${result.command}, ${result.stdout.length} chars):\n${toPreview(result.stdout)}`
      )
    }
    if (result.stderr) {
      logger.debug(
        `Command stderr (${result.command}, ${result.stderr.length} chars):\n${toPreview(result.stderr)}`
      )
    }
    res.status(status).json({
      value: {
        ok: result.exitCode === 0,
        ...result,
      },
    })
  } catch (err) {
    logger.error(`Command execution failed: ${err instanceof Error ? err.message : String(err)}`)
    res.status(500).json({
      value: {
        error: 'command_execution_failed',
        message: err instanceof Error ? err.message : String(err),
      },
    })
  }
}
