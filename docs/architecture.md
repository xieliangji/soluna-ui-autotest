# soluna-ui-autotest Architecture

本文档记录 `soluna-ui-autotest` 的架构边界、当前实现事实和后续演进约束。它是框架设计判断的事实来源；字段级 schema 说明以 [docs/schemas.md](schemas.md) 为准，真实 schema 位于 `src/main/resources/schemas/v1/`。

`soluna` 只是项目名前缀，不表达业务域。

## 1. 项目定位

`soluna-ui-autotest` 是一个 Kotlin/JVM UI 自动化执行框架，面向 iOS 和 Android 真机，通过 Appium / WebDriver 协议驱动设备。

Gradle group、框架 Kotlin package、CLI mainClass、ServiceLoader SPI 名称和内置扩展包名均使用 `io.soluna` 命名空间。框架脚手架生成的扩展项目必须使用 `io.soluna` 或其子命名空间作为 package/group。

核心方向：

- 测试表达使用 YAML DSL，执行模型固定为 `Plan -> Stage -> Case -> Action`。
- 用例主体保持线性，不把 case DSL 做成脚本语言。
- 可复用生命周期 fragment 可以表达业务无关的流程控制。
- 参数、元素、fragment、设备、产物和通知配置都拆成独立文件，并通过 plan 直接或间接引用。
- App 默认已安装；启动、重启、清数据、登录态准备和清理由 plan/stage/case lifecycle 动作或 fragment 显式表达。
- 报告模型和 HTML 渲染由本项目自有，不引入第三方测试报告插件。
- 正式执行产物应上传到 MinIO；当前实现允许不配置 artifact store，此时只写本地报告和资源。
- 靠近设备、宿主机和 Appium Server 的增强能力优先收敛到内置 Appium 插件 `soluna-ext`，框架侧通过客户端抽象消费。

本项目是框架和契约包，不是业务用例项目。业务资产应作为 Soluna asset project 管理；本仓库中的 `AIot-Tests/` 是当前联调资产，不应把业务用例进度写入框架架构文档。

## 2. 运行入口和编排

当前 CLI 入口：

```text
soluna run <plan.yaml>
soluna debug <plan.yaml> source|screenshot|tap|tap-element|longPress|longPress-element|swipe|swipe-element|input|tap-template
soluna debug <plan.yaml> shell
soluna scaffold app-log-plugin
```

`soluna run` 只接收 plan 路径作为执行根。runner 不再单独接收 device、case、element、data、fragment 或 artifact 配置路径。

当前 `PlanRunner` 编排顺序：

1. 解析 plan YAML，执行 JSON Schema 校验和 DSL policy 校验，并把关键字动作归一化为内部 `ActionDefinition`。
2. 从 plan 的 `deviceConfig` 解析设备配置；从 plan 的 `artifactStore` 可选解析 MinIO/通知配置。
3. 启动或探测 Appium Server。managed server 会分配端口、确保 Appium plugin/driver 安装、在解析到显式或内置 FFmpeg 时注入 PATH，并等待 `/status` ready。
4. 通过 `soluna-ext` 解析设备元信息。设备配置可只写 UDID；缺失的 platform、device name 和 iOS OS version 会尽量从插件补齐。
5. iOS managed WDA 启用时，必要的 WDA bundle 信息通过 `soluna-ext` 解析；随后由 go-ios 管理 WDA。
6. 按 plan 引用链装配 case、element、fragment 和模板资产路径。
7. 应用 plan defaults，再解析 `${...}` 参数引用。
8. 通过 `soluna-ext` 解析已安装 app 元信息，用真实 app name/platform 覆盖 plan 中的展示兜底值。
9. 发送可配置的 `planStarted` DingTalk 通知。
10. 创建 Appium session。session 默认只绑定设备和能力，不自动安装或启动业务 app；目标 app 操作由 DSL 动作表达。
11. `LinearExecutionEngine` 串行执行 `Plan -> Stage -> Case -> Action`。
12. 执行结束后发送 `testFinished`，写 `execution-result.json` / `index.html`，写 `plan-resource-manifest.json`，把报告和显式资源入队上传，执行报告必需资源的 bounded drain，发送 `reportPublished`。
13. 如果配置 `localArtifacts.cleanup.mode: after-upload-success`，只有所有上传任务成功后才删除本地 run 目录。
14. 停止本次创建的 WebDriver session、managed WDA runwda/forward、managed Appium Server，并关闭自有 uploader。

