export interface IosInstalledApplication {
  bundleId: string
  name?: string
  version?: string
  executable?: string
  applicationType?: string
}

export interface WdaBundleLookupResult {
  exists: boolean
  udid: string
  bundleId?: string
  app?: IosInstalledApplication
  message?: string
}
