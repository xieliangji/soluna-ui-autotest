import {expect} from 'chai'
import express from 'express'
import request from 'supertest'
import {handleGetAppInfo} from '../../lib/http/app-route'
import type {CommandRunner} from '../../lib/cli/exec'
import {getCommandLookupCommand} from '../../lib/cli/preflight'

describe('GET /soluna/app', () => {
  const lookupCommand = getCommandLookupCommand()
  const app = express()
  const runner: CommandRunner = async (command: string, args: string[] = []) => {
    if (command === 'adb' && args.length === 1 && args[0] === 'devices') {
      return {stdout: 'List of devices attached\nabc123\tdevice\n\n', stderr: ''}
    }
    if (
      command === 'adb' &&
      args[0] === '-s' &&
      args[1] === 'abc123' &&
      args[2] === 'shell' &&
      args[3] === 'getprop'
    ) {
      return {
        stdout: '[ro.product.model]: [Pixel 8]\n[ro.build.version.release]: [14]\n',
        stderr: '',
      }
    }
    if (
      command === 'adb' &&
      args[0] === '-s' &&
      args[1] === 'abc123' &&
      args[2] === 'shell' &&
      args[3] === 'pm' &&
      args[4] === 'path'
    ) {
      if (args[5] === 'com.example.app') {
        return {stdout: 'package:/data/app/example/base.apk\n', stderr: ''}
      }
      return {stdout: '', stderr: ''}
    }
    if (
      command === 'adb' &&
      args[0] === '-s' &&
      args[1] === 'abc123' &&
      args[2] === 'pull'
    ) {
      return {stdout: '', stderr: ''}
    }
    if (command === lookupCommand && args[0] === 'aapt') {
      return {stdout: '/usr/bin/aapt\n', stderr: ''}
    }
    if (command.endsWith('/aapt') || command === 'aapt') {
      return {
        stdout: [
          "package: name='com.example.app' versionCode='42' versionName='1.2.3'",
          "application-label:'Example App'",
        ].join('\n'),
        stderr: '',
      }
    }
    throw new Error(`Unexpected command ${command} ${args.join(' ')}`)
  }

  app.get('/soluna/app', async (req, res) => {
    await handleGetAppInfo(req, res, runner)
  })

  it('returns 400 when required parameters are missing', async () => {
    const response = await request(app).get('/soluna/app')

    expect(response.status).to.equal(400)
    expect(response.body.value.error).to.equal('invalid_argument')
  })

  it('returns installed app metadata from android badging', async () => {
    const response = await request(app)
      .get('/soluna/app')
      .query({udid: 'abc123', appId: 'com.example.app'})

    expect(response.status).to.equal(200)
    expect(response.body.value.exists).to.equal(true)
    expect(response.body.value.app.platform).to.equal('android')
    expect(response.body.value.app.udid).to.equal('abc123')
    expect(response.body.value.app.appId).to.equal('com.example.app')
    expect(response.body.value.app.name).to.equal('Example App')
    expect(response.body.value.app.version).to.equal('1.2.3')
    expect(response.body.value.app.versionCode).to.equal('42')
  })

  it('returns 404 when app is not installed', async () => {
    const response = await request(app)
      .get('/soluna/app')
      .query({udid: 'abc123', appId: 'missing'})

    expect(response.status).to.equal(404)
    expect(response.body.value.exists).to.equal(false)
  })
})
