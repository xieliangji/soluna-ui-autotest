# ugreen-audio App 日志断言插件

`ugreen-audio` 是绿联声学设备用例使用的 Soluna App 日志断言插件，主要用于校验 UI 操作后 App 日志中是否出现蓝牙交互证据。

当前插件提供一个平台中立断言，并保留历史别名：

- `ble-write-triggered`
- `ios-ble-write-triggered`
- `android-ble-write-triggered`

三个断言名最终都映射到同一套实现。插件会根据运行上下文中的 `platform` 选择默认匹配规则：

- iOS 默认匹配 CoreBluetooth 写入标记：`CBMsgIdCharacteristicWriteValue`、`Writing value without response`、`com.ugreen.iot-central`。
- Android 默认匹配 App 实际蓝牙 payload 日志：`[蓝牙下发]`、`发送 ble 数据 data:`、`[蓝牙上报]`、`Payload 已解密`。

Android 默认断言只能证明日志窗口内出现过一次下发和上报 payload 交换，不能证明 payload 内容正确、设备实际收到指令，或耳机侧 ACK/上报语义正确。需要校验具体业务状态时，应在用例数据中补充 `androidContainsAll`、`androidContainsAny`、`androidRawRegex` 等 Android 专用参数。

## 构建

构建前需要把 `SOLUNA_HOME` 指向已经安装好的 Soluna 分发目录：

```bash
SOLUNA_HOME=/path/to/soluna gradle test jar
```

也可以通过 Gradle 属性传入：

```bash
gradle test jar -PsolunaHome=/path/to/soluna
```

## 安装

构建完成后，将插件 JAR 复制到运行时 App 日志插件目录：

```bash
mkdir -p ../../plugins/app-log
cp build/libs/ugreen-audio-app-log-plugin-*.jar ../../plugins/app-log/
```

安装后，YAML 用例可以在 `customAssertAppLog` 中引用该插件：

```yaml
- customAssertAppLog:
    id: assert-ble-write
    plugin: ugreen-audio
    assertion: ble-write-triggered
    args:
      containsAll:
        - CBMsgIdCharacteristicWriteValue
        - Writing value without response
        - com.ugreen.iot-central
      androidContainsAny:
        - "命令: PROMPT_LANG"
        - "命令: VOLUME"
```

如果省略 `args`，插件会使用当前平台默认规则。为了兼容早期 iOS 编写的共用用例，Android 运行时如果发现 `containsAll`/`containsAny` 等通用参数正好等于 iOS 默认写入标记，会忽略这些 iOS 默认标记，并回退到 Android payload 默认规则。

## 匹配参数

通用参数适用于当前平台，字段名如下：

- `contains`：单个字符串，日志窗口中必须包含。
- `containsAll`：字符串数组，所有值都必须出现在日志窗口中。
- `containsAny`：字符串数组，至少一个值必须出现在日志窗口中。
- `command`：`containsAll` 的简写，用于要求日志中出现某个命令文本。
- `status`：`containsAll` 的简写，用于要求日志中出现某个状态文本。
- `regex`：针对日志组合文本的正则匹配。
- `messageRegex`：只针对日志 `message` 字段组合文本的正则匹配。
- `rawRegex`：只针对日志 `raw` 字段组合文本的正则匹配。
- `caseSensitive`：是否区分大小写，默认 `false`。

Android 专用参数在同名字段前加 `android` 前缀，优先级高于通用参数：

- `androidContains`
- `androidContainsAll`
- `androidContainsAny`
- `androidCommand`
- `androidStatus`
- `androidRegex`
- `androidMessageRegex`
- `androidRawRegex`
- `androidCaseSensitive`

Android 专用参数存在时，Android 断言只使用 Android 专用参数生成匹配规则。没有 Android 专用参数时，插件才会考虑通用参数；如果通用参数不存在或只是 iOS 默认写入标记，则使用 Android payload 默认规则。

## 日志读取范围

插件读取 `captureAppLogStop` 保存的 JSONL 日志文件，并把以下内容纳入检索：

- `message`
- `raw`
- `tag`
- `process`
- `level`

`messageRegex` 只匹配 `message` 字段组合文本，`rawRegex` 只匹配 `raw` 字段组合文本，`regex` 和 `contains*` 匹配上述字段组合后的文本。

## T8 用例使用建议

- iOS 蓝牙交互用例可以继续使用 `iosBleWriteContainsAll` 或默认 iOS 写入标记。
- Android 常规蓝牙交互可以先使用默认 payload 交换标记。
- Android 高音质解码这类需要校验业务状态的用例，必须补充 `androidContainsAll`/`androidContainsAny`，例如 `highQuality`、`gameMode`、codec 和设备 MAC 证据。
- 日志采集窗口只包围真正触发蓝牙交互的动作，不要把页面导航、弹窗处理和状态恢复动作放进同一段采集窗口。
- 共用 case 中优先通过 data 文件配置平台化参数，避免在 YAML 动作里硬编码平台差异。
