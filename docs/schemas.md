# Schema 合同

JSON Schema 是本项目对外数据合同的主格式。schema 文件位于：

```text
src/main/resources/schemas/v1/
```

当前 v1 schema 均使用 `schemaVersion: "1.0"`。Kotlin data class 是运行时模型，不等同于外部合同；外部资产、平台请求、报告数据和资源清单应以本目录下的 schema 为准。

schema `$id` 使用 `https://schemas.io.soluna.local/v1/` 命名空间；文件名仍按合同角色命名，例如 `plan.schema.json`、`case.schema.json`。

DSL 输入的处理顺序是：

1. YAML 解析为 JSON-compatible tree。
2. 按对应 JSON Schema 校验结构、必填字段、枚举和基础类型。
3. 执行框架 policy 校验，例如 case 线性规则、动作关键字规则、locator 文案规则。
4. 将动作 DSL 归一化为内部 `ActionDefinition`，再进入引用解析、参数解析和执行。

破坏性合同变化必须新建版本目录，不应静默改变 v1 语义。

## 1. Schema 清单

| 文件 | 角色 | 当前消费者或产物 |
| --- | --- | --- |
| `plan.schema.json` | plan 根 DSL，运行入口合同 | `YamlPlanParser`、`PlanRunner` |
| `case.schema.json` | 独立 case DSL | `YamlCaseParser`、`PlanReferenceResolver` |
| `element-catalog.schema.json` | 元素定位器 catalog | `YamlElementCatalogParser` |
| `fragment-catalog.schema.json` | 可复用 fragment catalog | `YamlFragmentCatalogParser` |
| `parameter-data.schema.json` | 参数数据文件 | `YamlParameterDataParser`、`PlanParameterResolver` |
| `device-config.schema.json` | 单设备 Appium/WDA 配置 | `YamlDeviceConfigParser`、device resolver |
| `artifact-store.schema.json` | MinIO、上传队列、压缩、重试和通知引用 | `YamlArtifactStoreConfigParser` |
| `notification-sender.schema.json` | DingTalk 机器人发送器配置 | `YamlNotificationSenderConfigParser` |
| `report-data.schema.json` | `execution-result.json` 报告数据合同 | `LocalReportWriter` |
| `plan-resource-manifest.schema.json` | `plan-resource-manifest.json` 显式资源清单合同 | `PlanResourceManifestWriter` |
| `soluna-project.schema.json` | asset project 根元数据合同 | 工具、平台和 Codex 资产生成流程 |
| `run-request.schema.json` | 平台到 runner 的执行请求摘要 | 平台边界合同 |
| `run-result.schema.json` | runner 到平台的执行结果摘要 | 平台边界合同 |

## 2. 输入 DSL 合同

### Plan

`plan.schema.json` 是执行根合同。必填字段为：

- `schemaVersion`
- `id`
- `name`
- `productModel`
- `deviceConfig`
- `stages`

`deviceConfig` 指向设备配置文件；`productModel` 是报告和 DingTalk 通知展示用的产品/型号名称；`artifactStore` 可选，存在时启用 MinIO 上传和通知配置解析。stage 必须包含 `cases` 或 `caseRefs`，新资产应优先使用 `caseRefs` 引用独立 case 文件，inline `cases` 只作为兼容路径保留。

`app` 只描述目标应用展示与参数 seed：

- `id`
- `name`
- `platform`
- `reset`

当前实现会把这些值注入 `${app.*}` 参数上下文。`app.reset` 不会自动清理应用数据；reset 需要通过 lifecycle fragment/action 显式表达。

`defaults` 当前包含：

- `implicitWaitMs`：WebDriver session 默认隐式等待，默认 5000ms。
- `actionWait`：动作默认显式等待。`PlanDefaultsResolver` 只填充未显式声明 `wait` 的 setup、case action 和 teardown action。
- `failureStrategy`：命名失败策略。当前 runner 支持空值/default、`stop-case`、`fail-fast`、`continue-case`。
- `retryStrategy`：schema/model 已声明，但 `PlanRunner` 当前未把该字段映射为命名运行时策略，仍使用构造时注入的 `RetryStrategy`。

`trace.screenshots` 控制失败诊断截图：

