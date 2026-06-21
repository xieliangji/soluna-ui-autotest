# soluna-ui-autotest Architecture

本文档记录 `soluna-ui-autotest` 的架构约束和设计边界。它是后续代码骨架、DSL schema、组件 schema、插件接口和报告模型的事实来源。

## 1. 项目定位

`soluna-ui-autotest` 是一个基于 Appium / WebDriver 协议、面向 iOS / Android 真机的 UI 自动化测试框架。

核心目标：

- 使用 YAML DSL 模板表达测试计划、阶段、用例和动作。
- 用例主体默认单线顺序执行，不把用例 DSL 设计成脚本语言。
- 可复用初始化片段允许逻辑控制表达。
- 所有执行产物上传到 MinIO。
- 自研测试报告，不依赖第三方测试报告插件。
- 框架整体可插拔，核心上下文保持紧凑，外围能力通过 hook / async worker 消费。
- 靠近设备、宿主机和 Appium Server 的增强能力，优先放到自定义 Appium 插件中实现。

## 2. 执行模型

执行模型固定为四层：

```text
Plan
  Stage
    Case
      Action
```

- `Plan`：一次测试计划，包含计划级元信息、参数数据引用、设备配置引用、阶段列表和报告配置。
- `Stage`：同一计划下的不同初始状态执行阶段。例如未登录、已登录、权限已授权、清理后状态。
- `Case`：测试用例。默认按动作列表顺序单线执行，不支持逻辑控制。
- `Action`：动作、等待、断言、截图、采集证据等最小执行单元。

执行约束：

- 单个进程内只绑定一个设备，单设备串行执行。
- 多设备执行通过多进程或外部调度实现，每个设备使用独立配置文件。
- 设备配置文件由模板拷贝生成，每个设备实例配置独立存在，推荐用设备 UDID 命名。
- 设备配置可只声明 UDID；平台、名称、型号、系统版本等设备信息优先通过 `soluna-ext` 获取。
- App 默认已安装。
- 是否重置 App 状态可配置。
- Appium session 默认按 `Plan` 复用，但必须有健康检查和恢复机制。
- 执行引擎持有稳定的逻辑 session id；底层 Appium 物理 session 可在 server 异常退出后重建。

运行编排边界：

- DSL 运行时编排由本项目自己的 `PlanRunner` 和执行结果模型负责。
- JUnit/TestNG 不作为 DSL plan 的编排核心，避免其 suite/test/lifecycle/report 模型反向约束 Soluna 的阶段、hook、产物和报告语义。
- JUnit 仅用于框架自身单元测试、集成测试和 opt-in 真机 smoke 测试。
- 后续如需 CI/IDE 兼容，可增加 JUnit XML exporter 或 JUnit Platform adapter，但 adapter 只能消费 Soluna runner 结果，不能支配核心执行。

服务化和平台边界：

- 本项目定位为执行引擎和契约包，不是实际业务用例项目。
- 实际业务资产应作为独立 Soluna asset project 管理；资产项目负责 plans、cases、elements、data、fragments、devices 和 artifact configs。
- 实际业务用例的编写进度、调试路径、账号/设备前置条件和产品差异说明，应记录在对应 asset project 的 `docs/` 目录中，不写入本框架项目的进度文档。
- 本项目维护随分发包交付的 Codex skill `codex/skills/soluna-ui-autotest-creator`，用于指导外部 asset project 的创建、校验、调试、执行编排和严格的框架能力缺口上报；该 skill 跟随框架 schema、CLI、关键字和 debug 行为一起版本化。
- 测试平台负责资产版本、计划选择、参数覆盖、设备调度、执行触发和结果消费，不直接依赖 Kotlin 内部模型。
- 平台调用 Runner 服务时应使用 `run-request.schema.json`；Runner 回传平台时应使用 `run-result.schema.json`。
- `run-result` 是平台消费摘要，不替代报告数据 `execution-result.json` 和显式截图 `plan-resource-manifest.json`。

文件编排边界：

