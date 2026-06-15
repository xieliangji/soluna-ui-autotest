# Schemas

The v0 implementation uses JSON Schema as the external data-contract format.

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

`plan.schema.json` is the root execution contract consumed by `PlanRunner`. A run starts from the plan path; device config, artifact store config, parameter data, case files, element catalogs, and fragment catalogs must be referenced by the plan directly or indirectly. `deviceConfig` is required on the plan. `artifactStore` is optional and points to an artifact config file when a run should publish report/resource artifacts.

`plan.schema.json` supports both legacy inline cases and the preferred `caseRefs` composition model. New plans should keep cases in standalone YAML files and let stages reference them through `caseRefs`.

Plan defaults currently include:

- `implicitWaitMs`: default implicit wait budget.
- `actionWait`: default explicit wait for actions. It is applied after case/fragment references are assembled and only fills setup, action, and teardown actions that do not already declare `wait`.
- `failureStrategy`: named failure strategy selection.
- `retryStrategy`: named retry strategy selection.

Plan diagnostics and local artifact handling currently include:

- `trace.screenshots.enabled`: enables before-action trace screenshots.
- `trace.screenshots.beforeAction`: currently `never` or `onFailure`; `onFailure` retains before-action screenshots in memory and publishes them only if an action fails.
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

Action DSL now uses a single keyword field whose value is the action id, for example `tap: open-mine-tab`. `case.schema.json`, `plan.schema.json`, and `fragment-catalog.schema.json` explicitly enumerate the currently supported action keywords and aliases: `tap`, `input`, `restartApp`, `getText`, `wait`, `assertElementAttrEquals`, `assertElementAttrRegexMatch`, `assertSourceRegexMatch`, and `screenshot`. Unsupported action types fail schema/policy validation before execution.

Element attribute assertions use an explicit `attr` field instead of encoding the attribute in the keyword. `attr` may contain slash-separated fallback candidates such as `name/label/text`. Regex assertions use contains-style matching by default through the regex engine; cases that need full-string matching should use anchors such as `^...$`. Assertion actions poll until matched or timed out when they have a `wait` value; plan-level `defaults.actionWait` supplies that wait unless the action overrides it.

Action-specific fields are declared directly on the action object instead of through open `args`. For `tap`, an action should normally use `element: alias.name`; when the UI surface has no stable element identity, such as an app modal backdrop, `tap` can use viewport-relative coordinates through top-level `xRatio` and `yRatio`. Coordinate taps are action parameters, not locator definitions. The parser normalizes this external DSL into the compact internal `ActionDefinition` model consumed by executors.

Plan and stage schemas expose the same lifecycle pattern through `setupFragments` / `setupActions` and `teardownFragments` / `teardownActions`. Teardown results are recorded separately from main action results.

`element-catalog.schema.json` is the only v1 DSL input schema that stores locator definitions. Parameter syntax remains `${...}`; element syntax is a distinct `element: alias.name` field on actions. An element can define common `strategy` / `value`, or platform-specific `android` and `ios` locators.

`case.schema.json`, `plan.schema.json`, and `fragment-catalog.schema.json` do not allow inline `locator` on actions. Actions reference elements through `element`; `PlanReferenceResolver` resolves that reference into the internal runtime `ActionDefinition.locator` before execution. This keeps locator ownership in element catalogs while preserving compact runtime executor inputs.

Runtime variables are not parameter data. Actions can write/read `@{plan.name}` and `@{case.name}` values during execution.

`report-data.schema.json` defines the current report data contract written by `LocalReportWriter` as `execution-result.json`. It is a report-consumption view, not a direct serialization of internal `PlanRunResult` or `PlanExecutionResult`. The report data JSON includes `schemaVersion: "1.0"` and lifecycle buckets:

- plan: `setupActions`, `teardownActions`
- stage: `setupActions`, `teardownActions`
- case: `setupActions`, `actions`, `teardownActions`
- top level: `traceArtifacts`, containing failed-action diagnostic screenshot links when trace screenshots were published.

`device-config.schema.json` currently covers:

