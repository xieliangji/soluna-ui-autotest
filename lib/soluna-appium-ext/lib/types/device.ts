export type Platform = 'android' | 'ios'

export interface UnifiedDeviceInfo {
  platform: Platform
  udid: string
  name: string
  model: string
  osVersion: string
}

export interface DeviceLookupResult {
  found: boolean
  device?: UnifiedDeviceInfo
}
