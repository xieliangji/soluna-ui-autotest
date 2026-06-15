# Copilot Instructions for `soluna-appium-ext`

## Build, test, and lint commands

Use npm scripts at repository root:

- Install: `npm install`
- Build: `npm run build`
- Lint: `npm run lint`
- Full unit test suite: `npm run test:unit`
- Single test file: `npm run test:single test/unit/<file>.spec.ts`

## High-level architecture

The project is an Appium plugin implemented in TypeScript.

- `lib/plugin.ts` is the plugin entrypoint class (`SolunaExtPlugin`) extending `BasePlugin`.
- Startup safety checks are in `lib/cli/preflight.ts`.
  - Required host tools: `adb` and `go-ios` (or `ios` alias).
  - Missing commands must throw and log explicit errors to block Appium startup.
- Device lookup HTTP handler is in `lib/http/device-route.ts`.
  - Exposes `GET /soluna/device?udid=...` through `updateServer`.
- Platform-specific discovery is separated by service:
  - Android: `lib/services/android.ts` (`adb devices`, `adb shell getprop`)
  - iOS: `lib/services/ios.ts` (`ios|go-ios list --details`)
- Cross-platform normalization is centralized via `UnifiedDeviceInfo` in `lib/types/device.ts` and composed in `lib/services/device-service.ts`.

## Key conventions

- Keep all external command execution behind `CommandRunner` (`lib/cli/exec.ts`) and pass it through service/handler layers to keep testability high.
- The iOS command name must support both `go-ios` and `ios` alias; do not assume only one binary name.
- HTTP responses are wrapped under a top-level `value` object, consistent with Appium-style JSON response shape used in this repo.
- Android and iOS outputs must be returned as a unified abstraction (`platform`, `udid`, `name`, `model`, `osVersion`) rather than platform-specific raw payloads.