`soluna debug` 复用 plan 的设备、Appium Server、`soluna-ext` 和 iOS WDA 配置，只执行低层定位、输入、点击、长按、滑动、截图或模板点击。debug 不进入 plan/stage/case 生命周期，不生成报告、manifest、上传任务或通知。

平台服务边界：

- 平台触发 runner 应使用 `run-request.schema.json`。
- runner 回传平台摘要应使用 `run-result.schema.json`。
- `run-result` 只是平台消费摘要，不替代 `execution-result.json`、`plan-resource-manifest.json` 和 HTML 报告。
- `soluna-project.yaml` 已有 schema，用于 asset project 元数据和未来项目发现；当前 CLI 不强制读取它。

## 3. 文件和资产组织

推荐 asset project 结构：

```text
<asset-root>/
  soluna-project.yaml
  apps/<app-id>/
    plans/
    cases/
    elements/
    data/
    fragments/
    plugins/app-log/
    docs/
  devices/<platform>/<udid>.yaml
  artifacts/
```

边界规则：

- `Plan` 表达测试目的、设备配置引用、参数引用、fragment 引用、artifact store 引用、阶段和用例编排。
- `Stage` 表达同一 plan 下的初始状态或执行阶段，通过 `caseRefs` 装配 case 文件；inline cases 只保留为小型调试或兼容形式。
- `Case` 表达线性动作列表，可引用 case-scoped data 和 element catalogs。
- `Fragment catalog` 管理可复用 setup/teardown 片段，适合登录态收敛、权限处理、清理和复杂前置状态。
- `Element catalog` 是 locator 的唯一外部 DSL 归属；case/plan/fragment 动作不允许直接写 inline locator。
- `Parameter data` 只保存测试输入、期望值、环境值、文案资源和视觉模板路径，不承担元素归属。
- 业务用例编写进度、调试路径、账号/设备前置条件和产品差异说明写入 asset project 的 `docs/`，不写入框架 `docs/progress.md`。

`PlanReferenceResolver` 当前行为：

- plan 先加载 `fragmentRefs` 指向的 catalog；fragment 动作只在被引用时解析。
- case refs 按 stage 装配；`caseRef.id` 可覆盖被引用 case 的 id。
- element catalog 可同时包含 Android-only、iOS-only 和通用元素。装配时只加载当前 platform 可用 locator；未被当前平台支持的元素会被跳过。如果动作实际引用被跳过的元素，解析失败。
- fragment catalog 可以混合平台专属片段。只要当前 plan/stage/case 没引用该 fragment，就不会解析其元素；实际引用到不支持当前平台的 fragment 时仍会失败。
- 视觉模板字段如果是非动态相对路径，会按 case data 目录、plan data 目录和当前文件目录等资产根解析为绝对路径；`${...}` 和 `@{...}` 动态引用不在引用装配期解析。

## 4. DSL 契约

YAML DSL 的解析顺序固定为：

1. YAML 解析成 JSON-compatible tree。
2. 对应 v1 JSON Schema 校验。
3. Framework policy 校验，例如 case 线性约束和 locator 文案规则。
4. 关键字动作归一化。
5. 映射到 Kotlin 运行时模型。

关键字支持中文和英文别名，但必须归一到一个内部 action model。新用例推荐使用单关键字嵌套对象形式：

```yaml
- tap:
    id: open-mine-tab
    element: common.mineTab
    desc: 打开我的页
```

旧的 `tap: open-mine-tab` 加同级字段形式仍兼容，但不作为新资产首选。

用例主体约束：

- case `actions` 不支持 `if` / `else` / `for` / `while` / `switch` / branch 等逻辑控制。
- case setup/teardown inline actions 同样不支持逻辑控制。
- 业务判断应通过普通动作或断言表达，不新增 `ifLoggedIn`、`ifElementPresent` 这类混合流程和业务语义的关键字。

fragment 控制流：

- 当前只有 `fragment-catalog.schema.json` 支持 `if` / `then` / `else`。
- `if` 值必须是一个已有动作或断言关键字；条件动作执行成功为 true，失败为 false。
- 条件失败不会直接判定用例失败；被选中分支中的动作失败才会让整个 `if` action 失败。
- 当前执行结果只记录 wrapper `if` action 的结果。条件动作只作为 predicate 执行，不发布独立 hook/trace，也不写入报告；分支动作按普通 action 执行并发布 hook/trace，但也不作为独立 action result 写入报告动作列表。

