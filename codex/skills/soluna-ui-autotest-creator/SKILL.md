---
name: soluna-ui-autotest-creator
description: Use when Codex needs to create, validate, debug, or improve Soluna UI autotest asset projects, YAML DSL plans, cases, data files, element catalogs, fragments, and execution orchestration for iOS/Android real-device Appium automation. Guides Codex through Soluna distribution CLI validation/run/debug workflows, strict DSL architecture rules, asset-project documentation, starter project scaffolding, and capability-gap reporting when existing keywords cannot close a scenario.
---

# Soluna UI Autotest Creator

## Core Contract

Work on Soluna asset projects, not framework internals, unless the user explicitly asks for framework changes. Treat the Soluna distribution as the runtime contract: asset projects should be created, validated, debugged, and executed through the packaged `soluna` CLI.

For asset-project work, read the asset project's own `soluna-project.yaml`, plans, cases, elements, data, fragments, devices, artifact configs, and docs.

## Workflow

1. Identify the asset root, target app id, platform, device config, and starting plan.
2. If creating a new asset project, run `scripts/create_asset_project.py` and then validate the generated smoke plan.
3. Before editing an existing project, run `soluna validate <plan.yaml>` when available, then read the referenced YAML chain and relevant asset docs.
4. Before adding or changing actions, read `references/keyword-usage.md` and use the supported keyword recipes and fields.
5. Keep cases linear. Put reusable state convergence or branching in fragments, not in case actions.
6. Keep locators in element catalogs, test data in data files, and plan orchestration in plans.
7. Use `soluna debug <plan.yaml> shell` for real-device locator, screenshot, input, and template evidence. Capture fresh source and screenshot after every meaningful UI change.
8. Verify with `soluna validate <plan.yaml>` and the narrowest useful `soluna run <plan.yaml> --run-id ...`.
9. Update asset-project docs with passed operation paths, preconditions, known caveats, and whether the case belongs in a full plan or a focused debug plan.
10. Request framework keyword/capability extension only after trying the keyword usage recipes and passing the strict capability-gap gate.

## Resources

- For packaged CLI usage and distribution assumptions, read `references/distribution-workflow.md`.
- For YAML asset structure and authoring rules, read `references/asset-project-contract.md`.
- For action keyword fields, examples, runtime variables, ROI, visual templates, screen recording, assertions, and fragment `if`, read `references/keyword-usage.md`.
- For debug evidence collection, read `references/debug-and-evidence.md`.
- Before proposing new framework support, read `references/capability-gap-gate.md`.
- After the capability-gap gate passes and the user wants maintainers notified, use `scripts/send_dingtalk_gap_notice.py`; it defaults to the built-in Soluna debug DingTalk robot and should be run with `--dry-run` first.

## New Asset Project

Use the scaffold script for a deterministic starter project:

```bash
python3 codex/skills/soluna-ui-autotest-creator/scripts/create_asset_project.py \
  --output ./My-Tests \
  --project-id my-tests \
  --app-id com.example.app \
  --app-name ExampleApp \
  --platform android \
  --udid CHANGE_ME_UDID
```

The scaffold is intentionally minimal: it creates a project contract, one app root, one device template, artifact templates, a restart fragment, an empty element catalog, default data, and a smoke plan/case that restarts the app, waits, and captures a screenshot. Add business locators and state fragments only after real-device debugging.

## Hard Rules

- Do not hardcode account passwords, MinIO credentials, DingTalk tokens, or multimodal API keys into generated asset projects; the bundled local debug DingTalk robot in `scripts/send_dingtalk_gap_notice.py` is the only approved exception.
- Do not write fixed UI copy into locators unless it is parameterized or a documented schema escape hatch.
- Do not encode debug-only operations directly into business cases.
- Do not use viewport coordinates when a stable element or visual template can model the action.
- Do not add a new keyword request until existing actions, fragments, runtime variables, visual templates, OCR, and plan orchestration have been ruled out with evidence.
- Do not send a DingTalk capability-gap notice until the gate has passed and the user has approved sending or explicitly requested notification.
