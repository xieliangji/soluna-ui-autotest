import {expect} from 'chai'
import {
  getCommandLookupCommand,
  isCommandAvailable,
  resolveIosCommand,
  runPreflightChecks,
} from '../../lib/cli/preflight'
import type {CommandRunner} from '../../lib/cli/exec'

function makeRunner(
  availability: Record<string, boolean>,
  lookupCommand: 'which' | 'where' = 'which'
): CommandRunner {
  return async (command: string, args: string[] = []) => {
    if (command !== lookupCommand) {
      return {stdout: '', stderr: ''}
    }
    const name = args[0]
    if (availability[name]) {
      return {stdout: `/usr/bin/${name}\n`, stderr: ''}
    }
    throw new Error('not found')
  }
}

describe('preflight checks', () => {
  it('should choose lookup command by platform', () => {
    expect(getCommandLookupCommand('darwin')).to.equal('which')
    expect(getCommandLookupCommand('linux')).to.equal('which')
    expect(getCommandLookupCommand('win32')).to.equal('where')
  })

  it('should check command availability with where on windows', async () => {
    const runner = makeRunner({adb: true}, 'where')
    const available = await isCommandAvailable('adb', runner, 'win32')
    expect(available).to.equal(true)
  })

  it('should check command availability with which on non-windows', async () => {
    const runner = makeRunner({adb: true}, 'which')
    const available = await isCommandAvailable('adb', runner, 'linux')
    expect(available).to.equal(true)
  })

  it('should resolve go-ios first', async () => {
    const runner = makeRunner({adb: true, 'go-ios': true, ios: true})
    const cmd = await resolveIosCommand(runner)
    expect(cmd).to.equal('go-ios')
  })

  it('should fallback to ios alias', async () => {
    const runner = makeRunner({adb: true, ios: true})
    const cmd = await resolveIosCommand(runner)
    expect(cmd).to.equal('ios')
  })

  it('should fail when adb missing', async () => {
    const runner = makeRunner({ios: true})
    let caught: unknown
    try {
      await runPreflightChecks(runner)
    } catch (err) {
      caught = err
    }
    expect(String(caught)).to.include('adb')
  })

  it('should fail when go-ios and ios missing', async () => {
    const runner = makeRunner({adb: true})
    let caught: unknown
    try {
      await runPreflightChecks(runner)
    } catch (err) {
      caught = err
    }
    expect(String(caught)).to.include('go-ios')
  })

  it('should run preflight on windows with where lookup', async () => {
    const runner = makeRunner({adb: true, 'go-ios': true}, 'where')
    await runPreflightChecks(runner, 'win32')
  })
})