- 最终执行入口以 `Plan` 文件路径为配置引用根。runner 不应再单独接收 device config、data、element、fragment 等配置文件路径。
- `Plan` 必须声明 `deviceConfig`，设备配置只声明设备标识、Appium server 和设备级 capabilities，不声明目标 app。
- 其它配置文件通过 `Plan` 直接引用，或通过 `Plan -> Stage -> Case` 间接引用。
- 测试输入资产推荐按 app id 分组，例如 `AIot-Tests/apps/com.ugreen.iot/{plans,cases,elements,data,fragments}`；`AIot-Tests/devices` 这类设备目录保持在 app 资产根之外。
- 资产项目根可声明 `soluna-project.yaml`，由 `soluna-project.schema.json` 校验。当前 CLI 不强制读取该文件；后续 project resolver 和平台资产管理应以它作为项目发现契约。
- `Plan` 应主要表达测试目的、阶段和用例编排，不应直接承载大段用例步骤。
- 推荐一个 `Case` 一个 YAML 文件，便于按不同测试目的复用和重组。
- App 资产下的 `cases` 目录按模块组织。公共功能放在 `cases/common/...`；各型号设备相关功能放在 `cases/<model-or-module>/...`。这里的型号目录可理解为 app 的业务模块边界，runner 不对目录名赋予特殊语义，只通过 plan 中的 `caseRefs` 装配。
- Case 专属 `data` 文件可以跟随 case 路径和命名组织；跨用例共享数据可以按模块放在 `data/common/mine.yaml` 这类文件中。数据文件表达测试输入、期望值、环境值和文案资源，不承担元素归属。
- `elements` 目录必须保持模块化 catalog，不按单个用例命名。公共模块元素放在 `elements/common.yaml`，型号或模块专属元素放在 `elements/<model-or-module>.yaml`，例如 `elements/UGREEN HiTune X8.yaml`。`common` 可覆盖登录、设备、我的等公共模块元素。
- `Stage` 通过 `caseRefs` 引用 case 文件；inline cases 只作为小型调试或兼容形式保留。
- `Case` 可以通过 `dataRefs` 引用参数数据文件，通过 `elementRefs` 引用元素定义文件。
- 元素定义不应混在参数数据文件里；参数数据负责输入值、期望值、环境值等测试数据。
- 可复用初始化/清理片段通过 fragment catalog 管理，并由 plan/stage/case 以引用方式装配。
- 初始化和清理片段属于 DSL 生命周期，不属于 hook。hook 只观察 before/after 事件，不承载重启 app、准备登录态、清理状态等动作。

## 3. DSL

### 3.1 文件格式

DSL 使用 YAML。

所有 DSL 关键字支持中英双语，但解析后必须映射到统一的内部 action model。中英文只是输入别名，不产生两套执行语义。

### 3.2 用例主体

用例主体不支持逻辑控制：

- 不支持 `if` / `else`
- 不支持 `for` / `while`
- 不支持分支跳转
- 不支持运行时动态插入步骤

用例主体只表达线性意图：

```yaml
cases:
  - id: login_success
    name: 登录成功
    actions:
      - tap:
          id: tap-login-button
          element: login.loginButton
          desc: 点击登录按钮
      - input:
          id: input-username
          element: login.usernameInput
          value: ${account.username}
          desc: 输入用户名
      - assertElementAttrEquals:
          id: assert-home-title
          element: home.title
          attr: name/label/text
          expected: ${home.expectedTitle}
          desc: 校验首页标题
```

动作推荐使用关键字嵌套对象格式，使 `element`、`value`、`desc`、`wait` 等都成为动作关键字的属性。旧的 `tap: action-id` 加同级属性格式仅作为兼容输入保留，不作为新用例首选写法。

### 3.3 生命周期片段

可复用生命周期片段支持逻辑控制，用于处理复杂前置状态或清理状态：

- 条件判断
- 循环
- 重试
- 权限处理
- 登录态准备
- App 状态清理
- 环境准备

初始化/清理片段和用例主体使用不同 schema，避免普通用例逐渐演化为脚本语言。

逻辑控制关键字必须与业务判断解耦。控制结构只表达流程，例如 `if` / `then` / `else`；业务状态判断仍由已有动作或断言关键字完成。不要引入 `ifElementPresent`、`ifLoggedIn` 这类把流程控制、元素探测和业务语义耦合在一起的关键字。

当前实现先支持 fragment schema 中的通用 `if`：

```yaml
actions:
  - if:
      assertElementAttrRegexMatch:
        id: detect-login-page
        element: common.loginPageMarker
        attr: name/label/text
        pattern: ${appState.patterns.loginPage}
        desc: 判断是否在登录页
    then:
      - tap:
          id: enter-guest-device-page
          element: common.guestEntry
          desc: 进入游客设备页
    else: []
```

`if` 下面必须是一个已有动作或断言。该条件动作执行成功表示条件为真；执行失败表示条件为假并进入 `else`，不直接判定用例失败。被选中分支中的动作失败，才会让当前 fragment action 失败。分支中的动作仍按普通动作执行，拥有 action hook 和 trace 处理；条件动作当前作为 predicate 执行，不写入报告动作列表。

plan/stage/case 均可声明：

