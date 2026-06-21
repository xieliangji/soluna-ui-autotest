# Keyword Usage

Use this reference before adding or changing Soluna case, fragment, plan setup, or teardown actions. It explains how to write the supported DSL keywords, not just what keywords exist.

## Contents

- Action form
- Shared fields
- Element locators
- Runtime values
- Keyword recipes
- Fragment control flow
- Capability-gap checklist

## Action Form

Prefer the nested keyword form for every action:

```yaml
- tap:
    id: open-settings
    element: common.settingsButton
    desc: Open settings.
```

The keyword field is the action type. Its object payload must include `id`. Use the canonical English keyword unless an existing asset project already uses aliases.

Some legacy short forms are accepted for simple actions, but avoid creating new short-form actions because required fields are split between the keyword value and sibling fields:

```yaml
- tap: open-settings
  element: common.settingsButton
```

Do not put more than one keyword field in one action. Do not put `if` in case actions; `if` is allowed only in fragments and lifecycle actions that use fragment syntax.

## Shared Fields

- `id`: Stable action id. Required in nested form.
- `desc`: Human-readable operation intent.
- `element`: Element alias, such as `common.submitButton`. Define the locator in an element catalog.
- `wait`: Per-action wait object. Common shape:

```yaml
wait:
  timeoutMs: 8000
  intervalMs: 500
```

Use `wait` on element actions and assertions when the UI may transition. Do not replace state checks with long global sleeps.

## Element Locators

Actions should reference `element`, not inline locator expressions. Define platform locators in catalog files:

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

If copy-based matching is unavoidable, put the copy in parameter data and reference it from the locator. Do not hardcode business copy in locator values.

## Runtime Values

Use parameter references for static test data:

```yaml
value: ${auth.phone}
pattern: ${messages.sendCodeCountdownRegex}
template: ${templates.feedbackIcon}
```

Use runtime variable references for values captured during execution:

```yaml
expected: "@{case.nicknameBefore}"
roi: "@{case.feedbackRowRoi}"
source: "@{case.loginVideo}"
```

`scope` defaults to `case` for captured values. Set `scope: plan` only when later cases must read the value.

## Keyword Recipes

### tap

Use for element taps. Prefer element taps over viewport coordinates.

```yaml
- tap:
    id: open-profile
    element: common.profileEntry
    wait:
      timeoutMs: 8000
      intervalMs: 500
```

Optional fields:

- `elementXRatio`, `elementYRatio`: Click inside the visible element area. Defaults to center `0.5`.
- `settleMs`: Post-tap settle delay. Defaults to `800`.
- `xRatio`, `yRatio`: Viewport tap ratios. Use only when no stable element or visual template can model the action.

Viewport tap example:

```yaml
- tap:
    id: dismiss-backdrop
    xRatio: 0.50
    yRatio: 0.15
    desc: Dismiss modal backdrop when it exposes no element.
```

### input

Use for text input into an element.

```yaml
- input:
    id: enter-phone
    element: auth.phoneField
    value: ${auth.phone}
    clearFirst: true
```

`clearFirst` defaults to `true`. Values are scalar and may be parameter or runtime references.

### longPress

Use for press-and-hold interactions such as opening a contextual action sheet from a list item. Prefer an element target over viewport coordinates.

```yaml
- longPress:
    id: open-device-actions
    element: device.firstDeviceCard
    durationMs: 1000
    wait:
      timeoutMs: 8000
      intervalMs: 500
```

Optional fields:

- `durationMs`: Press duration in milliseconds. Defaults to `1000`.
- `elementXRatio`, `elementYRatio`: Press inside the visible element area. Defaults to center `0.5`.
- `settleMs`: Post-press settle delay. Defaults to `800`.
- `xRatio`, `yRatio`: Viewport press ratios. Use only when no stable element or visual template can model the action.

Aliases are `longTap`, `pressAndHold`, `长按`, and `长按点击`; prefer canonical `longPress` in new assets.

### wait

Use only for brief UI settling or platform delays that cannot be asserted directly.

```yaml
- wait:
    id: wait-after-restart
    durationMs: 2000
```

`durationMs` and `timeoutMs` both sleep for that many milliseconds in the current executor. Prefer `durationMs` for clarity.

### restartApp

Use to restart the target app and wait for foreground state.

```yaml
- restartApp:
    id: restart-target-app
    appId: ${app.id}
    wait:
      timeoutMs: 15000
      intervalMs: 500
```

Put common restart behavior in setup fragments. Do not repeat it in every business case unless the case needs a specific restart point.

### clearAppData

Use for Android app data reset flows or first-use state convergence.

