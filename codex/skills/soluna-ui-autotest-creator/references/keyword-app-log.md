# App Log 关键字

case 需要采集 Android/iOS app log，或通过 `customAssertAppLog` 断言 app-specific 日志语义时读取本文件。

先读 `keyword-usage.md`。创建、构建、安装或加载 app-log assertion plugin 时，再读 `distribution-workflow.md`。

## 目录

- 设计边界
- `captureAppLogStart`
- `captureAppLogEnd`
- `customAssertAppLog`
- 插件流程
- 证据规则

## 设计边界

App-specific log 语义必须放在独立 JVM app-log assertion plugin 中，并通过 `customAssertAppLog` 调用。不要把业务协议解析写进 case YAML、data、element catalog、fragment 或默认框架关键字。

当 UI 证据无法证明行为时，才使用 app-log assertion，例如 BLE 命令 ACK 或 native SDK 事件。

## captureAppLogStart

在预期产生日志的 UI 操作前开启采集。采集窗口要窄，并在采集阶段过滤。

```yaml
- captureAppLogStart:
    id: start-ble-log-capture
    saveAs: bleLogCapture
    filter:
      messageContains: BLE
      android:
        tagRegex: Bluetooth|BLE
      ios:
        processRegex: Ugreen|Bluetooth
```

必填字段：

- `saveAs`：日志 session 描述符的 runtime variable 名。

runner 同时把最新描述符写入 `@{case.lastAppLogCapture}`。

可选字段：

- `filter`：采集时过滤。顶层字段会与当前平台分支 `android` 或 `ios` 共同生效。
- `maxBufferEntries`、`maxSessionBytes`、`ttlMs`：传给底层 log session 的边界。
- `udid`：只在 focused diagnostics 中覆盖 plan device UDID。

filter 字段：

- `source`
- `level` / `levels`
- `tag` / `tags`
- `tagRegex`
- `process` / `processes`
- `processRegex`
- `messageContains`
- `messageRegex`
- `rawContains`
- `rawRegex`

## captureAppLogEnd

在 UI 操作后读取有限批次、关闭 log session，并写出 JSONL 显式资源。

```yaml
- captureAppLogEnd:
    id: stop-ble-log-capture
    source: "@{case.bleLogCapture}"
    resourceId: ble-command-log
    saveAs: bleLogFile
```

默认值：

- `source`: `@{case.lastAppLogCapture}`
- `readLimit`: `500`
- `maxReadBatches`: `20`
- `maxEntries`: `5000`

runner 写出 `application/x-ndjson` 资源，把描述符写入 `@{case.lastAppLogFile}`，如果声明了 `saveAs` 也写入该变量。

当前 manifest 边界：runtime 可写 `type=log` / `purpose=app_log_capture`，但 v1 `plan-resource-manifest.schema.json` 目前只枚举 image/video 显式资源。若外部服务要严格校验包含 App log 的 manifest，应先补齐 schema/test 或作为框架合同缺口处理。

## customAssertAppLog

在 `captureAppLogEnd` 后使用，用 app-specific parser/matcher 断言日志语义。

```yaml
- customAssertAppLog:
    id: assert-ble-command-succeeded
    source: "@{case.bleLogFile}"
    plugin: ugreen-audio
    assertion: ble-command-ack
    args:
      command: ${deviceLog.expectedCommand}
      status: success
```

必填字段：

- `plugin`：app-log assertion plugin id。
- `assertion`：该 plugin 提供的 assertion name。

默认值：

- `source`: `@{case.lastAppLogFile}`

`args` 是 plugin-specific 结构化数据。plugin/assertion 未注册会导致 action 失败；不要把该关键字当作 no-op 占位符。

## 插件流程

通过打包 Soluna CLI 创建 starter app-log assertion plugin：

```bash
soluna scaffold app-log-plugin ./ugreen-audio-log-plugin \
  --plugin-id ugreen-audio \
  --package io.soluna.ugreen.applog \
  --assertion ble-command-ack
```

`--package` 和显式 `--group` 必须使用 `io.soluna` 或其子命名空间。

构建：

```bash
SOLUNA_HOME=/path/to/soluna gradle test jar
```

或：

```bash
gradle test jar -PsolunaHome=/path/to/soluna
```

runtime discovery 会查找：

- classpath
- 当前 distribution 下的 `plugins/app-log/*.jar`
- 当前工作目录下的 `plugins/app-log/*.jar`
- 推断出的 plan asset root 下的 `plugins/app-log/*.jar`
- `soluna.appLogPluginDirs` 或 `SOLUNA_APP_LOG_PLUGIN_DIRS` 指定的目录

多个显式 plugin 目录使用宿主机 path separator。

## 证据规则

- UI 操作前立即开始 capture，预期效果后立即结束 capture。
- 采集阶段过滤，不要先读取巨大日志再离线过滤。
- 用稳定 `resourceId` 保存 JSONL 资源。
- 业务期望放 parameter data 或 plugin `args`。
- docs 中记录 run id、case id、log resource id、plugin id、assertion name 和 plugin artifact 位置。
