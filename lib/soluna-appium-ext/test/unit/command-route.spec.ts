import {expect} from 'chai'
import express from 'express'
import request from 'supertest'
import {handleExecuteCommand} from '../../lib/http/command-route'
import type {CommandExecuteResult} from '../../lib/types/command'
import type {SolunaLogger} from '../../lib/logger'

describe('POST /soluna/command', () => {
  const app = express()
  app.use(express.json())

  app.post('/soluna/command', async (req, res) => {
    await handleExecuteCommand(req, res)
  })

  it('returns 400 for invalid tool', async () => {
    const response = await request(app)
      .post('/soluna/command')
      .send({tool: 'bash', args: ['-lc', 'echo hi']})

    expect(response.status).to.equal(400)
    expect(response.body.value.error).to.equal('invalid_argument')
  })

  it('returns 422 with error payload when command exits non-zero', async () => {
    const response = await request(app)
      .post('/soluna/command')
      .send({tool: 'adb', args: ['invalid-sub-command'], timeoutMs: 1000})

    expect([422, 500]).to.include(response.status)
    expect(response.body).to.have.property('value')
  })

  it('logs command request and result summary', async () => {
    const records: string[] = []
    const testLogger: SolunaLogger = {
      info: (...args: unknown[]) => records.push(`info:${args.join(' ')}`),
      debug: (...args: unknown[]) => records.push(`debug:${args.join(' ')}`),
      warn: (...args: unknown[]) => records.push(`warn:${args.join(' ')}`),
      error: (...args: unknown[]) => records.push(`error:${args.join(' ')}`),
    }

    const loggingApp = express()
    loggingApp.use(express.json())
    loggingApp.post('/soluna/command', async (req, res) => {
      const executeCommand = async (): Promise<CommandExecuteResult> => ({
        command: 'adb',
        args: ['devices'],
        exitCode: 0,
        timedOut: false,
        truncated: false,
        durationMs: 12,
        stdout: 'List of devices attached\nabc123\tdevice\n',
        stderr: '',
      })

      await handleExecuteCommand(req, res, {logger: testLogger, executeCommand})
    })

    const response = await request(loggingApp)
      .post('/soluna/command')
      .send({tool: 'adb', args: ['devices'], timeoutMs: 1000, maxOutputBytes: 2048})

    expect(response.status).to.equal(200)
    expect(records.some((entry) => entry.includes('Executing command request'))).to.equal(true)
    expect(records.some((entry) => entry.includes('Command execution finished'))).to.equal(true)
    expect(records.some((entry) => entry.includes('Command stdout (adb,'))).to.equal(true)
  })

  it('truncates huge stdout in debug logs', async () => {
    const records: string[] = []
    const testLogger: SolunaLogger = {
      info: (...args: unknown[]) => records.push(`info:${args.join(' ')}`),
      debug: (...args: unknown[]) => records.push(`debug:${args.join(' ')}`),
      warn: (...args: unknown[]) => records.push(`warn:${args.join(' ')}`),
      error: (...args: unknown[]) => records.push(`error:${args.join(' ')}`),
    }

    const loggingApp = express()
    loggingApp.use(express.json())
    loggingApp.post('/soluna/command', async (req, res) => {
      const executeCommand = async (): Promise<CommandExecuteResult> => ({
        command: 'adb',
        args: ['devices'],
        exitCode: 0,
        timedOut: false,
        truncated: false,
        durationMs: 5,
        stdout: 'x'.repeat(1500),
        stderr: '',
      })

      await handleExecuteCommand(req, res, {logger: testLogger, executeCommand})
    })

    const response = await request(loggingApp)
      .post('/soluna/command')
      .send({tool: 'adb', args: ['devices']})

    expect(response.status).to.equal(200)
    const stdoutLog = records.find((entry) => entry.startsWith('debug:Command stdout'))
    expect(stdoutLog).to.be.a('string')
    expect(stdoutLog?.includes('...<truncated for debug log>')).to.equal(true)
  })

  it('logs validation failures as warn', async () => {
    const records: string[] = []
    const testLogger: SolunaLogger = {
      info: (...args: unknown[]) => records.push(`info:${args.join(' ')}`),
      debug: (...args: unknown[]) => records.push(`debug:${args.join(' ')}`),
      warn: (...args: unknown[]) => records.push(`warn:${args.join(' ')}`),
      error: (...args: unknown[]) => records.push(`error:${args.join(' ')}`),
    }

    const loggingApp = express()
    loggingApp.use(express.json())
    loggingApp.post('/soluna/command', async (req, res) => {
      await handleExecuteCommand(req, res, {logger: testLogger})
    })

    const response = await request(loggingApp)
      .post('/soluna/command')
      .send({tool: 'bash', args: ['-lc', 'echo hi']})

    expect(response.status).to.equal(400)
    expect(records.some((entry) => entry.startsWith('warn:Command request validation failed'))).to
      .equal(true)
  })
})
