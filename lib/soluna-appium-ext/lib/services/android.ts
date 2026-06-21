import {runCommand, type CommandRunner} from '../cli/exec'
import {getCommandLookupCommand} from '../cli/preflight'
import {mkdtemp, rm, readdir, access} from 'node:fs/promises'
import {join} from 'node:path'
import {tmpdir} from 'node:os'
import type {InstalledAppInfo} from '../types/app'
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

function parsePackagePath(output: string): string | null {
  for (const rawLine of output.split(/\r?\n/)) {
    const line = rawLine.trim()
    if (line.startsWith('package:') && line.endsWith('.apk')) {
      return line.substring('package:'.length)
    }
  }
  return null
}

function parseAndroidBadgingOutput(output: string): {
  name?: string
  version?: string
  versionCode?: string
} {
  const packageMatch = output.match(/^package:\s+.*\bversionCode='([^']*)'.*\bversionName='([^']*)'/m)
  const explicitLabel = output.match(/^application-label(?:-[^:]+)?:'([^']*)'/m)
  const applicationLabel = output.match(/^application:\s+.*\blabel='([^']*)'/m)
  const name = explicitLabel?.[1] || applicationLabel?.[1]
  return {
    name: name?.trim() || undefined,
    versionCode: packageMatch?.[1]?.trim() || undefined,
    version: packageMatch?.[2]?.trim() || undefined,
  }
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

async function exists(path: string): Promise<boolean> {
  try {
    await access(path)
    return true
  } catch {
    return false
  }
}

async function resolveAaptCommand(
  runner: CommandRunner,
  env: NodeJS.ProcessEnv = process.env
): Promise<string | null> {
  const roots = [env.ANDROID_HOME, env.ANDROID_SDK_ROOT].filter((item): item is string => Boolean(item))
  const candidates: string[] = []
  for (const root of roots) {
    const buildToolsDir = join(root, 'build-tools')
    try {
      const versions = await readdir(buildToolsDir)
      versions
        .sort((left, right) => right.localeCompare(left, undefined, {numeric: true}))
        .forEach((version) => candidates.push(join(buildToolsDir, version, 'aapt')))
    } catch {
      // Continue to PATH lookup below.
    }
  }

  for (const candidate of candidates) {
    if (await exists(candidate)) {
      return candidate
    }
  }

  try {
    const lookup = await runner(getCommandLookupCommand(), ['aapt'])
    return lookup.stdout.split(/\r?\n/)[0]?.trim() || 'aapt'
  } catch {
    return null
  }
}

export async function findAndroidInstalledAppById(
  udid: string,
  appId: string,
  runner: CommandRunner = runCommand,
  options: {
    aaptCommand?: string | null
    env?: NodeJS.ProcessEnv
  } = {}
): Promise<InstalledAppInfo | null> {
  let apkPath: string | null
  try {
    const pathResult = await runner('adb', ['-s', udid, 'shell', 'pm', 'path', appId])
    apkPath = parsePackagePath(pathResult.stdout)
  } catch {
    return null
  }
  if (!apkPath) {
    return null
  }

  const aaptCommand = options.aaptCommand === undefined
    ? await resolveAaptCommand(runner, options.env)
    : options.aaptCommand
  if (!aaptCommand) {
    return {
      platform: 'android',
      udid,
      appId,
      source: apkPath,
    }
  }

  const tempDir = await mkdtemp(join(tmpdir(), 'soluna-app-'))
  const localApk = join(tempDir, 'base.apk')
  try {
    await runner('adb', ['-s', udid, 'pull', apkPath, localApk])
    const badging = await runner(aaptCommand, ['dump', 'badging', localApk])
    const parsed = parseAndroidBadgingOutput(badging.stdout)
    return {
      platform: 'android',
      udid,
      appId,
      name: parsed.name,
      version: parsed.version,
      versionCode: parsed.versionCode,
      source: apkPath,
    }
  } catch {
    return {
      platform: 'android',
      udid,
      appId,
      source: apkPath,
    }
  } finally {
    await rm(tempDir, {recursive: true, force: true})
  }
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
  parsePackagePath,
  parseAndroidBadgingOutput,
}
