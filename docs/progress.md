# 项目进度

本文档是 Soluna 框架项目的精简进度记录，记录框架能力、合同边界、运行时状态、验证基线和下一步工作。业务用例编写、逐条调试、账号/设备前置条件和具体操作路径应记录在对应 asset project 的 `docs/` 目录中。

## 维护规则

- 保持精简，只记录框架里程碑、当前状态、验证结果和下一步框架工作。
- 不粘贴长命令输出、完整堆栈、每一次失败尝试或业务用例逐条进展。
- 业务用例暴露出的可复用框架需求，在抽象成框架能力或合同变化后再记录到这里。
- 行为、边界、schema、生命周期假设、命令或依赖变化时，同步更新相关设计文档、schema 文档、README 或 bundled Codex skill。
- 每轮实现结束前更新本文件；如果只改文档，也要写明验证和未运行测试的原因。

## 当前状态

框架已经具备可运行基础。当前工作重点是 v1 收口、合同清晰度、真实 asset project 驱动的能力补齐和分发一致性。

已实现能力：

- YAML DSL 执行模型：`Plan -> Stage -> Case -> Action`。
- plan-rooted runner：执行入口只接收 plan 路径，其它资产由 plan 直接或间接引用。
- schema-first 校验：plan、case、element catalog、fragment catalog、parameter data、device config、artifact store、notification sender、report data、resource manifest、asset project、runner request/result 均有 v1 schema。
- 关键字即字段的动作语法：推荐 `tap: { id, element, desc }`，兼容旧的 `tap: open-mine-tab` 形式。
- case/data/element/fragment 拆分；case DSL 保持线性；fragment 支持业务无关 `if` / `then` / `else`。
- Android 和 iOS 真机执行，底层使用 Appium Java Client。
- managed Appium server：运行时端口、扩展安装校验、driver 安装校验、`/status` 探测和 FFmpeg PATH 注入。
- managed iOS WDA：go-ios 管理、iOS 17+ tunnel 路径、host-global tunnel 复用、不停止外部 tunnel。
- recovering WebDriver adapter：逻辑 session 稳定，物理 session / Appium server / WDA 可恢复重建。
- `soluna-ext` 客户端：设备元信息、已安装 app 元信息、WDA bundle 查询、受控命令和日志会话。
- 默认动作：`tap`、`tapPosition`、`longPress`、`swipe`、`input`、`wait`、`restartApp`、`clearAppData`、`getText`、`saveElementRect`、`screenshot`、`tapVisualTemplate`、`assertImageColorRatio`、`assertImageTextRegexMatch`、`startScreenRecording`、`stopScreenRecording`、`assertScreenRecordingTextRegexMatch`、`captureAppLogStart`、`captureAppLogEnd`、`assertElementExists`、`assertElementAttrEquals`、`assertElementAttrRegexMatch`、`assertSourceRegexMatch`、`customAssertAppLog`。
- viewport-visible 元素交互：tap/longPress/swipe 每次重新定位当前可见元素，按可见区域计算坐标。
- 运行时变量：`@{plan.name}` 和 `@{case.name}`；参数引用：`${...}`。
- 显式资源 manifest：截图、录屏、OCR 命中帧、App log JSONL 等业务显式资源进入 `plan-resource-manifest.json`。
- 失败诊断：失败前 trace 截图和 page source 进入 diagnostic artifact。
- 本地 JSON/HTML 报告：执行摘要、失败摘要、动作元数据、trace 链接、报告资源入口和 per-case 动作明细弹窗。
- App/device 展示名优先使用 `soluna-ext` 真实元数据。
- MinIO 异步上传：压缩、重试、bounded drain、上传成功后本地清理。
- DingTalk 生命周期通知和上传失败聚合告警。
- CLI：`soluna run <plan.yaml>`。
- Debug CLI：`soluna debug <plan.yaml> source|screenshot|tap|tap-element|longPress|longPress-element|swipe|swipe-element|input|tap-template|shell`。
- 分发包包含 `lib/soluna-appium-ext`、`tools/ffmpeg`、`plugins/app-log` 和中文化、按任务路由的 bundled Codex skill。

