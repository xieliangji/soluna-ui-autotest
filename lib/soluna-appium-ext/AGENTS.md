# AGENTS.md

## Scope

This directory contains the Appium plugin source copied into the main `soluna-ui-autotest` repository for integrated development.

The upstream GitHub project is:

```text
https://github.com/xieliangji/soluna-appium-ext
```

Keep changes suitable for submission back to that upstream project after verification.

## Plugin Boundaries

This plugin owns host/device-adjacent Appium Server capabilities:

- Device discovery and metadata.
- Android and iOS device logs.
- Controlled `adb`, `go-ios`, or `ios` command execution.
- Host dependency preflight checks.
- Device file/system-state helpers added in future iterations.

The main framework should consume these capabilities through HTTP/client abstractions instead of executing host commands directly.

## Local Architecture

Preserve the existing layering:

- `lib/http`: HTTP request/response handling.
- `lib/services`: business orchestration and platform differences.
- `lib/cli`: external command execution and host preflight.
- `lib/types`: externally visible data contracts.

Keep route handlers thin. Put reusable behavior in services and keep dependencies injectable for unit tests.

## Development

- Use npm for this plugin.
- Do not commit `node_modules` or `build`.
- Keep package changes upstreamable.
- Update tests when route behavior, service behavior, command validation, or data contracts change.
- Update README or plugin docs when routes, request/response shapes, startup behavior, or required host tools change.
- Record plugin changes in the main repository `docs/progress.md`.

## Verification

Use these commands when relevant:

```bash
npm ci
npm test
npm run build
npm run lint
```
