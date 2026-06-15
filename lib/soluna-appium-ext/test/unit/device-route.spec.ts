import {expect} from 'chai'
import express from 'express'
import request from 'supertest'
import {handleGetDeviceInfo, handleListDevices} from '../../lib/http/device-route'
import type {CommandRunner} from '../../lib/cli/exec'
import {getCommandLookupCommand} from '../../lib/cli/preflight'

describe('GET /soluna/device', () => {
  const app = express()
  const lookupCommand = getCommandLookupCommand()
  const runner: CommandRunner = async (command: string, args: string[] = []) => {
    if (command === 'adb' && args.length === 1 && args[0] === 'devices') {
      return {stdout: 'List of devices attached\nabc123\tdevice\nandroid-2\tdevice\n\n', stderr: ''}
    }
    if (
      command === 'adb' &&
      args.length >= 4 &&
      args[0] === '-s' &&
      args[2] === 'shell' &&
      args[3] === 'getprop'
    ) {
      if (args[1] === 'abc123') {
        return {
          stdout: '[ro.product.model]: [Pixel 8]\n[ro.build.version.release]: [14]\n',
          stderr: '',
        }
      }
      if (args[1] === 'android-2') {
        return {
          stdout: '[ro.product.model]: [Galaxy S24]\n[ro.build.version.release]: [15]\n',
          stderr: '',
        }
      }
    }
    if (command === lookupCommand && args[0] === 'adb') {
      return {stdout: '/usr/bin/adb\n', stderr: ''}
    }
    if (command === lookupCommand && args[0] === 'go-ios') {
      return {stdout: '/usr/local/bin/go-ios\n', stderr: ''}
    }
    if (command === 'go-ios' && args[0] === 'list' && args[1] === '--details') {
      return {
        stdout: JSON.stringify({
          deviceList: [
            {
              Udid: 'ios-1',
              ProductName: 'iPhone 15',
              ProductType: 'iPhone15,4',
              ProductVersion: '17.5',
            },
          ],
        }),
        stderr: '',
      }
    }
    if (command === lookupCommand && args[0] === 'ios') {
      throw new Error('not found')
    }
    throw new Error(`Unexpected command ${command} ${args.join(' ')}`)
  }

  app.get('/soluna/device', async (req, res) => {
    await handleGetDeviceInfo(req, res, runner)
  })

  app.get('/soluna/devices', async (req, res) => {
    await handleListDevices(req, res, runner)
  })

  it('returns 400 when udid missing', async () => {
    const response = await request(app).get('/soluna/device')
    expect(response.status).to.equal(400)
    expect(response.body.value.error).to.equal('invalid_argument')
  })

  it('returns 404 when device not found', async () => {
    const response = await request(app).get('/soluna/device').query({udid: 'not-exist'})
    expect(response.status).to.equal(404)
    expect(response.body.value.exists).to.equal(false)
  })

  it('returns 200 with unified device info', async () => {
    const response = await request(app).get('/soluna/device').query({udid: 'abc123'})
    expect(response.status).to.equal(200)
    expect(response.body.value.exists).to.equal(true)
    expect(response.body.value.device.platform).to.equal('android')
  })

  it('returns 200 with all connected devices', async () => {
    const response = await request(app).get('/soluna/devices')
    expect(response.status).to.equal(200)
    expect(response.body.value.count).to.equal(3)
    expect(response.body.value.devices).to.have.length(3)
    expect(response.body.value.devices.map((item: {platform: string}) => item.platform)).to.deep.equal([
      'android',
      'android',
      'ios',
    ])
  })

  it('returns empty list when no devices connected', async () => {
    const emptyApp = express()
    const emptyRunner: CommandRunner = async (command: string, args: string[] = []) => {
      if (command === 'adb' && args.length === 1 && args[0] === 'devices') {
        return {stdout: 'List of devices attached\n\n', stderr: ''}
      }
      if (command === lookupCommand && args[0] === 'adb') {
        return {stdout: '/usr/bin/adb\n', stderr: ''}
      }
      if (command === lookupCommand && (args[0] === 'go-ios' || args[0] === 'ios')) {
        throw new Error('not found')
      }
      throw new Error(`Unexpected command ${command} ${args.join(' ')}`)
    }

    emptyApp.get('/soluna/devices', async (req, res) => {
      await handleListDevices(req, res, emptyRunner)
    })

    const response = await request(emptyApp).get('/soluna/devices')
    expect(response.status).to.equal(200)
    expect(response.body.value.count).to.equal(0)
    expect(response.body.value.devices).to.deep.equal([])
  })
})
