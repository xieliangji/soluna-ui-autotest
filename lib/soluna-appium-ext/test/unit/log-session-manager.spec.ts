import {expect} from 'chai'
import {EventEmitter} from 'node:events'
import {mkdtemp, readFile} from 'node:fs/promises'
import {tmpdir} from 'node:os'
import path from 'node:path'
import {LogSessionManager, LogSessionHttpError} from '../../lib/services/log-session-manager'

type FakeReadable = EventEmitter & NodeJS.ReadableStream
type FakeChild = EventEmitter & {
  stdout: FakeReadable
  stderr: FakeReadable
  kill: (signal?: NodeJS.Signals) => boolean
}

function createFakeChild(): FakeChild {
  const child = new EventEmitter() as FakeChild
  child.stdout = new EventEmitter() as FakeReadable
  child.stderr = new EventEmitter() as FakeReadable
  child.kill = () => {
    setTimeout(() => {
      child.emit('close', 143)
    }, 0)
    return true
  }
  return child
}

describe('log session manager', () => {
  it('creates, reads and deletes a session with disk persistence', async () => {
    const baseDir = await mkdtemp(path.join(tmpdir(), 'soluna-log-test-'))
    const child = createFakeChild()

    const manager = new LogSessionManager({
      baseDir,
      sessionIdFactory: () => 'session-1',
      spawnProcess: () => child,
      lookupDeviceByUdid: async () => ({
        found: true,
        device: {
          platform: 'android',
          udid: 'abc123',
          name: 'Pixel',
          model: 'Pixel',
          osVersion: '14',
        },
      }),
      resolveIosCommand: async () => 'ios',
      cleanupIntervalMs: 100000,
    })

    const created = await manager.createSession({udid: 'abc123', maxBufferEntries: 2})
    expect(created.session.sessionId).to.equal('session-1')
    expect(created.session.status).to.equal('running')
    expect(created.session.command).to.equal('adb')

    child.stdout.emit('data', Buffer.from('E/Tag( 42): first\n'))
    child.stdout.emit('data', Buffer.from('E/Tag( 42): second\n'))
    child.stdout.emit('data', Buffer.from('E/Tag( 42): third\n'))

    await new Promise((resolve) => setTimeout(resolve, 20))

    const readFromAdjusted = await manager.readSession({
      sessionId: 'session-1',
      cursor: 0,
      limit: 10,
    })
    expect(readFromAdjusted.cursorAdjusted).to.equal(false)
    expect(readFromAdjusted.entries.length).to.equal(3)
    expect(readFromAdjusted.entries[0].seq).to.equal(0)

    const fileContent = await readFile(path.join(baseDir, 'session-1.jsonl'), 'utf8')
    expect(fileContent).to.contain('"message":"first"')

    const deletion = await manager.deleteSession({sessionId: 'session-1'})
    expect(deletion.removed).to.equal(true)
    await manager.dispose()
  })

  it('rejects unknown device on create', async () => {
    const baseDir = await mkdtemp(path.join(tmpdir(), 'soluna-log-test-'))
    const manager = new LogSessionManager({
      baseDir,
      spawnProcess: () => createFakeChild(),
      lookupDeviceByUdid: async () => ({found: false}),
      cleanupIntervalMs: 100000,
    })

    try {
      await manager.createSession({udid: 'missing'})
      // noinspection ExceptionCaughtLocallyJS
      throw new Error('expected to fail')
    } catch (err) {
      expect(err).to.be.instanceOf(LogSessionHttpError)
      expect((err as LogSessionHttpError).status).to.equal(404)
    } finally {
      await manager.dispose()
    }
  })

  it('filters captured android logs before buffering and persistence', async () => {
    const baseDir = await mkdtemp(path.join(tmpdir(), 'soluna-log-test-'))
    const child = createFakeChild()

    const manager = new LogSessionManager({
      baseDir,
      sessionIdFactory: () => 'session-filter',
      spawnProcess: () => child,
      lookupDeviceByUdid: async () => ({
        found: true,
        device: {
          platform: 'android',
          udid: 'abc123',
          name: 'Pixel',
          model: 'Pixel',
          osVersion: '14',
        },
      }),
      resolveIosCommand: async () => 'ios',
      cleanupIntervalMs: 100000,
    })

    await manager.createSession({
      udid: 'abc123',
      filter: {
        android: {
          tag: 'BluetoothCmd',
          messageRegex: 'reported',
        },
      },
    })

    child.stdout.emit('data', Buffer.from('D/Other( 42): reported\n'))
    child.stdout.emit('data', Buffer.from('D/BluetoothCmd( 42): ignored\n'))
    child.stdout.emit('data', Buffer.from('D/BluetoothCmd( 42): command reported\n'))

    await new Promise((resolve) => setTimeout(resolve, 20))

    const read = await manager.readSession({
      sessionId: 'session-filter',
      cursor: 0,
      limit: 10,
    })
    expect(read.entries.map((entry) => entry.message)).to.deep.equal(['command reported'])
    expect(read.nextCursor).to.equal(1)

    const fileContent = await readFile(path.join(baseDir, 'session-filter.jsonl'), 'utf8')
    expect(fileContent).to.contain('command reported')
    expect(fileContent).to.not.contain('ignored')
    await manager.dispose()
  })

  it('uses platform specific ios filter branch', async () => {
    const baseDir = await mkdtemp(path.join(tmpdir(), 'soluna-log-test-'))
    const child = createFakeChild()

    const manager = new LogSessionManager({
      baseDir,
      sessionIdFactory: () => 'session-ios-filter',
      spawnProcess: () => child,
      lookupDeviceByUdid: async () => ({
        found: true,
        device: {
          platform: 'ios',
          udid: 'ios-123',
          name: 'iPhone',
          model: 'iPhone',
          osVersion: '17',
        },
      }),
      resolveIosCommand: async () => 'ios',
      cleanupIntervalMs: 100000,
    })

    await manager.createSession({
      udid: 'ios-123',
      filter: {
        messageContains: 'BLE',
        android: {
          tag: 'BluetoothCmd',
        },
        ios: {
          processRegex: 'UgreenAudio',
        },
      },
    })

    child.stdout.emit('data', Buffer.from('Jun 21 20:20:00 iPhone OtherApp[10] <Notice>: BLE command reported\n'))
    child.stdout.emit('data', Buffer.from('Jun 21 20:20:01 iPhone UgreenAudio[11] <Notice>: unrelated\n'))
    child.stdout.emit('data', Buffer.from('Jun 21 20:20:02 iPhone UgreenAudio[11] <Notice>: BLE command reported\n'))

    await new Promise((resolve) => setTimeout(resolve, 20))

    const read = await manager.readSession({
      sessionId: 'session-ios-filter',
      cursor: 0,
      limit: 10,
    })
    expect(read.entries.map((entry) => entry.process)).to.deep.equal(['UgreenAudio'])
    expect(read.entries[0].message).to.equal('BLE command reported')
    await manager.dispose()
  })
})