当前已知差距：

- `Plan.defaults.retryStrategy` 字段尚未映射到运行时命名 retry 策略。
- `app.reset` 当前只作为 `app.*` 参数 seed，不触发自动 reset；reset 仍需用 lifecycle fragment/action 显式表达。
- `clearAppData` 和 Android screen recording 仍在 Kotlin WebDriver adapter 内直接调用 `adb`，后续应迁移到 `soluna-ext` 能力和客户端抽象。
- `plan-resource-manifest.schema.json` 当前枚举只覆盖 image/video 显式资源，但 `captureAppLogEnd` 已可通过通用资源 sink 写出 `type=log` / `purpose=app_log_capture`；后续需要补齐 manifest schema/test 或调整 App log 资源边界。
- 报告、manifest 和生命周期通知仍主要由 `PlanRunner` 编排，尚未完全转为 hook consumer。
- `soluna-project.yaml` 已有 schema，但当前 CLI 不强制 project discovery。
- 生命周期通知配置仍挂在 artifact store config 下，后续应从 artifact 配置中解耦。

## 近期框架迭代

### 2026-06-28 io.soluna 命名基线

- Gradle group、框架 Kotlin package、CLI mainClass、simplelogger 包级配置、ServiceLoader SPI 文件名、App log plugin 示例包名和 scaffold package/group 约束均使用 `io.soluna` 命名空间。
- schema `$id` 使用 `https://schemas.io.soluna.local/v1/` 命名空间；README、架构文档、schema 文档和 bundled Codex skill 均按当前命名规则描述。
- `soluna scaffold app-log-plugin` 会校验 `--package` 和显式 `--group`：只接受 `io.soluna` 或其子命名空间；生成的 README 使用中文说明 package/group 和 ServiceLoader 约束。

验证：

- `./gradlew test`
- `python3 /Users/xieliangji/.codex/skills/.system/skill-creator/scripts/quick_validate.py codex/skills/soluna-ui-autotest-creator`
- `git diff --check -- codex/skills/soluna-ui-autotest-creator README.md docs src/main src/test AIot-Tests/apps/com.ugreen.iot/log-plugins/ugreen-audio build.gradle.kts`
- `./gradlew installDist`
- 确认 `build/install/soluna/codex/skills/soluna-ui-autotest-creator/SKILL.md` 存在且包含中文入口和 `io.soluna` 约束。
- `build/install/soluna/bin/soluna scaffold app-log-plugin /private/tmp/soluna-io-soluna-scaffold-check-20260628-b --plugin-id sample-log --package io.soluna.sample.applog --assertion contains-text --force`
- `./gradlew -p /private/tmp/soluna-io-soluna-scaffold-check-20260628-b test -PsolunaHome=/Users/xieliangji/IdeaProjects/soluna-ui-autotest/build/install/soluna`
- `./gradlew -p AIot-Tests/apps/com.ugreen.iot/log-plugins/ugreen-audio test -PsolunaHome=/Users/xieliangji/IdeaProjects/soluna-ui-autotest/build/install/soluna`

未运行：

- 未运行 `lib/soluna-appium-ext` 的 npm 测试；Appium plugin 源码未触达。

下一步：

- 后续新增 JVM 扩展、ServiceLoader SPI、脚手架模板或 schema 版本目录时，继续保持 `io.soluna` 命名空间约束。

### 2026-06-28 README 中文优化

- 将 `README.md` 从英文说明整理为中文项目入口文档，聚焦项目定位、核心模型、当前能力、asset project 合同、bundled Codex skill、CLI/debug CLI、报告上传、runtime tools/OCR、Appium plugin 和开发验证入口。
- 同步当前实现事实：CLI 只有 `run`、`debug` 和 `scaffold app-log-plugin`，没有独立 `validate`；`soluna-project.yaml` 当前是项目元数据合同，不驱动 CLI project discovery；`app.reset` 不自动 reset；manifest 对 App log JSONL 仍存在 schema 枚举缺口。
- 保留并整合最近 README 已补充的 debug `longPress` / `longPress-element`、报告 failure message 展示和 App log plugin 发现路径等描述。