- device identity: UDID plus optional platform and display name
- optional device OS version; for iOS this can be resolved through `soluna-ext` when omitted
- UDID-only device configs; missing platform can be resolved through `soluna-ext`
- Appium server mode: managed or external
- optional Appium server location for external servers
- executable, plugins, extra args, startup timeout, and optional environment overrides
- device/session-level Appium capabilities
- iOS WDA lifecycle config under `ios.wda`, including managed/external mode, local host port, device WDA port, go-ios executable, optional WDA identity overrides, tunnel mode, tunnel info/userspace tunnel ports, startup timeout, tunnel startup delay, and runwda startup delay

Device config must not contain target app identity or app reset intent. App lifecycle belongs to plan/stage/case setup fragments or explicit actions, not session creation.

For iOS, WDA is treated as a device-adjacent capability. `DeviceConfigResolver` fills missing iOS `device.osVersion` through `soluna-ext`; WDA management consumes that resolved version to choose the iOS 17+ tunnel path or the legacy path. The WDA runner bundle is resolved through `soluna-ext` by default and can be overridden with `ios.wda.bundleId`, `ios.wda.testRunnerBundleId`, and `ios.wda.xctestConfig` when needed. iOS 17+ managed WDA defaults to go-ios userspace tunnel mode and uses runtime-allocated tunnel info/userspace ports unless the device config pins them. Case and plan DSL files should not branch on iOS versions.

For managed Appium servers, `host` and `port` are optional. When `port` is omitted, the manager chooses an available local port at runtime. The Appium child process inherits the current runner process environment by default; `environment` only adds or overrides variables for exceptional cases.

Android session construction adds these defaults when the per-device config does not override them:

- `appium:unicodeKeyboard=true`
- `appium:resetKeyboard=true`

This keeps Android text input stable across real devices with different system keyboard implementations.

`artifact-store.schema.json` currently covers:

- MinIO endpoint, bucket, prefix, and optional public base URL.
- direct MinIO credentials through `credentials.accessKey` / `credentials.secretKey`, with `accessKeyEnv` / `secretKeyEnv` still supported as optional indirection.
- async upload worker count, queue capacity, bounded drain timeout, compression policy, and retry policy.
- `upload.compression` defaults to gzip-compressing text-like artifacts such as HTML, JSON, XML, JavaScript, and structured `+json` / `+xml` content types.
- optional upload-failure notification config reference, normally another YAML file under `examples/artifacts/`.
- optional plan lifecycle notification references: `planStarted`, `testFinished`, and `reportPublished`. The legacy `planFinished` field is still accepted as a compatibility alias for `reportPublished`.

`notification-sender.schema.json` currently covers DingTalk robot config:

- direct DingTalk robot `webhook` and optional signing `secret`, with `webhookEnv` / `secretEnv` still supported as optional indirection.
- optional DingTalk at-list settings.
- upload-failure alert window, threshold, and suppression interval.

`plan-resource-manifest.schema.json` stores plan-level metadata and explicit screenshot resources. It is written as `plan-resource-manifest.json` beside report files and is not a step execution-detail file.

Example files:

- `examples/plans/daily-smoke.yaml`
- `examples/plans/ugreen-profile-nickname.yaml`
- `examples/cases/ugreen-profile-nickname.yaml`
- `examples/data/default.yaml`
- `examples/data/ugreen-profile.yaml`
- `examples/elements/daily-smoke.yaml`
- `examples/elements/ugreen-profile.yaml`
- `examples/fragments/app-lifecycle.yaml`
- `examples/devices/00008150-001E15AA1140401C.yaml`
- `examples/devices/AMRF026323000807.yaml`
- `examples/artifacts/minio.yaml`
- `examples/artifacts/dingtalk-upload-alert.yaml`

Runtime model classes live under:

```text
src/main/kotlin/com/ugreen/iot/soluna/autotest/core/model/
src/main/kotlin/com/ugreen/iot/soluna/autotest/config/
src/main/kotlin/com/ugreen/iot/soluna/autotest/artifact/
src/main/kotlin/com/ugreen/iot/soluna/autotest/notification/
```

The DSL parser validates YAML in this order:

1. Parse YAML into a JSON tree.
2. Validate the tree against `plan.schema.json`.
3. Apply framework policy validation, including linear case DSL and locator text rules.
4. Map the validated tree into Kotlin model classes.

Schema files are versioned by directory. Breaking contract changes should create a new version directory instead of silently changing v1 semantics.