- `enabled`
- `beforeAction`: `never` 或 `onFailure`
- `retainBeforeActionCount`
- `upload`: `never` 或 `onFailure`

trace 是诊断产物，不进入 `plan-resource-manifest.json`。`localArtifacts.cleanup.mode` 当前支持 `never` 和 `after-upload-success`，后者只在必需上传 drain 成功后清理本地 run 目录。

Plan、stage、case 均支持 lifecycle 字段：

- `setupFragments` / `setupActions`
- `teardownFragments` / `teardownActions`
- `caseSetupFragments` / `caseSetupActions`
- `caseTeardownFragments` / `caseTeardownActions`

case setup 合并顺序为 plan、stage、case；case teardown 合并顺序为 case、stage、plan。teardown 结果和主动作结果分开记录。

### Case

`case.schema.json` 的必填字段为：

- `schemaVersion`
- `id`
- `name`
- `actions`

case DSL 必须保持线性，不允许 `if`、`else`、`for`、`while`、`loop`、`repeat`、`switch` 等通用控制键。case 可以声明：

- `dataRefs`：case 级参数数据文件。
- `elementRefs`：动作 `element: alias.name` 使用的元素 catalog。
- lifecycle fragments/actions：只作用于当前 case 或每个 case 的 scoped setup/teardown。
- `parameters`：case 内联参数。

`PlanReferenceResolver` 会在引用解析阶段把 `element` 解析为内部 `locator`，执行器不直接读取外部 element catalog。

### Fragment

`fragment-catalog.schema.json` 的必填字段为 `schemaVersion`、`id`、`fragments`。fragment 是当前唯一允许通用控制流的 DSL 容器：

```yaml
- if:
    assertElementExists:
      id: detect-page
      element: common.pageMarker
  then:
    - tap:
        id: continue
        element: common.next
  else: []
```

`if` 必须是一个普通可执行动作或断言动作；`then` / `else` 是普通动作数组。fragment catalog 会先作为 catalog 解析，具体 fragment 只在 plan、stage 或 case 引用时才解析元素和资产路径，因此共享 catalog 可以同时包含 Android-only 和 iOS-only fragment，但当前平台实际引用缺失 fragment 或元素时仍会失败。

### Element Catalog

`element-catalog.schema.json` 的必填字段为 `schemaVersion`、`id`、`elements`。元素可声明 common locator，也可声明 `android` / `ios` 平台分支。引用装配时只加载当前平台可用 locator；如果动作引用的平台元素不存在，解析失败。

定位器 policy：

- 外部动作不允许 inline `locator`；动作应通过 `element: alias.name` 引用 catalog。
- locator 表达式不能使用坐标或尺寸属性，例如 `@x`、`@y`、`@width`、`@height`。
- 固定 UI 文案不能硬编码进 locator。
- 文案 locator 只有在语言无关时才允许固定或参数化，例如 MAC 后缀、型号名、品牌名、资源式 accessibility 名称。
- `parameterizedTextReason` 和 `hardcodedTextReason` 当前只允许 `language_insensitive_text`。
- `@value='1'` / `@value='0'` / boolean 这类控件状态比较不按 UI 文案处理。

语言相关文案应放在参数数据文件中，用于输入和断言，不应进入 locator。

### Parameter Data

`parameter-data.schema.json` 的必填字段为 `schemaVersion`、`id`、`values`，可选 `secrets`。参数引用语法为 `${path.to.value}`；当整个字符串是一个参数引用时，会保留原 JSON 类型，否则按字符串插值。

参数合并顺序：

1. plan `parameters` 引用的数据文件。
2. plan `app.*` seed。
3. run request parameter overrides。
4. stage inline `parameters`。
5. case `dataRefs`。
6. case inline `parameters`。
7. case 范围再次应用 overrides。

嵌套对象递归合并；内联参数名支持 dotted path，例如 `appState.mine.entryIndex`。运行时变量 `@{plan.name}` / `@{case.name}` 是执行期变量，不属于 parameter data。

## 3. 动作 DSL 合同

动作对象必须声明且只能声明一个关键字字段。推荐使用 nested object 形式：