验证：

- `git diff --check -- README.md docs/progress.md`
- `rg -n "soluna validate|Current Status|Design Summary|distributed|The framework|This smoke|Next steps" README.md`

未运行：

- 未运行 Gradle / npm 测试；本轮只修改 README 和进度文档，未改运行时代码、schema 或分发内容。

下一步：

- 后续 README 只保留项目入口和常用命令；字段级和生命周期细节继续以 `docs/architecture.md`、`docs/schemas.md` 和 bundled skill references 为准。

### 2026-06-28 Bundled Codex Skill 逐文件精修

- 按文件盘点并精修 `codex/skills/soluna-ui-autotest-creator`：保持 `SKILL.md` 作为中文入口和路由，保留 `agents/openai.yaml` 的中文短描述和包含 `$soluna-ui-autotest-creator` 的默认提示。
- 补强 reference 的加载时机和实现事实：长 reference 增加简短目录；明确 `soluna run` 承担校验和执行、当前 CLI 不读取 `soluna-project.yaml`、plan-relative 引用规则、case 线性 / fragment 控制流、setup / caseSetup 分工、App log manifest schema 缺口和能力缺口 gate。
- 精修 scaffold 脚本和模板：`create_asset_project.py` 输出中文提示，不再建议不存在的 `soluna validate`；修正 `plans/common/` 下 plan 模板的 data/fragment/device/case 相对路径；生成的 plan/case/fragment/data/element/docs 文案改为中文；`soluna-project.yaml` 的 `defaultPlan` 改为 `plans/common/<platform>-smoke.yaml`。
- 精修 `send_dingtalk_gap_notice.py` 的中文参数说明、默认标题和 dry-run 输出，保留内置 Soluna debug DingTalk robot fallback 行为。

验证：

- `python3 codex/skills/soluna-ui-autotest-creator/scripts/create_asset_project.py --output /private/tmp/soluna-skill-scaffold-check-codex-20260628-a --project-id skill-check --app-id com.example.skillcheck --app-name 示例App --product-model 示例型号 --platform android --udid ANDROID_UDID`
- 检查生成文件路径、`apps/com.example.skillcheck/plans/common/android-smoke.yaml` 的关键引用路径、`soluna-project.yaml` 的 `defaultPlan` 和中文 `docs/case-authoring-notes.md`。
- `python3 codex/skills/soluna-ui-autotest-creator/scripts/send_dingtalk_gap_notice.py --message '能力缺口 dry-run 验证' --dry-run --no-default-robot`
- `python3 /Users/xieliangji/.codex/skills/.system/skill-creator/scripts/quick_validate.py codex/skills/soluna-ui-autotest-creator`
- `git diff --check -- codex/skills/soluna-ui-autotest-creator docs/progress.md`
- `./gradlew installDist`；首次 sandbox 运行因 Gradle wrapper 需要访问 `~/.gradle` lock 被拒，使用外部权限重跑后通过。
- 确认 `build/install/soluna/codex/skills/soluna-ui-autotest-creator/SKILL.md` 存在且包含中文入口。

未运行：

- 未运行完整 `./gradlew test`、`./gradlew build` 或 `lib/soluna-appium-ext` 的 npm 测试；本轮只修改 skill 文档、脚本和模板，未改 Kotlin runtime 或 Appium plugin 源码。

下一步：

- 后续若新增 CLI 子命令、DSL 字段、关键字、manifest 资源类型、App log plugin 发现规则或 asset project 目录合同，应同轮更新对应 reference、脚手架模板和分发验证。

### 2026-06-28 Bundled Codex Skill 中文整理

