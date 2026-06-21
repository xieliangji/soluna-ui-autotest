schemaVersion: "1.0"
id: smoke-screenshot
name: Smoke Screenshot
dataRefs:
  - id: defaults
    file: ../../data/default.yaml
elementRefs:
  - id: common
    file: ../../elements/common.yaml
actions:
  - wait:
      id: wait-after-launch
      durationMs: 2000
      desc: Wait briefly after app launch.
  - screenshot:
      id: capture-smoke-screen
      resourceId: smoke-screen
      desc: Capture the current screen as the starter resource.
