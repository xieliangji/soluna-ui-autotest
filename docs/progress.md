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
- Schema-first validation for plan, case, element catalog, fragment catalog, parameter data, device config, artifact store, notification sender, report data, and resource manifest.
- Keyword-as-field action syntax, for example `tap: open-mine-tab`.
- Case/data/element/fragment decomposition.
- Linear case DSL; setup/teardown fragments are separate lifecycle assets.
- Android and iOS real-device execution through Appium Java Client.
- Managed Appium server with runtime port allocation and `/status` probing.
- Recovering WebDriver adapter with logical session and physical session rebuild.
- Managed iOS WDA through go-ios, including iOS 17+ userspace tunnel handling.
- `soluna-ext` client for device metadata, WDA bundle lookup, commands, and logs.
- Default actions: `tap`, `input`, `wait`, `restartApp`, `getText`, `screenshot`, `assertElementAttrEquals`, `assertElementAttrRegexMatch`, `assertSourceRegexMatch`.
- Assertion actions poll by resolved `wait`.
- Runtime variables via `@{plan.name}` and `@{case.name}`.
- Parameter references via `${...}`.
- Local JSON/HTML report writer.
- Explicit screenshot resource manifest.
- Failure trace screenshots uploaded only on failure.
- Async MinIO uploads with compression, retry, bounded drain, and local cleanup after successful upload.
- DingTalk lifecycle notifications: plan started, test finished, report published.
- DingTalk aggregated upload-failure alerts.
- CLI runner: `soluna run <plan.yaml>`.

Primary verified real-device flow:

- `com.ugreen.iot` profile nickname edit/restore on Android and iOS.
- iOS upload-enabled run verified MinIO upload, report/resource links, local cleanup, DingTalk notifications, and assertion polling without fixed post-confirm sleeps.

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
- `wc -l README.md docs/architecture-v0.md docs/schemas.md docs/progress.md`: core docs reduced to 1225 total lines.
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