- `setupFragments` / `setupActions`：在当前层级主流程前执行。
- `teardownFragments` / `teardownActions`：在当前层级主流程后执行。主流程失败时仍会执行，用于恢复 app/账号/环境状态。

`planSetup` / `planTeardown` 只作用于当前 plan；现有 plan 级 `setupFragments` / `setupActions` 和 `teardownFragments` / `teardownActions` 是其兼容写法。`stageSetup` / `stageTeardown` 可在 plan 级或 stage 级表达；当前实现先保留 stage 自身的 `setupFragments` / `setupActions` 和 `teardownFragments` / `teardownActions`。`caseSetup` / `caseTeardown` 支持 plan、stage、case 三种作用域：plan 级作用于当前 plan 内所有 case，stage 级作用于当前 stage 下所有 case，case 级只作用于当前 case。装配顺序为 case setup: plan -> stage -> case；case teardown: case -> stage -> plan。

teardown 失败会让当前层级结果标记为 failed；如果主流程已经失败，teardown 结果只追加记录，不覆盖原失败事实。

### 3.4 参数管理

参数使用单独的数据文件管理，并支持参数化。

参数数据文件使用 YAML，并通过 JSON Schema 校验其 YAML 解析后的 JSON-compatible 数据结构。

设计要求：

- DSL 文件不直接承载大块测试数据。
- 参数数据文件独立管理，可被多个计划复用。
- 参数解析支持作用域和覆盖顺序。
- 敏感数据需要支持脱敏展示和安全注入。
- 输入值、断言期望值、环境变量均可引用参数。
- Stage / case inline `parameters` 必须参与后续生命周期动作、用例动作和元素定位解析，不能只作为元数据保留。
- 元素定位属于 element catalog；只有文案类、多语言资源类定位表达式需要把文案值参数化时，才在元素定义中引用参数。
- 运行时产生的数据不进入参数文件，使用执行变量集保存。每个 plan 和每次 case 执行都有独立变量作用域；case 作用域按 stage/case 执行上下文隔离。
- 运行时变量引用使用 `@{plan.name}` / `@{case.name}`，与参数引用 `${...}` 区分。

建议的覆盖顺序：

```text
runtime overrides > case data refs > device config > stage params > plan params > data file defaults
```

## 4. 元素定位

元素定位表达式不允许基于固定文案的硬编码定位，允许参数化表达。

不允许：

```yaml
locator:
  strategy: text
  value: 登录
```

允许：

```yaml
locator:
  strategy: text
  value: ${i18n.loginButtonText}
```

约束：

- 固定 UI 文案不能直接写在 locator 表达式里。
- 文案类定位必须来自参数、资源字典、环境配置或测试数据。
- 普通业务流程优先使用 resource-id、accessibility id、class、稳定层级或插件增强能力定位，避免因多语言切换导致用例失效。
- 语言设置页、多语言校验页等确实以文案为被测对象的场景，可以使用参数化文案辅助定位。
- 用例中引用元素时使用独立字段，例如 `element: common.nicknameInput`，不使用 `${...}` 参数引用语法。
- `case.schema.json`、`plan.schema.json` 和 `fragment-catalog.schema.json` 的 action 输入不允许直接声明 `locator`；locator 只允许定义在 `element-catalog.schema.json` 中。
- runner 在引用装配阶段把 `element` 解析为内部运行时 `ActionDefinition.locator`，该字段属于执行器输入，不是外部 DSL 输入。
- 当没有稳定元素可定位时，动作可以声明 executor 参数完成非定位交互。例如 `tap` 支持 viewport 相对坐标 `xRatio` / `yRatio`，用于 modal backdrop 这类非元素目标。坐标 tap 不是 locator，不应替代可维护的元素目录定位。
- 元素点击不直接依赖 WebDriver `click()` 或历史缓存元素。运行时必须重新解析当前元素，确认元素与屏幕 viewport 有可见交集，再按元素当前可见区域计算点击点。默认点击元素可见区域中心；需要点击元素内部特定区域时，`tap` 可声明 `elementXRatio` / `elementYRatio`。
- DSL parser / validator 必须能识别并阻断硬编码文案定位。
- 定位策略本身保持可扩展，例如 id、accessibility id、xpath、class chain、predicate、uiautomator、image 等。

元素定义文件示例：

```yaml
schemaVersion: "1.0"
id: ugreen-common
elements:
  nicknameInput:
    android:
      strategy: class
      value: android.widget.EditText
    ios:
      strategy: accessibility id
      value: nickname_input
```

用例引用示例：

```yaml
actions:
  - input: input-nickname
    element: common.nicknameInput
    value: ${profile.newNickname}
    desc: 输入新昵称
```