- 将 `codex/skills/soluna-ui-autotest-creator/SKILL.md` 改为中文精简入口，只保留基本合同、任务路由、默认循环、新建资产项目和硬性规则。
- 按 Codex progressive disclosure 方式重组 references：新增 `case-lifecycle-workflow.md`、`keyword-core-actions.md`、`keyword-visual-ocr.md`、`keyword-app-log.md`，让 Codex 在调试用例、管理用例、执行用例和处理能力缺口时只加载对应规范。
- 将原有 asset project、debug/evidence、distribution、capability-gap 和 keyword usage 文档全部改为中文，并修正当前 CLI 没有独立 `validate` 命令的边界。
- 更新 `agents/openai.yaml`，让默认提示和短描述与中文 skill 行为一致。

验证：

- `python3 /Users/xieliangji/.codex/skills/.system/skill-creator/scripts/quick_validate.py codex/skills/soluna-ui-autotest-creator`
- `./gradlew installDist`
- 确认 `build/install/soluna/codex/skills/soluna-ui-autotest-creator/SKILL.md` 已存在并包含中文入口。
- `git diff --check -- codex/skills/soluna-ui-autotest-creator docs/progress.md`

下一步：

- 后续 CLI、DSL、报告/产物合同、Appium/WebDriver authoring 行为变化时，继续同步更新对应 reference，并保持 `SKILL.md` 只做入口和路由。

### 2026-06-28 Schema 文档中文整理

- 将 `docs/schemas.md` 从英文实现说明整理为中文 schema 合同文档。
- 按当前 schema 文件和 Kotlin 消费路径重新描述 plan/case/fragment/element/parameter/device/artifact/notification/report/manifest/run request/run result/asset project 合同。
- 补充动作关键字 canonical/alias 清单、DSL 解析顺序、policy 校验边界、参数合并顺序和输出数据语义。
- 明确当前合同差距：`retryStrategy` 字段未映射运行时命名策略、`app.reset` 不自动 reset、App log 显式资源写出能力与 manifest schema 枚举尚未完全一致。

验证：

- `git diff --check -- docs/schemas.md docs/progress.md docs/architecture.md`
- 文档整理变更，未运行 Gradle / npm 测试。

下一步：

- 后续若补齐 manifest App log 资源枚举，应同步更新 schema、schema 测试、`docs/schemas.md` 和 bundled Codex skill。

### 2026-06-28 进度文档中文整理

- 将 `docs/progress.md` 从英文长流水整理为中文框架进度记录。
- 调整结构为维护规则、当前状态、已知差距、近期框架迭代、历史里程碑、验证基线和下一步。
- 移除业务用例逐条调试记录，只保留由业务联调抽象出的框架能力变化。

验证：

- `git diff --check -- docs/progress.md`
- 文档整理变更，未运行 Gradle / npm 测试。

下一步：

- 后续框架行为变化继续用中文记录，并保持业务用例进度在 asset project 文档中维护。

### 2026-06-28 架构文档重整

- 按当前 Kotlin/Appium 实现重构 `docs/architecture.md`，替换旧的路线图式混合描述。
- 明确 `PlanRunner` 流程、plan-rooted 资产解析、lifecycle 合并顺序、参数合并顺序、运行时变量作用域、hook 使用、失败/重试策略、Appium/WDA/session recovery、动作证据语义、产物/报告/通知流和 schema/service 合同。
- 显式记录当前实现差距：`retryStrategy` 未接入命名策略、`app.reset` 不触发自动 reset、部分 Android 宿主机命令仍在 adapter 内、报告/manifest/通知仍由 `PlanRunner` 编排、CLI 尚未强制读取 `soluna-project.yaml`。

验证：

- `git diff --check -- docs/architecture.md docs/progress.md`
- 文档-only 变更，未运行 Gradle / npm 测试。

下一步：

- 实现上述差距时，同步更新 `docs/architecture.md`、`docs/schemas.md` 和 bundled Codex skill。

