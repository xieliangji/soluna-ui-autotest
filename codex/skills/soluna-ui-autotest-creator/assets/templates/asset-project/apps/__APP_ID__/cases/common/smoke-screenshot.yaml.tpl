schemaVersion: "1.0"
id: smoke-screenshot
name: 冒烟截图
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
      desc: App 启动后短暂等待。
  - screenshot:
      id: capture-smoke-screen
      resourceId: smoke-screen
      desc: 截取当前屏幕作为 starter 显式资源。
