# 设备 App 日志用例

本模块记录型号设备相关 App 日志采集、日志断言插件和日志语义收敛情况。

日志语义不写进普通 case、data、element 或 fragment。需要解析 Bluetooth 命令、report 等 App 专属语义时，使用独立 JVM app-log assertion plugin，并通过 `customAssertAppLog` 调用。

## 插件

源码位置：`log-plugins/ugreen-audio/`

运行时 JAR 位置：`plugins/app-log/`

当前插件：

- `plugin: ugreen-audio`
- `assertion: ios-ble-write-triggered`

当前 `ios-ble-write-triggered` 只用于 iOS BLE 探索阶段：它读取 `captureAppLogEnd` 写出的 JSONL，并确认交互动作后出现 CoreBluetooth 写入线索，例如 `CBMsgIdCharacteristicWriteValue`、`Writing value without response` 和 `com.ugreen.iot-central`。该断言只证明 App 发起了 BLE characteristic write，不证明 payload 正确、耳机收到、耳机执行成功或业务 ACK/report 正确。

Android 与 iOS 日志语义需要区分：Android 侧已知专项测试中走 SPP，可能存在下发和上报的全量日志；但 Android/T8 本轮尚未探索，不在当前插件中预置 Android 断言。

构建和安装：

```bash
./gradlew -p AIot-Tests/apps/com.ugreen.iot/log-plugins/ugreen-audio test jar -PsolunaHome=/Users/ugreen/IdeaProjects/soluna-ui-autotest/build/install/soluna
mkdir -p AIot-Tests/apps/com.ugreen.iot/plugins/app-log
cp AIot-Tests/apps/com.ugreen.iot/log-plugins/ugreen-audio/build/libs/ugreen-audio-app-log-plugin-*.jar AIot-Tests/apps/com.ugreen.iot/plugins/app-log/
```

`plugins/app-log/*.jar` 是本地构建产物，不提交到 git。

## T8 Fixture

设备 fixture：`data/device/ugreen-hitune-t8.yaml`

关键参数：

- `device.targetName = UGREEN HiTune T8`
- `device.targetMacSuffix = 91:DC`
- `device.controls.noiseControl.*` 和 `device.controls.musicMode.*` 记录 T8 详情首页可交互控件语义；交互调试可先用详情页固定布局坐标点击，若控件能稳定解析为元素，则视觉状态断言优先保存元素截图后再做颜色占比判断。
- `device.log.iosProcessRegex = ^(iot_audio|bluetoothd)`，用于保留 App 进程和蓝牙守护进程日志，排除 `audiomxd`、kernel audio 等系统音频噪声。
- `device.log.iosBleWriteContainsAll` 是 iOS BLE 写入触发断言的强特征列表，当前要求同时出现 `CBMsgIdCharacteristicWriteValue`、`Writing value without response` 和 `com.ugreen.iot-central`。

## 用例记录

### TC001 T8-噪声控制蓝牙交互-iOS

状态：已实现 focused debug 用例；日志采集边界已收敛到详情首页蓝牙交互动作，不把进入详情页、固件升级弹窗关闭等状态准备动作作为日志断言目标。

计划：`plans/debug/ios-hitune-t8-app-log-debug.yaml`

用例：`cases/device/ugreen-hitune-t8/TC001_NOISE_CONTROL.yaml`

前置条件：

- iOS 登录态账号可进入设备列表。
- 设备列表中存在 MAC 后四位为 `91:DC` 的 `UGREEN HiTune T8`。
- 目标设备处于已连接状态。
- `ugreen-audio` app-log plugin JAR 已构建并安装到 `plugins/app-log/`。

操作路径：

1. Stage setup 收敛到登录态设备列表页。
2. Case setup 只重启 App，保持轻量 per-case 归一化。
3. Case `setupFragments` 调用 `device.openTargetDevice`，校验目标设备已连接、点击进入详情页，并处理可能出现的固件升级提示弹窗。
4. 启动 App 日志采集窗口，按 iOS App/蓝牙进程名过滤。
5. 按详情页固定布局坐标点击 `环境音`，触发噪声控制蓝牙交互。
6. 按详情页固定布局坐标点击 `降噪关`，恢复噪声控制状态并再次触发蓝牙交互。
7. 停止日志采集并写出 `t8-noise-control-app-log` JSONL 资源。
8. 通过 `ugreen-audio/ios-ble-write-triggered` 确认噪声控制交互采集窗口里存在 iOS BLE characteristic write 触发线索。

验证点：

- `captureAppLogStart` / `captureAppLogEnd` 只围绕 T8 详情首页噪声控制切换动作生成 JSONL 资源。
- 独立 app-log plugin 能被 asset root 下的 `plugins/app-log/` 自动发现。
- 噪声控制采集窗口同时存在 `CBMsgIdCharacteristicWriteValue`、`Writing value without response` 和 `com.ugreen.iot-central`，表示 App 在交互后触发了 BLE characteristic write。
- `t8-app-log-debug-20260622-004` 在 iOS 真机通过，报告位于 `build/soluna-runs/t8-app-log-debug-20260622-004/report/index.html`，MinIO 上传完成：`uploaded=4, failed=0, abandoned=0`。
- `t8-app-log-debug-20260622-004` 是打开详情窗口级验证；后续以噪声控制蓝牙交互窗口作为有效日志采集边界。
- `t8-app-log-debug-20260622-005` 在 iOS 真机通过，报告位于 `build/soluna-runs/t8-app-log-debug-20260622-005/report/index.html`，MinIO 上传完成：`uploaded=4, failed=0, abandoned=0`。
- `005` 只采集 `环境音 -> 降噪关` 噪声控制切换窗口，JSONL 资源为 `t8-noise-control-app-log-001.jsonl`，共 377 条；进程只保留 `iot_audio(...)` 和 `bluetoothd(...)`，并出现 `bluetoothd` BLE write 日志和 `bluetoothd(CoreUtils)` BLE scanner 日志。

注意事项：

- 当前 iOS 断言不是 Bluetooth 命令 ACK/report 语义，只用于确认交互动作后 App 发起 BLE characteristic write。普通 iOS 系统日志不保证暴露完整 BLE payload、notification、read response 或业务 ACK。
- T8 进入设备详情页可能出现固件升级提示弹窗；默认处理已放到 `device.openTargetDevice` fragment 末尾。该状态准备不属于日志采集和断言目标。
- 日志采集和断言只针对明确可能触发蓝牙命令/上报的动作，例如噪声控制、音乐模式等详情首页交互；不要为了任意 UI 操作都加 App 日志采集。
- 当前噪声控制按钮在 iOS source 中只暴露中文可访问名；schema-first 策略不允许把这类业务文案写成 element locator，因此 focused debug case 使用详情页固定布局坐标点击。若后续 App 暴露稳定 resource-id 或非文案 accessibility id，再迁移到 element catalog。
- 如果首次真机运行没有匹配到日志，不应新增框架关键字；先检查 JSONL 内容，再调整 `device.log.iosProcessRegex` 或插件 matcher 参数。
