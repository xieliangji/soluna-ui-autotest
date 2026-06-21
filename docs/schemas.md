# Schemas

The project uses JSON Schema as the external data-contract format.

Schema files live under:

```text
src/main/resources/schemas/v1/
```

Current schemas:

- `plan.schema.json`: YAML plan DSL after YAML is parsed into JSON-compatible data.
- `case.schema.json`: standalone case YAML referenced by plan stages.
- `element-catalog.schema.json`: reusable element locator definitions referenced by cases.
- `fragment-catalog.schema.json`: reusable setup fragment definitions referenced by plans, stages, or cases.
- `parameter-data.schema.json`: standalone parameter data files referenced by plans.
- `device-config.schema.json`: per-device config copied from a device config template.
- `artifact-store.schema.json`: MinIO artifact store, upload queue, retry, and upload-failure notification references.
- `notification-sender.schema.json`: DingTalk robot notification sender config.
- `plan-resource-manifest.schema.json`: plan-level explicit screenshot resource manifest written beside report files.
- `report-data.schema.json`: report data JSON written as `execution-result.json` and consumed by report renderers.
- `soluna-project.schema.json`: asset project root metadata consumed by tooling and future project discovery.
- `run-request.schema.json`: platform-to-runner execution request summary.
- `run-result.schema.json`: runner-to-platform execution result summary.

`plan.schema.json` is the root execution contract consumed by `PlanRunner`. A run starts from the plan path; device config, artifact store config, parameter data, case files, element catalogs, and fragment catalogs must be referenced by the plan directly or indirectly. `deviceConfig` is required on the plan. `artifactStore` is optional and points to an artifact config file when a run should publish report/resource artifacts.

`plan.schema.json` supports both legacy inline cases and the preferred `caseRefs` composition model. New plans should keep cases in standalone YAML files and let stages reference them through `caseRefs`.

`soluna-project.schema.json` defines a Soluna asset project, not the framework project. It records project identity, framework/schema compatibility, app roots, shared/device/artifact roots, and optional project defaults. The current CLI does not require this file; it is the contract for future project discovery, platform-managed assets, and Codex agents that generate cases outside this repository.

`run-request.schema.json` and `run-result.schema.json` are service/platform boundary contracts. They intentionally expose plan URI, asset revision, device selection, parameter overrides, run status, counts, and artifact links instead of internal Kotlin runner models.

Element catalogs reject hardcoded UI copy by default. `textLocatorPurpose` is the explicit escape hatch for stable non-business text: `iconName` only for icon resource names whose literal value starts with `icon `, `brandLogo` for stable brand markers, and `languageTitle` for parameterized language titles. State comparisons such as `@value='1'` are treated as control state, not UI copy.

Plan defaults currently include:

- `implicitWaitMs`: default implicit wait budget.
- `actionWait`: default explicit wait for actions. It is applied after case/fragment references are assembled and only fills setup, action, and teardown actions that do not already declare `wait`. It is not a substitute for WebDriver implicit wait and should be used sparingly for known slow action groups. When an action declares `wait`, element lookup for that action temporarily disables the session implicit wait, polls with the explicit action timeout, and then restores the configured implicit wait. Assertion actions treat `wait.timeoutMs` as the total assertion budget; each element probe also disables implicit wait so short branch predicates are not multiplied by the session implicit wait. This lets fragments use shorter state probes or longer slow-page probes without changing the session default.
- `failureStrategy`: named failure strategy selection. Built-in values are `stop-case` / `fail-fast` for fail-fast execution and `continue-case` for stopping only the failed case while continuing later cases and stages.
- `retryStrategy`: named retry strategy selection.

Plan diagnostics and local artifact handling currently include:

- `trace.screenshots.enabled`: enables before-action trace screenshots.
- `trace.screenshots.beforeAction`: currently `never` or `onFailure`; `onFailure` retains before-action screenshots and page source snapshots in memory and publishes them only if an action fails.
- `trace.screenshots.retainBeforeActionCount`: number of recent before-action screenshots retained for a failure.
- `trace.screenshots.upload`: currently `never` or `onFailure`.
- `localArtifacts.cleanup.mode`: `never` or `after-upload-success`. The latter deletes local run artifacts only after all queued upload tasks complete successfully.

