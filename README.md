# soluna-ui-autotest

`soluna-ui-autotest` 是一个 Kotlin/JVM UI 自动化执行框架，面向 iOS 和 Android 真机，通过 Appium / WebDriver 协议驱动设备。

本项目是框架和分发合同包，不是业务用例项目。业务 plan、case、data、element、fragment、device config、artifact config 和调试记录应放在外部 Soluna asset project 中；仓库内的 `AIot-Tests/` 只是当前联调用资产。

## 文档入口

- 架构事实来源：[docs/architecture.md](docs/architecture.md)
- Schema 合同：[docs/schemas.md](docs/schemas.md)
- 进度和已知差距：[docs/progress.md](docs/progress.md)
- Codex/Agent 维护规则：[AGENTS.md](AGENTS.md)

当 README 与架构或 schema 文档冲突时，以 `docs/architecture.md` 和真实 schema 为准，并同步更新 README。

## 核心模型

- Gradle group、框架 Kotlin package、CLI mainClass 和框架对外 SPI 均使用 `io.soluna` 命名空间；脚手架生成的扩展项目 `--package` / `--group` 也必须使用 `io.soluna` 或其子命名空间。
- 执行模型：`Plan -> Stage -> Case -> Action`
- 测试表达：YAML DSL
- 执行入口：`soluna run <plan.yaml>`
- 调试入口：`soluna debug <plan.yaml> ...`
- 执行方式：一个 runner 进程绑定一个真机，串行执行 case
- Driver：Appium over WebDriver
- 资产组织：plan-rooted 引用，device/case/element/data/fragment/artifact 都由 plan 直接或间接触达
- 报告：自有 JSON/HTML report，不接入第三方测试报告插件
- 产物：正式执行产物上传到 MinIO；未配置 artifact store 时只写本地报告和资源
- 设备侧扩展：靠近设备、宿主机和 Appium Server 的能力优先放入内置 Appium plugin `soluna-ext`

JUnit 只用于框架开发测试和 opt-in smoke test，不参与运行时 DSL plan 编排。

## 当前能力

框架已经具备可运行基础，当前重点是 v1 合同收口、真实 asset project 驱动的能力补齐和分发一致性。

已实现能力包括：

- schema-first YAML DSL：plan、case、fragment、element catalog、parameter data、device config、artifact store、notification、report、resource manifest、asset project、runner request/result 均有 v1 schema。
- schema `$id` 使用 `https://schemas.io.soluna.local/v1/` 命名空间。
- case DSL 保持线性；fragment 支持 `if` / `then` / `else`，用于可复用状态收敛。
- 关键字即字段动作语法，推荐新资产使用嵌套形式：

```yaml
- tap:
    id: open-mine-tab
    element: common.mineTab
    desc: 打开我的页
```

- 默认 action 覆盖点击、位置点击、长按、滑动、输入、等待、App 重启、Android 清数据、文本/矩形采集、截图、视觉模板点击、颜色断言、OCR、录屏、App log 采集、元素断言、源码断言和自定义 App log 断言。
- `tap` / `longPress` / `swipe` 支持 viewport-visible 元素交互，每次执行前重新定位当前可见区域。
- 参数引用 `${...}` 和运行时变量 `@{case.name}` / `@{plan.name}`。
- managed Appium server：端口分配、plugin/driver 安装校验、`/status` 探测和 FFmpeg PATH 注入。
- managed iOS WDA：go-ios 管理、iOS 17+ userspace tunnel、host-global tunnel 复用。
- recovering WebDriver adapter：逻辑 session 稳定，物理 session / Appium server / WDA 可恢复重建。
- `soluna-ext` 客户端：设备元信息、已安装 app 元信息、WDA bundle 查询、受控命令和日志会话。
- 本地 `execution-result.json` / `index.html` 报告，包含执行摘要、失败摘要、动作元数据、trace 链接、显式资源入口和 per-case 动作明细。
- `plan-resource-manifest.json` 记录 DSL 显式资源，例如 screenshot、recording、OCR match frame，以及当前 runtime 可写出的 App log JSONL 资源。
- 失败诊断 screenshot/page source 进入 diagnostics，通过 report `traceArtifacts` 暴露，不进入 manifest。
- MinIO 异步上传：压缩、重试、bounded drain、上传成功后本地清理。
- DingTalk 生命周期通知和上传失败聚合告警。
- 分发包包含 `tools/`、`plugins/soluna-appium-ext/`、`plugins/app-log/` 和 bundled Codex skill。

当前已知差距见 [docs/progress.md](docs/progress.md)。重要边界包括：`Plan.defaults.retryStrategy` 尚未映射到命名运行时策略；`app.reset` 只 seed 参数，不自动清理数据；`soluna-project.yaml` 当前不参与 CLI project discovery；manifest schema 对 App log JSONL 资源仍需补齐合同。

