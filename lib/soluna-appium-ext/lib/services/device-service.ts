import {runCommand, type CommandRunner} from '../cli/exec'
import {findAndroidDeviceByUdid} from './android'
import {findIosDeviceByUdid} from './ios'
import {listAndroidDevices} from './android'
import {listIosDevices} from './ios'
import type {DeviceLookupResult, UnifiedDeviceInfo} from '../types/device'

export async function lookupDeviceByUdid(
  udid: string,
  runner: CommandRunner = runCommand
): Promise<DeviceLookupResult> {
  const android = await findAndroidDeviceByUdid(udid, runner)
  if (android) {
    return {found: true, device: android}
  }

  const ios = await findIosDeviceByUdid(udid, runner)
  if (ios) {
    return {found: true, device: ios}
  }

  return {found: false}
}

export async function listConnectedDevices(
  runner: CommandRunner = runCommand
): Promise<UnifiedDeviceInfo[]> {
  const [androidDevices, iosDevices] = await Promise.all([
    listAndroidDevices(runner),
    listIosDevices(runner),
  ])

  return [...androidDevices, ...iosDevices]
}