同一个元素可以同时声明 Android 和 iOS 定位器。runner 根据当前 plan/device 平台选择对应分支；没有平台分支时可退回通用 `strategy` / `value` 定义。

同一个 element catalog 可以同时包含跨平台元素和单平台元素。runner 装配引用时只加载当前平台可用的 locator；当前平台不可用的元素会被跳过，不会因为 catalog 中存在 Android-only 或 iOS-only 元素而阻断另一端计划。若用例实际引用了被跳过的元素，动作解析仍会失败，以暴露该用例不适用于当前平台。

fragment catalog 按实际引用懒解析动作。共享 fragment 文件中可以同时存在 Android-only 和 iOS-only 的状态收敛片段；只要当前 plan/stage/case 没有引用对应片段，就不会解析其元素和视觉资产。当前平台实际引用到不支持的 fragment 时，解析仍会失败。

## 5. Hook 机制

所有执行层级都必须提供前后 hook：

```text
plan.before
plan.after
stage.before
stage.after
case.before
case.after
action.before
action.after
```

默认日志记录器订阅：

```text
plan.before
plan.after
stage.before
stage.after
case.before
case.after
action.before
```

设计要求：

- 核心上下文保持紧凑，只保存执行必要状态。
- hook payload 使用事件快照，不要求 consumer 直接持有可变核心上下文。
- 影响执行结果的 hook 可以同步执行。
- 产物上传、数据整理、报告聚合、钉钉通知等副作用优先异步执行。
- hook consumer 必须可插拔，可按配置启用、禁用或替换。

典型 hook consumer：

- 执行日志记录器
- 设备日志会话管理器
- 显式截图资源清单收集器
- MinIO 上传调度器
- 报告数据聚合器
- 钉钉通知器
- 失败诊断采集器

当前实现：

- `HookEvent` / `HookEventType` 定义计划、阶段、用例、动作前后事件。
- `SimpleHookBus` 提供同步 hook 分发骨架。
- `DefaultLoggingHook` 订阅默认日志节点：动作前、用例前后、阶段前后、计划前后。
- `PlanRunner` 默认注册 `DefaultLoggingHook`，通过 SLF4J 输出生命周期日志；命令行运行必须携带 SLF4J backend。
- managed Appium server 和 managed WDA/go-ios 管理器在启动、端口分配、命令构造、PATH 注入、ready probe、停止和失败清理等关键位置输出包级 debug 日志；日志不输出完整环境变量值，并对命令中的敏感参数做基础脱敏。
- `LinearExecutionEngine` 在执行 `Plan -> Stage -> Case -> Action` 时发布完整生命周期事件。

## 6. 失败策略和重试策略

失败策略和重试策略都必须可配置、可插拔。

失败策略需要覆盖：

- action 失败后是否终止当前 case
- case 失败后是否继续执行同 stage 后续 case
- stage 失败后是否继续执行后续 stage
- plan/stage/case 主流程失败后仍执行 teardown 生命周期动作
- 初始化片段失败后是否允许恢复或重试
- Appium session 异常后是否重建 session 并继续

重试策略需要覆盖：

- action 级重试
- case 级重试
- 初始化片段重试
- Appium / WebDriver 请求重试
- MinIO 上传任务重试
- 钉钉通知发送重试

策略接口应能按错误类型、动作类型、阶段、设备、历史失败次数和时间窗口做决策。

当前实现提供 `FailureStrategy` 接口和基础实现。

`FailFastFailureStrategy` / plan `defaults.failureStrategy: stop-case` 或 `fail-fast`：

- action 失败后停止当前 case。
- case 失败后停止当前 stage。
- stage 失败后停止整个 plan。
- plan/stage/case 的 teardown actions 不因对应主流程失败而跳过；teardown 自身失败会让当前层级结果为 failed。

`ContinueCaseFailureStrategy` / plan `defaults.failureStrategy: continue-case`：

- action 失败后停止当前 case。
- case 失败后继续执行同一 stage 的后续 case。
- stage 失败后继续执行后续 stage。
- plan 最终状态仍会反映任一失败 case 或 stage。

后续可在该接口下加入分级中断、重试后决策等策略。

当前实现还提供 `RetryStrategy` 接口和两个基础实现：

- `NoRetryStrategy`：默认不重试。
- `MaxAttemptsRetryStrategy`：按最大尝试次数进行 action 级重试。

`LinearExecutionEngine` 已接入 action 级 retry。默认 no-retry 时行为保持 fail-fast；配置可重试策略后，同一个 action executor 会重复执行，最终结果进入 action result。

## 7. 等待模型

框架提供默认隐式等待，同时支持显式等待覆盖隐式等待。

要求：

