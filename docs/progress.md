# Progress

This file is the compact project progress record. It should help future agents understand the current state without replaying every debugging step.

## Maintenance Rules

- Keep this file concise. Record milestones, current status, verification, and next work.
- Do not paste long command output, stack traces, or every failed attempt here. Use git history and test reports for forensic detail.
- If behavior, boundaries, schemas, lifecycle assumptions, commands, or dependencies change, update the relevant design/usage document in the same iteration.
- For each implementation iteration, add one short entry under "Recent Iterations".

## Current State

v0 is closed as a runnable foundation.

Implemented capabilities:

- YAML DSL with `Plan -> Stage -> Case -> Action`.
- Plan-rooted execution: runner accepts only a plan path; other assets are referenced directly or indirectly by the plan.
- Schema-first validation for plan, case, element catalog, fragment catalog, parameter data, device config, artifact store, notification sender, report data, resource manifest, asset project, runner request, and runner result contracts.
- Keyword-as-field action syntax with nested action payloads preferred, for example `tap: { id, element, desc }`; legacy `tap: open-mine-tab` remains compatible.
- Case/data/element/fragment decomposition.
- Linear case DSL; setup/teardown fragments are separate lifecycle assets.
- Fragment DSL supports business-neutral `if` / `then` / `else` control flow with existing action/assertion keywords as predicates.
- Android and iOS real-device execution through Appium Java Client.
- Managed Appium server with runtime port allocation and `/status` probing.
- Recovering WebDriver adapter with logical session and physical session rebuild.
- Managed iOS WDA through go-ios, including iOS 17+ userspace tunnel handling.
- `soluna-ext` client for device metadata, WDA bundle lookup, commands, and logs.
- Default actions: `tap`, `input`, `wait`, `restartApp`, `getText`, `screenshot`, `startScreenRecording`, `stopScreenRecording`, `assertElementAttrEquals`, `assertElementAttrRegexMatch`, `assertSourceRegexMatch`, `assertScreenRecordingTextRegexMatch`.
- `tap` resolves the current viewport-visible element, clicks the element-visible-area center by default, supports element-relative click ratios, and settles for 800ms by default.
- Assertion actions poll by resolved `wait`.
- Runtime variables via `@{plan.name}` and `@{case.name}`.
- Parameter references via `${...}`.
- Local JSON/HTML report writer.
- Explicit resource manifest for screenshots, screen recordings, and retained analysis frames.
- Failure trace screenshots uploaded only on failure.
- Async MinIO uploads with compression, retry, bounded drain, and local cleanup after successful upload.
- DingTalk lifecycle notifications: plan started, test finished, report published.
- DingTalk aggregated upload-failure alerts.
- CLI runner: `soluna run <plan.yaml>`.

Primary verified real-device flow:

- `com.ugreen.iot` profile nickname edit/restore on Android and iOS.
- `com.ugreen.iot` app-state fragments for login page, guest device page, and logged-in device page on Android and iOS.
- `com.ugreen.iot` iOS feedback flows for guest access, submit success with toast OCR, problem type, description boundary, history list, and history detail.
- iOS upload-enabled run verified MinIO upload, report/resource links, local cleanup, DingTalk notifications, and assertion polling without fixed post-confirm sleeps.

## Recent Iterations

### 2026-06-18 iOS Feedback History Polling Fix

- Diagnosed `app-feedback-ios-local` failure in `TC015_FEEDBACK_HISTORY_DETAIL`: the history feedback page was open but still showing `加载中...`, while the case asserted a generic history-list locator with no explicit polling.
- Updated feedback history list/detail cases to poll `common.feedbackFirstHistoryRecord` after entering the history page instead of asserting the generic `feedbackHistoryList` locator.
- Added explicit polling to the first-record tap and the post-detail-back check in `TC015_FEEDBACK_HISTORY_DETAIL`; no fixed sleep was added.

Verification:

- `./gradlew test --tests com.soluna.ui.autotest.dsl.YamlPlanParserTest --tests com.soluna.ui.autotest.schema.JsonSchemaDslValidatorTest` passed.

### 2026-06-18 WDA Runtime Health Recovery

- Wired managed iOS WDA health into `RecoveringWebDriverAdapter` runtime recovery instead of only checking WDA during startup.
- When the current WDA handle is unhealthy, recovery now restarts WDA before rebuilding the Appium server/session and refreshes `appium:webDriverAgentUrl` for the recovered session request.
- `PlanRunner` now passes the active WDA manager/config/handle into the recovering driver and stops the latest recovered WDA handle during plan cleanup.

