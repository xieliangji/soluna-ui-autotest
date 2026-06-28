schemaVersion: "1.0"
id: app-lifecycle
description: 可复用 App 生命周期 fragment。
fragments:
  restart:
    name: 重启 App
    actions:
      - restartApp:
          id: restart-app
          appId: "${app.id}"
          desc: 重启目标 App。