### 2026-06-28 App 日志与报告错误展示

- `soluna-ext` Android App log session 从当前 logcat tail 开始采集，避免历史日志误命中新交互断言。
- 报告执行概览和失败摘要中的错误/消息列改为固定宽度省略展示，完整文本保留在 tooltip 和动作明细弹窗中。
- 元素属性断言支持 slash-separated fallback，例如 Android switch 可从 `value` fallback 到 `checked` 并映射为现有 `1` / `0` 合同。
- App 级蓝牙日志语义继续放在独立 app-log assertion plugin 中，默认 DSL 不引入业务协议关键字。

验证：

- `./gradlew test --tests io.soluna.ui.autotest.report.LocalReportWriterTest`
- `./gradlew test --tests io.soluna.ui.autotest.cli.SolunaCliApplicationTest --tests io.soluna.ui.autotest.appium.action.WebDriverActionExecutorsTest`
- `./gradlew test --tests io.soluna.ui.autotest.report.LocalReportWriterTest installDist`
- `npm run build` in `lib/soluna-appium-ext`
- `npm test` in `lib/soluna-appium-ext`
- `./gradlew -p AIot-Tests/apps/com.ugreen.iot/log-plugins/ugreen-audio test -PsolunaHome=/Users/xieliangji/IdeaProjects/soluna-ui-autotest/build/install/soluna`
- Android 真机 asset plan 抽样验证通过。

下一步：

- 保持平台/业务协议语义在 app-log assertion plugin 和资产数据中，不进入通用 DSL 关键字。
- 当前大批 asset 调试收口后运行完整 Gradle sweep。

### 2026-06-28 Debug CLI 长按支持

- `soluna debug` 一次性命令和交互 shell 增加 `longPress` / `longPress-element`。
- 复用现有 WebDriver longPress adapter 路径，支持 viewport 比例、元素相对比例和 `durationMs`。
- 同轮补强属性断言 fallback，平台不支持的属性候选不会直接中断整个断言。
- 更新 README、架构说明和 bundled skill debug evidence reference。

验证：

- `./gradlew test --tests io.soluna.ui.autotest.cli.SolunaCliApplicationTest --tests io.soluna.ui.autotest.appium.action.WebDriverActionExecutorsTest`
- `python3 /Users/xieliangji/.codex/skills/.system/skill-creator/scripts/quick_validate.py codex/skills/soluna-ui-autotest-creator`
- `git diff --check`
- `./gradlew installDist`
- `build/install/soluna/bin/soluna --help`
- debug shell `help` 确认列出 `longPress` 和 `longPress-element`。

### 2026-06-27 iOS 设备名与 WDA tunnel 生命周期

- `soluna-ext` iOS device metadata 改为通过 `ios --udid=<udid> devicename` 获取真实设备名，不再把 `ios list --details` 的 `ProductName` 当作展示设备名。
- 增加 go-ios JSON-line `devicename` 输出解析，保留 `ProductType` 作为 model 来源。
- managed iOS WDA 启动时检测并复用已有 `ios` / `go-ios tunnel start` 进程。
- iOS tunnel 明确为 host-global singleton；plan 结束、WDA restart、WDA 启动失败清理都只停止框架拥有的 `runwda` 和 `forward`，不停止 tunnel。
- 默认 Appium/WDA 包级日志从 DEBUG 降到 INFO，避免正常 plan 运行刷屏。

验证：

- `npx mocha --require tsx/cjs test/unit/parsers.spec.ts test/unit/device-route.spec.ts` in `lib/soluna-appium-ext`
- `npm run build` / `npm run lint` in `lib/soluna-appium-ext`
- `./gradlew test --tests io.soluna.ui.autotest.config.DeviceConfigResolverTest --tests io.soluna.ui.autotest.report.LocalReportWriterTest`
- `./gradlew test --tests io.soluna.ui.autotest.appium.wda.LocalGoIosWdaManagerTest`
- `./gradlew installDist`
- 本地 iOS 设备元信息返回真实设备名、model 和 OS version。