- 默认隐式等待用于减少普通元素查找抖动。
- plan 可通过 `defaults.actionWait` 声明 action 级显式等待默认值，避免少数同类慢动作重复写相同的 timeout / interval。它不是隐式等待，不应作为所有动作的全局 10s 等待开关使用。
- action 可声明显式等待，显式等待优先于默认隐式等待。
- action 自己声明的 `wait` 优先级高于 `defaults.actionWait`，用于处理个别慢页面或特殊等待条件。
- `tap` 默认在点击后等待 800ms 作为 UI 转场 settle；可通过 `settleMs` 覆盖，或设为 `0` 关闭。
- 显式等待应支持条件表达式，例如可见、存在、消失、可点击、文本匹配、属性匹配、页面稳定。
- 等待策略可插拔。
- 等待结果应进入执行数据，但不要污染核心上下文。

## 8. 断言模型

支持多种断言共存，并保持可扩展。

初版断言类型：

- 元素存在 / 不存在
- 元素可见 / 不可见
- 元素属性断言
- 参数值断言
- 设备状态断言
- 日志断言
- 截图资源存在性断言

断言引擎应通过 registry 注册，DSL 只绑定断言类型和参数，不直接绑定实现类。

## 9. Appium Server 和自定义插件

项目自身应提供 Appium Server 维持机制，而不是完全依赖外部 server 运维。

要求：

- 框架可启动、停止和监控 Appium Server。
- 自定义 Appium 插件可随 server 一起打包和启用。
- Appium Server 意外退出时，框架可按策略重启。
- 重启后需要重建 Appium session，并对当前 plan 的可恢复性做判断。
- Appium session 默认按 plan 复用，但要具备健康检查、请求超时和失效恢复。

自定义插件边界：

- 设备查询
- iOS 已安装 WDA runner bundle 查询
- 设备日志
- `adb` / `go-ios` / `ios` 受控命令
- 宿主机依赖检查
- 设备文件、系统状态等靠近宿主机的增强能力

框架侧通过插件 HTTP API 消费这些能力，避免在测试框架中散落宿主机命令。

插件源码作为本项目内置组件维护在 `lib/soluna-appium-ext`，跟随当前框架一起开发、验证、提交和分发。插件能力扩展不再准备回提交到原独立 GitHub 项目。

插件能力协商、版本兼容、schema、实现和分发都由本项目统一协调。框架侧仍应通过插件 HTTP API 和客户端抽象消费能力，避免宿主机命令散落到执行器中。

当前状态摘要：

- 默认 driver 适配器基于 Appium Java Client；框架内部通过 `WebDriverAdapter` 抽象隔离实现。
- `PlanRunner` 只接收 plan 路径，按引用链解析 device、data、case、element、fragment 和 artifact 配置。
- managed Appium server 支持自动端口、Appium 扩展安装检查、`/status` readiness probe、插件启用和进程清理；外部 server 仍可配置。启动 managed server 前，框架会确保 `usePlugins` 中的插件和 `ensureDrivers` 中的 drivers 已安装；默认会确保项目自带 `soluna-ext`、`uiautomator2` 和 `xcuitest` 可用。`soluna-ext` 必须来自当前项目绑定源码，若宿主机已安装的同名插件不是项目源，会先卸载再从项目源安装。
- session 创建只绑定设备，不顺带启动目标 app；Android 默认启用 `appium:unicodeKeyboard=true` 和 `appium:resetKeyboard=true`。
- `RecoveringWebDriverAdapter` 维护逻辑 session，可在 managed Appium server、managed iOS WDA 或物理 session 失效后重建底层 session；若 WDA 不健康，恢复流程会先重启 WDA，再用新的 `appium:webDriverAgentUrl` 重建 Appium session。
- WebDriver 命令需要有有界等待。默认 adapter 在容易被 WDA/Appium 慢响应拖住的截图、source、元素查找、元素矩形、窗口尺寸、输入和 session health check 等命令外层加显式超时；命令超时属于 session 恢复信号，恢复判断不能再依赖一个可能继续卡住的无界 health check。
- iOS WDA 由 `LocalGoIosWdaManager` 通过 go-ios 管理，iOS 17+ 使用 userspace tunnel，并保证 runwda 重启后 forward 同步重启；go-ios v1.0.x 的 tunnel-info CLI 只传 `--tunnel-info-port`。
- `soluna-ext` 客户端用于设备元信息、iOS WDA runner bundle 和受控宿主机命令等设备邻近能力。
- 默认 action executor 覆盖 tap/input/wait/restartApp/clearAppData/getText/saveElementRect/screenshot/screen recording、视觉模板点击、属性/source 断言和录屏文本 OCR 断言；断言可按 `wait` 轮询。元素 tap 会重新定位当前元素、过滤屏幕外元素，并按元素可见区域计算点击点。`clearAppData` 当前是 Android 专用动作，通过 `pm clear` 清理应用数据并重新激活应用；如果当前 Android session 申请了 `autoGrantPermissions`，清理后会重新授予 package runtime permissions，避免首启流程被系统权限弹框打断。`saveElementRect` 可把元素可见矩形保存为像素 rect 或归一化 ROI，供后续步骤通过运行时变量引用。视觉模板点击通过当前截图、data 目录模板资产、归一化 `roi` 和 kt-visual 匹配得到目标区域，再转换为视口比例点击，不暴露平台 back 这类平台敏感动作；其 `roi` 可直接写对象或引用 `saveElementRect` 保存的 ROI，且 action 级 `wait` 会触发重复截图匹配。录屏文本断言默认使用 kt-visual Paddle OCR，也可通过 action 的 `recognizer: multimodal` 切到 OpenAI-compatible kt-visual multimodal OCR；多模态候选帧并发识别，stream 模式按 reasoning/content 输出刷新 idle timeout。动作级显式 `wait` 会覆盖元素查找的隐式等待预算：执行显式轮询期间临时关闭 session implicit wait，结束后恢复。`restartApp` 和 `clearAppData` 在返回前都需要等待目标 app 进入前台，动作级 `wait` 可覆盖默认前台等待预算。FFmpeg 作为项目运行工具解析，优先使用显式配置或分发包内 `tools/ffmpeg/<os>-<arch>/ffmpeg(.exe)`，最后才回退到宿主机 PATH。
- Android/iOS opt-in 真机验证覆盖基础 Appium、session recovery、AIot asset plan 执行、MinIO 上传和钉钉通知链路。

