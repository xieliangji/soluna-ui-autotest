import {BasePlugin} from '@appium/base-plugin'
import express from 'express'
import type {Application} from 'express'
import type {AppiumServer} from '@appium/types'
import {runPreflightChecks} from './cli/preflight'
import {handleGetAppInfo} from './http/app-route'
import {handleGetDeviceInfo, handleListDevices} from './http/device-route'
import {handleGetWdaBundle} from './http/ios-route'
import {handleExecuteCommand} from './http/command-route'
import {
  handleCreateLogSession,
  handleDeleteLogSession,
  handleReadLogSession,
} from './http/log-route'

export class SolunaExtPlugin extends BasePlugin {
  // noinspection JSUnusedGlobalSymbols
  static async updateServer(
    expressApp: Application,
    _httpServer: AppiumServer
  ): Promise<void> {
    void _httpServer
    await runPreflightChecks()

    expressApp.get('/soluna/device', async (req, res) => {
      await handleGetDeviceInfo(req, res)
    })

    expressApp.get('/soluna/devices', async (req, res) => {
      await handleListDevices(req, res)
    })

    expressApp.get('/soluna/app', async (req, res) => {
      await handleGetAppInfo(req, res)
    })

    expressApp.get('/soluna/ios/wda-bundle', async (req, res) => {
      await handleGetWdaBundle(req, res)
    })

    expressApp.post('/soluna/command', express.json(), async (req, res) => {
      await handleExecuteCommand(req, res)
    })

    expressApp.post('/soluna/logs/sessions', express.json(), async (req, res) => {
      await handleCreateLogSession(req, res)
    })

    expressApp.get('/soluna/logs/sessions/:sessionId', async (req, res) => {
      await handleReadLogSession(req, res)
    })

    expressApp.delete('/soluna/logs/sessions/:sessionId', async (req, res) => {
      await handleDeleteLogSession(req, res)
    })
  }
}

// noinspection JSUnusedGlobalSymbols
export default SolunaExtPlugin
