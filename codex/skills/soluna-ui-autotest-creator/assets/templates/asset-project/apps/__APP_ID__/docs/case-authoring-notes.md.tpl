# {{APP_NAME}} Case Authoring Notes

This document belongs to the Soluna asset project for `{{APP_ID}}`.

Record app-specific case authoring rules, operation paths, test-data preconditions, locator discoveries, and debug conclusions here. Do not put account passwords, tokens, MinIO credentials, DingTalk secrets, or multimodal API keys in this file.

## General Rules

- Keep case actions linear.
- Put reusable state convergence and branching in fragments.
- Keep locators in `elements/`, test values in `data/`, and orchestration in `plans/`.
- Capture fresh source and screenshot before accepting a new locator.
- Update the relevant module docs whenever a case is debugged through and accepted into a plan.