实现细节以代码和 schema 为准；本节只保留边界和能力摘要，避免后续维护两套细粒度说明。

## 10. MinIO 产物上传

所有执行产物最终都需要上传到 MinIO。

上传不能阻塞测试执行主流程，应由后台线程或异步 worker 处理。

要求：

- 上传任务进入队列，由后台 worker 消费。
- 上传状态可被监控。
- 上传失败采用智能重试。
- 对单个偶发失败任务保持持续重试或长周期重试。
- 当连续长时间出现同一时间段大量任务失败时，通过钉钉发送告警。
- 告警策略需要去重和抑制，避免刷屏。
- 上传请求启用压缩相关能力；文本类产物可压缩后上传，图片、视频等已压缩资源不重复压缩。
- MinIO 对象 key 生成规则稳定，可从 run / plan / stage / case / artifact 类型推导。
- 上传 worker 需要在 plan 结束后支持 bounded drain，用于确保报告必需资源完成上传或明确标记失败。

上传任务状态建议：

```text
pending
uploading
uploaded
failed_retryable
failed_permanent
abandoned
```

当前状态摘要：

- `artifact-store.schema.json` 定义 MinIO、上传队列、压缩、重试、通知引用和本地清理相关配置。
- `ArtifactStore` / `ArtifactUploader` 隔离存储实现；默认实现为 MinIO + 后台上传队列。
- 对象 key 规则稳定：`{prefix}/runs/{runId}/{report|resources|diagnostics}/{fileName}`。
- 文本类报告产物默认 gzip 上传；图片等已压缩资源不重复压缩。
- `localArtifacts.cleanup.mode: after-upload-success` 只在全部上传任务成功后删除本地 run 目录。

## 11. 显式资源清单

trace 中的截图按普通诊断产物处理，不进入显式资源清单。

当前 trace 截图策略：

- `trace.screenshots.enabled` 控制是否启用动作前 trace 截图。
- `beforeAction: onFailure` 在执行期只保留最近 N 张动作前截图和 page source 到内存，不为通过动作落盘。
- 当 action 最终失败时，保留的动作前截图和 page source XML 写入本地 `diagnostics/trace` 并以 `ArtifactKind.DIAGNOSTIC` 入队上传。
- 如果报告引用 trace 截图，启用 artifact uploader 时报告数据使用 MinIO URL；未启用上传时使用本地路径。
- trace 截图上传不写入 `plan-resource-manifest.json`，该 manifest 只面向业务 DSL 显式请求的资源。
- 正式 plan 运行中，managed iOS WDA/go-ios 子进程日志写入本地 run 目录的 `diagnostics/wda`，用于定位 WDA 启动、隧道和端口转发问题；该目录不进入显式资源清单。

