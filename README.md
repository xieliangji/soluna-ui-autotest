# soluna-ui-autotest

`soluna-ui-autotest` is a Kotlin/JVM framework project for iOS and Android real-device UI automation with Appium/WebDriver.

The framework is designed around YAML DSL test plans, pluggable execution components, hook-driven side effects, MinIO artifact storage, and a self-owned report model.

## Current Status

v0 is closed as a runnable foundation. Next development should start from real business cases and let v1 gaps drive new action keywords, wait/assertion improvements, and report UX work.

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

## Examples

```text
examples/plans/daily-smoke.yaml
examples/plans/ugreen-profile-nickname.yaml
examples/cases/ugreen-profile-nickname.yaml
examples/data/default.yaml
examples/data/ugreen-profile.yaml
examples/elements/daily-smoke.yaml
examples/elements/ugreen-profile.yaml
examples/fragments/app-lifecycle.yaml
examples/devices/00008150-001E15AA1140401C.yaml
examples/devices/AMRF026323000807.yaml
examples/artifacts/minio.yaml
examples/artifacts/dingtalk-upload-alert.yaml
AIot-Tests/soluna-project.yaml
```

The current v0 parser validates plan YAML with JSON Schema and framework policy checks before mapping it to Kotlin models.

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

The first real asset project example is under:

```text
AIot-Tests/
```

The current profile nickname cases are:

```text
AIot-Tests/apps/com.ugreen.iot/plans/profile/nickname-android.yaml
AIot-Tests/apps/com.ugreen.iot/plans/profile/nickname-ios.yaml
AIot-Tests/apps/com.ugreen.iot/cases/common/profile/update-and-restore-nickname-android.yaml
AIot-Tests/apps/com.ugreen.iot/cases/common/profile/update-and-restore-nickname-ios.yaml
```

Within an app asset root, case files are organized by app module. Shared app behavior goes under `cases/common/`; model-specific behavior should use one directory per model or module under `cases/`, for example `cases/UGREEN HiTune X8/...`. Plans continue to reference concrete case files through `caseRefs`, so the runner remains independent of the business directory naming.

Case-specific data may mirror the case path and logical case name. The current nickname input data is:

```text
AIot-Tests/apps/com.ugreen.iot/data/common/profile/update-and-restore-nickname.yaml
```

Element catalogs stay module-oriented instead of case-oriented. Public app elements such as login, device, and mine live in:

```text
AIot-Tests/apps/com.ugreen.iot/elements/common.yaml
```

Draft reusable app-state initialization fragments live at:

```text
AIot-Tests/apps/com.ugreen.iot/fragments/app-state.yaml
```

They use generic fragment `if` / `then` / `else` control flow with ordinary action/assertion predicates. The login and logout branches still require stable real-app flow details before being wired into executable plans.

Run them the same way as any plan:

```bash
./gradlew run --args='run AIot-Tests/apps/com.ugreen.iot/plans/profile/nickname-ios.yaml'
```

## v0 Status

v0 is closed as a runnable foundation. It supports:

- Plan-rooted YAML execution with schema-first validation, case/data/element/fragment references, and keyword-as-field actions such as `tap: open-mine-tab`.
- Linear real-device execution on Android and iOS through Appium Java Client, managed Appium server startup, session recovery, and managed iOS WDA/go-ios support.
- Pluggable execution boundaries for parsers, action executors, driver adapters, Appium server management, artifact upload, report writing, and notifications.
- Default actions for tap/input/wait/restart app/get text/screenshot and element-attribute or source regex assertions. Assertion actions poll by resolved `wait`.
- Runtime variables through `@{case.name}` / `@{plan.name}` and parameter references through `${...}`.
- Local JSON/HTML reporting, explicit screenshot resource manifest, failure trace screenshots, MinIO artifact upload, upload-success cleanup, and DingTalk lifecycle/upload-failure notifications.
- Optional real-device smoke plans for the `com.ugreen.iot` profile nickname edit/restore flow.

JUnit is used for framework development tests only. Runtime DSL plan orchestration belongs to the Soluna runner and result model.

## CLI Runner

Normal execution starts from a single plan path:

```bash
./gradlew run --args='run examples/plans/ugreen-profile-nickname.yaml'
```

The installed distribution exposes the executable as `soluna`:

```bash
./gradlew installDist
./build/install/soluna/bin/soluna run examples/plans/ugreen-profile-nickname.yaml
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

## Local Commands

```bash
./gradlew test
./gradlew build
```

The project currently uses Kotlin JVM with Java 21.

## Artifact Upload

Upload is enabled by adding an artifact store reference to a plan:

```yaml
artifactStore: ../artifacts/minio.yaml
```

The example MinIO config is:

```text
examples/artifacts/minio.yaml
```

It contains the endpoint, bucket, prefix, direct credentials, gzip compression policy, upload retry policy, and a relative reference to:

```text
examples/artifacts/dingtalk-upload-alert.yaml
```

The v0 config supports direct values in YAML:

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
  --tests com.ugreen.iot.soluna.autotest.appium.ext.RealAndroidSolunaExtSmokeTest \
  --tests com.ugreen.iot.soluna.autotest.appium.driver.RealAndroidAppiumSmokeTest
```

Without `SOLUNA_ANDROID_UDID`, these smoke tests return early and do not require a device.

