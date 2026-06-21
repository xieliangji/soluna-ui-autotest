import type {Request, Response} from 'express'
import {lookupAppById} from '../services/app-service'
import type {CommandRunner} from '../cli/exec'

export async function handleGetAppInfo(
  req: Request,
  res: Response,
  runner?: CommandRunner
): Promise<void> {
  const udid = typeof req.query.udid === 'string' ? req.query.udid.trim() : ''
  const appId = typeof req.query.appId === 'string' ? req.query.appId.trim() : ''
  if (!udid || !appId) {
    res.status(400).json({
      value: {
        error: 'invalid_argument',
        message: 'Missing required query parameters: udid and appId',
      },
    })
    return
  }

  const result = await lookupAppById(udid, appId, runner)
  if (!result.found || !result.app) {
    res.status(404).json({
      value: {
        exists: false,
        message: result.message ?? `App '${appId}' was not found on device '${udid}'`,
      },
    })
    return
  }

  res.status(200).json({
    value: {
      exists: true,
      app: result.app,
    },
  })
}
