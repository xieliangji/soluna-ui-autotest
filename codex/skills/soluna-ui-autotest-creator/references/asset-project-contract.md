# Asset Project Contract

Use this reference before creating or editing plans, cases, data files, element catalogs, fragments, or docs in an asset project.

## Layout

Recommended project layout:

```text
<asset-root>/
  soluna-project.yaml
  apps/<app-id>/
    plans/
    cases/
    data/
    elements/
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

## Locator Rules

- Use `element: alias.name` in actions.
- Define locators only in element catalogs.
- Prefer Android resource ids and stable accessibility identifiers.
- On iOS and WebView pages, inspect fresh XML before changing XPath/class-chain locators.
- Do not use hardcoded business copy in locator values. Put copy in parameter data when copy-based matching is unavoidable.
- Use visual templates for non-text visual affordances that are not exposed as stable accessible elements.

## Plan Rules

- Full plans can use `continue-case` when later cases should still execute after a failure.
- Focused debug plans usually use `stop-case` and keep local artifacts.
- Stage setup should converge the start state once. Case setup should be a lightweight restart or cleanup unless the plan requires full convergence before every case.
- Plan/stage/case setup and teardown actions use the same keyword payload rules as case actions.
- Do not pass device/data/element paths as CLI arguments. Put them in YAML references.