Optional Android recovery smoke test:

```bash
SOLUNA_APPIUM_RECOVERY_SMOKE=true \
SOLUNA_ANDROID_UDID=<device-udid> \
SOLUNA_APPIUM_EXECUTABLE=/opt/homebrew/bin/appium \
./gradlew test --tests com.ugreen.iot.soluna.autotest.appium.driver.RealAndroidAppiumRecoverySmokeTest
```

This smoke test starts a managed Appium server, creates an Android session, forcefully exits that Appium process, then validates that `RecoveringWebDriverAdapter` restarts Appium, rebuilds the physical session, and captures another screenshot through the same logical session.

Optional managed Appium server smoke test:

```bash
SOLUNA_MANAGED_APPIUM_SMOKE=true \
SOLUNA_APPIUM_EXECUTABLE=/opt/homebrew/bin/appium \
./gradlew test --tests com.ugreen.iot.soluna.autotest.appium.server.ManagedAppiumServerSmokeTest
```

This smoke test starts Appium through `LocalProcessAppiumServerManager` on an available local port, waits for `/status`, asserts it is running, and stops the process.

Optional iOS WDA smoke test:

```bash
SOLUNA_IOS_WDA_SMOKE=true \
SOLUNA_IOS_UDID=<ios-device-udid> \
SOLUNA_APPIUM_EXECUTABLE=/opt/homebrew/bin/appium \
SOLUNA_GO_IOS_EXECUTABLE=/opt/homebrew/bin/ios \
SOLUNA_IOS_WDA_STARTUP_DELAY_MS=10000 \
./gradlew test --tests com.ugreen.iot.soluna.autotest.appium.wda.RealIosWdaSmokeTest
```

This smoke test starts a managed Appium server with `soluna-ext`, resolves iOS device metadata and the installed WDA runner bundle through the plugin, starts go-ios userspace tunnel for iOS 17+, starts WDA, starts local port forwarding, probes WDA `/status`, then stops all managed processes.

Optional real Android UGREEN profile nickname YAML smoke:

```bash
SOLUNA_UGREEN_PROFILE_SMOKE=true \
./gradlew test --tests com.ugreen.iot.soluna.autotest.runner.RealAndroidUgreenProfilePlanTest
```

To run the same smoke with a local upload-enabled plan, point the test at another plan path:

```bash
SOLUNA_UGREEN_PROFILE_SMOKE=true \
SOLUNA_UGREEN_PROFILE_PLAN_PATH=.soluna/plans/ugreen-profile-nickname-upload.yaml \
SOLUNA_UGREEN_PROFILE_NEW_NICKNAME=SolunaFix42 \
SOLUNA_RUN_ID=ugreen-profile-minio-20260613-005 \
./gradlew test --tests com.ugreen.iot.soluna.autotest.runner.RealAndroidUgreenProfilePlanTest
```

The plan is `examples/plans/ugreen-profile-nickname.yaml`. It composes `examples/cases/ugreen-profile-nickname.yaml`, which references `examples/data/ugreen-profile.yaml` for the new nickname and `examples/elements/ugreen-profile.yaml` for stable non-copy locators. App restart is a stage setup from `examples/fragments/app-lifecycle.yaml`, and `examples/devices/AMRF026323000807.yaml` is the UDID-named real Android device config. The plan keeps repeated action waits under `defaults.actionWait`; the case captures the original nickname at runtime into `@{case.originalNickname}`, changes and verifies the new nickname in the main action flow, then restores the captured nickname from case teardown so cleanup still runs after a main-flow failure. The local report writer emits:

```text
build/soluna-runs/ugreen-profile-local/report/index.html
build/soluna-runs/ugreen-profile-local/report/execution-result.json
```

Optional real iOS UGREEN profile nickname YAML smoke:

```bash
SOLUNA_IOS_UGREEN_PROFILE_SMOKE=true \
SOLUNA_IOS_UGREEN_PROFILE_PLAN_PATH=examples/plans/ugreen-profile-nickname-ios.yaml \
SOLUNA_IOS_UGREEN_PROFILE_NEW_NICKNAME=SolunaIOS \
SOLUNA_RUN_ID=ugreen-profile-ios-local \
./gradlew test --tests com.ugreen.iot.soluna.autotest.runner.RealIosUgreenProfilePlanTest
```

The iOS smoke uses `examples/cases/ugreen-profile-nickname-ios.yaml` and the shared element catalog with iOS locator branches selected from `examples/elements/ugreen-profile.yaml`. The iOS case includes the current app workaround for the first nickname modal after restart: it uses viewport-relative `tap` actions on the modal backdrop, then reopens the nickname dialog before editing. The connected iOS device must already be logged in to an account where the mine page exposes the profile/avatar entry; otherwise the nickname flow cannot reach the personal information page.

## Appium Plugin

The Appium extension source is copied into this repository for integrated development:

```text
lib/soluna-appium-ext
```

Its upstream GitHub project is:

```text
https://github.com/xieliangji/soluna-appium-ext
```

Host/device-adjacent capabilities should generally be implemented in that Appium plugin layer and consumed here through a client abstraction. After plugin extension work is complete and verified, changes should be prepared for commit and push to the upstream GitHub project.

Plugin commands:

```bash
cd lib/soluna-appium-ext
npm ci
npm test
npm run build
npm run lint
```
