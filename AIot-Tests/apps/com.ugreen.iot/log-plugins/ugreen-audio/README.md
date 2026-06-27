# ugreen-audio-app-log-plugin

Soluna app-log assertion plugin for UGREEN audio-device iOS BLE exploration.

The current plugin exposes one assertion:

- `ios-ble-write-triggered`

This assertion verifies that an iOS App log capture contains CoreBluetooth write
markers after a UI interaction. It proves that the App triggered a BLE
characteristic write. It does not prove payload correctness, device receipt, or
headset ACK/report handling.

## Build

Point `SOLUNA_HOME` at an installed Soluna distribution root:

```bash
SOLUNA_HOME=/path/to/soluna gradle test jar
```

Or use a Gradle property:

```bash
gradle test jar -PsolunaHome=/path/to/soluna
```

## Install

Copy the built JAR to one of the runtime plugin directories:

```bash
mkdir -p ../../plugins/app-log
cp build/libs/ugreen-audio-app-log-plugin-*.jar ../../plugins/app-log/
```

`customAssertAppLog` can then reference:

```yaml
- customAssertAppLog:
    id: assert-ios-ble-write
    plugin: ugreen-audio
    assertion: ios-ble-write-triggered
    args:
      containsAll:
        - CBMsgIdCharacteristicWriteValue
        - Writing value without response
        - com.ugreen.iot-central
```

If `args` is omitted, the assertion uses the same default iOS markers above.

Supported matcher args:

- `contains`: one string that must appear in the captured log window.
- `containsAll`: string array; every value must appear in the captured log window.
- `containsAny`: string array; at least one value must appear in the captured log window.
- `command`: shorthand for a required captured text value.
- `status`: shorthand for a required captured text value.
- `regex`: regex matched against the combined captured log text.
- `messageRegex`: regex matched against the combined `message` fields.
- `rawRegex`: regex matched against the combined `raw` fields.
- `caseSensitive`: defaults to `false`.
