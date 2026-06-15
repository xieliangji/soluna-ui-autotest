import {expect} from 'chai'
import express from 'express'
import request from 'supertest'
import {handleGetWdaBundle} from '../../lib/http/ios-route'
import type {CommandRunner} from '../../lib/cli/exec'
import {getCommandLookupCommand} from '../../lib/cli/preflight'

describe('GET /soluna/ios/wda-bundle', () => {
  const lookupCommand = getCommandLookupCommand()

  it('returns 400 when udid missing', async () => {
    const app = express()
    app.get('/soluna/ios/wda-bundle', async (req, res) => {
      await handleGetWdaBundle(req, res)
    })

    const response = await request(app).get('/soluna/ios/wda-bundle')

    expect(response.status).to.equal(400)
    expect(response.body.value.error).to.equal('invalid_argument')
  })

  it('returns installed WDA runner bundle id', async () => {
    const app = express()
    const runner: CommandRunner = async (command: string, args: string[] = []) => {
      if (command === lookupCommand && args[0] === 'go-ios') {
        throw new Error('not found')
      }
      if (command === lookupCommand && args[0] === 'ios') {
        return {stdout: '/usr/local/bin/ios\n', stderr: ''}
      }
      if (
        command === 'ios' &&
        args.join(' ') === '--udid=ios-001 apps --all --list'
      ) {
        return {
          stdout: [
            'com.example.app Demo 1.0',
            'com.facebook.WebDriverAgentRunner.xctrunner WebDriverAgentRunner-Runner 1.0',
          ].join('\n'),
          stderr: '',
        }
      }
      throw new Error(`Unexpected command ${command} ${args.join(' ')}`)
    }
    app.get('/soluna/ios/wda-bundle', async (req, res) => {
      await handleGetWdaBundle(req, res, runner)
    })

    const response = await request(app).get('/soluna/ios/wda-bundle').query({udid: 'ios-001'})

    expect(response.status).to.equal(200)
    expect(response.body.value.exists).to.equal(true)
    expect(response.body.value.bundleId).to.equal('com.facebook.WebDriverAgentRunner.xctrunner')
  })

  it('returns 404 when WDA runner is not installed', async () => {
    const app = express()
    const runner: CommandRunner = async (command: string, args: string[] = []) => {
      if (command === lookupCommand && args[0] === 'go-ios') {
        return {stdout: '/usr/local/bin/go-ios\n', stderr: ''}
      }
      if (
        command === 'go-ios' &&
        args.join(' ') === '--udid=ios-001 apps --all --list'
      ) {
        return {stdout: 'com.example.app Demo 1.0\n', stderr: ''}
      }
      throw new Error(`Unexpected command ${command} ${args.join(' ')}`)
    }
    app.get('/soluna/ios/wda-bundle', async (req, res) => {
      await handleGetWdaBundle(req, res, runner)
    })

    const response = await request(app).get('/soluna/ios/wda-bundle').query({udid: 'ios-001'})

    expect(response.status).to.equal(404)
    expect(response.body.value.exists).to.equal(false)
  })
})
