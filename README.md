# soluna-ui-autotest

`soluna-ui-autotest` is a Kotlin/JVM framework project for iOS and Android real-device UI automation with Appium/WebDriver.

The framework is designed around YAML DSL test plans, pluggable execution components, hook-driven side effects, MinIO artifact storage, and a self-owned report model.

## Current Status

The framework is now a runnable contract and execution package. Current work is v1-style hardening driven by real asset-project cases: action keyword coverage, wait/assertion behavior, recovery diagnostics, report usability, platform integration contracts, and Codex-assisted asset-project authoring.

Primary design reference:

- [docs/architecture.md](docs/architecture.md)

Progress record:

- [docs/progress.md](docs/progress.md)

Schema notes:

- [docs/schemas.md](docs/schemas.md)

Codex agent development guidance:

- [AGENTS.md](AGENTS.md)

## Design Summary

- Execution model: `Plan -> Stage -> Case -> Action`
- DSL format: YAML
- Case execution: linear, no logic control
- Init fragments: reusable and allowed to contain logic control
- Devices: one process, one real device, serial execution
- Driver: Appium over WebDriver
- Appium server: managed by the project, with custom plugin support
- Artifacts: uploaded to MinIO
- Reports: self-owned single HTML output backed by JSON data files
- Extension model: schema-first, hook-driven, and pluggable
- Runtime orchestration: owned by Soluna runner, not JUnit/TestNG

## Example Asset Project

```text
AIot-Tests/soluna-project.yaml
AIot-Tests/apps/com.ugreen.iot/plans/app-state/android.yaml
AIot-Tests/apps/com.ugreen.iot/plans/app-state/ios.yaml
AIot-Tests/apps/com.ugreen.iot/plans/common/android.yaml
AIot-Tests/apps/com.ugreen.iot/plans/common/ios.yaml
AIot-Tests/apps/com.ugreen.iot/cases/common/app-state/login-page.yaml
AIot-Tests/apps/com.ugreen.iot/elements/common.yaml
AIot-Tests/apps/com.ugreen.iot/fragments/app-state.yaml
AIot-Tests/devices/android/AMRF026323000807.yaml
AIot-Tests/devices/ios/00008140-001805D80C93801C.yaml
AIot-Tests/artifacts/minio.template.yaml
AIot-Tests/artifacts/dingtalk.template.yaml
```

The parser validates plan YAML with JSON Schema and framework policy checks before mapping it to Kotlin models.

Execution starts from a plan path. Other YAML files, including device config, parameter data, cases, element catalogs, and setup fragments, are reached through references declared by the plan directly or indirectly.

## Asset Project Contract

Real test assets should live outside the framework source tree as a Soluna asset project. The framework is the runner and contract package; an asset project owns business plans, cases, elements, data, fragments, devices, and artifact configs.

The first project-level contract is:

```text
src/main/resources/schemas/v1/soluna-project.schema.json
```

Service and platform integration contracts are:

```text
src/main/resources/schemas/v1/run-request.schema.json
src/main/resources/schemas/v1/run-result.schema.json
```

The CLI still starts from a single plan path. Asset project metadata is a stable contract for future project discovery, platform use-case management, and Runner service requests.

## Codex Skill

The project maintains a bundled Codex skill for creating and debugging external Soluna asset projects:

```text
codex/skills/soluna-ui-autotest-creator
```

`./gradlew installDist` copies the skill into:

```text
build/install/soluna/codex/skills/soluna-ui-autotest-creator
```

The skill is versioned with the framework because it depends on the current DSL schema, CLI behavior, action keywords, debug workflow, and capability-extension rules. When those contracts change, update the skill in the same iteration.

The skill includes a deterministic starter scaffold for external asset projects:

```bash
python3 codex/skills/soluna-ui-autotest-creator/scripts/create_asset_project.py \
  --output ./My-Tests \
  --project-id my-tests \
  --app-id com.example.app \
  --app-name ExampleApp \
  --platform android \
  --udid CHANGE_ME_UDID
```

The generated project is intentionally minimal. It creates a smoke plan that restarts the app, waits, and captures a screenshot; business locators, state fragments, and test data should be added after real-device debugging through the distributed Soluna CLI validation, run, and debug workflows.

The skill also includes `scripts/send_dingtalk_gap_notice.py` for approved capability-gap notifications. It defaults to the built-in Soluna debug DingTalk robot; override it with `SOLUNA_CODEX_DINGTALK_WEBHOOK` and `SOLUNA_CODEX_DINGTALK_SECRET` when another robot should receive notices.

The in-repository asset project example is:

```text
AIot-Tests/
```

Within an app asset root, case files are organized by app module. Shared app behavior goes under `cases/common/`; model-specific behavior should use one directory per model or module under `cases/`, for example `cases/UGREEN HiTune X8/...`. Plans reference concrete case files through `caseRefs`, so the runner remains independent of business directory naming.

