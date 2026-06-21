schemaVersion: "1.0"
id: {{UDID}}
device:
  udid: {{UDID}}
  platform: {{PLATFORM}}
appium:
  server:
    mode: managed
    usePlugins:
      - soluna-ext
    ensureDrivers:
      - uiautomator2
      - xcuitest
  capabilities: {}
