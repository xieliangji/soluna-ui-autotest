# Progress

This file is the compact progress record for the Soluna framework project.
It records framework capabilities, contracts, runtime boundaries, and verification
status. Business case authoring and case debugging progress must be recorded in
the relevant Soluna asset project docs instead.

## Maintenance Rules

- Keep this file concise. Record framework milestones, current status, verification, and next framework work.
- Do not paste long command output, stack traces, or every failed attempt here.
- Do not record business case writing progress, case-by-case debug status, test data state, or detailed business operation paths in this framework document.
- When a business case exposes a reusable framework need, record only the generalized framework change here after it is implemented.
- Record business case notes under the asset project, for example `AIot-Tests/apps/<app-id>/docs/`.
- If behavior, boundaries, schemas, lifecycle assumptions, commands, or dependencies change, update the relevant design or usage document in the same iteration.

## Current State

Runnable framework foundation is in place. Current work is focused on v1 hardening, contract clarity, and asset-project driven capability gaps.

Implemented capabilities:

- YAML DSL with `Plan -> Stage -> Case -> Action`.
- Plan-rooted execution: runner accepts only a plan path; other assets are referenced directly or indirectly by the plan.
- Schema-first validation for plan, case, element catalog, fragment catalog, parameter data, device config, artifact store, notification sender, report data, resource manifest, asset project, runner request, and runner result contracts.
- Keyword-as-field action syntax with nested action payloads preferred, for example `tap: { id, element, desc }`; legacy `tap: open-mine-tab` remains compatible.
- Case/data/element/fragment decomposition with linear case DSL and separate reusable setup/teardown fragments.
- Fragment DSL supports business-neutral `if` / `then` / `else` control flow with existing action/assertion keywords as predicates.
- Android and iOS real-device execution through Appium Java Client.
- Managed Appium server with runtime port allocation, extension bootstrap, required driver bootstrap, and `/status` probing.
- Managed iOS WDA through go-ios, including iOS 17+ userspace tunnel handling.
- Recovering WebDriver adapter with logical session and physical session rebuild.
- `soluna-ext` client for device metadata, installed app metadata, WDA bundle lookup, commands, and logs.
- Default actions: `tap`, `input`, `wait`, `restartApp`, `clearAppData`, `getText`, `saveElementRect`, `screenshot`, `tapVisualTemplate`, `startScreenRecording`, `stopScreenRecording`, `assertElementAttrEquals`, `assertElementAttrRegexMatch`, `assertSourceRegexMatch`, and `assertScreenRecordingTextRegexMatch`.
- `tap` resolves the current viewport-visible element, clicks the element-visible-area center by default, supports element-relative click ratios, and settles for 800ms by default.
- `clearAppData` supports Android app data reset through `adb shell pm clear`, then reactivates the app and waits for foreground state.
- `saveElementRect` stores an element's visible viewport rectangle as a pixel rect or normalized ROI for later runtime-variable reuse.
- `tapVisualTemplate` supports static ROI objects, runtime-variable ROI objects, and action-level wait retry with fresh screenshots.
- Assertion actions poll by resolved `wait`; explicit assertion waits isolate each probe from the session implicit wait.
- Runtime variables via `@{plan.name}` and `@{case.name}`; parameter references via `${...}`.
- Local JSON/HTML report writer with execution summary, failure summary, action metadata, trace links, report-resource links, and per-case action detail dialogs.
- App and device display names in reports and lifecycle notifications prefer real `soluna-ext` metadata when available.
- Explicit resource manifest for screenshots, screen recordings, and retained analysis frames.
- Failure trace screenshots and page source diagnostics.
- Async MinIO uploads with compression, retry, bounded drain, and local cleanup after successful upload.
- DingTalk lifecycle notifications with execution statistics and failure summaries, plus aggregated upload-failure alerts.
- CLI runner: `soluna run <plan.yaml>`.
- Debug CLI: `soluna debug <plan.yaml> source|screenshot|tap|tap-element|input|tap-template|shell`.

## Recent Iterations

### 2026-06-21 Asset Creator Device Case Layout Guidance

