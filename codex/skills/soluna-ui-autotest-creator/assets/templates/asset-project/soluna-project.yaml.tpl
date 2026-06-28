schemaVersion: "1.0"
id: {{PROJECT_ID}}
name: {{PROJECT_ID}}
framework:
  schemaVersion: v1
  minRunnerVersion: "{{RUNNER_MIN_VERSION}}"
paths:
  appsRoot: apps
  sharedRoot: shared
  devicesRoot: devices
  artifactsRoot: artifacts
apps:
  - id: {{APP_ID}}
    name: {{APP_NAME}}
    root: apps/{{APP_ID}}
    platforms:
{{PLATFORMS_YAML}}
    defaultPlan: plans/common/{{PLATFORM}}-smoke.yaml