Case-specific data may mirror the case path and logical case name. Shared test data stays under module-oriented data files such as `AIot-Tests/apps/com.ugreen.iot/data/common/mine.yaml`.

Element catalogs stay module-oriented instead of case-oriented. Public app elements such as login, device, and mine live in:

```text
AIot-Tests/apps/com.ugreen.iot/elements/common.yaml
```

Reusable app-state initialization fragments live at:

```text
AIot-Tests/apps/com.ugreen.iot/fragments/app-state.yaml
```

They use generic fragment `if` / `then` / `else` control flow with ordinary action/assertion predicates. Asset-project docs should carry app-specific account state, device state, and debug-path notes.

Run an example asset-project plan the same way as any plan:

```bash
./gradlew run --args='run AIot-Tests/apps/com.ugreen.iot/plans/app-state/ios.yaml'
```

## Capability Snapshot

The current runner supports:

- Plan-rooted YAML execution with schema-first validation, case/data/element/fragment references, and keyword-as-field actions such as `tap: { id, element, desc }`.
- Linear real-device execution on Android and iOS through Appium Java Client, managed Appium server startup, managed Appium extension/driver bootstrap, session recovery, and managed iOS WDA/go-ios support.
- Pluggable execution boundaries for parsers, action executors, driver adapters, Appium server management, artifact upload, report writing, notifications, failure strategy, and retry strategy.
- Default actions for tap/input/wait/restart app/clear app data/get text/save element rect/screenshot/visual-template tap/screen recording and element-attribute, source-regex, or screen-recording OCR assertions. Assertion actions poll by resolved `wait`.
- Runtime variables through `@{case.name}` / `@{plan.name}` and parameter references through `${...}`.
- Local JSON/HTML reporting with summary/failure/action metadata, explicit resource manifest for screenshots/recordings/OCR evidence, failure trace screenshots and page source, MinIO artifact upload, upload-success cleanup, and DingTalk lifecycle/upload-failure notifications with execution statistics.
- Debug CLI for source/screenshot/tap/tap-element/input/tap-template/shell inspection from a plan's device and app config.

JUnit is used for framework development tests only. Runtime DSL plan orchestration belongs to the Soluna runner and result model.

## CLI Runner

Normal execution starts from a single plan path:

```bash
./gradlew run --args='run AIot-Tests/apps/com.ugreen.iot/plans/app-state/android.yaml'
```

The installed distribution exposes the executable as `soluna`:

```bash
./gradlew installDist
./build/install/soluna/bin/soluna run AIot-Tests/apps/com.ugreen.iot/plans/app-state/android.yaml
```

Supported optional runtime flags:

```bash
soluna run <plan.yaml> \
  --run-id run-001 \
  --param profile.newNickname=SolunaTester \
  --report-root build/soluna-runs \
  --expect passed
```

Device config, artifact store config, cases, elements, data, and fragments are not CLI arguments. They must be referenced by the plan directly or indirectly.

For temporary real-device inspection, the installed distribution also exposes a debug command. It starts a managed Appium/WDA session from the plan device/app config, runs one low-level action, and exits without executing cases, reports, uploads, or notifications:

```bash
soluna debug <plan.yaml> source --out build/soluna-debug/source.xml
soluna debug <plan.yaml> screenshot --out build/soluna-debug/screen.png
soluna debug <plan.yaml> tap --x-ratio 0.50 --y-ratio 0.50
soluna debug <plan.yaml> tap-element --strategy xpath --locator "//XCUIElementTypeButton[1]" --element-x-ratio 0.50 --element-y-ratio 0.50
soluna debug <plan.yaml> input --strategy class --locator XCUIElementTypeTextView --text "debug text" --clear-first true
soluna debug <plan.yaml> tap-template --template AIot-Tests/apps/com.ugreen.iot/data/common/templates/feedback-back-icon.png --roi 0,0.04,0.2,0.12
soluna debug <plan.yaml> shell
```

Use debug output as locator evidence; do not encode platform-specific debug operations into business cases.

## Local Commands

```bash
./gradlew test
./gradlew build
```

The project currently uses Kotlin JVM with Java 21.

## Bundled Runtime Tools

Screen-recording toast analysis needs FFmpeg in two places: Appium XCUITest uses a command named `ffmpeg` from the Appium server process PATH to record iOS screens, and the runner uses FFmpeg to extract frames from recorded videos before OCR.

Place platform binaries under:

```text
tools/ffmpeg/macos-arm64/ffmpeg
tools/ffmpeg/macos-x64/ffmpeg
tools/ffmpeg/linux-arm64/ffmpeg
tools/ffmpeg/linux-x64/ffmpeg
tools/ffmpeg/windows-x64/ffmpeg.exe
```