Verification:

- `./gradlew clean test --tests com.soluna.ui.autotest.appium.driver.RecoveringWebDriverAdapterTest --tests com.soluna.ui.autotest.runner.PlanRunnerTest` passed.

### 2026-06-18 Appium And WDA Debug Logging

- Added SLF4J debug logging to managed Appium server startup, command construction, FFmpeg PATH injection, readiness waiting, health checks, process stop, and startup failure cleanup.
- Added SLF4J debug logging to managed WDA/go-ios startup, iOS 17+ tunnel decision, port allocation, tunnel/runwda/forward process launch, readiness waiting, health checks, stop, and startup failure cleanup.
- Enabled package-level debug output for `com.ugreen.iot.soluna.autotest.appium.server` and `com.ugreen.iot.soluna.autotest.appium.wda` in `simplelogger.properties`; other runtime logs remain at info by default.
- Manager debug logs print environment keys but not environment values, and command values with sensitive-looking names are redacted.

Verification:

- `./gradlew test --tests com.ugreen.iot.soluna.autotest.appium.server.AppiumServerManagerTest --tests com.ugreen.iot.soluna.autotest.appium.wda.LocalGoIosWdaManagerTest` passed.

### 2026-06-18 Bundled FFmpeg Tool Resolution

- Added a shared FFmpeg tool resolver with explicit path, environment override, bundled `tools/ffmpeg/<os>-<arch>/ffmpeg(.exe)`, installed-distribution, working-tree, and PATH fallback candidates.
- Wired managed Appium server startup to prepend resolved explicit or bundled FFmpeg directories to PATH, which is required by Appium XCUITest screen recording on iOS.
- Rewired screen-recording frame extraction to use the same FFmpeg resolver instead of hardcoded `/opt/homebrew/bin/ffmpeg`.
- Added `tools/ffmpeg` platform directories and Gradle distribution packaging so `installDist` carries bundled runtime tools when binaries are supplied.
- Downloaded bundled FFmpeg executables for `macos-arm64`, `macos-x64`, `linux-arm64`, `linux-x64`, and `windows-x64` from `eugeneware/ffmpeg-static` release `b6.1.1`; upstream README/LICENSE files are kept beside each executable. The upstream package license is `GPL-3.0-or-later`.
- Documented FFmpeg placement and override options in README, schema notes, and architecture docs.
- No JavaCV/Bytedeco dependency was added for this iteration because Appium XCUITest iOS recording still requires a command named `ffmpeg` in the Appium server process PATH.

Verification:

- `./gradlew test --tests com.ugreen.iot.soluna.autotest.tool.FfmpegToolResolverTest --tests com.ugreen.iot.soluna.autotest.appium.server.AppiumServerManagerTest --tests com.ugreen.iot.soluna.autotest.appium.action.WebDriverActionExecutorsTest installDist` passed.
- `./gradlew installDist` passed again after adding actual FFmpeg binaries; `build/install/soluna/tools/ffmpeg` contains the five executable binaries and upstream license/readme files.
- `tools/ffmpeg/macos-arm64/ffmpeg -version` passed on the local host.
- `file` confirmed the bundled binaries are Mach-O arm64, Mach-O x86_64, Linux aarch64 ELF, Linux x86-64 ELF, and Windows x86-64 PE32+ executables.
- `git diff --check` passed.

### 2026-06-18 Default SLF4J Lifecycle Logging

- Added `org.slf4j:slf4j-api` and `org.slf4j:slf4j-simple` so CLI/runtime execution has a concrete SLF4J backend instead of dropping logs with the no-provider warning.
- Added `Slf4jExecutionLogger` and wired `PlanRunner`'s default hook bus to `DefaultLoggingHook`, so plan/stage/case before/after and action-before events are logged by default.
- Added `simplelogger.properties` to emit lifecycle logs to stdout during local CLI runs.

Verification:

- `./gradlew test --tests com.ugreen.iot.soluna.autotest.core.execution.LinearExecutionEngineTest --tests com.ugreen.iot.soluna.autotest.runner.PlanRunnerTest installDist` passed.
- `build/install/soluna/lib` contains `slf4j-api-2.0.17.jar` and `slf4j-simple-2.0.17.jar`.

### 2026-06-18 Assertion Explicit Wait Probe Fix