- Updated the bundled asset-creator skill contract so device-related asset cases are separated from app-wide common cases: cross-model device cases belong in `cases/device/common/`, model-specific cases belong in `cases/device/<model-slug>/`, and numbering restarts inside each case module/directory.
- Added target-device setup guidance to the asset-project contract: compose the device-list state fragment and `device.openTargetDevice` at stage setup, keep target device name/MAC in `data/device/<model-slug>.yaml`, and wait for the target device item to be connected before tapping it.
- Updated the scaffold docs template and `create_asset_project.py` so generated projects include device case/data directories, an `elements/device/` location for one YAML catalog per model, and document the device case/locator layout rules.
- Added plan directory guidance to the bundled skill: formal app-wide/cross-model plans live in `plans/common/`, formal model-specific plans live in `plans/device/<model-slug>/`, and temporary/focused debug plans live in `plans/debug/`.
- Kept keyword-specific guidance out of `references/keyword-usage.md`; the device layout and orchestration rules live in `references/asset-project-contract.md`.

Verification:

- `python3 /Users/xieliangji/.codex/skills/.system/skill-creator/scripts/quick_validate.py codex/skills/soluna-ui-autotest-creator`
- `python3 -m py_compile codex/skills/soluna-ui-autotest-creator/scripts/create_asset_project.py codex/skills/soluna-ui-autotest-creator/scripts/send_dingtalk_gap_notice.py`
- `python3 codex/skills/soluna-ui-autotest-creator/scripts/create_asset_project.py --output /private/tmp/soluna-skill-device-layout-check --project-id device-layout-check --app-id com.example.device --app-name DeviceApp --product-model DeviceApp --platform ios --udid TEST_UDID --force`
- Confirmed the scaffold writes the starter smoke plan under `plans/common/` and creates `plans/debug`, `plans/device`, `cases/device/common`, `data/device`, and `elements/device`.
- `./gradlew installDist`; confirmed the packaged skill exists under `build/install/soluna/codex/skills/soluna-ui-autotest-creator`.
- `git diff --check -- codex/skills/soluna-ui-autotest-creator docs/progress.md`

### 2026-06-21 Long Press Action

- Added a generalized `longPress` WebDriver action with English and Chinese aliases for Appium-backed iOS/Android real-device automation.
- Added schema, keyword registry, policy validation, adapter forwarding, and focused executor coverage for the new action.
- Updated framework usage docs and the bundled asset-project creator skill so generated/maintained asset projects can use `longPress` without page-object abstractions.

Verification:

- `./gradlew test --tests com.soluna.ui.autotest.appium.action.WebDriverActionExecutorsTest --tests com.soluna.ui.autotest.schema.JsonSchemaDslValidatorTest --tests com.soluna.ui.autotest.dsl.YamlPlanParserTest` passed.
- `./gradlew --rerun-tasks processResources installDist` passed.
- `python3 -m py_compile codex/skills/soluna-ui-autotest-creator/scripts/create_asset_project.py codex/skills/soluna-ui-autotest-creator/scripts/send_dingtalk_gap_notice.py` passed.
- `ios.yaml` parsed through `YamlPlanParser -> PlanReferenceResolver -> PlanDefaultsResolver -> PlanParameterResolver` without starting Appium.
- iOS real-device focused runs passed for device rename, delete-cancel, disconnect, and guest rename/delete debug plans.

### 2026-06-21 Report And DingTalk Final Polish

- Updated `DeviceConfigResolver` so successful `soluna-ext` metadata lookup overrides configured placeholder display names for report, Appium session, and DingTalk display; complete configured device fields still remain as fallback when lookup is unavailable.
- Added `AppMetadataResolver` so plan `app.name` is also resolved from the installed app metadata returned by `soluna-ext` when `app.id` is available; configured app name remains the fallback.
- Appium plugin changes: added `/soluna/app?udid=...&appId=...`, Android installed-app lookup through `adb shell pm path` plus `aapt dump badging`, and iOS installed-app lookup through the existing app list helpers.
- Finalized `LocalReportWriter` HTML: removed the secondary plan subtitle from the hero, removed generated-time display, kept the device label as `设备名称`, placed report resources above execution summary, kept report resource items in left/right label-link layout, made execution overview collapsible, vertically centered action/duration cells, and opened action details from case rows or the `操作` column `动作明细` link instead of showing homepage action details.
- Refined report action detail modals so stage/case context stays in the modal title area, the detail table no longer repeats stage/case columns, the close control is an icon button, only the modal body scrolls, and opening a modal locks the underlying report page from scrolling.
- Finalized lifecycle DingTalk Markdown: fixed title remains `App UI自动化测试`, body title/subtitle use styled color/size markup with dividers before and after the blockquote subtitle, field lists start with `设备名称` and `设备标识`, finished/report-published cards use execution start/end time, and report generation time is omitted.
- Updated architecture, schema docs, and README wording for real app/device metadata, report layout, and DingTalk card behavior; kept the bundled asset-creator skill update limited to `productModel`, report evidence usage, and extension-resolved display metadata that affect asset-project authoring/debugging.

