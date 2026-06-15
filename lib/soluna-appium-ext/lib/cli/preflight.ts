import {runCommand, type CommandRunner} from './exec'

export function getCommandLookupCommand(platform: NodeJS.Platform = process.platform): 'which' | 'where' {
  return platform === 'win32' ? 'where' : 'which'
}

export async function isCommandAvailable(
  command: string,
  runner: CommandRunner = runCommand,
  platform: NodeJS.Platform = process.platform
): Promise<boolean> {
  const lookupCommand = getCommandLookupCommand(platform)
  try {
    await runner(lookupCommand, [command])
    return true
  } catch {
    return false
  }
}

export async function resolveIosCommand(
  runner: CommandRunner = runCommand,
  platform: NodeJS.Platform = process.platform
): Promise<string | null> {
  if (await isCommandAvailable('go-ios', runner, platform)) {
    return 'go-ios'
  }
  if (await isCommandAvailable('ios', runner, platform)) {
    return 'ios'
  }
  return null
}

export async function runPreflightChecks(
  runner: CommandRunner = runCommand,
  platform: NodeJS.Platform = process.platform
): Promise<void> {
  const missing: string[] = []

  if (!(await isCommandAvailable('adb', runner, platform))) {
    missing.push('adb')
  }

  const iosCommand = await resolveIosCommand(runner, platform)
  if (!iosCommand) {
    missing.push('go-ios (or alias: ios)')
  }

  if (missing.length > 0) {
    const message =
      'Preflight failed: missing required CLI tool(s): ' +
      missing.join(', ') +
      '. Install them before starting Appium.'
    console.error(message)
    throw new Error(message)
  }
}
