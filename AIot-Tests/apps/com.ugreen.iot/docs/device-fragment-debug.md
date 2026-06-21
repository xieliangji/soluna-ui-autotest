# Device Fragment Debug Record

This note records the intended usage and iOS debug result for `AIot-Tests/apps/com.ugreen.iot/fragments/device.yaml`.

The fragment is a public app-level helper, not a model-specific resource. It only opens a target device from the device list. It does not validate the destination page after tapping the device item.

## Fragment

Fragment: `device.openTargetDevice`

Parameters:

- `device.targetMacSuffix`: target MAC suffix, provided as `XX:XX`.
- `device.targetName`: fallback device name used when the MAC label is not present or not matched.
- `mine.device.connectedPattern`: connected-state text pattern supplied by common data.

The first target-device fixture is `data/device/ugreen-hitune-s6-pro.yaml`, with `device.targetName = UGREEN HiTune S6 Pro` and `device.targetMacSuffix = CD:45`. This fixture identifies the device used by setup fragments; it does not make common device-action cases model-specific.

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
2. If the MAC probe matches, wait until the same cell also contains `${mine.device.connectedPattern}`, then tap that connected cell.
3. If the MAC probe does not match within the short branch wait, wait until the first visible cell whose static text attribute contains `${device.targetName}` also contains `${mine.device.connectedPattern}`, then tap that connected cell.

The fallback is only for cases where the MAC label is missing or the supplied suffix intentionally does not match. It should still use a device name supplied through parameters, not hardcoded locator text. If a MAC-matched device exists but is not connected, the fragment fails instead of falling back by name.

## iOS Locator Notes

- Use fresh source before judging the locator. Do not reuse XML captured before a restart, popup, or page transition.
- XCUITest did not match the broader `contains(., 'CD:45')` form reliably for the device cell. The working locator checks static text attributes explicitly with `string(@name)`, `string(@label)`, and `string(@value)`.
- The current debug device exposed `mac: 3D:88:D4:3C:CD:45` in the visible cell for `UGREEN HiTune S6 Pro`.
- The fragment stops at tapping the connected device item. Do not add return-navigation or detail-page assertions to this helper.

## Debug Evidence

Validated on iOS with step-by-step `soluna debug ... shell` operations:

- `device.targetMacSuffix = CD:45` matched the visible MAC label, confirmed the target device item was connected, and tapped the device item.
- `device.targetMacSuffix = ZZ:99` did not match the MAC locator.
- With the missing MAC suffix, `device.targetName = UGREEN HiTune S6 Pro` matched the first visible connected device-name cell and tapped the device item.

Focused schema/parser verification after the asset change:

```bash
./gradlew test --tests com.soluna.ui.autotest.schema.JsonSchemaDslValidatorTest --tests com.soluna.ui.autotest.dsl.YamlPlanParserTest
git diff --check
```