下一步：

- 分发包更新后继续跑短 iOS plan，确认报告展示真实物理设备名。

### 2026-06-27 Locator 文案 reason 与 `tapPosition`

- 用 `parameterizedTextReason` / `hardcodedTextReason` 替代旧 `textLocatorPurpose`。
- `language_insensitive_text` 是唯一允许 reason，且由框架拥有，不做项目级扩展。
- locator policy 增加坐标/尺寸属性拦截，并识别 XPath、iOS predicate、Android UiAutomator 等常见文本函数。
- 新增 author-facing `tapPosition` 关键字及中英文别名，显式表达 viewport 或元素可见区域比例点击。
- `tapPosition` 归一化为内部 `tap` action；element 场景把 `xRatio` / `yRatio` 映射到 `elementXRatio` / `elementYRatio`。
- 更新 schema、policy、架构/schema docs 和 bundled skill keyword reference。

验证：

- `./gradlew test --tests io.soluna.ui.autotest.dsl.YamlPlanParserTest --tests io.soluna.ui.autotest.schema.JsonSchemaDslValidatorTest --tests io.soluna.ui.autotest.runner.PlanReferenceResolverTest`
- `./gradlew test --tests io.soluna.ui.autotest.appium.action.WebDriverActionExecutorsTest`
- `./gradlew installDist`
- `git diff --check`
- skill quick validation 在部分环境因缺少 PyYAML 未能运行；已记录环境原因。

下一步：

- 继续把可稳定锚定的 viewport-only `tapPosition` 资产替换成 element-relative 目标。

### 2026-06-26 条件点击、元素截图和静态 OCR

- `tap` 支持可选缺失元素跳过，但必须使用预定义 `ignoreMissingElementReason`，当前为 `optionalFirmwareUpgradePrompt`。
- `screenshot` 支持带 `element` 的元素截图；无 element 时仍截全屏。
- `screenshot.saveAs` 可把本地图片路径写入运行时变量。
- 新增 `assertImageTextRegexMatch`，用于稳定截图或图片文件 OCR，避免静态页面必须走录屏 OCR。
- 更新 v1 schemas、Kotlin keyword registry、执行文档和 bundled skill keyword reference。

验证：

- `./gradlew test --tests io.soluna.ui.autotest.appium.action.WebDriverActionExecutorsTest --tests io.soluna.ui.autotest.schema.JsonSchemaDslValidatorTest --tests io.soluna.ui.autotest.dsl.YamlPlanParserTest --tests io.soluna.ui.autotest.runner.PlanReferenceResolverTest`
- `./gradlew test`
- `./gradlew installDist`
- `git diff --check`

## 历史里程碑

### 2026-06-22 图像颜色断言和 iOS 日志解析

- 新增 `assertImageColorRatio`，基于 kt-visual named color 检测图片颜色覆盖率。
- 图片颜色断言支持 `source`、`color`、`minRatio`、`minPixels`、ROI 和 action wait。
- `soluna-ext` 归一化 JSON-wrapped iOS syslog `msg` 字段后再解析 process、level、message，同时保留原始 `raw`。
- App log 过滤可正确作用于 JSON wrapper 形态的 iOS syslog。

验证：

- 相关 WebDriver action、DSL parser、schema validator、runner resolver、App log plugin loader、`lib/soluna-appium-ext` npm test/build/lint、`./gradlew installDist` 均在对应迭代通过。

### 2026-06-21 Swipe、App log 扩展和 asset creator 维护

