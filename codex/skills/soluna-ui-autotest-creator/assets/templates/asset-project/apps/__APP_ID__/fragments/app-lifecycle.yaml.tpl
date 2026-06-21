schemaVersion: "1.0"
id: app-lifecycle
description: Reusable app lifecycle fragments.
fragments:
  restart:
    name: Restart App
    actions:
      - restartApp:
          id: restart-app
          appId: "${app.id}"
          desc: Restart target app.