```yaml
- tap:
    id: open-settings
    element: common.settings
    desc: 打开设置
```

兼容旧形式：

```yaml
- tap: open-settings
  element: common.settings
```

nested object 形式必须包含非空 `id`。解析后，关键字会通过 `KeywordRegistry` 归一化为内部 canonical keyword；`tapPosition` 会归一化为内部 `tap`，并把外部 `xRatio` / `yRatio` 转为元素相对坐标参数。

当前 canonical keyword 和别名：

| canonical | aliases |
| --- | --- |
| `tap` | `click`、`点击`、`轻点` |
| `tapPosition` | `tapAt`、`positionTap`、`点击位置`、`按位置点击`、`坐标点击` |
| `longPress` | `longTap`、`pressAndHold`、`长按`、`长按点击` |
| `swipe` | `滑动`、`划动` |
| `tapVisualTemplate` | `tapImage`、`tapTemplate`、`视觉点击`、`模板点击`、`图片点击` |
| `assertImageColorRatio` | `imageColorRatio`、`assertImageContainsColor`、`imageContainsColor`、`断言图片颜色占比`、`图片颜色占比`、`图片含颜色量`、`图片含蓝色量` |
| `input` | `type`、`输入`、`录入` |
| `restartApp` | `restart`、`重启应用`、`重启App`、`重启APP` |
| `clearAppData` | `clearApplicationData`、`清除应用数据`、`清理应用数据` |
| `getText` | `readText`、`saveText`、`获取文本`、`读取文本`、`保存文本` |
| `saveElementRect` | `getElementRect`、`saveElementRegion`、`获取元素矩形`、`保存元素矩形`、`保存元素区域` |
| `wait` | `sleep`、`pause`、`等待`、`暂停` |
| `assertElementExists` | `elementExists`、`assertElementPresent`、`elementPresent`、`断言元素存在`、`元素存在` |
| `assertElementAttrEquals` | `elementAttrEquals`、`attrEquals`、`断言元素属性相等`、`元素属性相等`、`属性相等` |
| `assertElementAttrRegexMatch` | `elementAttrRegexMatch`、`attrRegexMatch`、`断言元素属性匹配`、`元素属性匹配`、`属性匹配` |
| `assertSourceRegexMatch` | `sourceRegexMatch`、`断言源码匹配`、`源码匹配` |
| `screenshot` | `截图`、`显式截图` |
| `startScreenRecording` | `startRecording`、`开始录屏`、`开始屏幕录制` |
| `stopScreenRecording` | `stopRecording`、`停止录屏`、`停止屏幕录制` |
| `assertScreenRecordingTextRegexMatch` | `screenRecordingTextRegexMatch`、`断言录屏文本匹配`、`录屏文本匹配` |
| `assertImageTextRegexMatch` | `imageTextRegexMatch`、`assertScreenshotTextRegexMatch`、`screenshotTextRegexMatch`、`断言图片文本匹配`、`图片文本匹配`、`断言截图文本匹配`、`截图文本匹配` |
| `captureAppLogStart` | `startAppLogCapture`、`开始采集App日志`、`开始抓取App日志` |
| `captureAppLogEnd` | `stopAppLogCapture`、`结束采集App日志`、`结束抓取App日志` |
| `customAssertAppLog` | `assertCustomAppLog`、`自定义断言App日志`、`自定义App日志断言` |

关键动作约束：

