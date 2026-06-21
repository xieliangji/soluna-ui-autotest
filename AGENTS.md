# AGENTS.md

## Project

`soluna-ui-autotest` is a Kotlin/JVM project for iOS and Android real-device UI automation.

Use [docs/architecture.md](docs/architecture.md) as the source of truth for framework design. If implementation choices conflict with that document, update the document in the same change or call out the mismatch.

`soluna` is only the author's project prefix. Do not infer product or business domain from it.

## Core Direction

- Automation driver: Appium over the WebDriver protocol.
- Target devices: iOS and Android real devices.
- Test expression: YAML DSL templates.
- Parameter data: separate data files with parameterized references.
- Execution model: `Plan -> Stage -> Case -> Action`.
- Execution mode: one process binds one device and runs cases serially.
- App state: apps are installed by default; reset behavior is configurable.
- Reporting: self-owned report model and renderer. Do not introduce third-party report plugins.
- Artifacts: all execution artifacts must be uploaded to MinIO.

Do not introduce page-object-centered design unless explicitly requested. The DSL and execution engine are the primary abstraction.

## DSL Rules

- DSL keywords support Chinese and English aliases, but must map to one internal action model.
- Case DSL is linear and must not support logic control.
- Reusable init fragments may support logic control.
- Locator expressions must not hardcode fixed UI copy.
- Copy-based locators are allowed only through parameters, resource dictionaries, environment config, or data files.
- Parser and validator changes must preserve schema-first validation.

## Architecture Rules

- Keep the core execution context compact. Store only state required to execute the current plan.
- Prefer hook consumers and async workers for side effects such as uploads, report aggregation, data organization, and DingTalk notifications.
- Provide before/after hooks for plan, stage, case, and action.
- Default logging must cover action-before, case-before/after, stage-before/after, and plan-before/after.
- Failure strategies, retry strategies, wait strategies, assertions, artifact stores, report writers, notification senders, and Appium/WebDriver adapters must be replaceable behind interfaces.
- Add schema definitions for all externally consumed components and data files.

## Appium Extension Boundary

Host/device-adjacent capabilities belong in the custom Appium plugin whenever practical:

- Device discovery and metadata.
- Device logs.
- Controlled `adb`, `go-ios`, or `ios` command execution.
- Host dependency preflight checks.
- Device file/system-state helpers.

The Appium plugin source is copied into `lib/soluna-appium-ext` for integrated development. Its upstream project is the author's GitHub project `https://github.com/xieliangji/soluna-appium-ext`.

When changing plugin capabilities:

- Make framework-facing integration changes in this repository.
- Keep the plugin source suitable for submission back to its GitHub project.
- Record plugin changes separately in progress notes.
- After extension work is complete and verified, prepare the plugin changes to be committed and pushed to the upstream GitHub project.

The framework should consume plugin capabilities through a client abstraction instead of scattering host commands. Capability negotiation, versioning, and schemas are coordinated by this project, but plugin implementation changes must remain upstreamable.

## Artifact And Report Rules

- Trace screenshots follow the normal diagnostic artifact flow.
- Explicit screenshot actions in DSL must be collected into `plan-resource-manifest.json`.
- `plan-resource-manifest.json` contains plan-level metadata and explicit screenshot resources for other services/modules to consume.
- `plan-resource-manifest.json` must sit beside report files in MinIO and be linked by the report.
- Reports are single HTML files, but report data should be stored as JSON files so different renderers can consume the same raw data.
- Report HTML and all referenced resources must use MinIO links.
- MinIO upload must run in background workers and must not block normal test execution. Report-required resources may use bounded drain at plan completion.
- Upload failures need retry, monitoring, and DingTalk alerting for sustained batch failure patterns.

## Development Rules

- Prefer small, interface-first changes that match the existing Gradle/Kotlin layout.
- Keep public models and schemas versioned.
- Add or update tests for parser, schema validation, execution strategy, hook, report, and upload behavior when those areas change.
- Do not hide behavior in global singletons unless it is an explicit registry or plugin mechanism.
- Add and install required dependencies when they are needed to complete the task. Keep the dependency choice narrow, document why it is needed, and avoid broad framework additions without a clear benefit.
- Keep generated outputs, local build folders, logs, reports, and temporary artifacts out of git.

## Codex Skill Maintenance

The bundled Codex skill under `codex/skills/soluna-ui-autotest-creator` is part of this project's distribution contract. Keep it in sync with framework behavior during normal project development.

Update the skill in the same iteration when changing any of these areas:

- CLI entry points, validation/run/debug workflow, debug shell commands, or distribution layout.
- YAML DSL structure, schemas, keyword aliases, action fields, runtime variables, fragments, locators, parameter data, plan/stage/case lifecycle behavior, or artifact/report contracts.
- Appium/WebDriver behavior that affects how cases are authored or debugged.
- Capability-gap rules, keyword extension policy, DingTalk notification flow, scaffold templates, or starter asset project layout.

When updating the skill:

- Keep `SKILL.md` concise and move field-level details into `references/`.
- Update `references/keyword-usage.md` whenever action keywords, aliases, arguments, waits, assertions, visual template behavior, OCR, runtime variables, or fragment control flow change.
- Update scaffold templates and `scripts/create_asset_project.py` when the recommended external asset project structure changes.
- Update `scripts/send_dingtalk_gap_notice.py` and `references/capability-gap-gate.md` when capability-gap notification or approval rules change.
- Run `python3 /Users/xieliangji/.codex/skills/.system/skill-creator/scripts/quick_validate.py codex/skills/soluna-ui-autotest-creator` after skill edits.
- Run `./gradlew installDist` when distribution contents change, and confirm the packaged skill exists under `build/install/soluna/codex/skills/soluna-ui-autotest-creator`.
- Record skill changes and verification in `docs/progress.md`.

## Iteration Completion Rules

At the end of every implementation iteration, update the project record before handing work back.

Required updates:

- Update [docs/progress.md](docs/progress.md) with what changed, current status, verification performed, and next recommended work.
- Update related design documents when behavior, boundaries, schemas, extension points, or lifecycle assumptions change.
- Update schema files and schema documentation together when externally consumed data contracts change.
- Update README or local usage docs when commands, setup, module layout, or developer entry points change.
- Record newly added dependencies and the reason for adding them.
- State explicitly when tests or builds were not run, and why.

Do not treat implementation as complete if the code changed but the relevant progress/design documentation is stale.

## Verification

Use these commands when relevant:

```bash
./gradlew test
./gradlew build
```

For the Appium plugin under `lib/soluna-appium-ext`, use these commands when relevant:

```bash
npm test
npm run build
npm run lint
```

For documentation-only changes, Gradle verification is optional; state that tests were not run.
