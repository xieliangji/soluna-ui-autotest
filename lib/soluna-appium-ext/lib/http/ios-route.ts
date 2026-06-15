import type {Request, Response} from 'express'
import {findWdaBundleByUdid} from '../services/ios'
import type {CommandRunner} from '../cli/exec'

export async function handleGetWdaBundle(
  req: Request,
  res: Response,
  runner?: CommandRunner
): Promise<void> {
  const udid = typeof req.query.udid === 'string' ? req.query.udid.trim() : ''
  if (!udid) {
    res.status(400).json({
      value: {
        error: 'invalid_argument',
        message: 'Missing required query parameter: udid',
      },
    })
    return
  }

  try {
    const result = await findWdaBundleByUdid(udid, runner)
    if (!result.exists) {
      res.status(404).json({
        value: result,
      })
      return
    }

    res.status(200).json({
      value: result,
    })
  } catch (err) {
    res.status(500).json({
      value: {
        error: 'wda_bundle_lookup_failed',
        message: err instanceof Error ? err.message : String(err),
      },
    })
  }
}