生命周期动作：

- plan/stage/case 都有 `setupFragments` / `setupActions` 和 `teardownFragments` / `teardownActions`。
- plan/stage 还可以声明 `caseSetupFragments` / `caseSetupActions` 和 `caseTeardownFragments` / `caseTeardownActions`，作用于其范围内每个 case。
- case 自身也可以声明 `caseSetup*` / `caseTeardown*`，用于插入到该 case 的 lifecycle。

当前 case setup 合并顺序：

```text
plan caseSetup -> stage caseSetup -> case caseSetup -> case setup
```

当前 case teardown 合并顺序：

```text
case teardown -> case caseTeardown -> stage caseTeardown -> plan caseTeardown
```

任一层级主流程失败后，当前层级 teardown 仍会执行。setup/teardown action list 内部遇到第一个失败会停止该 list；teardown 失败会把当前层级结果标为 failed，不抹掉主流程原始失败事实。

## 5. 参数和运行时变量

参数引用使用 `${name.path}`，运行时变量引用使用 `@{plan.name}` 或 `@{case.name}`。两者语义不同，不能混用。

当前 `PlanParameterResolver` 参数合并事实：

- plan 级动作使用：plan parameter files -> plan app seed -> CLI/request overrides。
- stage lifecycle 动作使用：plan 合并结果 -> stage inline `parameters`。
- case lifecycle 和 case actions 使用：stage 合并结果 -> case `dataRefs` -> case inline `parameters` -> CLI/request overrides。
- inline parameter 支持嵌套对象递归合并，也支持 dotted key，例如 `appState.mine.entryIndex`。
- `deviceConfig` 当前不是 `${...}` 参数源。
- `plan.app.id/name/platform/reset` 会 seed 到 `app.*` 参数路径；其中 `app.reset` 当前不会触发自动 reset 行为。

运行时变量：

- 变量只存在于一次执行的内存上下文，不写回参数文件。
- `plan` 变量作用域在整个 plan 内共享。
- `case` 变量作用域按 `stageId:caseId` 隔离，同一个 case 文件在不同 stage 中互不共享 case 变量。
- `getText`、`saveElementRect`、`screenshot`、`stopScreenRecording`、`captureAppLogStart` 和 `captureAppLogEnd` 等动作可以写运行时变量。

## 6. 元素定位

元素定位规则是框架稳定性的核心边界：

- 外部 DSL 中 locator 只能写在 `element-catalog.schema.json`。
- case/plan/fragment 动作使用 `element: alias.name` 引用元素，不使用 `${...}` 表示元素。
- 引用装配后，runner 把 `element` 转成内部 `ActionDefinition.locator`；该 locator 是执行器输入，不是外部 DSL 输入。
- 同一元素可定义通用 `strategy` / `value`，也可定义 `android` 和 `ios` 平台分支。
- 定位策略保持可扩展，例如 id、accessibility id、xpath、class chain、predicate、uiautomator、image 等。

文案定位规则：

- 固定 UI 文案不能无说明地直接写进 locator。
- 参数化 locator 文案只允许用于多语言不敏感值，例如 MAC 后缀、设备型号或稳定资源式 accessibility name，并必须声明 `parameterizedTextReason: language_insensitive_text`。
- 固定文本值只允许用于多语言不敏感值，例如品牌名、版本标志或资源式 accessibility name，并必须声明 `hardcodedTextReason: language_insensitive_text`。
- `language_insensitive_text` 是当前唯一允许 reason，不支持项目级扩展。
- 语言相关 UI 文案应放入语言版本化 data 文件，用于输入、断言、OCR 或日志辅助断言，不作为 locator 条件。
- locator 表达式禁止使用 `@x`、`@y`、`@width`、`@height` 等坐标/尺寸属性。

非定位交互：

- `tapPosition` 是显式比例点击关键字。无 `element` 时比例相对 viewport；有 `element` 时比例相对元素当前可见区域。
- viewport/element 比例参数不是 locator，不能替代可维护的元素目录定位。
- 没有稳定元素时才使用 viewport 比例，例如 modal backdrop 或无稳定滚动容器。

## 7. 执行引擎、hook 和策略

`LinearExecutionEngine` 是当前 DSL plan 的唯一编排核心。JUnit 不参与 plan 编排；JUnit 只用于框架单元测试、集成测试和 opt-in 真机 smoke。