## Asset Project

推荐外部 asset project 结构：

```text
<asset-root>/
  soluna-project.yaml
  apps/<app-id>/
    plans/
      common/
      device/<model-slug>/
      debug/
    cases/
      common/
      device/common/
      device/<model-slug>/
    data/
      common/
      device/
    elements/
      common.yaml
      device/<model-slug>.yaml
    fragments/
    plugins/app-log/
    docs/
  devices/
    android/
    ios/
  artifacts/
```

执行从 plan path 开始。`deviceConfig`、`artifactStore`、`parameters`、`fragmentRefs`、`caseRefs`、`dataRefs` 和 `elementRefs` 必须写在 YAML 引用链中，不通过 CLI 传入。

`soluna-project.yaml` 已有 schema，是项目元数据和未来平台发现入口；当前 CLI 不读取它来推导默认 plan、device 或 artifact config。

资产归属规则：

- `plans/`：编排 app identity、platform、device config、parameters、fragment refs、stages、case refs、diagnostics、artifact config 和 defaults。
- `cases/`：表达线性用户意图。公共 App 用例放 `cases/common/`，跨型号设备用例放 `cases/device/common/`，型号专属用例放 `cases/device/<model-slug>/`。
- `elements/`：唯一外部 locator 归属。动作使用 `element: alias.name`，不要 inline locator。
- `data/`：输入、期望值、语言文案、regex、模板路径、型号数据和环境值。
- `fragments/`：可复用 setup/teardown 和状态收敛，可以使用 fragment control flow。
- `docs/`：记录业务前置条件、真实设备调试路径、账号/设备限制和最终通过 run/report。

每个 plan 必须声明 `productModel`。公共 App plan 使用 App 展示名；型号相关 plan 使用具体产品型号。`app.reset` 当前不会自动 reset，需要用 lifecycle fragment/action 显式表达。

## Bundled Codex Skill

本仓库维护一个随框架分发的 Codex skill：

```text
codex/skills/soluna-ui-autotest-creator
```

`./gradlew installDist` 会把它复制到：

```text
build/install/soluna/codex/skills/soluna-ui-autotest-creator
```

这个 skill 与框架 DSL、CLI、关键字、debug workflow、报告/产物合同和能力扩展规则强绑定。修改这些合同时，需要同轮更新 skill、脚手架模板和 `docs/progress.md`。

生成最小 asset project：

```bash
python3 codex/skills/soluna-ui-autotest-creator/scripts/create_asset_project.py \
  --output ./My-Tests \
  --project-id my-tests \
  --app-id com.example.app \
  --app-name ExampleApp \
  --product-model ExampleApp \
  --platform android \
  --udid CHANGE_ME_UDID
```

脚手架只生成 starter smoke plan：重启 App、短暂等待、采集显式 screenshot。业务 locator、状态 fragment、测试数据和型号 catalog 必须在真实设备调试后补充。

能力缺口通知 helper：

```bash
python3 codex/skills/soluna-ui-autotest-creator/scripts/send_dingtalk_gap_notice.py \
  --file capability-gap.md \
  --dry-run
```

helper 默认使用内置 Soluna debug DingTalk robot；需要发送到其他机器人时，用 `SOLUNA_CODEX_DINGTALK_WEBHOOK` 和 `SOLUNA_CODEX_DINGTALK_SECRET` 覆盖。capability-gap gate 未完成或用户未批准时，不要发送通知。

## CLI

构建并安装本地 distribution：

```bash
./gradlew installDist
```

执行 plan：

```bash
build/install/soluna/bin/soluna run AIot-Tests/apps/com.ugreen.iot/plans/common/android.yaml
```

常用运行参数：

```bash
soluna run <plan.yaml> \
  --run-id run-001 \
  --param profile.newNickname=SolunaTester \
  --report-root build/soluna-runs \
  --expect passed
```

当前 CLI 没有独立 `validate` 命令。`soluna run` 在启动阶段完成 schema、policy、引用、device、artifact、Appium 和 plugin 检查。

创建 App log assertion plugin 项目：

```bash
soluna scaffold app-log-plugin ./ugreen-audio-log-plugin \
  --plugin-id ugreen-audio \
  --package io.soluna.ugreen.applog \
  --assertion ble-command-ack
```

`--package` 和可选 `--group` 必须使用 `io.soluna` 或其子命名空间。

`customAssertAppLog` 的业务日志解析应放在独立 JVM plugin JAR 中，不写进 case/data/element/fragment。运行时会查找 classpath、distribution `plugins/app-log/*.jar`、当前工作目录 `plugins/app-log/*.jar`、推断出的 asset root `plugins/app-log/*.jar`，以及 `-Dsoluna.appLogPluginDirs=<paths>` 或 `SOLUNA_APP_LOG_PLUGIN_DIRS=<paths>` 指定目录。