`case.schema.json` keeps the case body linear. A case can reference:

- `dataRefs`: case-scoped parameter data files.
- `elementRefs`: element catalogs used by `element: alias.name` action fields.
- `setupFragments`: reusable setup fragments that execute in the case setup lifecycle before case actions.
- `setupActions`: inline lifecycle setup actions.
- `teardownFragments`: reusable teardown fragments that execute after case actions even when the main action flow failed.
- `teardownActions`: inline teardown actions.

`fragment-catalog.schema.json` is the only current DSL schema that accepts generic control flow. The supported control structure is `if` / `then` / `else`; the `if` value must be one existing executable action or assertion keyword, and branch arrays contain normal action objects. Control keys are intentionally business-neutral. Business predicates such as page state, login state, or element visibility must be expressed through ordinary action/assertion keywords like `assertElementExists`, `assertElementAttrRegexMatch`, or `assertSourceRegexMatch`.

Action DSL now uses a single keyword field. New assets should use the nested object form, for example:

```yaml
- tap:
    id: open-mine-tab
    element: common.mineTab
    desc: 打开我的页
```

The older `tap: open-mine-tab` form with sibling fields remains accepted for compatibility. `case.schema.json`, `plan.schema.json`, and `fragment-catalog.schema.json` explicitly enumerate the currently supported action keywords and aliases: `tap`, `tapVisualTemplate`, `tapImage`, `tapTemplate`, `input`, `restartApp`, `clearAppData`, `getText`, `saveElementRect`, `wait`, `assertElementExists`, `assertElementAttrEquals`, `assertElementAttrRegexMatch`, `assertSourceRegexMatch`, `screenshot`, `startScreenRecording`, `stopScreenRecording`, and `assertScreenRecordingTextRegexMatch`. Unsupported action types fail schema/policy validation before execution.

`assertElementExists` asserts that the configured `element` can be found and is the preferred predicate for fragment page-state branches. Element attribute assertions use an explicit `attr` field instead of encoding the attribute in the keyword. `attr` may contain slash-separated fallback candidates such as `name/label/text`. Regex assertions use contains-style matching by default through the regex engine; cases that need full-string matching should use anchors such as `^...$`. Assertion actions poll until matched or timed out when they have a `wait` value; that timeout is the total assertion budget, not a per-probe timeout. Plan-level `defaults.actionWait` supplies that wait unless the action overrides it.

Action-specific fields are declared directly on the action object instead of through open `args`. For `tap`, an action should normally use `element: alias.name`; runtime tap resolves the current element, requires it to intersect the screen viewport, and clicks the center of the element's visible area by default. `elementXRatio` and `elementYRatio` can override the element-relative click point. When the UI surface has no stable element identity, such as an app modal backdrop, `tap` can use viewport-relative coordinates through top-level `xRatio` and `yRatio`. Coordinate taps are action parameters, not locator definitions. `tap` waits 800ms after execution by default; `settleMs` can override that delay or disable it with `0`. The parser normalizes this external DSL into the compact internal `ActionDefinition` model consumed by executors.

`tapVisualTemplate` / `tapImage` / `tapTemplate` click a non-text visual affordance by matching a template image against the current screenshot with kt-visual, then tapping a configurable percentage point inside the matched region. Supported fields are `template`, `threshold`, `scales`, `roi`, `targetXRatio`, `targetYRatio`, `settleMs`, and action-level `wait`. `roi` uses normalized screenshot coordinates (`x`, `y`, `width`, `height`) and should be supplied for repeated or small controls to reduce false matches; it can also be an exact runtime variable reference such as `@{case.titleBarRoi}` when a previous action saved a normalized ROI object. When `wait` is present, template matching retries with fresh screenshots until the match is found or the timeout expires. Template assets belong under the project data tree, typically `data/<module>/templates/`; case and fragment DSL should reference the path through parameter data, for example `template: "${mine.visualTemplates.feedbackBackIcon}"`, instead of hardcoding an asset path in the case. During reference and parameter resolution, non-dynamic relative template paths are resolved relative to the data file directory, then inherited plan/case asset directories.