执行模型：

```text
Plan
  Stage
    Case
      Action
```

执行约束：

- 一个 runner 进程绑定一个设备。
- 单设备内串行执行 stages、cases 和 actions。
- 多设备并发由外部调度多个进程实现。
- Appium session 默认按 plan 复用；`RecoveringWebDriverAdapter` 暴露稳定逻辑 session id，底层物理 session 可重建。

Hook 事件：

```text
plan.before / plan.after
stage.before / stage.after
case.before / case.after
action.before / action.after
```

当前实现：

- `SimpleHookBus` 同步分发 hook。
- `DefaultLoggingHook` 默认订阅 plan、stage、case 和 action 生命周期日志。
- `LinearExecutionEngine` 发布完整生命周期事件。
- failure trace 通过 `ActionTraceCollector` 接入执行器前后，不是 HookBus consumer。
- 报告写入、manifest 生成、DingTalk 生命周期通知和上传 drain 当前由 `PlanRunner` 直接编排；架构目标仍是外围副作用优先通过 hook consumer 和 async worker 解耦。

失败策略：

- `fail-fast` / `stop-case`：action 失败停止当前 case，case 失败停止当前 stage，stage 失败停止整个 plan。
- `continue-case`：action 失败仍停止当前 case，但继续同 stage 后续 case；stage 失败后继续后续 stage；plan 最终状态反映任一失败。
- setup 失败会跳过该层级主流程；teardown 仍执行。

重试策略：

- `RetryStrategy` 接口和 `NoRetryStrategy`、`MaxAttemptsRetryStrategy` 已存在。
- `LinearExecutionEngine` 已接入 action 级 retry。
- `PlanRunner` 当前默认使用注入的 `NoRetryStrategy`，尚未把 plan `defaults.retryStrategy` 的字符串值映射为运行时策略。
- 当前 retry 只在具备 stage/case 上下文的 action 上决策；plan/stage 级 setup/teardown 不走 action retry。

等待模型：

- `defaults.implicitWaitMs` 写入 session capabilities，并作为 session implicit wait。
- `defaults.actionWait` 在引用装配后填充到没有自定义 `wait` 的动作，包括嵌套分支动作。
- action 自己的 `wait` 优先于 `defaults.actionWait`。
- 显式 `wait` 执行期间，WebDriver element lookup 会临时把 implicit wait 置零，按显式 timeout/interval 轮询，结束后恢复 implicit wait。
- 断言动作按 `wait` 轮询，timeout 是该断言总预算。
- `tap`、`tapPosition`、`longPress` 和 `swipe` 默认执行后 settle 800ms，可用 `settleMs` 覆盖或设为 `0`。

## 8. Appium、WDA 和 `soluna-ext`

框架侧通过 `WebDriverAdapter` 隔离 Appium Java Client。默认实现 `AppiumJavaClientWebDriverAdapter` 负责：

- session 创建、停止和健康检查。
- 元素查找、输入、点击、长按、滑动、截图、元素截图、page source、属性读取和矩形读取。
- App restart、Android clear app data、Android/iOS screen recording。
- 对截图、source、元素查找、元素矩形、窗口尺寸、输入和 health check 等慢 WebDriver 命令增加有界 timeout；timeout 是 session recovery 信号。
- 每次元素交互重新按 locator 查找当前 viewport-visible 元素，不依赖历史 WebElement 缓存。
- 点击、长按和元素滑动基于元素与 viewport 的可见交集计算坐标，并尽量避开软键盘遮挡区域。

session 创建事实：

- plan `app.platform` 必须与 device platform 一致。
- platform 缺失时依赖 `soluna-ext` 从设备元信息补齐。
- Android 默认补 `appium:unicodeKeyboard=true` 和 `appium:resetKeyboard=true`，除非设备 capabilities 已覆盖。
- iOS managed WDA 会把 `appium:webDriverAgentUrl` 写入 capabilities。
- session 创建不自动安装 app，也不把 `app.reset` 转成 session reset 行为。

managed Appium Server：