## Debug CLI

真实设备调试优先使用长生命周期 shell：

```bash
soluna debug <plan.yaml> shell
```

也可以执行 one-shot debug action：

```bash
soluna debug <plan.yaml> restart-app
soluna debug <plan.yaml> source --out build/soluna-debug/source.xml
soluna debug <plan.yaml> screenshot --out build/soluna-debug/screen.png
soluna debug <plan.yaml> tap --x-ratio 0.50 --y-ratio 0.50
soluna debug <plan.yaml> tap-element --strategy xpath --locator "//XCUIElementTypeButton[1]" --element-x-ratio 0.50 --element-y-ratio 0.50
soluna debug <plan.yaml> longPress --x-ratio 0.50 --y-ratio 0.30 --duration-ms 1200
soluna debug <plan.yaml> longPress-element --strategy xpath --locator "//*[@resource-id='com.example:id/device_card']" --element-x-ratio 0.50 --element-y-ratio 0.50 --duration-ms 1200
soluna debug <plan.yaml> swipe --start-x-ratio 0.50 --start-y-ratio 0.80 --end-x-ratio 0.50 --end-y-ratio 0.25 --duration-ms 500
soluna debug <plan.yaml> swipe-element --strategy xpath --locator "//XCUIElementTypeScrollView[1]" --start-x-ratio 0.50 --start-y-ratio 0.90 --end-x-ratio 0.50 --end-y-ratio 0.10
soluna debug <plan.yaml> input --strategy class --locator XCUIElementTypeTextView --text "debug text" --clear-first true
soluna debug <plan.yaml> tap-template --template AIot-Tests/apps/com.ugreen.iot/data/common/templates/feedback-back-icon.png --roi 0,0.04,0.2,0.12
```

debug 会从 plan 的 device/app config 启动 managed Appium/WDA session，但不执行 case 生命周期，不生成报告、manifest、上传任务或通知。debug 输出只能作为 locator/template/OCR 证据，不要把 debug-only 操作写进业务 case。

## 报告和上传

本地报告默认写入：

```text
build/soluna-runs/{runId}/report/index.html
build/soluna-runs/{runId}/report/execution-result.json
build/soluna-runs/{runId}/report/plan-resource-manifest.json
```

在 plan 中声明 artifact store 后启用 MinIO 上传：

```yaml
artifactStore: ../../../../artifacts/minio.local.yaml
```

示例模板位于：

```text
AIot-Tests/artifacts/minio.template.yaml
AIot-Tests/artifacts/dingtalk.template.yaml
```

上传 object key 形状：

```text
runs/{runId}/report/index.html
runs/{runId}/report/execution-result.json
runs/{runId}/report/plan-resource-manifest.json
runs/{runId}/resources/<explicit-resource>
runs/{runId}/diagnostics/<failure-trace>
```

HTML 中的资源链接会改写为 MinIO URL。`plan-resource-manifest.json` 只服务 DSL 显式资源；失败 trace screenshot/page source 属于 diagnostics。

不要提交真实 MinIO 凭据、DingTalk token 或 secret。把模板复制为 `*.local.yaml` 或放在私有 asset project 中。

失败 trace 和本地清理示例：

```yaml
trace:
  screenshots:
    enabled: true
    beforeAction: onFailure
    retainBeforeActionCount: 5
    upload: onFailure
localArtifacts:
  cleanup:
    mode: after-upload-success
```

`after-upload-success` 只在报告必需资源和入队上传任务全部成功后删除本地 run 目录。

## Runtime Tools 和 OCR

屏幕录制文本识别需要 FFmpeg：

- Appium XCUITest 录制 iOS 屏幕时，需要 Appium server 进程 PATH 中有 `ffmpeg`。
- runner 从录屏提取帧做 OCR 时，也需要 FFmpeg。

可随分发包放置平台二进制：

```text
tools/ffmpeg/macos-arm64/ffmpeg
tools/ffmpeg/macos-x64/ffmpeg
tools/ffmpeg/linux-arm64/ffmpeg
tools/ffmpeg/linux-x64/ffmpeg
tools/ffmpeg/windows-x64/ffmpeg.exe
```

`./gradlew installDist` 会复制 `tools/` 到 `build/install/soluna/tools`。managed Appium server 启动时会把解析出的 FFmpeg 目录注入 PATH。

覆盖路径：

```bash
export SOLUNA_FFMPEG=/path/to/ffmpeg
export SOLUNA_TOOLS_DIR=/path/to/tools
```

也可使用 JVM system properties：`-Dsoluna.ffmpeg.path=...`、`-Dsoluna.tools.dir=...`。