- Fixed assertion element lookup so explicit `wait` remains isolated from the session implicit wait for every polling probe. Previously only the first assertion probe used the explicit wait; later probes passed `wait=null` and could fall back to the plan implicit wait, making short fragment predicates much slower than configured.
- Documented that assertion `wait.timeoutMs` is the total assertion budget and that each element probe disables implicit wait.

Verification:

- `./gradlew test --tests com.ugreen.iot.soluna.autotest.appium.action.WebDriverActionExecutorsTest` passed.

### 2026-06-18 App-State Fragment Debug Recheck

- Added `restart-app` to the debug CLI so interactive sessions can always start from a real app restart before inspecting source or tapping elements.
- Reworked the `com.ugreen.iot` app-state fragments around the verified app flow from device page to login page: Mine tab -> avatar/profile entry -> guest login prompt or logged-in personal info logout path.
- Tightened iOS common locators for login submit and profile entry using source-backed structure instead of broad XPath traversal.
- Added ignored local account override files for iOS and Android app-state data, and ignored `*.local.yaml` under `AIot-Tests/apps/com.ugreen.iot/data/`.
- Added `AIot-Tests/apps/com.ugreen.iot/docs/app-state-fragment-debug.md` with the debug-shell steps, verified iOS/Android paths, and locator notes.
- Added a CLI unit test for `soluna debug <plan.yaml> restart-app --app-id ...`.

Verification:

- iOS app-state plan passed: `app-state-ios-fragments-2`; report at `build/soluna-runs/app-state-ios-fragments-2/report/index.html`.
- Android app-state plan passed after correcting the local Android password value: `app-state-android-fragments-2`; report at `build/soluna-runs/app-state-android-fragments-2/report/index.html`.
- The earlier Android failed trace showed `请输入正确的密码` after submit, proving the login button was tapped and the remaining issue was credential data, not locator delivery.
- `git diff --check` passed.
- `./gradlew test --tests com.ugreen.iot.soluna.autotest.cli.SolunaCliApplicationTest` passed.
- `./gradlew test` passed.

Next work:

- Continue Android feedback-case completion using these app-state fragments as the stable preconditions.

### 2026-06-18 iOS Feedback Real-Device Debug

- Tuned the iOS feedback cases and common feedback locators so the currently executable feedback suite uses module-owned elements, nested action payloads, visual-template clicks for WebView icon affordances, and ROI-cropped recording OCR for the submit-success toast.
- Kept the feedback history icon template under `AIot-Tests/apps/com.ugreen.iot/data/common/templates/` and referenced it through parameter data instead of hardcoding template paths in cases.
- Added `visual-diff-uniform` as a screen-recording candidate strategy, while TC010 uses ROI + `all` candidates because the success toast is visible for only a few frames.
- Extended the debug CLI with shell mode, `tap-element`, and `input` so real-device exploration can proceed step by step inside one temporary Appium/WDA session.
- Hardened WebDriver command handling by adding bounded timeouts to health checks and treating WebDriver command timeout as a session recovery signal.
- TC012 feedback device selection remains excluded from the iOS debug pack because the current iOS account did not expose the device field after selecting the relevant problem types; this is tracked as test-data/precondition missing rather than a passed automation path.

Verification:

- iOS real device single-case runs passed: `common-ios-feedback-tc009-2`, `common-ios-feedback-tc010-2`, `common-ios-feedback-tc011-2`, `common-ios-feedback-tc013-3`, `common-ios-feedback-tc014-2`, and `common-ios-feedback-tc015-2`.
- iOS TC012 was rerun separately as `common-ios-feedback-tc012-2` with expected failure; it failed at `assert-device-field-visible` after tapping the device-related type because the device selection field was not present in the current app state. Failure trace artifacts were uploaded with `uploaded=13, failed=0`.
- iOS feedback aggregate debug plan passed: `common-ios-feedback-pack-18`; report generated at `build/soluna-runs/common-ios-feedback-pack-18/report/index.html`; MinIO upload completed with `uploaded=5, failed=0`.
- `./gradlew test --tests com.ugreen.iot.soluna.autotest.appium.action.WebDriverActionExecutorsTest --tests com.ugreen.iot.soluna.autotest.appium.driver.AppiumJavaClientWebDriverAdapterTest --tests com.ugreen.iot.soluna.autotest.appium.driver.RecoveringWebDriverAdapterTest --tests com.ugreen.iot.soluna.autotest.cli.SolunaCliApplicationTest --tests com.ugreen.iot.soluna.autotest.schema.JsonSchemaDslValidatorTest installDist` passed.