```yaml
- clearAppData:
    id: reset-app-data
    appId: ${app.id}
    wait:
      timeoutMs: 20000
      intervalMs: 500
```

Treat it as destructive. Keep it in focused plans, setup fragments, or final isolated stages unless the plan explicitly expects data reset.

### getText

Use to capture element text into runtime variables.

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

Read it later with `@{case.nicknameBefore}` or `@{plan.nicknameBefore}` depending on scope.

### saveElementRect

Use to capture an element rectangle. Use `asRoi: true` when the next visual or OCR action needs a normalized ROI.

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

ROI options:

- `fullWidth`, `fullHeight`: Expand ROI to full viewport width or height.
- `expandLeftRatio`, `expandRightRatio`, `expandTopRatio`, `expandBottomRatio`: Expand by a multiple of the element size.

Use the ROI with `roi: "@{case.feedbackRowRoi}"`.

### tapVisualTemplate

Use for visual affordances that do not expose a stable element. Store template image paths in data files.

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

Defaults:

- `threshold`: `0.88`
- `scales`: `[1.0]`
- `targetXRatio`, `targetYRatio`: `0.5`
- `settleMs`: `800`

Record debug evidence for the chosen screenshot, template, ROI, threshold, scales, match score, and bounds.

### screenshot

Use explicit screenshots when another service or module must consume the resource. These screenshots are written to `plan-resource-manifest.json`.

```yaml
- screenshot:
    id: capture-result-page
    resourceId: result-page
    desc: Capture result page for downstream review.
```

Failure trace screenshots are diagnostics and do not replace explicit screenshot actions.

### startScreenRecording

Use before a transient UI event such as a toast, animation, or mixed-background text.

```yaml
- startScreenRecording:
    id: start-toast-recording
    timeLimitMs: 10000
```

`timeLimitMs` defaults to `10000`.

### stopScreenRecording

Use after the transient event and optionally save the video path for OCR.

```yaml
- stopScreenRecording:
    id: stop-toast-recording
    resourceId: toast-recording
    saveAs: toastVideo
    scope: case
```

The runner also saves the last video path to `@{case.lastScreenRecording}`.

### assertElementExists

Use for presence or page-state assertions.

```yaml
- assertElementExists:
    id: profile-page-loaded
    element: profile.title
    wait:
      timeoutMs: 10000
      intervalMs: 500
```

### assertElementAttrEquals

Use when a known element attribute must equal expected text or state.

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

`attr` may contain slash-separated candidates. The executor uses the first non-blank attribute value.

### assertElementAttrRegexMatch

Use when an element attribute should match a regex.

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

Regex uses DOT_MATCHES_ALL. Escape regex content in YAML as needed.

### assertSourceRegexMatch

Use when the relevant state is only visible in page source and a stable element alias is not practical.

```yaml
- assertSourceRegexMatch:
    id: source-has-feedback-entry
    pattern: ${feedback.historyEntryRegex}
```

Prefer element assertions when possible. Keep copy in data files.

### assertScreenRecordingTextRegexMatch

Use for transient text such as toast-like messages or text that source polling cannot capture.

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

Defaults:

- `source`: `@{case.lastScreenRecording}`
- `recognizer`: `paddle`
- `candidateStrategy`: `visual-diff`
- `candidateMaxFrames`: `5`
- `framesPerSecond`: `5`
- `maxFrames`: `40`
- `visualDifferenceThreshold`: `0.01`

Use `recognizer: multimodal` only when Paddle OCR is insufficient and runtime multimodal environment variables are configured.

## Fragment Control Flow

Use `if` only in fragments or reusable lifecycle flows, not in case `actions`.

```yaml
fragments:
  ensureLoggedIn:
    name: Ensure logged-in state
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

Keep case files linear. Put branchy state convergence in fragments referenced by plan, stage, or case setup.

## Capability-Gap Checklist

Before requesting a new keyword or framework capability, prove why the current recipes cannot close the scenario:

- Element action path tried: `tap`, `input`, `getText`, element assertions.
- Source path tried: `assertSourceRegexMatch` with parameterized regex.
- Visual path tried: `saveElementRect` plus `tapVisualTemplate` or OCR ROI.
- Transient text path tried: `startScreenRecording`, `stopScreenRecording`, `assertScreenRecordingTextRegexMatch`.
- State orchestration tried: plan/stage/case setup and teardown, fragment `if` branches, focused platform-specific cases.
- Evidence collected: fresh source, screenshot, template/ROI match data, run report, and minimal reproducing plan/case.

Only request extension after the relevant recipe fails for a general framework reason, not because of missing data, unstable locator work, account state, permissions, or environment setup.
