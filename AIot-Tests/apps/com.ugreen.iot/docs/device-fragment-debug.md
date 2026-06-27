# Device Fragment Debug Record

This note records the intended usage and iOS debug result for `AIot-Tests/apps/com.ugreen.iot/fragments/device.yaml`.

The fragment is a public app-level helper, not a model-specific resource. It opens a target device from the device list, verifies connected state through the green Bluetooth indicator in the device-card screenshot, then performs the default post-open firmware-upgrade prompt dismissal through a language-insensitive prompt structure. It does not validate the destination page after tapping the device item.

## Fragment

Fragment: `device.openTargetDevice`

Parameters:

- `device.targetMacSuffix`: target MAC suffix, provided as `XX:XX`.
- `device.targetName`: fallback device name used when the MAC label is not present or not matched.

Target-device fixtures include:

- `data/device/ugreen-hitune-s6-pro.yaml`: `device.targetName = UGREEN HiTune S6 Pro`, `device.targetMacSuffix = CD:45`.
- `data/device/ugreen-hitune-t8.yaml`: `device.targetName = UGREEN HiTune T8`, `device.targetMacSuffix = 91:DC`.

These fixtures identify the device used by setup fragments; they do not make common device-action cases model-specific.

Expected starting state:

- App has already been restarted and converged to the device list.
- The converged state may be logged-in device list or guest device list.

The fragment should be composed after an app-state fragment in plan or stage setup:

```yaml
fragmentRefs:
  - id: appState
    file: ../fragments/app-state.yaml
  - id: device
    file: ../fragments/device.yaml

stages:
  - id: example-device-stage
    setupFragments:
      - appState.loggedInDevicePage
      - device.openTargetDevice
```

For guest-capable device scenarios, replace `appState.loggedInDevicePage` with `appState.guestDevicePage`.

## Selection Logic

1. Probe the visible iOS device-list table for the first visible cell whose static text attribute contains `${device.targetMacSuffix}`.
2. If the MAC probe matches, capture that card and assert a minimum green color ratio before tapping the card.
3. If the MAC probe does not match within the short branch wait, capture the first visible cell whose static text attribute contains `${device.targetName}` and assert a minimum green color ratio before tapping the card.
4. After entering the device detail page, try for up to 5 seconds to tap the firmware-upgrade prompt ignore button. Missing the button is allowed only because the prompt appears conditionally when newer firmware is available.

The fallback is only for cases where the MAC label is missing or the supplied suffix intentionally does not match. It should still use a device name supplied through parameters, not hardcoded locator text. If a MAC-matched device exists but is not connected, the fragment fails instead of falling back by name.

## iOS Locator Notes

- Use fresh source before judging the locator. Do not reuse XML captured before a restart, popup, or page transition.
- XCUITest did not match the broader `contains(., 'CD:45')` form reliably for the device cell. The working locator checks static text attributes explicitly with `string(@name)`, `string(@label)`, and `string(@value)`.
- The current debug device exposed `mac: 3D:88:D4:3C:CD:45` in the visible cell for `UGREEN HiTune S6 Pro`.
- The fragment stops after the default post-open firmware prompt dismissal. Do not add return-navigation or detail-page assertions to this helper.
- Disconnect/reconnect cases that can surface the same firmware prompt after returning to detail should add their own conditional ignore-button tap in that reconnect path instead of assuming the initial fragment already handled it.

## Debug Evidence

Validated on iOS with step-by-step `soluna debug ... shell` operations:

- `device.targetMacSuffix = CD:45` matched the visible MAC label, confirmed the target device item was connected, and tapped the device item.
- `device.targetMacSuffix = ZZ:99` did not match the MAC locator.
- With the missing MAC suffix, `device.targetName = UGREEN HiTune S6 Pro` matched the first visible connected device-name cell and tapped the device item.
- `t8-app-log-debug-20260622-004` validated the previous default post-open firmware prompt handling through `device.openTargetDevice`; that handling has since been replaced by a 5-second optional tap using a language-insensitive firmware prompt structure.
- `t8-find-route-debug-20260623-earbud-icon-002` validated the T8 find-route flow, then passed native map and focused earbuds-icon named-color assertions.

Focused schema/parser verification after the asset change:

```bash
./gradlew test --tests com.soluna.ui.autotest.schema.JsonSchemaDslValidatorTest --tests com.soluna.ui.autotest.dsl.YamlPlanParserTest --tests com.soluna.ui.autotest.runner.PlanReferenceResolverTest
git diff --check
```
