# {{APP_NAME}} Case Authoring Notes

This document belongs to the Soluna asset project for `{{APP_ID}}`.

Record app-specific case authoring rules, operation paths, test-data preconditions, locator discoveries, and debug conclusions here. Do not put account passwords, tokens, MinIO credentials, DingTalk secrets, or multimodal API keys in this file.

## General Rules

- Keep case actions linear.
- Put reusable state convergence and branching in fragments.
- Keep locators in `elements/`, test values in `data/`, and orchestration in `plans/`.
- Put formal app-wide and cross-model plans in `plans/common/`; put model-specific formal plans in `plans/device/<model-slug>/`; put focused debug plans in `plans/debug/`.
- Put app-wide public cases in `cases/common/`.
- Put cross-model device cases in `cases/device/common/`; put model-specific device cases in `cases/device/<model-slug>/`.
- Put public app and cross-model device-list locators in `elements/common.yaml`; put model-specific device locators in `elements/device/<model-slug>.yaml`.
- Restart case numbering within each case module or directory.
- Capture fresh source and screenshot before accepting a new locator.
- Update the relevant module docs whenever a case is debugged through and accepted into a plan.