`saveElementRect` / `getElementRect` / `saveElementRegion` reads the visible viewport rectangle of an element and stores it in a runtime variable with `saveAs` and optional `scope`. By default the saved object is pixel based: `x`, `y`, `width`, `height`, `viewportWidth`, and `viewportHeight`. With `asRoi: true`, it saves normalized ROI coordinates compatible with visual actions. `fullWidth` or `fullHeight` can expand the saved ROI to the viewport edge while preserving the element's other axis; `expandLeftRatio`, `expandRightRatio`, `expandTopRatio`, and `expandBottomRatio` expand the element rectangle by a multiple of the element width or height before normalization.

`restartApp` terminates and activates the target app, then waits until Appium reports the target app is running in the foreground. Its action-level `wait` overrides the default foreground wait budget.

`clearAppData` / `clearApplicationData` clears Android app data for the supplied `appId`, reactivates the app, and waits for foreground state. It is Android-only. If the current session requested `appium:autoGrantPermissions=true` or `autoGrantPermissions=true`, the action re-reads runtime permissions through `dumpsys package <appId>` after `pm clear` and attempts to grant each runtime permission before relaunch. This keeps clear-data first-use flows from being interrupted by system permission prompts while preserving the normal app data reset semantics.

Toast-like transient text should be checked with screen recording analysis instead of page source polling. `startScreenRecording` starts an Appium recording with optional `timeLimitMs`; `stopScreenRecording` writes a video resource and can save its local path with `saveAs`; `assertScreenRecordingTextRegexMatch` extracts frames from that video with the resolved FFmpeg tool, optionally crops each frame by normalized `roi`, selects candidate frames through `candidateStrategy`, runs kt-visual OCR on those candidates, and stores the matched frame as a manifest resource when the regex matches. `recognizer` defaults to `paddle`; use `multimodal` for OpenAI-compatible kt-visual multimodal OCR. Multimodal OCR reads `soluna.visual.ocr.multimodal.baseUrl` / `SOLUNA_VISUAL_OCR_MULTIMODAL_BASE_URL`, optional `apiKey`, `model`, `timeoutMs`, `reasoningEffort`, `stream`, prompt, stream idle timeout, stream HTTP timeout, and multimodal candidate parallelism settings from system properties or environment variables. Defaults are `model=gpt-5.5`, `reasoningEffort=high`, `stream=true`, `streamIdleTimeoutMs=60000`, `streamHttpTimeoutMs=600000`, and multimodal candidate `parallelism=4`; stream chunks are logged at info level, and the idle timeout is reset only by reasoning or content output. The default multimodal prompt is tuned to include low-contrast UI overlay text and visible substrings of clipped toast text without completing hidden characters. FFmpeg resolution prefers `soluna.ffmpeg.path` / `SOLUNA_FFMPEG`, then `tools/ffmpeg/<os>-<arch>/ffmpeg(.exe)` from configured tool roots, the working tree, or the installed distribution, and finally `ffmpeg` from PATH. Managed Appium server startup also prepends the resolved explicit or bundled FFmpeg directory to PATH because Appium XCUITest iOS recording invokes a command named `ffmpeg` in the server process. Candidate strategies are `visual-diff` for transient visual changes, `uniform` for evenly sampled videos, `visual-diff-uniform` for choosing visually changed frames and then spreading OCR candidates across that set, and `all` for short ROI-cropped recordings where every extracted frame should be OCRed. `candidateMaxFrames` bounds OCR work for `visual-diff`, `visual-diff-uniform`, and `uniform`; `visualDifferenceThreshold` controls visual-diff sensitivity.

Plan and stage schemas expose the same lifecycle pattern through `setupFragments` / `setupActions` and `teardownFragments` / `teardownActions`. Teardown results are recorded separately from main action results.

Case lifecycle fields support scoped setup/teardown around every case:

- Plan-level `caseSetupFragments` / `caseSetupActions` and `caseTeardownFragments` / `caseTeardownActions` apply to all cases in the plan.
- Stage-level `caseSetupFragments` / `caseSetupActions` and `caseTeardownFragments` / `caseTeardownActions` apply to all cases in that stage.
- Case-level `caseSetupFragments` / `caseSetupActions` and `caseTeardownFragments` / `caseTeardownActions` apply only to that case.

Setup order is plan, then stage, then case. Teardown order is case, then stage, then plan.

`element-catalog.schema.json` is the only v1 DSL input schema that stores locator definitions. Parameter syntax remains `${...}`; element syntax is a distinct `element: alias.name` field on actions. An element can define common `strategy` / `value`, or platform-specific `android` and `ios` locators.

Stage and case inline `parameters` are merged into the parameter context used to resolve later lifecycle actions, case actions, and element locators. Nested objects merge recursively; dotted inline parameter names such as `appState.mine.entryIndex` are also supported.

Asset projects should keep case-specific data close to the case naming convention, while element catalogs stay module-oriented. For example, shared mine-module data can live under `data/common/mine.yaml`, but shared login/device/mine locators should live in `elements/common.yaml` rather than a case-named element file.

`case.schema.json`, `plan.schema.json`, and `fragment-catalog.schema.json` do not allow inline `locator` on actions. Actions reference elements through `element`; `PlanReferenceResolver` resolves that reference into the internal runtime `ActionDefinition.locator` before execution. This keeps locator ownership in element catalogs while preserving compact runtime executor inputs.

Runtime variables are not parameter data. Actions can write/read `@{plan.name}` and `@{case.name}` values during execution.

`report-data.schema.json` defines the current report data contract written by `LocalReportWriter` as `execution-result.json`. It is a report-consumption view, not a direct serialization of internal `PlanRunResult` or `PlanExecutionResult`. The report data JSON includes `schemaVersion: "1.0"`, optional top-level `summary` and `failures` views, and lifecycle buckets:

- plan: `setupActions`, `teardownActions`
- stage: `setupActions`, `teardownActions`
- case: `setupActions`, `actions`, `teardownActions`
- top level: `traceArtifacts`, containing failed-action diagnostic screenshot and page-source links when trace screenshots were published.

The `summary` view contains stage/case/action totals by status for report renderers and notifications. The `failures` view contains flattened failed action locations with stage, case, phase, index, action id, action keyword, message, and error. Action records may include action id, keyword, name, attempt, started/finished timestamps, and duration when the result came from the execution engine.

`device-config.schema.json` currently covers:

- device identity: UDID plus optional platform and display name
- optional device OS version; for iOS this can be resolved through `soluna-ext` when omitted
- UDID-only device configs; missing platform can be resolved through `soluna-ext`
- Appium server mode: managed or external
- optional Appium server location for external servers
- executable, plugins, required driver names, extra args, startup timeout, and optional environment overrides
- device/session-level Appium capabilities
- iOS WDA lifecycle config under `ios.wda`, including managed/external mode, local host port, device WDA port, go-ios executable, optional WDA identity overrides, tunnel mode, tunnel info/userspace tunnel ports, startup timeout, tunnel startup delay, and runwda startup delay

Device config must not contain target app identity or app reset intent. App lifecycle belongs to plan/stage/case setup fragments or explicit actions, not session creation.

For iOS, WDA is treated as a device-adjacent capability. `DeviceConfigResolver` fills missing iOS `device.osVersion` through `soluna-ext`; WDA management consumes that resolved version to choose the iOS 17+ tunnel path or the legacy path. The WDA runner bundle is resolved through `soluna-ext` by default and can be overridden with `ios.wda.bundleId`, `ios.wda.testRunnerBundleId`, and `ios.wda.xctestConfig` when needed. iOS 17+ managed WDA defaults to go-ios userspace tunnel mode and uses runtime-allocated tunnel info/userspace ports unless the device config pins them. Case and plan DSL files should not branch on iOS versions.