Verification:

- `./gradlew test`
- `npm test`, `npm run build`, and `npm run lint` under `lib/soluna-appium-ext`
- `python3 /Users/xieliangji/.codex/skills/.system/skill-creator/scripts/quick_validate.py codex/skills/soluna-ui-autotest-creator`
- `./gradlew installDist`; confirmed packaged skill and plugin are present under `build/install/soluna`
- `./gradlew test --tests com.soluna.ui.autotest.report.LocalReportWriterTest`
- `git diff --check`
- Android real-device run on `ZT4225X3C2`: `build/install/soluna/bin/soluna run /private/tmp/soluna-android-about-language.yaml --run-id android-about-language-report-notify-20260621-002`; cases `TC001_MINE_ABOUT` and `TC002_MINE_LANGUAGE_SETTINGS` both passed, report upload completed with 3 uploaded and 0 failed/abandoned.
- Confirmed generated report uses real app name `UgreenAudio`, real device name `moto g - 2025`, includes start/end time, omits old subtitle/generated-time display, places report resources before execution overview, renders the execution overview as collapsible, and exposes `操作` / `动作明细` links with centered action/duration cells.
- Android real-device rerun on `ZT4225X3C2`: `build/install/soluna/bin/soluna run /private/tmp/soluna-android-about-language-stage-setup-only.yaml --run-id android-about-language-modal-20260621-002`; cases `TC001_MINE_ABOUT` and `TC002_MINE_LANGUAGE_SETTINGS` both passed, report upload completed with 3 uploaded and 0 failed/abandoned. The temporary plan kept `appState.loggedInDevicePage` at stage `setupFragments` and used `appState.restartApp` as `caseSetupFragments`.
- Confirmed generated modal report has icon-only close buttons, fixed modal header/body scroll separation, page scroll locking, and action detail tables without repeated stage/case columns.

### 2026-06-21 Report Homepage And DingTalk Card Refinement

- Added required plan-level `productModel` to the v1 plan contract for report and DingTalk display; AIot example plans now declare it.
- Updated the asset-project scaffold to write `productModel`, defaulting to the app name unless `--product-model` is supplied.
- Reworked `LocalReportWriter` HTML into an overview-first report with product/app/run/device/start/end metadata, summary metrics, dedicated report-resource panel, case overview, failure summary, trace resources, and per-case action detail dialogs instead of a homepage action timeline.
- Extended `execution-result.json` with product/app display fields, resolved device display name, plan start/end timestamps, plus stage and case display names.
- Reworked lifecycle DingTalk notifications into compact Chinese Markdown cards with fixed title `App UI自动化测试`, a blockquote summary headed by `<productModel> UI 自动化测试`, and Chinese semantic item labels.
- Updated schema/design/usage docs and the bundled Codex asset-creator skill guidance for the new plan/report/notification contract.

Verification:

- `./gradlew test --tests com.soluna.ui.autotest.report.LocalReportWriterTest --tests com.soluna.ui.autotest.runner.PlanRunnerTest --tests com.soluna.ui.autotest.dsl.YamlPlanParserTest --tests com.soluna.ui.autotest.schema.JsonSchemaDslValidatorTest`
- `./gradlew test --tests com.soluna.ui.autotest.report.LocalReportWriterTest --tests com.soluna.ui.autotest.runner.PlanRunnerTest --tests com.soluna.ui.autotest.schema.JsonSchemaDslValidatorTest`
- `./gradlew test`
- `python3 /Users/xieliangji/.codex/skills/.system/skill-creator/scripts/quick_validate.py codex/skills/soluna-ui-autotest-creator`
- `python3 codex/skills/soluna-ui-autotest-creator/scripts/create_asset_project.py --output /private/tmp/soluna-product-model-scaffold-check --project-id product-check --app-id com.example.product --app-name ExampleApp --product-model 'Example Model' --platform android --udid TEST_UDID --force`
- `git diff --check`
- `./gradlew installDist`