Follow-up:

- Removed feedback WebView recovery checks from the shared `app-state` fragments because feedback page recovery is business-module-specific and does not belong in generic login/device-page state convergence.
- iOS feedback aggregate debug plan was rerun after that cleanup as `common-ios-feedback-pack-19`; all six included cases passed and MinIO upload completed with `uploaded=5, failed=0`.
- Added `docs/handoff.md` with the current iOS verification record, Android feedback status, safe debug commands, and next recommended work for a new session.

### 2026-06-17 Visual Template Tap Resolver

- Added `tapVisualTemplate` with `tapImage` / `tapTemplate` aliases for non-text visual affordance clicks using current screenshots, kt-visual template matching, normalized `roi`, match thresholds, scale options, target-region click ratios, and the standard 800ms tap settle.
- Added two-stage template asset resolution: literal template paths are checked during reference assembly, while parameterized `template: "${...}"` values resolve after data merge and are interpreted relative to the owning data file directory.
- Added feedback back-icon template data under `AIot-Tests/apps/com.ugreen.iot/data/common/templates/` and updated feedback cases to pass the template through `mine.visualTemplates.feedbackBackIcon`.
- Added `soluna debug <plan.yaml> source|screenshot|tap|tap-template`, a temporary Appium/WDA debug manager that reuses plan device/app configuration without executing cases, reports, uploads, or notifications.
- Extended failure trace diagnostics to retain page source XML beside before-action screenshots, making locator fixes auditable from source evidence.
- Used the debug source command on the iOS feedback page and corrected feedback problem-type locators from unsupported table-cell assumptions to WebView-scoped visible static-text order.
- Updated schema files, schema docs, architecture notes, and focused tests for the visual template action and data-relative template path resolution.

Verification:

- `jq empty src/main/resources/schemas/v1/*.json` passed.
- `./gradlew test --tests com.ugreen.iot.soluna.autotest.appium.action.WebDriverActionExecutorsTest --tests com.ugreen.iot.soluna.autotest.runner.PlanParameterResolverTest --tests com.ugreen.iot.soluna.autotest.runner.PlanReferenceResolverTest --tests com.ugreen.iot.soluna.autotest.dsl.YamlPlanParserTest --tests com.ugreen.iot.soluna.autotest.schema.JsonSchemaDslValidatorTest` passed.
- `./gradlew test --tests com.ugreen.iot.soluna.autotest.runner.PlanRunnerTest --tests com.ugreen.iot.soluna.autotest.schema.JsonSchemaDslValidatorTest --tests com.ugreen.iot.soluna.autotest.appium.action.WebDriverActionExecutorsTest --tests com.ugreen.iot.soluna.autotest.runner.PlanReferenceResolverTest` passed after adding debug source trace.
- `build/install/soluna/bin/soluna debug AIot-Tests/apps/com.ugreen.iot/plans/common/ios-feedback-debug.yaml source --out build/soluna-debug/ios-feedback-source.xml` passed on the iOS real device and produced feedback page XML evidence.
- iOS and Android feedback real-device plans still need rerun after the source-backed locator correction.

### 2026-06-17 Screen Recording Toast Analysis

- Added first-class DSL actions for Appium screen recording start/stop and screen-recording text regex assertions.
- Generalized explicit screenshot resource handling into a plan resource sink so screenshots, videos, and screen-recording matched frames share the same MinIO manifest/upload path.
- Implemented recording frame extraction through a replaceable `VideoFrameExtractor`; the default uses host `ffmpeg` and kt-visual Paddle OCR for text recognition.
- Updated `TC010_FEEDBACK_SUBMIT_SUCCESS.yaml` to validate the submit-success toast by recording around the submit action, extracting frames, OCR matching `提交成功`, and retaining the recording plus matched frame as explicit resources.
- Updated action schemas, schema docs, architecture notes, and focused tests for the new recording DSL keywords.

Verification:

- `jq empty src/main/resources/schemas/v1/*.json` passed.
- `./gradlew test --tests com.ugreen.iot.soluna.autotest.appium.action.WebDriverActionExecutorsTest --tests com.ugreen.iot.soluna.autotest.dsl.YamlPlanParserTest --tests 'com.ugreen.iot.soluna.autotest.schema.JsonSchemaDslValidatorTest.validates screen recording text assertion action schemas' --tests com.ugreen.iot.soluna.autotest.artifact.PlanResourceManifestWriterTest` passed.
- Full `./gradlew test` is still blocked by pre-existing missing profile/nickname asset files referenced by schema/reference tests.

### 2026-06-16 App-State Fragments And Tap Semantics

- Converted the app-state fragments to the nested action payload style and added app-state cases/plans for login page, guest device page, and logged-in device page.
- Kept common public-module elements in `elements/common.yaml`; removed coordinate-like and hardcoded-copy app-state patterns, with `UgreenAudio` retained only as the stable brand logo marker.
- Fixed iOS login submit locator to use the agreement checkbox as a structural anchor and select the following page submit button, avoiding keyboard-dependent button ordering.
- Changed element tap execution to re-resolve the current element instead of using cached WebElements, require viewport intersection, click the element visible-area center by default, and support `elementXRatio` / `elementYRatio`.
- Added default `tap` settle of 800ms with per-action `settleMs` override.
- Removed global `defaults.actionWait` from app-state plans; final page convergence assertions now carry explicit waits instead of making every action wait 10s.
- Fixed parameter/reference handling so numeric scalar inputs such as phone-like usernames are treated as text and resolved element references do not re-resolve as both `element` and `locator`.
- Improved fragment branch failure messages to report the failed branch child action id.

Verification:

- Focused Gradle tests passed for action executors, recovering/Appium adapters, parser, schema validation, and plan reference resolution.
- `jq empty` passed for updated action schemas.
- `./gradlew installDist` passed.
- Real-device app-state plan passed on iOS: `build/install/soluna/bin/soluna run AIot-Tests/apps/com.ugreen.iot/plans/app-state/ios.yaml --run-id app-state-ios-local ...`
- Real-device app-state plan passed on Android: `build/install/soluna/bin/soluna run AIot-Tests/apps/com.ugreen.iot/plans/app-state/android.yaml --run-id app-state-android-local ...`

## Milestone Summary

### 2026-06-12 Foundation

- Initialized Kotlin/JVM project, Gradle wrapper, schemas, parser, core models, hook bus, execution skeleton, failure/retry interfaces, and Appium abstraction boundaries.
- Copied `soluna-appium-ext` into `lib/soluna-appium-ext` for integrated development while keeping upstream compatibility in mind.

Verification:

- `./gradlew test` passed after initial setup fixes.

### 2026-06-12 Appium And Base Actions

- Added Appium Java Client adapter, WebDriver action executors, Android smoke tests, and first real-device Appium validation.
- Added Android keyboard defaults: `appium:unicodeKeyboard=true`, `appium:resetKeyboard=true`.

Verification:

- Focused Appium adapter/action tests passed.
- Android real-device Appium smoke passed when explicitly enabled.

### 2026-06-13 Runner And Asset Decomposition

- Built `PlanRunner` around a single plan path.
- Added device config parsing, managed Appium server startup, session creation, referenced case files, element catalogs, parameter data, fragment catalogs, setup/teardown lifecycles, runtime variables, and default action wait.
- Moved the profile nickname flow into plan/case/data/element/fragment assets.

Verification:

- Focused parser/resolver/runner tests passed.
- Android profile nickname smoke passed.

### 2026-06-13 Recovery And Lifecycle Hardening

- Added `RecoveringWebDriverAdapter`.
- Added action trace screenshots on failure and teardown-based nickname restore hardening.
- Verified managed Appium recovery by killing the physical Appium process and continuing through the logical session.

Verification:

- Recovery tests passed.
- Android recovery smoke passed when explicitly enabled.

### 2026-06-13 Artifacts, Reports, CLI, Notifications

- Added report JSON/HTML writer, explicit screenshot manifest, MinIO artifact store, async upload queue, gzip upload policy, DingTalk robot sender, upload-failure notifier, local cleanup after successful upload, and CLI runner.

Verification:

- Artifact/report/notification/CLI tests passed.
- Android MinIO upload smoke passed.

### 2026-06-13 iOS WDA

- Added iOS WDA management through go-ios.
- Added iOS 17+ userspace tunnel handling, WDA runner bundle discovery through `soluna-ext`, and managed forward restart after runwda restart.

Verification:

- Focused WDA tests passed.
- Initial real-device attempts exposed app login/state prerequisites and locator gaps.