- 可使用外部 server，也可由框架启动本地 Appium 进程。
- managed server 支持自动端口、`/status` readiness probe、进程清理和 FFmpeg PATH 注入。
- 启动前会确保 `usePlugins` 和 `ensureDrivers` 已安装。默认插件为 `soluna-ext`，默认 driver 为 `uiautomator2` 和 `xcuitest`。
- `soluna-ext` 是项目内置组件，源码位于 `lib/soluna-appium-ext`，分发时进入 `plugins/soluna-appium-ext`。如果宿主机已有同名插件但不是当前项目源码安装，会卸载后重新从项目源码安装。

iOS WDA：

- `LocalGoIosWdaManager` 通过 go-ios/ios 管理 WDA。
- iOS 17+ 会进入 go-ios tunnel 路径；是否需要 tunnel 由 `soluna-ext` 补齐的 iOS OS version 决定，默认 tunnel mode 为 `userspace`。
- managed WDA 会运行 go-ios `runwda` 和 `forward`；iOS 17+ 还需要 tunnel。
- tunnel 是宿主机全局单例资源。框架优先复用已存在的 `ios` / `go-ios tunnel start` 进程；plan 结束、WDA restart 或启动失败清理都不能停止 tunnel 进程，只停止本框架管理的 runwda 和 forward。
- WDA runner bundle 优先从 `soluna-ext` 查询已安装应用元信息推断；必要时可在 device config 中同时覆盖 `bundleId`、`testRunnerBundleId` 和 `xctestConfig`。

`soluna-ext` 插件能力边界：

- 设备发现和设备元信息。
- 已安装 app 元信息。
- iOS WDA runner bundle 查询。
- App 日志 / 设备日志采集会话。
- 受控 `adb`、`go-ios`、`ios` 命令执行。
- 宿主机依赖预检和设备邻近辅助能力。

当前实现差距：

- `clearAppData` 和 Android screen recording 仍在 WebDriver adapter 内直接调用 `adb`。后续应优先迁移到 `soluna-ext` 客户端抽象。
- Appium/WDA 进程管理仍在 Kotlin 框架侧；这属于 runner infrastructure，不要求放入插件。

## 9. 动作能力和证据采集

当前默认 action executors 覆盖：

- 交互：`tap`、`tapPosition`、`longPress`、`swipe`、`input`、`wait`。
- App 生命周期：`restartApp`、`clearAppData`。
- 数据读取：`getText`、`saveElementRect`。
- 断言：`assertElementExists`、`assertElementAttrEquals`、`assertElementAttrRegexMatch`、`assertSourceRegexMatch`。
- 显式资源：`screenshot`、`startScreenRecording`、`stopScreenRecording`。
- 视觉能力：`tapVisualTemplate`、`assertImageColorRatio`、`assertImageTextRegexMatch`、`assertScreenRecordingTextRegexMatch`。
- App 日志：`captureAppLogStart`、`captureAppLogEnd`、`customAssertAppLog`。

关键语义：

- `tapPosition` 在外部 DSL 中是独立关键字，归一化后走内部 `tap` 执行器。
- `tap` 的 `ignoreMissingElement` 只允许预定义条件 UI，目前 reason 只有 `optionalFirmwareUpgradePrompt`。
- 元素属性断言的 `attr` 可用 `/` 声明候选属性，例如 `name/label/text`；当前平台不支持或返回空值的候选会跳过。
- `restartApp` 终止并激活目标 app，然后等待前台状态；action `wait` 可覆盖前台等待。
- `clearAppData` 当前仅 Android 支持，执行 `pm clear` 后重新激活 app；如果 session 请求了 autoGrantPermissions，会尝试重新授予 runtime permissions。
- `screenshot` 无 `element` 时截全屏，有 `element` 时截元素图；`saveAs` 写运行时变量，并同时更新 `@{case.lastScreenshot}`。
- `saveElementRect` 可保存 pixel rect，也可用 `asRoi: true` 保存归一化 ROI，供视觉动作复用。
- `tapVisualTemplate` 基于当前截图和 kt-visual 模板匹配，支持 normalized ROI、scale、threshold、目标点比例和 action wait 重试。
- `assertImageColorRatio` 对图片文件做 kt-visual 命名色覆盖断言，支持 ROI 和 wait 轮询；它重读同一个 source 文件，不重新截图。
- `assertImageTextRegexMatch` 对静态图片 OCR；默认 source 为 `@{case.lastScreenshot}`。
- `startScreenRecording` / `stopScreenRecording` 生成显式视频资源。Android 当前直接使用 `adb screenrecord`；iOS 使用 Appium XCUITest recording。
- `assertScreenRecordingTextRegexMatch` 用 FFmpeg 抽帧，可按 ROI 裁剪，按 candidate strategy 选帧，再用 kt-visual Paddle OCR 或 OpenAI-compatible multimodal OCR 做正则匹配；匹配帧会作为显式资源写入 manifest。
- multimodal OCR 的 base URL、API key、model、reasoning effort、prompt、timeout、stream 和 parallelism 只来自系统属性或环境变量，不写入用例资产。
- FFmpeg 解析顺序为显式配置、分发包内 `tools/ffmpeg/<os>-<arch>/ffmpeg(.exe)`、最后宿主机 PATH。