For managed Appium servers, `host` and `port` are optional. When `port` is omitted, the manager chooses an available local port at runtime. Before launching Appium, the manager ensures configured `usePlugins` are installed and configured `ensureDrivers` are installed. The default plugin list is `["soluna-ext"]`; the default driver list is `["uiautomator2", "xcuitest"]`. `soluna-ext` is treated as project-owned: if an installed copy is not from the project-bundled source, it is uninstalled and reinstalled from the bundled source. The Appium child process inherits the current runner process environment by default; `environment` only adds or overrides variables for exceptional cases.

Android session construction adds these defaults when the per-device config does not override them:

- `appium:unicodeKeyboard=true`
- `appium:resetKeyboard=true`

This keeps Android text input stable across real devices with different system keyboard implementations.

`artifact-store.schema.json` currently covers:

- MinIO endpoint, bucket, prefix, and optional public base URL.
- direct MinIO credentials through `credentials.accessKey` / `credentials.secretKey`, with `accessKeyEnv` / `secretKeyEnv` still supported as optional indirection.
- async upload worker count, queue capacity, bounded drain timeout, compression policy, and retry policy.
- `upload.compression` defaults to gzip-compressing text-like artifacts such as HTML, JSON, XML, JavaScript, and structured `+json` / `+xml` content types.
- optional upload-failure notification config reference, normally another YAML file under the asset project's `artifacts/` directory.
- optional plan lifecycle notification references: `planStarted`, `testFinished`, and `reportPublished`. The legacy `planFinished` field is still accepted as a compatibility alias for `reportPublished`.

`notification-sender.schema.json` currently covers DingTalk robot config:

- direct DingTalk robot `webhook` and optional signing `secret`, with `webhookEnv` / `secretEnv` still supported as optional indirection.
- optional DingTalk at-list settings.
- upload-failure alert window, threshold, and suppression interval.

Lifecycle DingTalk notifications currently reuse the report summary view to include stage/case/action totals, first failed action summaries, trace artifact count, upload status, and report/manifest links where available.

`plan-resource-manifest.schema.json` stores plan-level metadata and explicit DSL resources, including screenshots, screen recordings, and screen-recording text match frames. It is written as `plan-resource-manifest.json` beside report files and is not a step execution-detail file.

Example asset-project files:

- `AIot-Tests/soluna-project.yaml`
- `AIot-Tests/apps/com.ugreen.iot/plans/app-state/android.yaml`
- `AIot-Tests/apps/com.ugreen.iot/plans/app-state/ios.yaml`
- `AIot-Tests/apps/com.ugreen.iot/cases/common/app-state/login-page.yaml`
- `AIot-Tests/apps/com.ugreen.iot/data/app-state.yaml`
- `AIot-Tests/apps/com.ugreen.iot/elements/common.yaml`
- `AIot-Tests/apps/com.ugreen.iot/fragments/app-state.yaml`
- `AIot-Tests/devices/android/AMRF026323000807.yaml`
- `AIot-Tests/devices/ios/00008140-001805D80C93801C.yaml`
- `AIot-Tests/artifacts/minio.template.yaml`
- `AIot-Tests/artifacts/dingtalk.template.yaml`

Runtime model classes live under:

```text
src/main/kotlin/com/soluna/ui/autotest/core/model/
src/main/kotlin/com/soluna/ui/autotest/config/
src/main/kotlin/com/soluna/ui/autotest/artifact/
src/main/kotlin/com/soluna/ui/autotest/notification/
```

The DSL parser validates YAML in this order:

1. Parse YAML into a JSON tree.
2. Validate the tree against `plan.schema.json`.
3. Apply framework policy validation, including linear case DSL and locator text rules.
4. Map the validated tree into Kotlin model classes.

Schema files are versioned by directory. Breaking contract changes should create a new version directory instead of silently changing v1 semantics.

Element catalogs may contain platform-specific entries that are not available on every platform. During reference assembly, the runner loads only locators available for the current plan platform and skips entries that have no matching platform/common locator. If an action references a skipped element, action reference resolution still fails because that element is unavailable for the current platform.

Fragment catalogs are parsed as catalogs, but fragment actions are resolved lazily only when a plan, stage, or case references that fragment. This lets a shared fragment catalog contain Android-only or iOS-only helper fragments without making the other platform fail during plan assembly, while still failing if the current platform actually references an unsupported fragment.