### 2026-06-15 iOS Real Flow

- Added current iOS device config and iOS profile nickname case.
- Completed iOS profile nickname edit/restore smoke.
- Verified upload-enabled iOS run with MinIO upload and local cleanup.

Verification:

- `./gradlew test` passed.
- iOS local and upload-enabled profile nickname smoke passed when explicitly enabled.

### 2026-06-15 DSL Action Surface

- Migrated external action DSL to keyword-as-field syntax.
- Added schema-enumerated action keywords/aliases.
- Replaced generic text equality with explicit attribute/source assertion actions.
- Added WebDriver attribute and page-source access.

Verification:

- Schema validation and focused parser/runner/action tests passed.
- iOS real-device smoke passed after migration.

### 2026-06-15 Plan Lifecycle Notifications

- Split plan notification into `planStarted`, `testFinished`, and `reportPublished`.
- Kept legacy `planFinished` as a compatibility alias for `reportPublished`.
- Updated MinIO/DingTalk configs and runner tests.

Verification:

- Focused runner/schema/artifact parser tests passed.
- `./gradlew test` passed.

### 2026-06-16 Fragment Control Flow

- Added schema/model/parser/resolver/execution support for generic fragment `if` / `then` / `else`.
- Kept case DSL linear; case schemas and policy validation still reject logic control.
- Added draft `com.ugreen.iot` initialization fragments for login page, guest device page, and logged-in device page under `AIot-Tests/apps/com.ugreen.iot/fragments/app-state.yaml`.
- Recorded that unknown login/logout business flows are explicit failing placeholders until stable real-app elements and account flow are supplied.

Verification:

- `jq empty src/main/resources/schemas/v1/*.json` passed.
- Focused parser/schema/reference/parameter/default/execution tests passed.
- `./gradlew build` passed.

### 2026-06-16 Case Asset Layout

- Moved the current public profile nickname cases under `AIot-Tests/apps/com.ugreen.iot/cases/common/profile/`.
- Updated profile nickname plans and schema validation tests to reference the new paths.
- Documented the app case organization rule: shared behavior under `cases/common/...`, model-specific app behavior under `cases/<model-or-module>/...`.
- Moved nickname case input data under `data/common/profile/update-and-restore-nickname.yaml`.
- Consolidated public login/mine/profile locators into module catalog `elements/common.yaml`; removed case/flow-oriented element files.

Verification:

- `git diff --check` passed.
- Focused schema/parser/reference/parameter tests passed.
- Upload-enabled iOS smoke passed and asserted delivered notifications.

### 2026-06-15 v0 Closure

- Added wait-based polling to assertion actions.
- Removed fixed post-confirm waits from the iOS nickname smoke case.
- Verified v0 with full unit tests, Gradle build, and upload-enabled iOS smoke.

Verification:

- `jq empty src/main/resources/schemas/v1/*.json` passed.
- `git diff --check` passed.
- `./gradlew test` passed.
- `./gradlew build` passed.
- Upload-enabled iOS real-device smoke passed.

### 2026-06-15 Documentation Compaction

- Compressed README v0 implementation notes into capability groups.
- Compressed architecture implementation notes into boundary/status summaries.
- Replaced the long chronological progress log with milestone summaries and maintenance rules.

Verification:

- Documentation-only iteration.
- `wc -l README.md docs/architecture.md docs/schemas.md docs/progress.md`: core docs reduced to 1225 total lines.
- Scan for stale implementation-stage wording passed with no matches.
- `git diff --check`: passed.
- Gradle tests were not rerun for this edit.

### 2026-06-15 v0 Commit Preparation

- Sanitized tracked MinIO/DingTalk examples to use placeholder credentials and kept real local credentials in ignored `*.local.yaml` files.
- Excluded IDE metadata, local runtime assets, and iOS identity files from git.
- Removed unused, unresolved `kt-visual` dependencies from the v0 build; visual/OCR dependencies should be reintroduced only when real v1 actions require them.

Verification:

- `git diff --cached --check` passed.
- Staged secret scan for the known local MinIO/DingTalk credentials passed with no matches.

### 2026-06-15 v1 Visual Dependency Prep

- Reintroduced `kt-visual` dependencies after the v0 commit using the GitHub Releases jar layout for `xieliangji/kt-visual`.
- Configured a Gradle Ivy artifact pattern for `v0.3.1` release assets and mapped the Paddle OCR model jar as a classifier dependency.

