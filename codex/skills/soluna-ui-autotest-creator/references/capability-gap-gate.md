# Capability Gap Gate

Read this before asking maintainers to extend Soluna framework keywords, schemas, Appium extension capabilities, reports, or runner behavior.

## Gate

Read `keyword-usage.md` before applying this gate. Submit a capability gap only when all conditions are true:

1. The scenario is a valid automation closure requirement, not missing test data, account state, device state, permissions, or environment setup.
2. Existing plan/stage/case setup and teardown orchestration cannot close it.
3. Fragment `if` / `then` / `else` with existing assertion/action predicates cannot close it.
4. Existing actions cannot close it after trying the relevant `keyword-usage.md` recipes:
   - Element/gesture path: `tap`, `swipe`, `input`, `getText`, `assertElementExists`, `assertElementAttrEquals`, `assertElementAttrRegexMatch`
   - App state path: `restartApp`, `clearAppData`, `wait`
   - Source path: `assertSourceRegexMatch`
   - Explicit resource path: `screenshot`
   - Visual path: `saveElementRect`, `tapVisualTemplate`
   - Transient text path: `startScreenRecording`, `stopScreenRecording`, `assertScreenRecordingTextRegexMatch`
   - App log path: `captureAppLogStart`, `captureAppLogEnd`, existing `customAssertAppLog` plugins
5. Parameter data, runtime variables, element catalogs, visual templates, ROI narrowing, OCR, platform-specific case splits, and teardown cannot close it.
6. Fresh debug source/screenshot evidence proves the issue is not stale XML, weak locator selection, or incorrect ROI/template assets.
7. A minimal focused plan/case reproduces the gap.
8. The proposed support is general framework capability, not a business-app shortcut.

## Request Format

Use this format:

```text
Capability Gap Request

Scenario:
- ...

Why existing keywords are insufficient:
- Tried ...
- Used keyword recipes from keyword-usage.md ...
- Failed because ...

Evidence:
- Asset root:
- Plan:
- Case:
- Debug source/screenshot:
- Run/report path:
- Error or observed behavior:

Proposed minimal extension:
- Keyword/API shape:
- Inputs:
- Outputs/runtime variables:
- Failure behavior:
- Schema impact:
- Android/iOS scope:
- Appium extension impact:

Rejected workarounds:
- ...
```

If any gate item is missing, continue debugging or ask the user for the missing precondition instead of requesting framework expansion.

App-specific log semantics, such as Bluetooth command/report parsing, should become an independent app-log assertion plugin behind `customAssertAppLog`. Do not add business-specific default keywords or put parser/matcher logic in the case asset project.

## DingTalk Notification

When the gate passes and the user wants maintainers notified, send the completed request through the bundled helper. The helper defaults to the built-in Soluna debug DingTalk robot. Override it with environment variables or command-line arguments only when a different robot should receive the notice.

Optional override environment variables:

```bash
export SOLUNA_CODEX_DINGTALK_WEBHOOK="https://oapi.dingtalk.com/robot/send?access_token=..."
export SOLUNA_CODEX_DINGTALK_SECRET="SEC..."
```

First dry-run:

```bash
python3 codex/skills/soluna-ui-autotest-creator/scripts/send_dingtalk_gap_notice.py \
  --file capability-gap.md \
  --dry-run
```

Then send, after user approval when required by the execution environment:

```bash
python3 codex/skills/soluna-ui-autotest-creator/scripts/send_dingtalk_gap_notice.py \
  --file capability-gap.md \
  --title "Soluna Capability Gap: <short summary>"
```

The script also works from stdin:

```bash
cat capability-gap.md | python3 codex/skills/soluna-ui-autotest-creator/scripts/send_dingtalk_gap_notice.py --dry-run
```

Use `--no-default-robot` when you want the command to fail unless an explicit webhook is supplied.
