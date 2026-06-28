# 基础动作关键字

普通 UI 交互、App 状态、等待、文本/矩形采集、元素断言和源码断言使用本文件。

先读 `keyword-usage.md`，了解 action 形式、locator 规则、runtime value 和 fragment control flow。

## 目录

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

## tap

用于普通元素点击。稳定元素优先使用中心点击。

```yaml
- tap:
    id: open-profile
    element: common.profileEntry
    wait:
      timeoutMs: 8000
      intervalMs: 500
```

可选字段：

- `settleMs`：点击后等待，默认 `800`。
- `ignoreMissingElement` / `ignoreMissingElementReason`：只允许元素点击使用，只用于被批准的条件 UI。当前允许原因只有 `optionalFirmwareUpgradePrompt`。

条件弹窗示例：

```yaml
- tap:
    id: dismiss-firmware-upgrade-prompt-if-present
    element: common.firmwareUpgradeIgnoreButton
    ignoreMissingElement: true
    ignoreMissingElementReason: optionalFirmwareUpgradePrompt
    wait:
      timeoutMs: 5000
      intervalMs: 500
```

## tapPosition

用于明确位置点击。`xRatio` 和 `yRatio` 必填。

- 不带 `element`：比例相对 viewport。
- 带 `element`：比例相对元素当前可见区域。

```yaml
- tapPosition:
    id: set-volume-to-10
    element: device.promptSoundVolumeSlider
    xRatio: 0.64
    yRatio: 0.30
    wait:
      timeoutMs: 5000
      intervalMs: 500
```

viewport tap 只允许用于没有稳定元素或视觉模板的表面，例如 modal backdrop。

```yaml
- tapPosition:
    id: dismiss-backdrop
    xRatio: 0.50
    yRatio: 0.15
```

## longPress

用于长按交互。优先使用元素目标，不用 viewport 坐标。

```yaml
- longPress:
    id: open-device-actions
    element: device.firstDeviceCard
    durationMs: 1000
    wait:
      timeoutMs: 8000
      intervalMs: 500
```

可选字段：

- `durationMs`：按住时长，默认 `1000`。
- `elementXRatio` / `elementYRatio`：元素可见区域内的按压点，默认中心。
- `settleMs`：长按后等待，默认 `800`。
- `xRatio` / `yRatio`：viewport 比例；只在没有稳定元素/模板时使用。

## swipe

用于拖动或滑动。存在稳定滚动容器时，优先使用元素相对滑动。

viewport 示例：

```yaml
- swipe:
    id: scroll-device-detail
    startXRatio: 0.50
    startYRatio: 0.80
    endXRatio: 0.50
    endYRatio: 0.25
    durationMs: 500
```

元素相对示例：

```yaml
- swipe:
    id: scroll-settings-list
    element: device.settingsList
    startElementXRatio: 0.50
    startElementYRatio: 0.90
    endElementXRatio: 0.50
    endElementYRatio: 0.10
```

规则：

- viewport swipe 必须提供四个 viewport ratio 字段。
- element swipe 必须提供 `element` 和四个 element ratio 字段。
- 不要混用 viewport ratio 和 element ratio。
- `durationMs` 默认 `500`；`settleMs` 默认 `800`。

## input

用于向元素输入文本。

```yaml
- input:
    id: enter-phone
    element: auth.phoneField
    value: ${auth.phone}
    clearFirst: true
```

`clearFirst` 默认 `true`。`value` 可使用参数或 runtime variable。

## wait

只用于短暂 UI settle 或无法直接断言的平台延迟。

```yaml
- wait:
    id: wait-after-restart
    durationMs: 2000
```

`durationMs` 和 `timeoutMs` 都表示 sleep 对应毫秒数。优先用 `durationMs`。

## restartApp

重启目标 app 并等待前台状态。

```yaml
- restartApp:
    id: restart-target-app
    appId: ${app.id}
    wait:
      timeoutMs: 15000
      intervalMs: 500
```

通用 restart 应放 setup fragment。除非 case 需要特定重启点，不要在每个业务 case 里重复写。

## clearAppData

用于 Android app 数据 reset 或首启状态收敛。

```yaml
- clearAppData:
    id: reset-app-data
    appId: ${app.id}
    wait:
      timeoutMs: 20000
      intervalMs: 500
```

当前仅支持 Android。它是破坏性操作；除非 plan 明确期望 reset，否则放 focused plan、setup fragment 或隔离的最后阶段。

## getText

采集元素文本到 runtime variable。

```yaml
- getText:
    id: remember-current-nickname
    element: profile.nicknameValue
    saveAs: nicknameBefore
    scope: case
    wait:
      timeoutMs: 8000
      intervalMs: 500
```

后续用 `@{case.nicknameBefore}` 或 `@{plan.nicknameBefore}` 读取，取决于 `scope`。

## saveElementRect

采集元素矩形。后续视觉或 OCR 动作需要归一化 ROI 时使用 `asRoi: true`。

```yaml
- saveElementRect:
    id: save-feedback-row-roi
    element: feedback.firstRow
    saveAs: feedbackRowRoi
    scope: case
    asRoi: true
    fullWidth: true
    expandTopRatio: 0.25
    expandBottomRatio: 0.25
```

ROI 选项：

- `fullWidth` / `fullHeight`：扩展到完整 viewport 宽/高。
- `expandLeftRatio` / `expandRightRatio` / `expandTopRatio` / `expandBottomRatio`：按元素尺寸倍数扩展。

后续使用：

```yaml
roi: "@{case.feedbackRowRoi}"
```

## assertElementExists

用于元素存在或页面状态断言。

```yaml
- assertElementExists:
    id: profile-page-loaded
    element: profile.title
    wait:
      timeoutMs: 10000
      intervalMs: 500
```

## assertElementAttrEquals

用于元素属性等于期望文本或状态。

```yaml
- assertElementAttrEquals:
    id: nickname-restored
    element: profile.nicknameValue
    attr: text/label/name/value
    expected: "@{case.nicknameBefore}"
    wait:
      timeoutMs: 10000
      intervalMs: 500
```

`attr` 可包含 slash-separated 候选。执行器会跳过不支持或空白的候选，使用第一个可读非空值。

## assertElementAttrRegexMatch

用于元素属性匹配 regex。

```yaml
- assertElementAttrRegexMatch:
    id: countdown-visible
    element: auth.sendCodeButton
    attr: text/label/name/value
    pattern: ${messages.countdownRegex}
    wait:
      timeoutMs: 10000
      intervalMs: 500
```

regex 默认是 contains-style 匹配。需要完整匹配时使用 `^...$`。

## assertSourceRegexMatch

相关状态只存在于 page source，且不适合稳定 element alias 时使用。

```yaml
- assertSourceRegexMatch:
    id: source-has-feedback-entry
    pattern: ${feedback.historyEntryRegex}
```

能用 element assertion 时优先用 element assertion。copy 和 regex 放 data 文件。