Verification:

- `./gradlew dependencies --configuration compileClasspath` passed.
- `./gradlew compileKotlin --rerun-tasks` passed.
- `./gradlew test` passed.

### 2026-06-15 v1 Asset Project Contracts

- Added `soluna-project.schema.json`, `run-request.schema.json`, and `run-result.schema.json`.
- Added `AIot-Tests/soluna-project.yaml` as the first asset project metadata example.
- Updated README, schema docs, and architecture boundaries to define framework / asset project / platform separation.

Verification:

- `jq empty src/main/resources/schemas/v1/*.json` passed.
- `git diff --check` passed.
- `./gradlew test --tests com.ugreen.iot.soluna.autotest.schema.JsonSchemaDslValidatorTest` passed.
- `./gradlew test` passed.
- `./gradlew build` passed.

### 2026-06-15 Architecture Document Rename

- Renamed the legacy v0 architecture document to `docs/architecture.md` because it is now the ongoing architecture source of truth after v0 closure.
- Updated README, AGENTS guidance, and progress references to the new path.

Verification:

- Legacy architecture path reference scan passed with no matches.
- `git diff --check` passed.
- Gradle tests were not rerun for this docs-only rename.

### 2026-06-15 First Asset Project Cases

- Added the first real asset-project case set under `AIot-Tests/apps/com.ugreen.iot`.
- Migrated the verified profile nickname update/restore flow into Android and iOS asset project plans, cases, element catalog, data, fragment, and device config files.
- Added tests that schema-validate the AIot asset files and resolve both asset project plans through case/data/element/fragment references.

Verification:

- `jq empty src/main/resources/schemas/v1/*.json` passed.
- `git diff --check` passed.
- `./gradlew test --tests com.ugreen.iot.soluna.autotest.schema.JsonSchemaDslValidatorTest --tests com.ugreen.iot.soluna.autotest.runner.PlanReferenceResolverTest` passed.
- `./gradlew test` passed.
- `./gradlew build` passed.

### 2026-06-17 Common Mine Cases

- Filled the eight common mine-module case files under `AIot-Tests/apps/com.ugreen.iot/cases/common`.
- Added common mine fixture data for language title/value and withdraw-verification countdown assertions.
- Extended `elements/common.yaml` with module-level mine, language, about, personal-information-protection, WebView, and withdraw-verification elements instead of case-owned element definitions.
- Added Android and iOS common plans with login-page, logged-in-device-page, and guest-device-page stages. TC001-TC007 are planned for both logged-in and guest device stages; TC008 is planned only for logged-in device state.
- Adjusted TC003 to enter the app system-permissions management page and verify the Bluetooth, location, microphone, album, and camera permission rows are present, without jumping into OS settings pages.
- Added common artifact templates under `AIot-Tests/artifacts/`; the common plans reference ignored `minio.local.yaml` so report upload and DingTalk lifecycle notifications are part of the full plan configuration without committing local secrets.
- Moved TC002 language-settings to the end of each common-plan device-page stage so no other case runs after a language-switching case in that stage.
- TC002 switches to English, returns to Mine and verifies the page source contains the expected language value, then switches back to Simplified Chinese and verifies the restored value. The success assertion avoids the unstable iOS right-side-value element subtree.
- Added a dedicated logout confirmation element for logout action sheets; protocol confirmations and guest-login prompts still use the generic dialog-positive element.
- Added scoped `caseSetupFragments` / `caseSetupActions` lifecycle support at plan, stage, and case levels. The common logged-in and guest device stages use stage-level `caseSetupFragments` to restart the app before each case while the stage state fragment still runs only once.
- Wired `defaults.implicitWaitMs` into Appium session `timeouts.implicit` and set current common/app-state plans to 8000ms.
- Changed explicit action element lookup to temporarily disable the session implicit wait while polling with the action's own `wait`, then restore the session implicit wait.
- Changed `restartApp` to wait for the target app to report foreground state before returning; an action-level `wait` can override the default foreground wait.
- Fixed stage/case inline `parameters` so they merge into the parameter context used by later lifecycle actions, case actions, and element locators.
- Fixed the iOS logout confirmation locator to select the right-side confirmation button in the logout sheet; the prior `last()-1` locator selected cancel.
- Broadened the iOS common back-button locator to match visible icon buttons whose `name` or `label` contains `common back`, covering WebView agreement pages where the icon name is not prefixed by `common back`.
- Added a dedicated `common.webBackButton` and updated TC004-TC007 to use it when returning from WebView agreement/privacy/list pages, leaving `common.backButton` for native page returns and state probes.

