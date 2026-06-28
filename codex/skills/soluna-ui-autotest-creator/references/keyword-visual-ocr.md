# 视觉、OCR 和录屏关键字

显式截图、视觉模板点击、颜色断言、静态图 OCR、屏幕录制、瞬态文本 OCR 和 ROI 流程使用本文件。

先读 `keyword-usage.md`。从真实设备选择 locator、template、threshold、ROI 或 OCR 参数时，同时读 `debug-and-evidence.md`。

## 目录

- ROI 模式
- `screenshot`
- `tapVisualTemplate`
- `assertImageColorRatio`
- `assertImageTextRegexMatch`
- `startScreenRecording`
- `stopScreenRecording`
- `assertScreenRecordingTextRegexMatch`
- 证据规则

## ROI 模式

优先使用元素派生 ROI，不要手写全屏坐标：

```yaml
- saveElementRect:
    id: save-feedback-row-roi
    element: feedback.firstRow
    saveAs: feedbackRowRoi
    asRoi: true
    fullWidth: true
    expandTopRatio: 0.25
    expandBottomRatio: 0.25
```

后续使用：

```yaml
roi: "@{case.feedbackRowRoi}"
```

只有没有稳定元素能锚定区域时，才手写 normalized ROI：

```yaml
roi:
  x: 0.05
  y: 0.65
  width: 0.90
  height: 0.25
```

## screenshot

其他服务、模块、报告消费者或后续 action 需要消费图片时，使用显式 screenshot。显式 screenshot 会写入 `plan-resource-manifest.json`。

```yaml
- screenshot:
    id: capture-result-page
    resourceId: result-page
    saveAs: resultScreenshot
    desc: 截取结果页供下游查看
```

使用 `saveAs` 时，本地图片路径默认写入 `@{case.<name>}`。runner 同时更新 `@{case.lastScreenshot}`。

有明确元素证据时添加 `element`：

```yaml
- screenshot:
    id: capture-status-badge
    element: device.statusBadge
    resourceId: status-badge
    saveAs: statusBadgeImage
```

失败 trace screenshot 是诊断产物，不能替代显式 screenshot action。

## tapVisualTemplate

用于没有稳定可访问元素的非文字视觉控件。template path 放 data 文件。

```yaml
- tapVisualTemplate:
    id: tap-feedback-icon
    template: ${templates.feedbackIcon}
    roi: "@{case.feedbackRowRoi}"
    threshold: 0.78
    scales: [0.8, 1.0, 1.2]
    targetXRatio: 0.5
    targetYRatio: 0.5
    wait:
      timeoutMs: 8000
      intervalMs: 500
```

默认值：

- `threshold`: `0.88`
- `scales`: `[1.0]`
- `targetXRatio` / `targetYRatio`: `0.5`
- `settleMs`: `800`

debug 证据必须包含 screenshot、template file、ROI、threshold、scales、match score 和 bounds。

## assertImageColorRatio

稳定视觉信号是颜色覆盖，而不是特定模板时使用。

```yaml
- screenshot:
    id: capture-map
    resourceId: route-map
    saveAs: routeMapScreenshot
- assertImageColorRatio:
    id: assert-blue-location-marker
    source: "@{case.routeMapScreenshot}"
    color: blue
    minRatio: 0.0005
    minPixels: 50
    roi:
      x: 0.20
      y: 0.35
      width: 0.60
      height: 0.45
    wait:
      timeoutMs: 3000
      intervalMs: 500
```

规则：

- 必填字段：`source`、`color`、`minRatio`。
- 可选字段：`minPixels`、`roi`、`wait`。
- 支持颜色来自 kt-visual `NamedColor`：`red`、`orange`、`yellow`、`green`、`cyan`、`blue`、`purple`、`pink`、`white`、`black`、`gray`。
- 检查的是命名颜色族，不是精确 RGB。
- 带 `wait` 时会重复读取同一个 `source` 图片，不会重新截图。

能定位精确元素时优先截元素图。没有稳定元素时再用全屏截图加 ROI。

## assertImageTextRegexMatch

用于稳定 screenshot 或静态图 OCR，例如产品手册或 PDF 页面。先截图，再断言文本。

```yaml
- screenshot:
    id: capture-manual-page
    resourceId: manual-page
    saveAs: manualScreenshot
- assertImageTextRegexMatch:
    id: assert-manual-keywords
    source: "@{case.manualScreenshot}"
    pattern: "(?s)产品说明书.*UGREEN HiTune T8"
    recognizer: paddle
```

规则：

- 必填字段：`pattern`。
- `source` 默认 `@{case.lastScreenshot}`。
- 可选字段：`roi`、`recognizer`。
- 稳定页面用静态图 OCR；toast-like 瞬态文本用录屏 OCR。

## startScreenRecording

瞬态 UI 事件前使用，例如 toast、动画或混合背景文字。

```yaml
- startScreenRecording:
    id: start-toast-recording
    timeLimitMs: 10000
```

`timeLimitMs` 默认 `10000`。

## stopScreenRecording

瞬态事件后停止录屏，并可保存视频描述符。

```yaml
- stopScreenRecording:
    id: stop-toast-recording
    resourceId: toast-recording
    saveAs: toastVideo
    scope: case
```

runner 同时把最新视频描述符写入 `@{case.lastScreenRecording}`。

## assertScreenRecordingTextRegexMatch

用于 source polling 无法捕捉的瞬态文本。

```yaml
- assertScreenRecordingTextRegexMatch:
    id: toast-text-visible
    source: "@{case.toastVideo}"
    pattern: ${messages.savedToastRegex}
    resourceId: toast-text
    roi:
      x: 0.05
      y: 0.65
      width: 0.90
      height: 0.25
    candidateStrategy: visual-diff-uniform
    candidateMaxFrames: 5
    framesPerSecond: 5
    maxFrames: 40
    recognizer: paddle
```

默认值：

- `source`: `@{case.lastScreenRecording}`
- `recognizer`: `paddle`
- `candidateStrategy`: `visual-diff`
- `candidateMaxFrames`: `5`
- `framesPerSecond`: `5`
- `maxFrames`: `40`
- `visualDifferenceThreshold`: `0.01`

候选帧策略：

- `visual-diff`：选择视觉变化帧。
- `uniform`：均匀采样。
- `visual-diff-uniform`：先选视觉变化帧，再分散候选。
- `all`：OCR 每一帧；只用于短视频和小 ROI。

只有 Paddle OCR 不足且运行时 multimodal 环境变量已配置时，才使用 `recognizer: multimodal`。不要把 multimodal API key 写进资产。

## 证据规则

- 选 ROI 前先抓最新 screenshot/source。
- template 文件放 data/templates，通过参数引用。
- debug 证据记录 threshold 和 scales。
- OCR 证据记录 recording resource id、ROI、candidate strategy、recognizer，以及可用时的 matched frame/resource。
- 显式 screenshot、recording 和 OCR match frame 进入 `plan-resource-manifest.json`；失败 trace screenshot 不进入 manifest。