App 日志动作：

- `captureAppLogStart` 通过 `soluna-ext` 创建日志会话并保存 descriptor。
- 日志 filter 支持通用字段和 `android` / `ios` 平台分支；匹配语义是通用规则与当前平台分支的交集。
- Android logcat 会话从当前日志尾开始采集，避免历史日志误命中新交互断言。
- iOS syslog 如果以 JSON `msg` 包裹真实文本，插件先按 `msg` 归一化再解析进程、级别和消息，同时保留原始 `raw`。
- `captureAppLogEnd` 读取 bounded batches，关闭会话，写 JSONL 显式资源并保存 `@{case.lastAppLogFile}`。
- `customAssertAppLog` 通过 `plugin` + `assertion` 找 JVM `AppLogAssertionPlugin`，未找到插件或断言必须失败。
- App log assertion 插件可来自 classpath、发行包 `plugins/app-log/*.jar`、当前工作目录 `plugins/app-log/*.jar`、plan 资产根 `plugins/app-log/*.jar`，或 `soluna.appLogPluginDirs` / `SOLUNA_APP_LOG_PLUGIN_DIRS`。

## 10. 产物、上传和报告

产物分类：

- `report`：`index.html`、`execution-result.json`、`plan-resource-manifest.json`。
- `resources`：DSL 显式请求保留的截图、录屏、OCR 命中帧、App log JSONL。
- `diagnostics`：失败 trace 截图、page source、WDA/go-ios 日志等诊断材料。

MinIO 上传：

- `ArtifactStore` / `ArtifactUploader` 隔离存储实现。
- 默认实现为 MinIO + `AsyncArtifactUploader` 后台队列。
- object key 稳定格式为 `{prefix}/runs/{runId}/{report|resources|diagnostics}/{fileName}`。
- 文本类产物按配置 gzip 上传；图片、视频等已压缩资源不重复压缩。
- 上传任务状态包含 pending、uploading、uploaded、failed_retryable、failed_permanent、abandoned。
- report-required 资源在 plan 结束后执行 bounded drain。
- `localArtifacts.cleanup.mode: after-upload-success` 只有在全部上传任务成功后才删除本地 run 目录。
- upload failure notifier 可聚合失败并通过 DingTalk 告警，支持时间窗口、阈值和抑制间隔。

显式资源 manifest：

- `plan-resource-manifest.json` 只记录 DSL 显式资源，不记录失败 trace。
- manifest 包含 plan 级元信息、run 批次信息、资源 id、类型、purpose、action id、MinIO object key / URL、content type、size 和 capturedAt。
- manifest 与报告文件放在同级 report 目录；启用 uploader 时报告 HTML 中链接会改写为 MinIO URL。

失败 trace：

- `trace.screenshots.enabled` 控制是否采集。
- `beforeAction: onFailure` 会在内存中保留最近 N 个 action 前截图和 page source。
- action 最终失败时，保留的截图和 source 写入 `diagnostics/trace`，并作为 report-required diagnostic 上传。
- trace 不进入 `plan-resource-manifest.json`。

报告：

- `LocalReportWriter` 写 `execution-result.json` 和单体 `index.html`。
- `report-data.schema.json` 是报告消费视图，不是内部 `PlanRunResult` 的直接序列化。
- 报告数据包含 plan/app/device 展示字段、开始/结束时间、stage/case/action 统计、失败摘要、生命周期动作列表和 trace artifacts。
- app name 和 device name 优先使用 `soluna-ext` 元数据；iOS 设备名必须来自 `ios devicename`，不能使用 `ios list --details` 的 `ProductName`。
- HTML 首屏展示概览、资源入口、统计、可折叠用例概览、失败摘要和 trace 资源。
- 每个 case 的动作明细通过弹窗查看；失败原因/错误列在概要表中固定宽度省略，完整文本保留在 tooltip 和动作明细中。