Current status:

- Android common plan previously passed on real device before the final TC002 language-switch ordering; TC002-only Android real-device verification passed after the final language changes.
- iOS WDA is restored. TC002-only iOS real-device verification passed after the final language changes.
- Common logged-in and guest device stages now run their app-state setup once at stage start, then restart the app before each case through stage-level `caseSetupFragments`; the login-page stage remains unchanged because its app-state case already has its own setup.
- Full upload-enabled common plans require a local ignored `AIot-Tests/artifacts/minio.local.yaml` copied from the template with real MinIO and DingTalk credentials.
- Local `minio.local.yaml` was updated to reference ignored `dingtalk.local.yaml` for upload-failure and lifecycle notifications.

Verification:

- `git diff --check` passed.
- Focused Gradle tests passed for Appium session timeout injection and case setup scope resolution.
- Android real-device common plan passed after changing Android WebView content locator to the WebView under `lyWebView`.
- iOS TC002-only debug plan passed: `common-ios-tc002-local-2`.
- Android TC002-only debug plan passed: `common-android-tc002-local-1`.
- Android upload-enabled common plan `common-android-full-local-1` ran with `minio.local.yaml`; upload completed with `uploaded=8, failed=0`, DingTalk lifecycle config was present, and local cleanup deleted the local run after upload. The plan failed in TC002 because TC008 left the app on the withdraw-verification page; this led to the stage-level `caseSetupFragments` fix above.
- Android upload-enabled common plan `common-android-full-local-3` ran with `minio.local.yaml`; upload completed with `uploaded=8, failed=0`, DingTalk lifecycle config was present, and local cleanup deleted the local run after upload. The logged-in stage passed completely, then guest TC005 failed because `restartApp` returned while the device was still on the Android launcher/activation transition, allowing the next action to run against stale app state. The `restartApp` foreground wait fix addresses this failure mode.
- Android upload-enabled common plan `common-android-full-local-4` passed with `minio.local.yaml`; upload completed with `uploaded=3, failed=0`, and local cleanup deleted the local run after upload.
- iOS upload-enabled common plan `common-ios-full-local-1` failed in login-page stage setup because the logout confirmation locator tapped cancel instead of confirm. Upload completed with `uploaded=8, failed=0`, and local cleanup deleted the local run after upload.
- iOS upload-enabled common plan `common-ios-full-local-2` passed login-page setup and TC001/TC003 in the logged-in stage, then failed in logged-in TC004 returning from the agreement WebView because the common back-button locator was too narrow for the WebView page icon.
- iOS upload-enabled common plan `common-ios-full-local-3` confirmed that the WebView back icon does not expose `common back`; the WebView return action now uses the dedicated `common.webBackButton` instead of widening the native back-button locator further.
- iOS upload-enabled common plan `common-ios-full-local-4` passed with `minio.local.yaml`; upload completed with `uploaded=3, failed=0`, and local cleanup deleted the local run after upload.
- Focused Gradle tests passed for parameter resolution, WebDriver action executors, Appium session timeout injection, and Appium locator mapping.
- Full `./gradlew test` is currently blocked by pre-existing deleted profile asset files referenced by older tests.

## Current Verification Baseline

Last full v0 closure verification:

```bash
jq empty src/main/resources/schemas/v1/*.json
git diff --check
./gradlew test
./gradlew build
SOLUNA_IOS_UGREEN_PROFILE_SMOKE=true \
SOLUNA_IOS_UGREEN_PROFILE_PLAN_PATH=.soluna/plans/ugreen-profile-nickname-ios-upload.yaml \
SOLUNA_IOS_UGREEN_PROFILE_NEW_NICKNAME=SolunaIOS630 \
SOLUNA_RUN_ID=ugreen-profile-ios-minio-20260615-v0-close-001 \
./gradlew test --rerun-tasks --tests com.ugreen.iot.soluna.autotest.runner.RealIosUgreenProfilePlanTest
```

## Next Work

Start v1 from real business cases:

- Write broader real cases first.
- Add action keywords only when real cases expose a stable need.
- Improve report UX from actual debugging pain points.
- Move lifecycle notification configuration out of artifact-store coupling.
- Keep schema/docs/progress compact and contract-focused.
