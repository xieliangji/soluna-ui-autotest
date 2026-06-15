import {expect} from 'chai'
import {parseUnifiedLogLine} from '../../lib/services/log-parser'

describe('log parser', () => {
  it('parses android logcat threadtime-like line', () => {
    const entry = parseUnifiedLogLine({
      platform: 'android',
      udid: 'abc123',
      seq: 1,
      source: 'stdout',
      line: 'E/ActivityManager( 1234): crashed',
    })

    expect(entry.platform).to.equal('android')
    expect(entry.level).to.equal('error')
    expect(entry.tag).to.equal('ActivityManager')
    expect(entry.pid).to.equal(1234)
    expect(entry.message).to.equal('crashed')
  })

  it('falls back to raw-only parsing when android line format is unknown', () => {
    const entry = parseUnifiedLogLine({
      platform: 'android',
      udid: 'abc123',
      seq: 2,
      source: 'stderr',
      line: 'plain message',
    })
    expect(entry.message).to.equal('plain message')
    expect(entry.raw).to.equal('plain message')
    expect(entry.level).to.equal(undefined)
  })

  it('parses ios syslog-like line', () => {
    const entry = parseUnifiedLogLine({
      platform: 'ios',
      udid: 'ios-1',
      seq: 3,
      source: 'stdout',
      line: 'Mar 25 10:00:00 host MyApp[4321] <Notice>: hello ios',
    })

    expect(entry.platform).to.equal('ios')
    expect(entry.process).to.equal('MyApp')
    expect(entry.pid).to.equal(4321)
    expect(entry.level).to.equal('notice')
    expect(entry.message).to.equal('hello ios')
  })
})