## 11. DingTalk 通知

通知组件是可替换的 `NotificationSender`。当前配置入口位于 artifact store config：

- `planStarted`
- `testFinished`
- `reportPublished`
- 兼容字段 `planFinished` 映射到 `reportPublished`
- `uploadFailures`

当前 DingTalk 生命周期通知使用固定标题 `App UI自动化测试`，正文是中文 Markdown 卡片。字段从 `设备名称`、`设备标识` 开始，报告发布通知包含报告和 manifest 链接；执行结束和报告发布使用执行开始/结束时间，不展示报告生成时间。

架构目标是把生命周期通知从 artifact store 配置中解耦为独立 plan 通知配置；当前实现尚未完成该拆分。

## 12. Schema 和外部合同

所有外部消费的数据合同必须 schema-first。当前 v1 schema 覆盖：

- plan、case、fragment catalog、element catalog、parameter data。
- device config、artifact store、notification sender。
- report data、plan resource manifest。
- asset project metadata。
- runner request/result 平台边界。

规则：

- schema `$id` 使用 `https://schemas.io.soluna.local/v1/` 命名空间。
- schema 文件版本化，破坏性合同变化应新建版本目录，不静默改变 v1 语义。
- Kotlin runtime model 不替代外部 schema。
- parser/validator 变更必须保持 schema-first 校验顺序。
- `docs/schemas.md` 记录字段级用法和当前 schema 范围；本文档只记录架构边界和关键运行语义。

## 13. 可替换接口

核心层应依赖接口而不是固定实现。当前已有或应保持稳定的边界包括：

- DSL：`DslParser`、`JsonSchemaDslValidator`、`KeywordRegistry`、policy validator。
- 执行：`ExecutionEngine`、`ActionExecutor`、`FailureStrategy`、`RetryStrategy`、`HookBus`、`ActionTraceCollector`。
- Appium：`WebDriverAdapter`、`AppiumServerManager`、`WdaManager`、`WdaBundleResolver`、`SolunaAppiumExtClient`。
- 配置解析：device、parameter data、artifact store、notification sender。
- 产物：`ArtifactStore`、`ArtifactUploader`、`PlanResourceManifestWriter`。
- 报告：`ReportWriter`。
- 通知：`NotificationSender`。
- App 日志扩展：`AppLogAssertionPlugin` / `AppLogAssertionRegistry`。

新增能力应优先插入这些边界，不把策略、宿主机命令、报告格式或通知渠道硬编码进执行引擎。

## 14. 当前实现边界和 v1 收口

当前已打通的能力：

- schema-first DSL 和 plan-rooted runner。
- Android/iOS 真机 Appium 执行。
- managed Appium Server、managed iOS WDA、session recovery。
- 元素目录、参数数据、fragment、case refs 和 runtime variables。
- 视觉模板、颜色断言、静态图片 OCR、录屏 OCR。
- App log capture 和 JVM app-log assertion plugin。
- 显式资源 manifest、失败 trace、本地 JSON/HTML 报告。
- MinIO 异步上传、bounded drain、本地清理和 DingTalk 通知。
- Codex skill 分发目录随 Gradle distribution 打包。

需要继续收口的实现差距：

- `Plan.defaults.retryStrategy` 字段尚未接入命名策略选择。
- `app.reset` 目前只作为 plan app 参数 seed，不触发自动 reset；reset 行为应继续通过 lifecycle fragment/action 显式表达，直到 runner 提供清晰策略。
- 部分 host/device-adjacent 操作仍在 Kotlin adapter 内直接调用 `adb`，后续应迁移到 `soluna-ext` 能力和客户端抽象。
- 报告、manifest 和生命周期通知仍主要由 `PlanRunner` 编排，尚未完全转成 hook consumer。
- `soluna-project.yaml` 已有合同但当前 CLI 不强制 project discovery。

后续新增或调整能力时：

- 行为、边界或生命周期假设变化必须更新本文档。
- schema 字段变化必须同步更新 schema 文件和 [docs/schemas.md](schemas.md)。
- CLI、DSL、debug 行为、报告/产物合同、Appium/WebDriver authoring 行为变化时，必须同步检查 `codex/skills/soluna-ui-autotest-creator`。
- 每轮实现结束必须更新 [docs/progress.md](progress.md)，记录变更、状态、验证和下一步。
