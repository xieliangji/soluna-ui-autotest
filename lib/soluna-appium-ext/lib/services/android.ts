import {runCommand, type CommandRunner} from '../cli/exec'
import type {UnifiedDeviceInfo} from '../types/device'

interface AdbDeviceRecord {
  serial: string
  state: string
}

function parseAdbDevices(output: string): AdbDeviceRecord[] {
  return output
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith('List of devices attached'))
    .map((line) => line.split(/\s+/))
    .filter((parts) => parts.length >= 2)
    .map((parts) => ({serial: parts[0], state: parts[1]}))
}

function parseAndroidProps(output: string): Record<string, string> {
  const props: Record<string, string> = {}
  for (const line of output.split(/\r?\n/)) {
    const match = line.match(/^\[([^\]]+)]:\s*\[(.*)]$/)
    if (!match) {
      continue
    }
    props[match[1]] = match[2]
  }
  return props
}

function toUnifiedAndroidDeviceInfo(udid: string, props: Record<string, string>): UnifiedDeviceInfo {
  return {
    platform: 'android',
    udid,
    name: props['ro.product.model'] || props['ro.product.name'] || 'Android Device',
    model: props['ro.product.model'] || 'Unknown',
    osVersion: props['ro.build.version.release'] || 'Unknown',
  }
}

async function fetchAndroidDeviceInfo(
  udid: string,
  runner: CommandRunner
): Promise<UnifiedDeviceInfo> {
  const propsOutput = await runner('adb', ['-s', udid, 'shell', 'getprop'])
  const props = parseAndroidProps(propsOutput.stdout)
  return toUnifiedAndroidDeviceInfo(udid, props)
}

export async function findAndroidDeviceByUdid(
  udid: string,
  runner: CommandRunner = runCommand
): Promise<UnifiedDeviceInfo | null> {
  let listOutput: string
  try {
    const result = await runner('adb', ['devices'])
    listOutput = result.stdout
  } catch {
    return null
  }

  const devices = parseAdbDevices(listOutput)
  const target = devices.find((item) => item.serial === udid && item.state === 'device')
  if (!target) {
    return null
  }

  return await fetchAndroidDeviceInfo(udid, runner)
}

export async function listAndroidDevices(
  runner: CommandRunner = runCommand
): Promise<UnifiedDeviceInfo[]> {
  let listOutput: string
  try {
    const result = await runner('adb', ['devices'])
    listOutput = result.stdout
  } catch {
    return []
  }

  const connectedDevices = parseAdbDevices(listOutput).filter((item) => item.state === 'device')
  return await Promise.all(
    connectedDevices.map(async ({serial}) => {
      try {
        return await fetchAndroidDeviceInfo(serial, runner)
      } catch {
        return {
          platform: 'android',
          udid: serial,
          name: 'Android Device',
          model: 'Unknown',
          osVersion: 'Unknown',
        }
      }
    })
  )
}

export const __internal = {
  parseAdbDevices,
  parseAndroidProps,
}
