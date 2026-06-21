import type {Platform} from './device'

export interface InstalledAppInfo {
  platform: Platform
  udid: string
  appId: string
  name?: string
  version?: string
  versionCode?: string
  source?: string
}

export interface AppLookupResult {
  found: boolean
  app?: InstalledAppInfo
  message?: string
}
