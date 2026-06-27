import {runCommand, type CommandRunner} from '../cli/exec'
import {resolveIosCommand} from '../cli/preflight'
import type {InstalledAppInfo} from '../types/app'
import type {UnifiedDeviceInfo} from '../types/device'
import type {IosInstalledApplication, WdaBundleLookupResult} from '../types/ios'

interface GoIosDeviceDetails {
  udid: string
  DeviceName?: string
  ProductName?: string
  ProductType?: string
  ProductVersion?: string
}

interface GoIosListResponse {
  deviceList?: GoIosDeviceDetails[]
}

type RawRecord = Record<string, unknown>

const WDA_BUNDLE_HINT = 'webdriveragent'
const WDA_RUNNER_HINT = 'webdriveragentrunner'

function getStringValue(raw: RawRecord, candidateKeys: string[]): string | undefined {
  for (const key of candidateKeys) {
    const value = raw[key]
    if (typeof value === 'string' && value.trim()) {
      return value.trim()
    }
  }

  const lowerCaseMap = new Map<string, unknown>()
  for (const [key, value] of Object.entries(raw)) {
    lowerCaseMap.set(key.toLowerCase(), value)
  }

  for (const key of candidateKeys) {
    const value = lowerCaseMap.get(key.toLowerCase())
    if (typeof value === 'string' && value.trim()) {
      return value.trim()
    }
  }

  return undefined
}

function normalizeDeviceDetail(raw: unknown): GoIosDeviceDetails | null {
  if (typeof raw === 'string' && raw.trim()) {
    return {udid: raw.trim()}
  }

  if (!raw || typeof raw !== 'object') {
    return null
  }

  const record = raw as RawRecord
  const udid = getStringValue(record, ['udid', 'Udid', 'UDID'])
  if (!udid) {
    return null
  }

  return {
    udid,
    DeviceName: getStringValue(record, ['DeviceName', 'deviceName', 'devicename']),
    ProductName: getStringValue(record, ['ProductName', 'productName']),
    ProductType: getStringValue(record, ['ProductType', 'productType']),
    ProductVersion: getStringValue(record, ['ProductVersion', 'productVersion']),
  }
}

function parseIosDeviceNameOutput(output: string): string | undefined {
  for (const rawLine of output.split(/\r?\n/)) {
    const line = rawLine.trim()
    if (!line) {
      continue
    }
    try {
      const parsed = JSON.parse(line)
      if (parsed && typeof parsed === 'object') {
        const name = getStringValue(parsed as RawRecord, ['devicename', 'deviceName', 'DeviceName', 'name'])
        if (name) {
          return name
        }
      }
    } catch {
      return line
    }
  }
  return undefined
}

function parseGoIosListOutput(output: string): GoIosListResponse {
  let parsed: unknown
  try {
    parsed = JSON.parse(output)
  } catch {
    return {}
  }

  let rawList: unknown[] = []
  if (Array.isArray(parsed)) {
    rawList = parsed
  } else if (parsed && typeof parsed === 'object' && Array.isArray((parsed as {deviceList?: unknown[]}).deviceList)) {
    rawList = (parsed as {deviceList: unknown[]}).deviceList
  }

  const deviceList = rawList
    .map((item) => normalizeDeviceDetail(item))
    .filter((item): item is GoIosDeviceDetails => item !== null)

  return {deviceList}
}

function normalizeInstalledApplication(raw: unknown): IosInstalledApplication | null {
  if (!raw || typeof raw !== 'object') {
    return null
  }

  const record = raw as RawRecord
  const bundleId = getStringValue(record, ['CFBundleIdentifier', 'bundleId', 'BundleID'])
  if (!bundleId) {
    return null
  }

  return {
    bundleId,
    name: getStringValue(record, ['CFBundleDisplayName', 'CFBundleName', 'name']),
    version: getStringValue(record, ['CFBundleShortVersionString', 'CFBundleVersion', 'version']),
    executable: getStringValue(record, ['CFBundleExecutable', 'executable']),
    applicationType: getStringValue(record, ['ApplicationType', 'applicationType']),
  }
}

function parseIosAppsJsonOutput(output: string): IosInstalledApplication[] {
  let parsed: unknown
  try {
    parsed = JSON.parse(output)
  } catch {
    return []
  }

  const rawList = Array.isArray(parsed) ? parsed : []
  return rawList
    .map((item) => normalizeInstalledApplication(item))
    .filter((item): item is IosInstalledApplication => item !== null)
}

function parseIosAppsListOutput(output: string): IosInstalledApplication[] {
  const apps: IosInstalledApplication[] = []
  for (const rawLine of output.split(/\r?\n/)) {
    const line = rawLine.trim()
    if (!line || line.startsWith('{')) {
      continue
    }
    const match = /^([A-Za-z0-9][A-Za-z0-9._-]+)(?:\s+(.*))?$/.exec(line)
    if (!match) {
      continue
    }
    const bundleId = match[1]
    const rest = match[2]
    const parts = rest?.trim().split(/\s+/).filter(Boolean) ?? []
    const version = parts.length > 1 ? parts[parts.length - 1] : undefined
    const name = parts.length > 1 ? parts.slice(0, -1).join(' ') : rest?.trim()
    apps.push({
      bundleId,
      name: name || undefined,
      version,
    })
  }
  return apps
}