### 2026-06-21 Reporter And DingTalk Summary Enrichment

- Added action result metadata for action id, keyword, name, attempt, start/end timestamps, and duration.
- Added shared execution summary/failure-summary views used by both local reports and lifecycle DingTalk messages.
- Enhanced `LocalReportWriter` JSON and HTML output with stage/case/action totals, failure summaries, action timeline metadata, status styling, and trace artifact links.
- Enriched plan lifecycle DingTalk messages with planned scope, execution totals, first failure summaries, trace artifact count, upload state, and report/manifest links.
- Extended `report-data.schema.json` and schema docs for optional summary, failure, and action metadata fields.
- Updated the bundled Codex asset-creator skill distribution workflow reference so Codex agents inspect report summary, failures, action metadata, trace artifacts, and resource manifests in the intended order.

Verification:

- Focused reporter, runner notification, and schema Gradle tests passed during implementation.
- Bundled skill quick validation passed.

### 2026-06-21 Appium Plugin Ownership Boundary

- Updated the project boundary for `lib/soluna-appium-ext`: it is now maintained as an integrated component of this repository and distributed with the Soluna package.
- Removed documentation and agent guidance that required plugin changes to be prepared for submission back to the original standalone GitHub project.
- Kept the framework rule that host/device-adjacent capabilities should be implemented in the Appium plugin and consumed through client abstractions.

Verification:

- Documentation-only update; Gradle tests were not run.

### 2026-06-21 Documentation Refresh And Examples Directory Cleanup

- Removed the remaining empty root examples directory after the tracked legacy example assets had moved to `AIot-Tests`.
- Refreshed README wording around current status, AIot asset-project layout, current runner capabilities, artifact config, and opt-in real-device asset-plan smoke checks.
- Updated architecture and schema notes to remove stale v0/current-implementation labels, old package paths, and old profile-case data-path examples.
- Kept framework docs focused on runner/contracts/current capabilities; business case progress remains in asset-project docs.

Verification:

- Documentation and directory cleanup only; Gradle tests were not rerun.
- `rg` scans for stale root example paths, old package paths, old profile/nickname asset paths, and `.soluna` upload-plan references passed for README and core docs.

### 2026-06-21 Bundled Codex Asset Creator Skill

- Added the project-versioned Codex skill `codex/skills/soluna-ui-autotest-creator` for external asset project creation, validation/debug/run workflow guidance, and strict capability-gap reporting.
- Added a deterministic `create_asset_project.py` scaffold with minimal starter templates for `soluna-project.yaml`, app plans/cases/data/elements/fragments/docs, device config, and artifact templates.
- Added a `send_dingtalk_gap_notice.py` helper so Codex can dry-run and send approved capability-gap requests to the built-in Soluna debug DingTalk robot, with optional environment or CLI overrides for other robots.
- Added `references/keyword-usage.md` so the skill explains field-level keyword usage, runtime variables, ROI, visual templates, screen recording OCR, assertions, and fragment control flow before asking for framework expansion.
- Added `AGENTS.md` maintenance rules requiring the bundled skill to be updated with related CLI, DSL, schema, debug, artifact/report, scaffold, and capability-gap behavior changes.
- Kept maintenance responsibility out of distributed skill instructions; skill references now focus on asset-project use through the packaged distribution instead of source-checkout paths.
- Removed the legacy root examples asset tree; `AIot-Tests` is now the in-repository example Soluna asset project for docs and tests.
- Updated Gradle distribution packaging so `installDist` copies bundled skills under `build/install/soluna/codex/skills`.
- Updated README and architecture notes to make the skill part of the framework distribution contract and require skill updates when schema, CLI, keyword, debug, report, or extension-flow behavior changes.

Verification:

- `python3 /Users/xieliangji/.codex/skills/.system/skill-creator/scripts/quick_validate.py codex/skills/soluna-ui-autotest-creator`
- Manual cross-check against `DefaultKeywordRegistry`, v1 case/fragment schemas, and current action executor field semantics.
- `git diff --check -- AGENTS.md codex/skills/soluna-ui-autotest-creator docs/progress.md`
- Stale root example-path reference scan returned no matches outside generated output and git metadata.
- Focused parser, linear execution, artifact config, notification config, and schema validation Gradle tests passed against the staged snapshot.
- `python3 codex/skills/soluna-ui-autotest-creator/scripts/create_asset_project.py --output /private/tmp/soluna-skill-scaffold-check --project-id smoke-check --app-id com.example.smoke --app-name SmokeApp --platform android --udid TEST_UDID --force`
- `python3 codex/skills/soluna-ui-autotest-creator/scripts/send_dingtalk_gap_notice.py --message 'Capability gap dry run' --dry-run`
- `python3 codex/skills/soluna-ui-autotest-creator/scripts/send_dingtalk_gap_notice.py --message 'Capability gap dry run' --webhook 'https://oapi.dingtalk.com/robot/send?access_token=override12345678' --dry-run`
- `python3 codex/skills/soluna-ui-autotest-creator/scripts/send_dingtalk_gap_notice.py --message 'Capability gap dry run' --no-default-robot --dry-run`
- `./gradlew installDist`
- Confirmed `build/install/soluna/codex/skills/soluna-ui-autotest-creator/references/keyword-usage.md` exists after `installDist`.
- `python3 build/install/soluna/codex/skills/soluna-ui-autotest-creator/scripts/create_asset_project.py --output /private/tmp/soluna-skill-scaffold-check-dist --project-id smoke-check-dist --app-id com.example.dist --app-name DistApp --platform ios --udid IOS_TEST_UDID --force`
- `python3 build/install/soluna/codex/skills/soluna-ui-autotest-creator/scripts/send_dingtalk_gap_notice.py --message 'Capability gap dry run from dist' --dry-run`
- DingTalk helper verification used `--dry-run` only; no live DingTalk notification was sent.

### 2026-06-21 Platform-Aware Catalog Resolution

- Element catalog loading now skips entries that do not provide a locator for the current platform instead of failing the whole plan during reference assembly.
- Fragment catalogs are now resolved lazily by actual fragment references, so platform-specific helper fragments in a shared catalog do not block plans that never use them.
- This allows a shared catalog to contain Android-only and iOS-only elements while still failing at action resolution if a case actually references an element unavailable on the current platform.

Verification:

- `./gradlew test --tests com.soluna.ui.autotest.runner.PlanReferenceResolverTest --tests com.soluna.ui.autotest.schema.JsonSchemaDslValidatorTest`

### 2026-06-19 Android App Data Reset Permission Recovery

- Added Android `clearAppData` DSL execution support for clearing target app data and relaunching the app within the current Appium session.
- When an Android session requested `autoGrantPermissions`, `clearAppData` now re-grants runtime permissions discovered from `dumpsys package` after `pm clear`, so later first-launch flows are not blocked by system permission prompts.
- Updated schemas and schema/design docs for the `clearAppData` action and Android permission recovery behavior.

Verification:

- Android real-device plan execution passed with a final clear-data first-use stage.

### 2026-06-19 Element ROI And Template Retry

- Added `saveElementRect` as a generic action for saving element viewport rectangles or normalized ROI objects into runtime variables.
- `tapVisualTemplate` now accepts `roi` as either an inline normalized ROI object or an exact runtime-variable reference, and action-level `wait` retries matching with fresh screenshots.
- Updated v1 plan/case/fragment schemas and schema/design docs for saved element ROI and runtime ROI references.

Verification:

- `./gradlew test --tests com.soluna.ui.autotest.appium.action.WebDriverActionExecutorsTest --tests com.soluna.ui.autotest.dsl.YamlPlanParserTest --tests com.soluna.ui.autotest.schema.JsonSchemaDslValidatorTest`

### 2026-06-19 Framework / Asset Documentation Boundary

- Cleaned this framework progress record so it no longer tracks concrete business case authoring progress, per-case debug status, run IDs, or detailed operation paths.
- Established the asset-project rule that case authoring notes and debug-passed operation paths belong under the app asset project docs.
- Added an app asset case-authoring note document for reusable case-writing constraints, debug workflow, and operation-path records.
- Clarified the architecture maintenance boundary: `docs/progress.md` is for generalized framework changes only; case-specific progress is not a framework project record.

Verification:

- Documentation-only update; Gradle tests were not run.

### 2026-06-19 Failure Strategy And Managed Runtime Hardening

