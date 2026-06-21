schemaVersion: "1.0"
id: minio-template
type: minio
endpoint: "http://127.0.0.1:9000"
bucket: "soluna-runs"
prefix: "{{PROJECT_ID}}"
credentials:
  accessKey: "CHANGE_ME"
  secretKey: "CHANGE_ME"
upload:
  workers: 2
  queueCapacity: 200
  drainTimeoutMs: 30000
  retry:
    maxAttempts: 3
    initialDelayMs: 1000
    maxDelayMs: 10000
  compression:
    enabled: true