function parseIosAppsOutput(output: string): IosInstalledApplication[] {
  const trimmed = output.trim()
  if (!trimmed) {
    return []
  }
  if (trimmed.startsWith('[')) {
    return parseIosAppsJsonOutput(trimmed)
  }
  return parseIosAppsListOutput(trimmed)
}

function wdaCandidateScore(app: IosInstalledApplication): number {
  const bundle = app.bundleId.toLowerCase()
  const name = app.name?.toLowerCase() ?? ''
  const executable = app.executable?.toLowerCase() ?? ''
  const haystack = `${bundle} ${name} ${executable}`

  if (!haystack.includes(WDA_BUNDLE_HINT)) {
    return 0
  }
  let score = 10
  if (bundle.includes(WDA_RUNNER_HINT)) {
    score += 30
  }
  if (bundle.endsWith('.xctrunner')) {
    score += 30
  }
  if (name.includes(WDA_RUNNER_HINT) || executable.includes(WDA_RUNNER_HINT)) {
    score += 20
  }
  return score
}

function findWdaInstalledApplication(apps: IosInstalledApplication[]): IosInstalledApplication | null {
  return apps
    .map((app) => ({app, score: wdaCandidateScore(app)}))
    .filter((item) => item.score > 0)
    .sort((left, right) => right.score - left.score || left.app.bundleId.localeCompare(right.app.bundleId))[0]?.app ?? null
}

function toUnifiedIosDeviceInfo(device: GoIosDeviceDetails): UnifiedDeviceInfo {
  return {
    platform: 'ios',
    udid: device.udid,
    name: device.DeviceName || device.ProductName || 'iOS Device',
    model: device.ProductType || 'Unknown',
    osVersion: device.ProductVersion || 'Unknown',
  }
}

async function fetchGoIosDeviceList(runner: CommandRunner): Promise<GoIosDeviceDetails[]> {
  const iosCmd = await resolveIosCommand(runner)
  if (!iosCmd) {
    return []
  }

  let detailsOutput: string
  try {
    const result = await runner(iosCmd, ['list', '--details'])
    detailsOutput = result.stdout
  } catch {
    return []
  }

  const parsed = parseGoIosListOutput(detailsOutput)
  return parsed.deviceList ?? []
}

async function fetchIosDeviceName(
  udid: string,
  runner: CommandRunner
): Promise<string | undefined> {
  const iosCmd = await resolveIosCommand(runner)
  if (!iosCmd) {
    return undefined
  }

  try {
    const result = await runner(iosCmd, [`--udid=${udid}`, 'devicename'])
    return parseIosDeviceNameOutput(result.stdout)
  } catch {
    return undefined
  }
}

async function enrichIosDeviceName(
  device: GoIosDeviceDetails,
  runner: CommandRunner
): Promise<GoIosDeviceDetails> {
  const deviceName = await fetchIosDeviceName(device.udid, runner)
  return {
    ...device,
    DeviceName: deviceName || device.DeviceName,
  }
}

export async function findIosDeviceByUdid(
  udid: string,
  runner: CommandRunner = runCommand
): Promise<UnifiedDeviceInfo | null> {
  const list = await fetchGoIosDeviceList(runner)
  const targetUdid = udid.trim().toLowerCase()
  const candidate = list.find((item) => item.udid.toLowerCase() === targetUdid)
  if (!candidate) {
    return null
  }

  return toUnifiedIosDeviceInfo(await enrichIosDeviceName(candidate, runner))
}

export async function listIosDevices(runner: CommandRunner = runCommand): Promise<UnifiedDeviceInfo[]> {
  const list = await fetchGoIosDeviceList(runner)
  const enriched = await Promise.all(list.map((device) => enrichIosDeviceName(device, runner)))
  return enriched.map(toUnifiedIosDeviceInfo)
}

export async function listIosInstalledApps(
  udid: string,
  runner: CommandRunner = runCommand
): Promise<IosInstalledApplication[]> {
  const iosCmd = await resolveIosCommand(runner)
  if (!iosCmd) {
    throw new Error("Neither 'go-ios' nor 'ios' command is available on this host")
  }

  const result = await runner(iosCmd, [`--udid=${udid}`, 'apps', '--all', '--list'])
  return parseIosAppsOutput(result.stdout)
}

export async function findIosInstalledAppById(
  udid: string,
  appId: string,
  runner: CommandRunner = runCommand
): Promise<InstalledAppInfo | null> {
  const apps = await listIosInstalledApps(udid, runner)
  const app = apps.find((item) => item.bundleId === appId)
  if (!app) {
    return null
  }

  return {
    platform: 'ios',
    udid,
    appId,
    name: app.name,
    version: app.version,
  }
}

export async function findWdaBundleByUdid(
  udid: string,
  runner: CommandRunner = runCommand
): Promise<WdaBundleLookupResult> {
  const apps = await listIosInstalledApps(udid, runner)
  const app = findWdaInstalledApplication(apps)
  if (!app) {
    return {
      exists: false,
      udid,
      message: `WDA runner bundle was not found on device '${udid}'`,
    }
  }

  return {
    exists: true,
    udid,
    bundleId: app.bundleId,
    app,
  }
}

export const __internal = {
  parseGoIosListOutput,
  parseIosDeviceNameOutput,
  parseIosAppsOutput,
  findWdaInstalledApplication,
}
