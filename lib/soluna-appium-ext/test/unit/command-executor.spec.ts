import {expect} from 'chai'
import {EventEmitter} from 'node:events'
import {
  executeSupportedCommand,
  validateCommandRequest,
  type SpawnedCommandProcess,
} from '../../lib/services/command-executor'

type FakeReadable = EventEmitter & NodeJS.ReadableStream;

type SpawnEmitterProcess = SpawnedCommandProcess & EventEmitter;

type FakeSpawnFactory = (command: string, args: string[]) => SpawnedCommandProcess;

function createFakeProcess(options: {
  stdoutChunks?: string[];
  stderrChunks?: string[];
  closeCode?: number;
  closeDelayMs?: number;
  neverClose?: boolean;
}): SpawnEmitterProcess {
  const child = new EventEmitter() as unknown as SpawnEmitterProcess
  const stdout = new EventEmitter() as FakeReadable
  const stderr = new EventEmitter() as FakeReadable

  child.stdout = stdout
  child.stderr = stderr

  let closed = false

  child.kill = () => {
    if (!closed) {
      closed = true
      setTimeout(() => child.emit('close', 143), 0)
    }
    return true
  }

  setTimeout(() => {
    for (const chunk of options.stdoutChunks ?? []) {
      stdout.emit('data', Buffer.from(chunk))
    }
    for (const chunk of options.stderrChunks ?? []) {
      stderr.emit('data', Buffer.from(chunk))
    }

    if (!options.neverClose) {
      setTimeout(() => {
        if (!closed) {
          closed = true
          child.emit('close', options.closeCode ?? 0)
        }
      }, options.closeDelayMs ?? 0)
    }
  }, 0)

  return child
}

describe('command executor', () => {
  it('validates supported tools', () => {
    const req = validateCommandRequest({tool: 'adb', args: ['devices']})
    expect(req.tool).to.equal('adb')
    expect(req.args).to.deep.equal(['devices'])
  })

  it('rejects unsupported tools', () => {
    expect(() => validateCommandRequest({tool: 'bash'})).to.throw("'tool' must be one of")
  })

  it('captures stdout/stderr for normal completion', async () => {
    const fakeSpawn: FakeSpawnFactory = () =>
      createFakeProcess({stdoutChunks: ['hello\n'], stderrChunks: ['warn\n'], closeCode: 0})

    const result = await executeSupportedCommand(
      {tool: 'adb', args: ['devices'], timeoutMs: 2000, maxOutputBytes: 1024},
      {spawnProcess: fakeSpawn}
    )

    expect(result.command).to.equal('adb')
    expect(result.exitCode).to.equal(0)
    expect(result.timedOut).to.equal(false)
    expect(result.stdout).to.contain('hello')
    expect(result.stderr).to.contain('warn')
  })

  it('marks timeout for long-running commands', async () => {
    const fakeSpawn: FakeSpawnFactory = () => createFakeProcess({neverClose: true})

    const result = await executeSupportedCommand(
      {tool: 'ios', args: ['syslog'], timeoutMs: 50, maxOutputBytes: 1024},
      {spawnProcess: fakeSpawn}
    )

    expect(result.timedOut).to.equal(true)
    expect(result.exitCode).to.equal(143)
  })

  it('truncates oversized output', async () => {
    const fakeSpawn: FakeSpawnFactory = () =>
      createFakeProcess({stdoutChunks: ['a'.repeat(5000)], closeCode: 0})

    const result = await executeSupportedCommand(
      {tool: 'adb', args: ['devices'], timeoutMs: 2000, maxOutputBytes: 1024},
      {spawnProcess: fakeSpawn}
    )

    expect(result.truncated).to.equal(true)
    expect(result.stdout.length).to.equal(1024)
  })

  it('falls back from go-ios to ios alias when command missing', async () => {
    let first = true
    const fakeSpawn: FakeSpawnFactory = (command) => {
      if (first && command === 'go-ios') {
        first = false
        const p = new EventEmitter() as unknown as SpawnEmitterProcess
        p.stdout = new EventEmitter() as FakeReadable
        p.stderr = new EventEmitter() as FakeReadable
        p.kill = () => true
        setTimeout(() => {
          const err = Object.assign(new Error('not found'), {code: 'ENOENT'})
          p.emit('error', err)
        }, 0)
        return p
      }
      return createFakeProcess({stdoutChunks: ['ok\n'], closeCode: 0})
    }

    const result = await executeSupportedCommand(
      {tool: 'go-ios', args: ['list'], timeoutMs: 1000, maxOutputBytes: 1024},
      {spawnProcess: fakeSpawn}
    )

    expect(result.command).to.equal('ios')
    expect(result.exitCode).to.equal(0)
    expect(result.stdout).to.contain('ok')
  })
})
