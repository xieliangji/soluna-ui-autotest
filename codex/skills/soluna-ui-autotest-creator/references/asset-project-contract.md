# Asset Project Contract

Use this reference before creating or editing plans, cases, data files, element catalogs, fragments, or docs in an asset project.

## Layout

Recommended project layout:

```text
<asset-root>/
  soluna-project.yaml
  apps/<app-id>/
    plans/
      common/
      device/
        <model-slug>/
      debug/
    cases/
      common/
      device/
        common/
        <model-slug>/
    data/
      common/
      device/
    elements/
      common.yaml
      device/
        <model-slug>.yaml
    fragments/
    docs/
  devices/
    android/
    ios/
  artifacts/
```

The runner starts from a plan path. Device config, artifact config, parameters, cases, elements, and fragments must be reached through the plan reference chain.

## YAML Ownership

- Plans own execution orchestration: app identity, platform, device config, parameters, fragment refs, stages, case refs, diagnostics, artifact config, defaults.
- Every plan must declare `productModel`. Use the app display name for public app-function plans, and use the concrete product model for model-specific plans. If the app display name is unknown, inspect the connected device through Soluna/Appium extension-backed debug evidence before finalizing the plan.
- Cases own linear user intent: actions, setup references, teardown references, data refs, element refs.
- Data files own inputs, expected values, environment values, resource dictionaries, and template paths.
- Element catalogs own locators.
- Fragments own reusable setup/teardown and state convergence, including `if` / `then` / `else` control flow.
- Docs own case authoring notes, operation paths, preconditions, debug caveats, and data limitations.

## Case Rules

- Keep case actions linear. Do not put `if`, loops, or branch jumps in a case.
- Before writing action payloads, read `keyword-usage.md` and follow the field-level recipes there.
- Prefer nested keyword action form:

```yaml
- tap:
    id: open-mine-tab
    element: common.mineTab
    desc: Open Mine tab
```

- Use teardown for any action that changes durable state such as nickname, language, region, login state, or app data.
- Put destructive or data-reset cases in focused plans unless the plan explicitly isolates them as the final stage.
- Put cases that depend on special account/device data in focused plans until stable preconditions exist.
- Put app-wide public cases in `cases/common/`.
- Put device-related cases that apply across models in `cases/device/common/`.
- Put model-specific device cases in `cases/device/<model-slug>/`, where the slug is stable ASCII such as `ugreen-hitune-s6-pro`.
- Restart case numbering within a new case module or directory. Do not carry app-common numbers into `cases/device/common/` or a model-specific directory.

## Locator Rules

- Use `element: alias.name` in actions.
- Define locators only in element catalogs.
- Put public app and cross-model device-list locators in shared catalogs such as `elements/common.yaml`.
- Put model-specific device page or capability locators in `elements/device/<model-slug>.yaml`; cases under `cases/device/<model-slug>/` should reference that catalog when they need model-specific UI.
- Prefer Android resource ids and stable accessibility identifiers.
- On iOS and WebView pages, inspect fresh XML before changing XPath/class-chain locators.
- Do not use hardcoded business copy in locator values. Put copy in parameter data when copy-based matching is unavoidable.
- Use visual templates for non-text visual affordances that are not exposed as stable accessible elements.

## Plan Rules

- Full plans can use `continue-case` when later cases should still execute after a failure.
- Focused debug plans usually use `stop-case` and keep local artifacts.
- Put formal app-wide and cross-model plans in `plans/common/`.
- Put formal model-specific device plans in `plans/device/<model-slug>/`.
- Put temporary, focused, or exploratory debug plans in `plans/debug/`; do not leave `*-debug.yaml` plans in `plans/common/` or model formal plan directories.
- Stage setup should converge the start state once for the stage. Use it for expensive or stateful initialization such as logging in, selecting a device page, or reaching a shared module entry.
- Case setup runs before every case. Use it only for per-case normalization such as `restartApp`, lightweight cleanup, or returning the app to foreground.
- Do not copy a stage initialization fragment into `caseSetupFragments` when narrowing a full plan to a focused debug plan. Keep the stage's original `setupFragments` and preserve the original `caseSetupFragments` unless the complete plan proves a different per-case reset is intended.
- If every focused case should start from a fresh foreground app, prefer a restart fragment such as `appState.restartApp` in `caseSetupFragments`; do not replace it with a page-convergence fragment such as `appState.loggedInDevicePage`.
- Before running any temporary plan, write down the intended setup layout in the working notes or user update: `setupFragments` for once-per-stage convergence, `caseSetupFragments` for per-case reset. Re-read the YAML if those roles are not obvious.
- Plan/stage/case setup and teardown actions use the same keyword payload rules as case actions.
- Do not pass device/data/element paths as CLI arguments. Put them in YAML references.
- For cases that start inside a selected device, compose setup fragments at the stage level: first converge to the device list, then open the target device. For example `appState.loggedInDevicePage` or `appState.guestDevicePage`, followed by `device.openTargetDevice`. Keep the target device name/MAC in `data/device/<model-slug>.yaml` or another referenced data file.
- A target-device opening fragment should wait for the target device item to be connected before tapping it. Do not tap a merely present device item when the scenario needs an online device.

Example focused-plan setup layout:

```yaml
# plans/debug/ios-mine-debug.yaml

stages:
  - id: logged-in-device-page
    setupFragments:
      - appState.loggedInDevicePage
    caseSetupFragments:
      - appState.restartApp
    caseRefs:
      - file: cases/common/TC001_MINE_ABOUT.yaml
      - file: cases/common/TC002_MINE_LANGUAGE_SETTINGS.yaml
```

Example device-detail setup layout:

```yaml
# plans/device/ugreen-hitune-s6-pro/ios.yaml

fragmentRefs:
  - id: appState
    file: fragments/app-state.yaml
  - id: device
    file: fragments/device.yaml

parameters:
  - id: targetDevice
    file: data/device/ugreen-hitune-s6-pro.yaml

stages:
  - id: target-device-detail
    setupFragments:
      - appState.loggedInDevicePage
      - device.openTargetDevice
    caseRefs:
      - file: cases/device/ugreen-hitune-s6-pro/TC001_MODEL_FEATURE.yaml
```