- Added `ContinueCaseFailureStrategy` so a failed case can stop only the current case while allowing later cases and stages to continue; final plan status still reflects any failure.
- Added managed Appium startup checks for required Appium plugins and drivers before launching the server.
- Managed startup installs the project-owned `soluna-ext` plugin from the bundled source when missing or when the installed copy is not the project copy.
- Managed startup ensures configured Appium drivers are installed, defaulting to `uiautomator2` and `xcuitest`.
- Added `ensureDrivers` to the device config contract and packaged the bundled `lib/soluna-appium-ext` source in the Gradle distribution.

Verification:

- Focused execution strategy, runner, Appium manager, device config parser, parser, and schema tests passed during implementation.
- No plugin implementation source under `lib/soluna-appium-ext` was changed.

### 2026-06-19 Multimodal OCR Support

- Added `recognizer` to `assertScreenRecordingTextRegexMatch` across normalization, policy validation, and v1 case/plan/fragment schemas.
- Kept Paddle OCR as the default recognizer and added a kt-visual OpenAI-compatible multimodal OCR recognizer.
- Added idle-aware OpenAI-compatible streaming OCR. Stream idle timeout is based on absence of reasoning/content chunks instead of a fixed request timeout.
- Multimodal screen-recording candidate frames are recognized concurrently; Paddle OCR remains sequential.
- Multimodal runtime configuration is supplied only by system properties or environment variables and is not encoded in case assets.
- Added the narrow `com.openai:openai-java-client-okhttp:4.39.1` runtime dependency required by the kt-visual multimodal client.
- Normalized multimodal OCR image input to RGB PNG and improved OCR failure cause reporting.

Verification:

- Focused WebDriver action, parser, schema, and distribution build checks passed during implementation.
- Direct multimodal OCR checks were used to verify stream behavior.

### 2026-06-19 iOS WDA Startup Diagnostics

- Plan execution now passes a run-scoped `diagnostics/wda` log directory into managed WDA/go-ios startup and session recovery.
- WDA startup diagnostics retain `go-ios-tunnel.log`, `go-ios-runwda.log`, and `go-ios-forward.log` under the local run directory.
- Managed WDA command construction no longer passes unsupported go-ios tunnel-info arguments.
- Managed Appium and WDA runtime port allocation now probes by binding explicitly to `127.0.0.1:0`, avoiding false availability from unspecified-address binding.

Verification:

- Focused runner and WDA/Appium manager tests passed during implementation.

### 2026-06-18 Runtime Tooling And Diagnostics

- Added a shared FFmpeg resolver with explicit path, environment override, bundled tool, installed-distribution, working-tree, and PATH fallback candidates.
- Managed Appium startup prepends the resolved FFmpeg directory to PATH for iOS recording compatibility.
- Screen-recording frame extraction uses the same FFmpeg resolver.
- Added SLF4J runtime logging and wired default plan/stage/case/action lifecycle logging.
- Added debug logging to managed Appium and WDA startup, health, port allocation, command construction, and cleanup paths.
- Added `restart-app` and shell support to the debug CLI for step-by-step device inspection.
- Extended failure trace diagnostics to retain page source XML beside before-action screenshots.

Verification:

- Focused FFmpeg resolver, Appium/WDA manager, CLI, runner, and action executor tests passed during implementation.
- `git diff --check` passed during the relevant iterations.

### 2026-06-18 Wait And Recovery Semantics

- Assertion element lookup now keeps explicit `wait` isolated from the session implicit wait for every polling probe.
- Managed iOS WDA health is integrated into `RecoveringWebDriverAdapter` runtime recovery.
- When the current WDA handle is unhealthy, recovery restarts WDA before rebuilding the Appium server/session and refreshes the recovered session request.
- `PlanRunner` stops the latest recovered WDA handle during plan cleanup.

Verification:

- Focused recovering adapter, runner, and action executor tests passed during implementation.

### 2026-06-17 Visual And Recording Actions

- Added `tapVisualTemplate` with `tapImage` / `tapTemplate` aliases for non-text visual affordance clicks using current screenshots, kt-visual template matching, normalized ROI, match thresholds, scale options, and target-region click ratios.
- Added two-stage template asset resolution: literal template paths are checked during reference assembly, while parameterized template values resolve after data merge and are interpreted relative to the owning data file directory.
- Added Appium screen recording start/stop actions and screen-recording text regex assertions.
- Generalized explicit screenshot resource handling into a plan resource sink so screenshots, videos, and analysis frames share the same MinIO manifest/upload path.
- Implemented recording frame extraction through a replaceable `VideoFrameExtractor`; the default uses FFmpeg and kt-visual OCR.
- Added visual-diff frame candidate strategies and ROI cropping for screen-recording analysis.
- Updated schema files, schema docs, architecture notes, and focused tests for the visual and recording action surface.