本地调试可使用 `soluna debug <plan.yaml> source|screenshot|tap|tap-element|input|tap-template`，也可以使用 `soluna debug <plan.yaml> shell` 在同一个临时 Appium/WDA session 中逐步执行这些低层动作。debug 命令复用 plan 的 device/app 配置、managed Appium server、soluna-ext 设备解析和 iOS WDA 管理，只执行少量定位、输入、截图或模板点击动作，不进入 plan/stage/case 生命周期，不生成报告、上传或通知。该路径用于采集 page source、截图或验证定位和视觉模板点击，不能替代正式 DSL 用例。

用例 DSL 中显式截图、录屏和录屏分析命中帧等资源，需要统一整理到一个 JSON 文件中。该 JSON 主要面向其他服务或模块消费，不是步骤级执行明细。

要求：

- 只收集业务 DSL 显式请求保留的资源；失败 trace 仍走诊断产物链路。
- JSON 文件包含执行计划整体元信息和资源列表。
- JSON 文件与测试报告文件放在 MinIO 同一级目录。
- 测试报告中必须包含该 JSON 文件的引用链接。
- JSON schema 必须版本化。

建议文件名：

```text
plan-resource-manifest.json
```

示例结构：

```json
{
  "schemaVersion": "1.0",
  "plan": {
    "planId": "daily-smoke",
    "planName": "每日冒烟",
    "planVersion": "2026.06.12",
    "environment": "staging",
    "source": {
      "type": "yaml",
      "uri": "minio://bucket/plans/daily-smoke.yaml"
    }
  },
  "resourceBatch": {
    "runId": "run-20260612-001",
    "generatedAt": "2026-06-12T10:00:00.000Z",
    "minioPrefix": "runs/run-20260612-001/report/"
  },
  "resources": [
    {
      "resourceId": "home_after_login",
      "type": "image",
      "purpose": "explicit_screenshot",
      "name": "登录后首页",
      "objectKey": "runs/run-20260612-001/resources/home_after_login.png",
      "url": "https://minio.example.com/bucket/runs/run-20260612-001/resources/home_after_login.png",
      "contentType": "image/png"
    }
  ]
}
```

当前状态摘要：

- 显式截图、显式录屏和录屏文本断言命中帧由 `PlanResourceSink` 写入本地资源目录，再由 `PlanResourceManifestWriter` 生成 `plan-resource-manifest.json`。
- `startScreenRecording` / `stopScreenRecording` 使用 Appium Java Client 的录屏能力。Appium XCUITest driver 的 iOS 录屏会在 Appium server 进程内调用名为 `ffmpeg` 的命令；managed Appium server 启动时会把解析到的项目绑定 FFmpeg 目录 prepend 到 PATH，外部 Appium server 需要由调用方自行保证 PATH。`assertScreenRecordingTextRegexMatch` 使用同一个 FFmpeg 工具解析器抽帧，可先按归一化 `roi` 裁剪，再用 `visual-diff` / `uniform` / `visual-diff-uniform` / `all` 候选帧策略控制 OCR 工作量，并交给 kt-visual Paddle OCR 或 OpenAI-compatible multimodal OCR 做文本匹配。多模态 OCR 的 base URL、API key、model、reasoning effort、prompt、非 stream timeout、stream idle timeout、stream HTTP timeout、parallelism 和 stream 开关只来自运行时系统属性或环境变量，不写入用例资产。`tapVisualTemplate` 使用相同的归一化 ROI 约定约束模板匹配范围。
- manifest 只保存计划级元信息和显式资源列表；不保存 action 执行明细。
- 启用 artifact uploader 时，manifest 中的资源包含 MinIO object key 和 URL。

## 12. 报告设计

报告不使用第三方测试报告插件。

报告输出：

- 单体 HTML 文件。
- 报告引用的所有资源都必须是 MinIO 链接。
- 报告数据使用 JSON 文件保存，可以拆分为多个 JSON。
- 报告器组件消费原始执行结果产物和数据 JSON，生成不同形态的报告。

建议同级目录：

```text
runs/{runId}/report/index.html
runs/{runId}/report/report-summary.json
runs/{runId}/report/report-details.json
runs/{runId}/report/plan-resource-manifest.json
```

报告 HTML 中至少引用：

- `report-summary.json`
- `report-details.json`
- `plan-resource-manifest.json`

报告数据和报告渲染器分离，为后续替换报告器组件预留空间。

当前状态摘要：

- `LocalReportWriter` 写出 `execution-result.json` 和单体 `index.html`。
- `report-data.schema.json` 定义报告数据视图；它不是内部执行结果模型的直接序列化。当前数据视图包含执行摘要、失败摘要、生命周期 action 列表、action id/keyword/attempt/duration 等动作元数据，以及 trace artifacts。
- 报告 HTML 引用 `execution-result.json` 和 `plan-resource-manifest.json`，启用上传时链接改写为 MinIO URL。HTML 首屏展示 plan/run/device 信息、stage/case/action 统计、失败摘要、动作时间线和 trace artifact 链接。
- 报告必需资源会执行 bounded drain；失败 trace 截图进入 `traceArtifacts`，显式截图进入 manifest。

