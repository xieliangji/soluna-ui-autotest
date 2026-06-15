import type {Request, Response} from 'express'
import {listConnectedDevices, lookupDeviceByUdid} from '../services/device-service'
import type {CommandRunner} from '../cli/exec'

export async function handleGetDeviceInfo(
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

  const result = await lookupDeviceByUdid(udid, runner)
  if (!result.found || !result.device) {
    res.status(404).json({
      value: {
        exists: false,
        message: `Device '${udid}' not found on this host`,
      },
    })
    return
  }

  res.status(200).json({
    value: {
      exists: true,
      device: result.device,
    },
  })
}

export async function handleListDevices(
  _req: Request,
  res: Response,
  runner?: CommandRunner
): Promise<void> {
  void _req
  const devices = await listConnectedDevices(runner)
  res.status(200).json({
    value: {
      count: devices.length,
      devices,
    },
  })
}