`./gradlew installDist` copies `tools/` into `build/install/soluna/tools` and the bundled Appium extension source into `build/install/soluna/plugins/soluna-appium-ext`. Managed Appium server startup prepends the resolved explicit or bundled FFmpeg directory to PATH. Override paths with `-Dsoluna.ffmpeg.path=...`, `SOLUNA_FFMPEG`, `-Dsoluna.tools.dir=...`, or `SOLUNA_TOOLS_DIR`.

The checked-in binaries are from `eugeneware/ffmpeg-static` release `b6.1.1` and follow that package's `GPL-3.0-or-later` license. Upstream README and LICENSE files are kept beside each platform binary.

`assertScreenRecordingTextRegexMatch` uses Paddle OCR by default. Cases can set `recognizer: multimodal` to use the kt-visual OpenAI-compatible multimodal OCR client for difficult translucent or mixed-background text. Configure it at runtime only:

```bash
export SOLUNA_VISUAL_OCR_MULTIMODAL_BASE_URL=http://host:port/v1
export SOLUNA_VISUAL_OCR_MULTIMODAL_API_KEY=<api-key>
export SOLUNA_VISUAL_OCR_MULTIMODAL_MODEL=gpt-5.5
export SOLUNA_VISUAL_OCR_MULTIMODAL_REASONING_EFFORT=high
```

The multimodal recognizer defaults to `stream=true` and logs stream chunks at info level; set `SOLUNA_VISUAL_OCR_MULTIMODAL_STREAM=false` to disable streaming. Stream mode uses a long HTTP timeout and an idle watchdog: `SOLUNA_VISUAL_OCR_MULTIMODAL_STREAM_IDLE_TIMEOUT_MS` controls how long the stream may go without reasoning or content output before it is stopped, while `SOLUNA_VISUAL_OCR_MULTIMODAL_STREAM_HTTP_TIMEOUT_MS` bounds the underlying HTTP request. Candidate frames for multimodal OCR are recognized concurrently; tune the worker count with `SOLUNA_VISUAL_OCR_MULTIMODAL_PARALLELISM`. The default prompt is tuned for UI assertions and low-contrast toast text; override it with `SOLUNA_VISUAL_OCR_MULTIMODAL_PROMPT` only when a specific model endpoint needs different wording.

## Artifact Upload

Upload is enabled by adding an artifact store reference to a plan:

```yaml
artifactStore: ../artifacts/minio.yaml
```

The example MinIO config is:

```text
AIot-Tests/artifacts/minio.template.yaml
```

It contains the endpoint, bucket, prefix, direct credentials, gzip compression policy, upload retry policy, and a relative reference to:

```text
AIot-Tests/artifacts/dingtalk.template.yaml
```

The current config supports direct values in YAML:

```yaml
credentials:
  accessKey: <minio-access-key>
  secretKey: <minio-secret-key>

robot:
  webhook: <dingtalk-webhook>
  secret: <dingtalk-signing-secret>
```

The tracked MinIO and DingTalk examples use placeholder credentials. Put private values in a copied `*.local.yaml` file or private test assets when the config should not be shared.

When artifact upload is enabled, `PlanRunner` uploads:

```text
runs/{runId}/report/execution-result.json
runs/{runId}/report/plan-resource-manifest.json
runs/{runId}/report/index.html
runs/{runId}/resources/<explicit-screenshot-file>
runs/{runId}/diagnostics/<failure-trace-screenshot-file>
```

The local report remains under `build/soluna-runs/{runId}/report/`; links in uploaded HTML are rewritten to MinIO URLs. Failure trace screenshots are diagnostic artifacts and do not enter `plan-resource-manifest.json`, which is reserved for explicit screenshot actions.

Plans can enable failed-action trace screenshots and optional local cleanup:

```yaml
trace:
  screenshots:
    enabled: true
    beforeAction: onFailure
    retainBeforeActionCount: 5
    upload: onFailure
localArtifacts:
  cleanup:
    mode: after-upload-success
```

`after-upload-success` deletes the local run directory only after all queued upload tasks have completed successfully.

Optional Android real-device smoke tests require a running Appium server with `soluna-ext` enabled:

```bash
appium --use-plugins=soluna-ext --port 4725 --log-level info
```

Then run:

```bash
SOLUNA_ANDROID_UDID=<device-udid> \
SOLUNA_APPIUM_SERVER_URL=http://127.0.0.1:4725 \
./gradlew test \
  --tests com.soluna.ui.autotest.appium.ext.RealAndroidSolunaExtSmokeTest \
  --tests com.soluna.ui.autotest.appium.driver.RealAndroidAppiumSmokeTest
```

Without `SOLUNA_ANDROID_UDID`, these smoke tests return early and do not require a device.

Optional Android recovery smoke test:

```bash
SOLUNA_APPIUM_RECOVERY_SMOKE=true \
SOLUNA_ANDROID_UDID=<device-udid> \
SOLUNA_APPIUM_EXECUTABLE=/opt/homebrew/bin/appium \
./gradlew test --tests com.soluna.ui.autotest.appium.driver.RealAndroidAppiumRecoverySmokeTest
```

This smoke test starts a managed Appium server, creates an Android session, forcefully exits that Appium process, then validates that `RecoveringWebDriverAdapter` restarts Appium, rebuilds the physical session, and captures another screenshot through the same logical session.

Optional managed Appium server smoke test:

```bash
SOLUNA_MANAGED_APPIUM_SMOKE=true \
SOLUNA_APPIUM_EXECUTABLE=/opt/homebrew/bin/appium \
./gradlew test --tests com.soluna.ui.autotest.appium.server.ManagedAppiumServerSmokeTest
```

This smoke test starts Appium through `LocalProcessAppiumServerManager` on an available local port, waits for `/status`, asserts it is running, and stops the process.

Optional iOS WDA smoke test:

```bash
SOLUNA_IOS_WDA_SMOKE=true \
SOLUNA_IOS_UDID=<ios-device-udid> \
SOLUNA_APPIUM_EXECUTABLE=/opt/homebrew/bin/appium \
SOLUNA_GO_IOS_EXECUTABLE=/opt/homebrew/bin/ios \
SOLUNA_IOS_WDA_STARTUP_DELAY_MS=10000 \
./gradlew test --tests com.soluna.ui.autotest.appium.wda.RealIosWdaSmokeTest
```

This smoke test starts a managed Appium server with `soluna-ext`, resolves iOS device metadata and the installed WDA runner bundle through the plugin, starts go-ios userspace tunnel for iOS 17+, starts WDA, starts local port forwarding, probes WDA `/status`, then stops all managed processes.

Optional real Android asset-plan smoke:

```bash
SOLUNA_UGREEN_PROFILE_SMOKE=true \
SOLUNA_UGREEN_PROFILE_PLAN_PATH=AIot-Tests/apps/com.ugreen.iot/plans/common/android.yaml \
./gradlew test --tests com.soluna.ui.autotest.runner.RealAndroidUgreenProfilePlanTest
```

To run an upload-enabled plan, point the test at a local plan that references a private artifact config such as `AIot-Tests/artifacts/minio.local.yaml`:

```bash
SOLUNA_UGREEN_PROFILE_SMOKE=true \
SOLUNA_UGREEN_PROFILE_PLAN_PATH=AIot-Tests/apps/com.ugreen.iot/plans/common/android.yaml \
SOLUNA_RUN_ID=ugreen-android-local \
./gradlew test --tests com.soluna.ui.autotest.runner.RealAndroidUgreenProfilePlanTest
```

The default in-repository Android asset plan is `AIot-Tests/apps/com.ugreen.iot/plans/common/android.yaml`. It composes cases under `AIot-Tests/apps/com.ugreen.iot/cases/common/`, uses shared data and elements under the same app asset root, and resolves the Android device config from `AIot-Tests/devices/android/`. The local report writer emits:

```text
build/soluna-runs/{runId}/report/index.html
build/soluna-runs/{runId}/report/execution-result.json
```

Optional real iOS asset-plan smoke:

```bash
SOLUNA_IOS_UGREEN_PROFILE_SMOKE=true \
SOLUNA_IOS_UGREEN_PROFILE_PLAN_PATH=AIot-Tests/apps/com.ugreen.iot/plans/common/ios.yaml \
SOLUNA_RUN_ID=ugreen-ios-local \
./gradlew test --tests com.soluna.ui.autotest.runner.RealIosUgreenProfilePlanTest
```

The default in-repository iOS asset plan is `AIot-Tests/apps/com.ugreen.iot/plans/common/ios.yaml`. The connected iOS device must satisfy that plan's account and app-state preconditions.

## Appium Plugin

The Appium extension source is maintained in this repository as an integrated project component:

```text
lib/soluna-appium-ext
```

Host/device-adjacent capabilities should generally be implemented in that Appium plugin layer and consumed here through a client abstraction. Plugin changes are committed with this repository and distributed with the Soluna package; they are no longer prepared for submission back to the original standalone project.

Managed Appium startup automatically checks required Appium extensions before launching the server:

- `soluna-ext` is installed from the project-bundled source when missing. If an installed `soluna-ext` is not from this project's bundled source, it is uninstalled and reinstalled from the bundled source.
- `uiautomator2` and `xcuitest` drivers are installed when missing.

Local hosts still need Node/npm and Appium installed. The runner owns Appium extension installation for managed servers; external Appium servers are left untouched.

Plugin commands:

```bash
cd lib/soluna-appium-ext
npm ci
npm test
npm run build
npm run lint
```
