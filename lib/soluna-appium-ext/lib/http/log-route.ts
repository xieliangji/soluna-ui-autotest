import type {Request, Response} from 'express'
import {
  getLogSessionManager,
  LogSessionHttpError,
  type LogSessionService,
} from '../services/log-session-manager'

function respondError(res: Response, err: unknown): void {
  if (err instanceof LogSessionHttpError) {
    res.status(err.status).json({
      value: {
        error: err.code,
        message: err.message,
      },
    })
    return
  }

  res.status(500).json({
    value: {
      error: 'log_session_failed',
      message: err instanceof Error ? err.message : String(err),
    },
  })
}

export async function handleCreateLogSession(
  req: Request,
  res: Response,
  service: LogSessionService = getLogSessionManager()
): Promise<void> {
  try {
    const result = await service.createSession(req.body)
    res.status(201).json({
      value: result,
    })
  } catch (err) {
    respondError(res, err)
  }
}

export async function handleReadLogSession(
  req: Request,
  res: Response,
  service: LogSessionService = getLogSessionManager()
): Promise<void> {
  try {
    const result = await service.readSession({
      sessionId: req.params.sessionId,
      cursor: req.query.cursor,
      limit: req.query.limit,
    })
    res.status(200).json({
      value: result,
    })
  } catch (err) {
    respondError(res, err)
  }
}

export async function handleDeleteLogSession(
  req: Request,
  res: Response,
  service: LogSessionService = getLogSessionManager()
): Promise<void> {
  try {
    const result = await service.deleteSession({
      sessionId: req.params.sessionId,
    })
    res.status(200).json({
      value: result,
    })
  } catch (err) {
    respondError(res, err)
  }
}