## 13. 钉钉通知

钉钉通知应设计成可配置、可插拔、可复用组件。

要求：

- 可在不同上下文作为组件引用。
- 支持 plan 开始通知。
- 支持测试执行结束通知，并区分正常结束、失败结束和 runner 异常结束。
- 支持测试报告发布通知。
- 支持 stage 失败通知。
- 支持上传系统异常告警。
- 支持 Appium Server 反复退出告警。
- 支持通知模板。
- 支持告警抑制、去重和频率控制。

钉钉通知不应直接耦合执行引擎，而应作为 hook consumer 或后台监控组件接入。

当前状态摘要：

- `notification-sender.schema.json` 定义 DingTalk robot sender；webhook/secret 支持直接 YAML 配置，也保留 env 间接引用。
- `PlanRunner` 支持 `planStarted`、`testFinished`、`reportPublished` 三个生命周期通知点；旧 `planFinished` 兼容映射到 `reportPublished`。生命周期通知包含 plan/run/device/platform、计划规模、执行统计、首批失败 action 摘要、trace artifact 数量、上传状态和报告链接。
- `DingTalkUploadFailureNotifier` 聚合上传失败告警，支持时间窗口、阈值和抑制间隔。

## 14. Schema First

所有组件都应有对应 schema 定义，方便 Codex agent 和跨项目消费者稳定使用。

当前使用 JSON Schema 作为外部数据合同格式，schema 文件位于：

```text
src/main/resources/schemas/v1/
```

Kotlin 数据模型是运行时模型，不替代对外 schema。

schema 需要版本化，并在运行前做严格校验。当前 v1 schema 文件和覆盖范围以 [docs/schemas.md](schemas.md) 为准；本文档只保留 schema-first 原则和边界，不重复维护完整清单。

## 15. 插件化组件边界

核心层只依赖接口，不绑定具体实现。

初版 SPI：

- `DslParser`
- `SchemaValidator`
- `KeywordRegistry`
- `ExecutionEngine`
- `ActionExecutor`
- `InitFragmentExecutor`
- `WebDriverClient`
- `AppiumServerManager`
- `SolunaAppiumExtClient`
- `DeviceService`
- `WaitStrategy`
- `AssertionEngine`
- `FailureStrategy`
- `RetryStrategy`
- `HookBus`
- `ArtifactStore`
- `UploadQueue`
- `ReportDataWriter`
- `ReportRenderer`
- `PlanResourceManifestWriter`
- `NotificationSender`

默认实现可以简单，但接口边界要先稳定。

## 16. 当前收口状态

当前已具备可运行骨架：schema-first DSL、plan-rooted runner、Android/iOS 真机 Appium 执行、managed Appium/WDA、session recovery、MinIO 上传、报告数据/HTML、显式截图 manifest、失败 trace、本地清理、DingTalk 生命周期通知、debug CLI、视觉模板点击和录屏 OCR 分析均已打通。

后续不再扩张基础说明文档。新增能力优先通过以下方式记录：

- schema 变化写入 `src/main/resources/schemas/v*/` 和 [docs/schemas.md](schemas.md)。
- 架构边界变化写入本文档对应章节。
- CLI、schema、关键字、debug 行为、报告产物或能力扩展流程变化时，同步检查并更新 `codex/skills/soluna-ui-autotest-creator`。
- 每轮框架实现只在 [docs/progress.md](progress.md) 追加高层摘要，不记录长命令输出和完整调试过程。
- 真实业务用例的编写进度、逐条调试状态和详细操作路径不进入本项目文档；这些内容进入对应 asset project 的 `docs/` 目录。
- 真实业务用例暴露出的动作关键字、报告体验和稳定性需求，只有在抽象为框架能力或契约变化后，才进入本项目文档和 v1 迭代记录。

## 17. v1 设计入口

v1 由真实业务用例驱动，优先关注：

- Soluna asset project 契约、project resolver 和平台 Runner 服务边界。
- 动作关键字补齐和别名治理。
- 轮询等待、条件等待和失败诊断的进一步抽象。
- 报告信息密度、资源预览、失败定位和 MinIO 链接可读性。
- plan 生命周期通知从 artifact-store 配置中解耦。
- `lib/soluna-appium-ext` 作为项目内置插件的版本治理、能力协商和分发校验。