- `tap`：需要 `element`，或同时提供 `xRatio` / `yRatio`。元素点击每次执行前重新定位，并点击可见区域中心或指定元素相对比例。
- `tapPosition`：必须有 `xRatio` / `yRatio`；无 `element` 时按 viewport 比例，有 `element` 时按元素可见区域比例。
- `tap.ignoreMissingElement` 只允许元素点击使用，且必须搭配 `ignoreMissingElementReason`。当前唯一允许原因是 `optionalFirmwareUpgradePrompt`。
- `longPress`：目标规则与 `tap` 相同，支持 `durationMs` 和 `settleMs`。
- `swipe`：viewport 滑动需要 `startXRatio`、`startYRatio`、`endXRatio`、`endYRatio`；元素内滑动需要 `element` 加四个 `startElement*` / `endElement*` 比例。
- `input`：需要 `element` 和 `value`，支持 `clearFirst`。
- `wait`：需要 `durationMs`、`timeoutMs`、`value` 或 `wait.timeoutMs`。
- `restartApp`：需要 `appId`，终止并激活目标 app，等待其回到前台。
- `clearAppData`：需要 `appId`，当前只支持 Android；实现仍在 Kotlin WebDriver adapter 内直接调用 `adb`。
- `getText`：需要 `element` 和 `saveAs`，把文本写入运行时变量。
- `saveElementRect`：需要 `element` 和 `saveAs`，默认保存像素矩形；`asRoi: true` 保存归一化 ROI，可供视觉/OCR 动作复用。
- `screenshot`：显式截图资源；无 `element` 截全屏，有 `element` 截元素图。`saveAs` 会写入变量，并更新 `@{case.lastScreenshot}`。
- `tapVisualTemplate`：需要 `template`，使用 kt-visual 匹配当前截图后点击命中区域；`roi` 可是归一化对象或精确 runtime 变量引用。
- `assertImageColorRatio`：需要 `source`、`color`、`minRatio`，读取静态图片并按 kt-visual 命名颜色族断言；带 `wait` 时重复读取同一 source。
- `assertImageTextRegexMatch`：需要 `pattern`，默认 source 是 `@{case.lastScreenshot}`，用于静态图片 OCR。
- `startScreenRecording` / `stopScreenRecording`：通过 Appium 录屏，`stop` 可写显式视频资源并通过 `saveAs` 保存描述符。
- `assertScreenRecordingTextRegexMatch`：需要 `pattern`，从录屏抽帧、可选 ROI 裁剪、OCR 后做正则断言；命中帧写为显式资源。
- `captureAppLogStart`：需要 `saveAs`，通过 `soluna-ext` 创建日志 session，可配置通用和平台分支 filter。
- `captureAppLogEnd`：读取并关闭日志 session，写 `application/x-ndjson` 显式资源，更新 `@{case.lastAppLogFile}`。
- `customAssertAppLog`：需要 `plugin` 和 `assertion`，委托 JVM app-log assertion 扩展执行。

属性断言使用显式 `attr` 字段。`attr` 支持 slash-separated fallback，例如 `name/label/text`；正则默认是 contains-style 匹配，完整匹配应使用 `^...$`。

## 4. 设备、产物和通知配置

### Device Config

`device-config.schema.json` 的必填字段为 `schemaVersion`、`id`、`device`、`appium`。

`device` 描述 UDID、可选平台、展示名和 OS 版本。平台或 iOS OS 版本缺失时，当前实现可通过 `soluna-ext` 解析。目标 app 身份和 reset 意图不属于 device config。

`appium.server` 支持 managed 和 external：

- managed server 可省略 `host` / `port`，运行时选择可用端口。
- 默认 `usePlugins` 为 `["soluna-ext"]`，默认 `ensureDrivers` 为 `["uiautomator2", "xcuitest"]`。
- runner 会校验并安装所需 Appium plugin/driver；`soluna-ext` 视为本仓库集成组件。
- Android session 默认补充 `appium:unicodeKeyboard=true` 和 `appium:resetKeyboard=true`，除非设备配置显式覆盖。

`ios.wda` 描述 iOS WDA 生命周期。managed WDA 使用 go-ios；iOS 17+ 默认走 userspace tunnel，tunnel 是 host-global singleton，当前 manager 会复用已有 tunnel 进程，不在 stop/restart/failure cleanup 中停止 tunnel。

### Artifact Store

`artifact-store.schema.json` 的必填字段为 `schemaVersion`、`id`、`type`、`endpoint`、`secure`、`bucket`、`credentials`。当前实现只支持 MinIO/S3 风格 artifact store。

配置覆盖：

- endpoint、bucket、prefix、publicBaseUrl。
- 直接 credential 和 env indirection。
- 上传 worker 数、队列容量、bounded drain timeout。
- gzip 压缩策略，默认覆盖 HTML/JSON/XML/JS 和 `+json` / `+xml` 类型。
- 重试次数、初始延迟、最大延迟和退避倍数。
- `notifications.uploadFailures`、`planStarted`、`testFinished`、`reportPublished`。