- 新增通用 `swipe` action，支持 viewport 和 element-relative 起止比例。
- 新增 App log capture/assertion 关键字：`captureAppLogStart`、`captureAppLogEnd`、`customAssertAppLog`。
- 新增 JVM ServiceLoader app-log assertion plugin 发现机制，支持 classpath、分发包/current working directory/plan asset root 下 `plugins/app-log/*.jar`，以及显式目录配置。
- 新增 `soluna scaffold app-log-plugin` 脚手架。
- `soluna-ext` 增加 capture-time App log filtering，支持 common filter 和 `android` / `ios` 平台分支。
- bundled Codex asset creator skill 增加设备用例目录、debug/focused plan、能力缺口上报和分发验证规则。
- `lib/soluna-appium-ext` 确认为本仓库集成组件，随框架一起开发、验证、提交和分发。

验证：

- 相关 Gradle parser/schema/action/CLI/plugin loader 测试通过。
- `npm test`、`npm run build`、`npm run lint` in `lib/soluna-appium-ext` 通过。
- bundled skill quick validation、脚手架生成、`./gradlew installDist` 和分发包内容检查通过。

### 2026-06-21 报告和 DingTalk 通知收口

- plan contract 增加 `productModel`，用于报告和 DingTalk 展示。
- `LocalReportWriter` 重构为 overview-first HTML：资源入口、统计、用例概览、失败摘要、trace 资源和 per-case 动作明细弹窗。
- `execution-result.json` 增加产品/app/device 展示字段、stage/case 展示名、执行摘要、失败摘要和 action metadata。
- DingTalk 生命周期通知改为中文 Markdown 卡片，使用固定标题 `App UI自动化测试` 和 `<productModel> UI 自动化测试` 副标题。
- `DeviceConfigResolver` 和 `AppMetadataResolver` 使报告/通知优先展示 `soluna-ext` 返回的真实设备名和 app name。

验证：

- `./gradlew test`
- `npm test`、`npm run build`、`npm run lint` in `lib/soluna-appium-ext`
- bundled skill quick validation
- `./gradlew installDist`
- Android 真机报告/通知抽样验证通过。

### 2026-06-19 运行时稳定性和视觉能力

- 新增 `ContinueCaseFailureStrategy`，允许失败 case 停止自身但继续后续 case/stage。
- managed Appium startup 会确保项目自带 `soluna-ext` 和默认 drivers 已安装。
- 新增 Android `clearAppData`，执行 `pm clear` 后重新激活 app；autoGrantPermissions 场景会尝试重新授予 runtime permissions。
- 新增 `saveElementRect`，保存元素 pixel rect 或 normalized ROI。
- `tapVisualTemplate` 支持 runtime ROI 和 action-level wait 重试。
- 新增 multimodal OCR recognizer，Paddle OCR 保持默认；OpenAI-compatible multimodal OCR 配置只来自系统属性或环境变量。
- managed WDA 诊断日志写入 run scoped `diagnostics/wda`。

验证：

- 相关 execution strategy、runner、Appium manager、device config parser、schema、action executor、WDA/Appium manager 测试在对应迭代通过。
- `./gradlew installDist` 在相关分发变更后通过。

### 2026-06-18 等待、恢复和诊断

- 显式 wait 与 session implicit wait 隔离，断言轮询 probe 不被隐式等待放大。
- managed iOS WDA health 接入 `RecoveringWebDriverAdapter` 恢复链路。
- session recovery 在 WDA 不健康时先重启 WDA，再重建 Appium server/session。
- 增加共享 FFmpeg resolver；managed Appium 启动时可 prepend FFmpeg 目录到 PATH。
- 默认 lifecycle logging 覆盖 plan/stage/case/action。
- debug CLI 增加 restart-app 和 shell；失败 trace 同时保留 page source。

验证：

- FFmpeg resolver、Appium/WDA manager、CLI、runner、recovering adapter 和 action executor 相关测试通过。

### 2026-06-17 视觉点击和录屏 OCR

- 新增 `tapVisualTemplate` 及 `tapImage` / `tapTemplate` aliases。
- 新增 Appium screen recording start/stop 和 `assertScreenRecordingTextRegexMatch`。
- 显式截图、视频和分析帧统一进入 plan resource sink / manifest / upload path。
- 录屏抽帧默认使用 FFmpeg；候选帧选择支持 visual-diff、uniform、visual-diff-uniform 和 all。
- 支持 normalized ROI 裁剪后 OCR。

