schemaVersion: "1.0"
id: {{PROJECT_ID}}-{{PLATFORM}}-smoke
name: {{APP_NAME}} {{PLATFORM}} 冒烟
productModel: {{PRODUCT_MODEL}}
version: "0.1.0"
metadata:
  owner: qa
  suite: smoke
  intent: 验证 Soluna distribution 能启动目标 App 并采集显式资源。
parameters:
  - id: defaults
    file: ../../data/default.yaml
fragmentRefs:
  - id: app
    file: ../../fragments/app-lifecycle.yaml
deviceConfig: ../../../../devices/{{PLATFORM}}/{{UDID}}.yaml
app:
  id: {{APP_ID}}
  name: {{APP_NAME}}
  platform: {{PLATFORM}}
  reset: false
defaults:
  implicitWaitMs: 8000
  failureStrategy: stop-case
  retryStrategy: no-retry
trace:
  screenshots:
    enabled: true
    beforeAction: onFailure
    retainBeforeActionCount: 5
    upload: onFailure
localArtifacts:
  cleanup:
    mode: never
stages:
  - id: smoke
    name: 冒烟
    setupFragments:
      - app.restart
    caseRefs:
      - file: ../../cases/common/smoke-screenshot.yaml