`notifications.planFinished` 仍作为 `reportPublished` 的兼容别名解析。

### Notification Sender

`notification-sender.schema.json` 的必填字段为 `schemaVersion`、`id`、`type`、`robot`。当前 sender 类型为 DingTalk：

- `robot.webhook` / `robot.secret` 可直接配置，也可用 `webhookEnv` / `secretEnv`。
- 支持 `atMobiles`、`atUserIds`、`isAtAll`。
- `uploadFailurePolicy` 控制上传失败聚合告警窗口、阈值和抑制间隔。

生命周期通知当前由 `PlanRunner` 读取 artifact store 中的 notification 引用并发送。通知内容复用报告摘要和失败摘要，字段标签为中文。

## 5. 输出和平台边界合同

### Report Data

`report-data.schema.json` 描述 `LocalReportWriter` 写出的 `execution-result.json`。它是报告消费视图，不是内部 `PlanRunResult` 或 `PlanExecutionResult` 的直接序列化。

必填顶层字段包括：

- 运行和计划信息：`generatedAt`、`startedAt`、`finishedAt`、`runId`、`planId`、`planName`、`productModel`。
- app/device 展示字段：`appId`、`appName`、`deviceId`、`deviceName`、`platform`。
- 状态和视图：`status`、`summary`、`failures`、`traceArtifacts`。
- lifecycle/action 结果：`setupActions`、`teardownActions`、`stages`。

`summary` 提供 stage/case/action 的总数和 passed/failed/skipped 统计。`failures` 是扁平失败摘要，包含 stage、case、phase、index、action id、action keyword、message 和 error。`traceArtifacts` 只包含失败诊断 trace 的链接，不包含显式截图资源。

### Plan Resource Manifest

`plan-resource-manifest.schema.json` 描述报告旁边的 `plan-resource-manifest.json`。它记录 plan 元数据、run resource batch 和显式 DSL 资源。当前 schema 枚举覆盖：

- `type`: `image`、`video`
- `purpose`: `explicit_screenshot`、`explicit_screen_recording`、`screen_recording_text_match_frame`

manifest 不记录失败前 trace；trace 仍走普通 diagnostic artifact。

当前实现边界：`captureAppLogEnd` 已通过通用 `PlanResourceSink` 写出 `type=log`、`purpose=app_log_capture`、`contentType=application/x-ndjson` 的显式资源，但 v1 `plan-resource-manifest.schema.json` 尚未补入该枚举。外部消费者如果按 schema 严格校验包含 App log 的 manifest，会遇到合同不匹配。后续应补齐 manifest schema/test，或调整 App log 资源边界。

### Run Request / Run Result

`run-request.schema.json` 和 `run-result.schema.json` 是平台边界摘要合同。它们暴露 plan URI、asset project revision、设备选择、参数覆盖、预期/实际状态、计数和 artifact 链接，不暴露内部 Kotlin 执行模型。

`run-result` 不能替代：

- `execution-result.json`
- `plan-resource-manifest.json`
- HTML report

### Asset Project

`soluna-project.schema.json` 描述外部 asset project 根元数据，包括项目身份、framework/schema 兼容性、app roots、共享路径、设备路径、artifact 路径和默认值。当前 CLI 不强制发现或读取 `soluna-project.yaml`；它主要服务未来平台资产管理、项目发现和 Codex 资产生成。

## 6. 验证建议

schema 或 DSL 行为变更时，至少运行：

```bash
./gradlew test --tests io.soluna.ui.autotest.schema.JsonSchemaDslValidatorTest
```

触达解析、引用装配、参数、执行策略或报告输出时，补充对应 focused tests：

```bash
./gradlew test --tests io.soluna.ui.autotest.dsl.YamlPlanParserTest
./gradlew test --tests io.soluna.ui.autotest.runner.PlanReferenceResolverTest
./gradlew test --tests io.soluna.ui.autotest.runner.PlanParameterResolverTest
./gradlew test --tests io.soluna.ui.autotest.report.LocalReportWriterTest
```

只修改文档时，至少运行：

```bash
git diff --check -- docs/schemas.md docs/progress.md
```
