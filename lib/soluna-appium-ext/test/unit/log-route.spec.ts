import {expect} from 'chai'
import express from 'express'
import request from 'supertest'
import {
  handleCreateLogSession,
  handleDeleteLogSession,
  handleReadLogSession,
} from '../../lib/http/log-route'

describe('log routes', () => {
  const app = express()
  app.use(express.json())

  const service = {
    createSession: async () => ({
      session: {
        sessionId: 's1',
        udid: 'abc123',
        platform: 'android' as const,
        status: 'running' as const,
        command: 'adb',
        args: ['-s', 'abc123', 'logcat', '-v', 'threadtime'],
        startedAt: new Date(0).toISOString(),
        lastActivityAt: new Date(0).toISOString(),
        ttlMs: 600000,
        nextSeq: 1,
        minSeq: 0,
        droppedCount: 0,
        maxBufferEntries: 1000,
        maxSessionBytes: 100 * 1024 * 1024,
      },
    }),
    readSession: async () => ({
      session: {
        sessionId: 's1',
        udid: 'abc123',
        platform: 'android' as const,
        status: 'running' as const,
        command: 'adb',
        args: ['-s', 'abc123', 'logcat', '-v', 'threadtime'],
        startedAt: new Date(0).toISOString(),
        lastActivityAt: new Date(0).toISOString(),
        ttlMs: 600000,
        nextSeq: 2,
        minSeq: 0,
        droppedCount: 0,
        maxBufferEntries: 1000,
        maxSessionBytes: 100 * 1024 * 1024,
      },
      cursor: 0,
      nextCursor: 1,
      cursorAdjusted: false,
      entries: [
        {
          seq: 0,
          ts: new Date(0).toISOString(),
          platform: 'android' as const,
          udid: 'abc123',
          source: 'stdout' as const,
          message: 'x',
          raw: 'x',
        },
      ],
    }),
    deleteSession: async () => ({
      sessionId: 's1',
      removed: true as const,
    }),
  }

  app.post('/soluna/logs/sessions', async (req, res) => {
    await handleCreateLogSession(req, res, service)
  })

  app.get('/soluna/logs/sessions/:sessionId', async (req, res) => {
    await handleReadLogSession(req, res, service)
  })

  app.delete('/soluna/logs/sessions/:sessionId', async (req, res) => {
    await handleDeleteLogSession(req, res, service)
  })

  it('creates session', async () => {
    const response = await request(app).post('/soluna/logs/sessions').send({udid: 'abc123'})
    expect(response.status).to.equal(201)
    expect(response.body.value.session.sessionId).to.equal('s1')
  })

  it('reads session logs', async () => {
    const response = await request(app).get('/soluna/logs/sessions/s1').query({cursor: 0})
    expect(response.status).to.equal(200)
    expect(response.body.value.entries).to.have.length(1)
  })

  it('deletes session', async () => {
    const response = await request(app).delete('/soluna/logs/sessions/s1')
    expect(response.status).to.equal(200)
    expect(response.body.value.removed).to.equal(true)
  })
})