Verification:

- JSON schema validation, parser, reference/parameter resolver, action executor, report/resource manifest, and runner tests passed during implementation.

### 2026-06-16 Fragment Control Flow And Lifecycle Scopes

- Added schema/model/parser/resolver/execution support for fragment `if` / `then` / `else`.
- Kept case DSL linear; case schemas and policy validation still reject logic control.
- Added scoped `caseSetupFragments` / `caseSetupActions` and `caseTeardownFragments` / `caseTeardownActions` at plan, stage, and case levels.
- Fixed stage/case inline `parameters` so they merge into the parameter context used by later lifecycle actions, case actions, and element locators.
- Changed `restartApp` to wait for the target app to report foreground state before returning; an action-level `wait` can override the default foreground wait.

Verification:

- Focused parser, schema, parameter/default, execution, runner, and action executor tests passed during implementation.
- `./gradlew build` passed during the control-flow iteration.

## Milestone Summary

### 2026-06-12 Foundation

- Initialized Kotlin/JVM project, Gradle wrapper, schemas, parser, core models, hook bus, execution skeleton, failure/retry interfaces, and Appium abstraction boundaries.
- Added `soluna-appium-ext` under `lib/soluna-appium-ext` for integrated development.

Verification:

- `./gradlew test` passed after initial setup fixes.

### 2026-06-12 Appium And Base Actions

- Added Appium Java Client adapter, WebDriver action executors, Android keyboard defaults, and opt-in real-device smoke coverage.

Verification:

- Focused Appium adapter/action tests passed.

### 2026-06-13 Runner And Asset Decomposition

- Built `PlanRunner` around a single plan path.
- Added device config parsing, managed Appium server startup, session creation, referenced case files, element catalogs, parameter data, fragment catalogs, setup/teardown lifecycles, runtime variables, and default action wait.

Verification:

- Focused parser/resolver/runner tests passed.

### 2026-06-13 Recovery, Artifacts, Reports, CLI, Notifications

- Added `RecoveringWebDriverAdapter`.
- Added action trace screenshots on failure.
- Added report JSON/HTML writer, explicit screenshot manifest, MinIO artifact store, async upload queue, gzip upload policy, DingTalk robot sender, upload-failure notifier, local cleanup after successful upload, and CLI runner.

Verification:

- Recovery, artifact, report, notification, and CLI tests passed.

### 2026-06-13 iOS WDA

- Added iOS WDA management through go-ios.
- Added iOS 17+ userspace tunnel handling, WDA runner bundle discovery through `soluna-ext`, and managed forward restart after runwda restart.

Verification:

- Focused WDA tests passed.

### 2026-06-15 DSL And Contract Closure

- Migrated external action DSL to keyword-as-field syntax.
- Added schema-enumerated action keywords/aliases.
- Replaced generic text equality with explicit attribute/source assertion actions.
- Added WebDriver attribute and page-source access.
- Added wait-based polling to assertion actions.
- Added `soluna-project.schema.json`, `run-request.schema.json`, and `run-result.schema.json`.
- Updated README, schema docs, and architecture boundaries to define framework / asset project / platform separation.

Verification:

- `jq empty src/main/resources/schemas/v1/*.json` passed.
- `git diff --check` passed.
- `./gradlew test` passed.
- `./gradlew build` passed.

## Current Verification Baseline

Last full framework baseline verification:

```bash
jq empty src/main/resources/schemas/v1/*.json
git diff --check
./gradlew test
./gradlew build
```

Current work uses focused verification based on touched areas.

## Next Work

- Keep framework docs focused on framework behavior, schemas, runtime boundaries, and generalized capabilities.
- Keep business case operation paths and authoring notes in each asset project's own `docs` directory.
- Continue v1 work from generalized needs observed through real business cases:
  - Soluna asset project discovery and platform Runner service boundary.
  - Action keyword and alias governance.
  - Wait/assertion and failure diagnostics.
  - Report resource preview and failure triage UX.
  - Plan lifecycle notification configuration decoupled from artifact-store config.
