# Handoff

Last updated: 2026-06-18

This file records the current handoff state for the next Codex session. It is intentionally operational and should be read together with `docs/architecture.md` and `docs/progress.md`.

## Current State

- The shared app-state fragments must stay generic. Do not add business-module checks such as feedback WebView recovery to `AIot-Tests/apps/com.ugreen.iot/fragments/app-state.yaml`.
- App-state convergence currently covers login page, logged-in device page, guest device page, and restart-to-current-stage device page.
- The feedback cases live under `AIot-Tests/apps/com.ugreen.iot/cases/common/TC009_*.yaml` through `TC015_*.yaml`.
- Common app elements, including feedback WebView elements, are module-owned in `AIot-Tests/apps/com.ugreen.iot/elements/common.yaml`.
- Visual templates are under `AIot-Tests/apps/com.ugreen.iot/data/common/templates/` and are referenced through `AIot-Tests/apps/com.ugreen.iot/data/common/visual-templates.yaml`.
- Local MinIO and DingTalk configs are intentionally ignored as `AIot-Tests/artifacts/*.local.yaml`; do not commit real credentials.

## Verified

- iOS feedback aggregate plan passed after removing feedback-specific recovery from app-state:
  - Run id: `common-ios-feedback-pack-19`
  - Plan: `AIot-Tests/apps/com.ugreen.iot/plans/common/ios-feedback-debug.yaml`
  - Report: `build/soluna-runs/common-ios-feedback-pack-19/report/index.html`
  - Passed cases: `TC010`, `TC011`, `TC013`, `TC014`, `TC015`, and `TC009`
  - Upload result: `uploaded=5, failed=0`
- Earlier iOS single-case runs also passed for `TC009`, `TC010`, `TC011`, `TC013`, `TC014`, and `TC015`.
- iOS `TC012_FEEDBACK_DEVICE` was run separately as `common-ios-feedback-tc012-2` and failed at `assert-device-field-visible` because the current iOS account/app state did not expose the device selection field after selecting the device-related problem type. Treat this as a test-data/precondition gap until the account has suitable device history.

## Android Status

- Android feedback is not fully tuned yet.
- Target debug device used in this session: `3B6F6KE910B3QRDN`.
- Android account should be supplied via runtime overrides or local secret data; do not commit the password.
- The first Android feedback attempts exposed infrastructure slowness around Appium UiAutomator2:
  - stale UiAutomator2 instrumentation/logcat processes can leave `newSession` slow or stuck;
  - `quit`, screenshot, and `findElements` calls can block long enough to trigger recovery;
  - `AppiumJavaClientWebDriverAdapter.stopSession` now uses a bounded `quit` timeout to avoid recovery hanging on old sessions.
- A temporary Android single-case plan was used during debugging and removed before this handoff.
- `AIot-Tests/apps/com.ugreen.iot/plans/common/android-feedback-rest-debug.yaml` remains the focused Android feedback debug pack.

## Useful Commands

Run iOS feedback regression with placeholders for secrets:

```bash
build/install/soluna/bin/soluna run AIot-Tests/apps/com.ugreen.iot/plans/common/ios-feedback-debug.yaml \
  --run-id common-ios-feedback-pack-<n> \
  --param appState.login.username=<ios-username> \
  --param appState.login.password=<ios-password>
```

Run Android feedback debug pack:

```bash
build/install/soluna/bin/soluna run AIot-Tests/apps/com.ugreen.iot/plans/common/android-feedback-rest-debug.yaml \
  --run-id common-android-feedback-pack-<n> \
  --param appState.login.username=<android-username> \
  --param appState.login.password=<android-password>
```

When Android Appium gets stuck before any report directory is created, inspect and clean only the selected device:

```bash
$HOME/Library/Android/sdk/platform-tools/adb devices -l
$HOME/Library/Android/sdk/platform-tools/adb -s 3B6F6KE910B3QRDN shell am force-stop io.appium.uiautomator2.server
$HOME/Library/Android/sdk/platform-tools/adb -s 3B6F6KE910B3QRDN shell am force-stop io.appium.uiautomator2.server.test
$HOME/Library/Android/sdk/platform-tools/adb -s 3B6F6KE910B3QRDN shell am force-stop com.ugreen.iot
```

Debug CLI supports one-shot and shell mode:

```bash
build/install/soluna/bin/soluna debug <plan.yaml> source --out build/soluna-debug/source.xml
build/install/soluna/bin/soluna debug <plan.yaml> screenshot --out build/soluna-debug/screen.png
build/install/soluna/bin/soluna debug <plan.yaml> tap-element --strategy xpath --locator '<xpath>'
build/install/soluna/bin/soluna debug <plan.yaml> input --strategy class --locator XCUIElementTypeTextView --text 'debug text'
build/install/soluna/bin/soluna debug <plan.yaml> shell
```

## Next Recommended Work

- Continue Android feedback with one case at a time before rerunning the aggregate pack.
- Keep state convergence generic. If a feedback case needs recovery from its own WebView, model that inside the feedback case/debug plan rather than app-state.
- Prefer source-regex probes for coarse app-state detection and reserve element lookup for actual action targets.
- If Android UiAutomator2 keeps hanging on `findElements`, capture Appium logs and page source through the debug CLI before changing locators.
- Re-run focused Gradle tests after any driver/schema/action change.