`assertScreenRecordingTextRegexMatch` 默认使用 Paddle OCR。困难的半透明或混合背景文字可在 case 中设置 `recognizer: multimodal`，并只通过运行时环境变量配置 OpenAI-compatible endpoint：

```bash
export SOLUNA_VISUAL_OCR_MULTIMODAL_BASE_URL=http://host:port/v1
export SOLUNA_VISUAL_OCR_MULTIMODAL_API_KEY=<api-key>
export SOLUNA_VISUAL_OCR_MULTIMODAL_MODEL=gpt-5.5
export SOLUNA_VISUAL_OCR_MULTIMODAL_REASONING_EFFORT=high
```

可选调优：

```bash
export SOLUNA_VISUAL_OCR_MULTIMODAL_STREAM=false
export SOLUNA_VISUAL_OCR_MULTIMODAL_STREAM_IDLE_TIMEOUT_MS=120000
export SOLUNA_VISUAL_OCR_MULTIMODAL_STREAM_HTTP_TIMEOUT_MS=300000
export SOLUNA_VISUAL_OCR_MULTIMODAL_PARALLELISM=2
```

不要把 multimodal API key 写入 asset project。

## Appium Plugin

内置 Appium extension 源码位于：

```text
lib/soluna-appium-ext
```

宿主机/设备邻近能力应优先放在该 plugin 层，并由框架通过 client abstraction 消费。plugin 是本仓库集成组件，会随 Soluna distribution 打包；不再按外部 standalone GitHub 项目提交。

managed Appium server 启动时会检查：

- `soluna-ext`：缺失时从本仓库 bundled source 安装；如果已安装版本不是 bundled source，会卸载后重装。
- `uiautomator2` / `xcuitest` driver：缺失时安装。

本机仍需要 Node/npm 和 Appium。managed server 会维护自己启动的 Appium；外部 Appium server 不会被修改。

plugin 开发命令：

```bash
cd lib/soluna-appium-ext
npm ci
npm test
npm run build
npm run lint
```

## 开发验证

常规框架验证：

```bash
./gradlew test
./gradlew build
```

本项目使用 Kotlin JVM 和 Java 21。

按需真机 smoke test：

```bash
SOLUNA_ANDROID_UDID=<device-udid> \
SOLUNA_APPIUM_SERVER_URL=http://127.0.0.1:4725 \
./gradlew test \
  --tests io.soluna.ui.autotest.appium.ext.RealAndroidSolunaExtSmokeTest \
  --tests io.soluna.ui.autotest.appium.driver.RealAndroidAppiumSmokeTest
```

```bash
SOLUNA_APPIUM_RECOVERY_SMOKE=true \
SOLUNA_ANDROID_UDID=<device-udid> \
SOLUNA_APPIUM_EXECUTABLE=/opt/homebrew/bin/appium \
./gradlew test --tests io.soluna.ui.autotest.appium.driver.RealAndroidAppiumRecoverySmokeTest
```

```bash
SOLUNA_MANAGED_APPIUM_SMOKE=true \
SOLUNA_APPIUM_EXECUTABLE=/opt/homebrew/bin/appium \
./gradlew test --tests io.soluna.ui.autotest.appium.server.ManagedAppiumServerSmokeTest
```

```bash
SOLUNA_IOS_WDA_SMOKE=true \
SOLUNA_IOS_UDID=<ios-device-udid> \
SOLUNA_APPIUM_EXECUTABLE=/opt/homebrew/bin/appium \
SOLUNA_GO_IOS_EXECUTABLE=/opt/homebrew/bin/ios \
SOLUNA_IOS_WDA_STARTUP_DELAY_MS=10000 \
./gradlew test --tests io.soluna.ui.autotest.appium.wda.RealIosWdaSmokeTest
```

按需真实 asset plan smoke：

```bash
SOLUNA_UGREEN_PROFILE_SMOKE=true \
SOLUNA_UGREEN_PROFILE_PLAN_PATH=AIot-Tests/apps/com.ugreen.iot/plans/common/android.yaml \
SOLUNA_RUN_ID=ugreen-android-local \
./gradlew test --tests io.soluna.ui.autotest.runner.RealAndroidUgreenProfilePlanTest
```

```bash
SOLUNA_IOS_UGREEN_PROFILE_SMOKE=true \
SOLUNA_IOS_UGREEN_PROFILE_PLAN_PATH=AIot-Tests/apps/com.ugreen.iot/plans/common/ios.yaml \
SOLUNA_RUN_ID=ugreen-ios-local \
./gradlew test --tests io.soluna.ui.autotest.runner.RealIosUgreenProfilePlanTest
```

这些 smoke test 依赖本地真机、Appium、go-ios、账号状态和对应 asset plan 前置条件。未设置对应环境变量时，部分 smoke test 会主动跳过。