验证：

- schema validation、parser、reference/parameter resolver、action executor、report/resource manifest 和 runner 测试通过。

### 2026-06-16 Fragment 控制流和生命周期作用域

- fragment catalog 支持 `if` / `then` / `else`。
- case DSL 保持线性，schema 和 policy validation 继续拒绝逻辑控制。
- plan/stage/case 增加 scoped `caseSetup*` 和 `caseTeardown*`。
- stage/case inline `parameters` 参与后续 lifecycle、case action 和 element locator 解析。
- `restartApp` 返回前等待目标 app 进入前台，action-level `wait` 可覆盖默认前台等待。

验证：

- parser、schema、parameter/default、execution、runner 和 action executor 相关测试通过。
- 该迭代 `./gradlew build` 通过。

### 2026-06-12 至 2026-06-15 基础骨架

- 初始化 Kotlin/JVM + Gradle 项目、core models、hook bus、execution skeleton、failure/retry interfaces 和 Appium abstraction。
- 引入 `lib/soluna-appium-ext` 作为集成开发组件。
- 添加 Appium Java Client adapter、基础 WebDriver action executor、Android keyboard defaults 和 opt-in 真机 smoke。
- 构建 `PlanRunner`：单 plan 路径入口、device config、managed Appium server、session 创建、case refs、element catalogs、parameter data、fragment catalogs、setup/teardown lifecycle、runtime variables 和 default action wait。
- 添加 `RecoveringWebDriverAdapter`、失败 trace、JSON/HTML 报告、显式截图 manifest、MinIO artifact store、async upload queue、gzip 上传、DingTalk robot sender、upload failure notifier、本地 cleanup 和 CLI runner。
- 添加 iOS WDA 管理、iOS 17+ userspace tunnel、WDA runner bundle discovery 和 forward restart。
- 外部 action DSL 迁移为 keyword-as-field；动作关键字/别名进入 schema 枚举；断言动作改为显式属性/source 断言。
- 增加 `soluna-project.schema.json`、`run-request.schema.json` 和 `run-result.schema.json`，明确 framework / asset project / platform 边界。

验证：

- 初始 `./gradlew test`、`./gradlew build`、schema JSON 检查和 `git diff --check` 在对应迭代通过。

## 当前验证基线

完整框架基线建议：

```bash
jq empty src/main/resources/schemas/v1/*.json
git diff --check
./gradlew test
./gradlew build
```

Appium 插件相关变更建议：

```bash
cd lib/soluna-appium-ext
npm test
npm run build
npm run lint
```

bundled Codex skill 相关变更建议：

```bash
python3 /Users/xieliangji/.codex/skills/.system/skill-creator/scripts/quick_validate.py codex/skills/soluna-ui-autotest-creator
./gradlew installDist
```

当前大工作区包含较多 asset 和框架并行修改；日常变更按触达范围先做 focused verification，收口前再跑完整 Gradle sweep。

## 下一步

- 保持框架文档聚焦框架行为、schema、运行边界和通用能力；业务操作路径继续进入 asset project 文档。
- 按 `docs/architecture.md` 中记录的当前差距推进 v1 收口：
  - 接入 plan `defaults.retryStrategy` 的命名策略映射。
  - 明确或移除 `app.reset` 的自动行为预期。
  - 把 Android host/device-adjacent `adb` 路径迁移到 `soluna-ext` 客户端抽象。
  - 将报告、manifest、通知等副作用逐步 hook consumer 化。
  - 为 `soluna-project.yaml` 增加 project discovery / platform asset 管理入口。
  - 将生命周期通知配置从 artifact store config 中解耦。
- 继续治理动作关键字和别名，避免业务协议语义进入默认 DSL。
- 完善报告资源预览、失败定位和 MinIO 链接可读性。
