import {runCommand, type CommandRunner} from '../cli/exec'
import {findAndroidDeviceByUdid, findAndroidInstalledAppById} from './android'
import {findIosDeviceByUdid, findIosInstalledAppById} from './ios'
import type {AppLookupResult} from '../types/app'

export async function lookupAppById(
  udid: string,
  appId: string,
  runner: CommandRunner = runCommand
): Promise<AppLookupResult> {
  const android = await findAndroidDeviceByUdid(udid, runner)
  if (android) {
    const app = await findAndroidInstalledAppById(udid, appId, runner)
    return app
      ? {found: true, app}
      : {found: false, message: `Android app '${appId}' was not found on device '${udid}'`}
  }

  const ios = await findIosDeviceByUdid(udid, runner)
  if (ios) {
    const app = await findIosInstalledAppById(udid, appId, runner)
    return app
      ? {found: true, app}
      : {found: false, message: `iOS app '${appId}' was not found on device '${udid}'`}
  }

  return {
    found: false,
    message: `Device '${udid}' not found on this host`,
  }
}
