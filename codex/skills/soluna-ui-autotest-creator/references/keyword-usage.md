# 关键字使用入口

在 case、fragment、plan setup、stage setup、case setup 或 teardown 中新增/修改 action 前读取本文件。

本文件只提供关键字路由和通用 DSL 合同。字段级写法按任务读取对应动作族文件：

- 基础 UI、App 状态、等待、元素/源码断言：`keyword-core-actions.md`
- 截图、视觉模板、颜色断言、OCR、录屏、ROI：`keyword-visual-ocr.md`
- App log 采集、JSONL 资源、自定义 App log 断言：`keyword-app-log.md`

## 目录

- Action 形式
- 共享字段
- Locator 规则
- 运行时值
- 关键字族
- Fragment Control Flow
- 请求新关键字前

## Action 形式

优先使用 nested keyword 形式：

```yaml
- tap:
    id: open-settings
    element: common.settingsButton
    desc: 打开设置
```

规则：

- keyword 字段就是 action 类型。
- nested payload 必须包含非空 `id`。
- 新资产优先使用 canonical English keyword，除非现有 asset project 已统一使用别名。
- 一个 action 只能有一个 keyword 字段。
- case `actions` 中不能写 `if`；fragment/lifecycle flow 才能使用 fragment control flow。

旧 short form 仍兼容，但不要在新资产中引入：

```yaml
- tap: open-settings
  element: common.settingsButton
```

## 共享字段

- `id`：稳定 action id，nested form 必填。
- `desc`：可读操作意图。
- `element`：元素别名，例如 `common.submitButton`。
- `wait`：action 级等待。
- `saveAs`：采集/读取类 action 写入的运行时变量名。
- `scope`：运行时变量作用域，通常用 `case`；只有后续 case 需要读取时才用 `plan`。
- `resourceId`：screenshot、recording、OCR match frame 或 log 的稳定显式资源 id。

通用 wait 形状：

```yaml
wait:
  timeoutMs: 8000
  intervalMs: 500
```

UI 状态可能转变时使用 `wait`。不要用长全局 sleep 替代状态断言。

## Locator 规则

action 应使用 `element`，不要 inline locator：

```yaml
elements:
  mineTab:
    android:
      strategy: id
      value: com.example:id/tab_mine
    ios:
      strategy: accessibilityId
      value: MineTab
```

规则：

- locator 只定义在 element catalog。
- 优先使用 resource id、稳定 accessibility id、结构 locator、元素相对比例或 visual template。
- 不要在 locator value 中使用语言相关 UI 文案。
- 参数化文案 locator 只允许语言无关值，并要求 `parameterizedTextReason: language_insensitive_text`。
- 固定文案 locator 只允许语言无关值，并要求 `hardcodedTextReason: language_insensitive_text`。
- locator 表达式不能使用 `@x`、`@y`、`@width`、`@height` 等坐标或尺寸属性。

语言相关文案应放在语言版本 data 文件中，用于 assertion、input、OCR 或 app-log assertion。

## 运行时值

静态数据使用参数引用：

```yaml
value: ${auth.phone}
pattern: ${messages.sendCodeCountdownRegex}
template: ${templates.feedbackIcon}
```

执行期采集值使用 runtime variable：

```yaml
expected: "@{case.nicknameBefore}"
roi: "@{case.feedbackRowRoi}"
source: "@{case.loginVideo}"
```

`@{case.*}` 只在当前 case 内有效。`@{plan.*}` 会跨 case 共享，应谨慎使用。

## 关键字族

基础 UI 和状态动作：

- `tap`
- `tapPosition`
- `longPress`
- `swipe`
- `input`
- `wait`
- `restartApp`
- `clearAppData`
- `getText`
- `saveElementRect`
- `assertElementExists`
- `assertElementAttrEquals`
- `assertElementAttrRegexMatch`
- `assertSourceRegexMatch`

视觉、OCR 和录屏动作：

- `screenshot`
- `tapVisualTemplate`
- `assertImageColorRatio`
- `assertImageTextRegexMatch`
- `startScreenRecording`
- `stopScreenRecording`
- `assertScreenRecordingTextRegexMatch`

App log 动作：

- `captureAppLogStart`
- `captureAppLogEnd`
- `customAssertAppLog`

## Fragment Control Flow

`if` 只能用于 fragment 或可复用 lifecycle flow，不能出现在 case `actions`。

```yaml
fragments:
  ensureLoggedIn:
    actions:
      - if:
          assertElementExists:
            id: login-page-present
            element: auth.loginButton
            wait:
              timeoutMs: 2000
              intervalMs: 500
        then:
          - input:
              id: enter-phone
              element: auth.phoneField
              value: ${auth.phone}
          - tap:
              id: submit-login
              element: auth.submitButton
        else:
          - assertElementExists:
              id: already-on-main-page
              element: common.mineTab
              wait:
                timeoutMs: 5000
                intervalMs: 500
```

case 文件保持线性。分支式状态收敛放在 plan、stage、case setup 或 teardown 引用的 fragment 中。

## 请求新关键字前

先读 `capability-gap-gate.md` 并完成证据清单。只有相关动作族方案因通用框架原因无法闭环时，才请求新增关键字；缺数据、旧 XML、locator 选择弱、账号状态、权限或环境问题都不构成新关键字需求。
