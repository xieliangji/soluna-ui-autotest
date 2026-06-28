# UGREEN HiTune T8 设备用例

本目录存放 `UGREEN HiTune T8` 型号专属用例。跨型号设备列表、改名、删除取消、断连等公共能力优先放在 `../common/`；只有依赖 T8 型号页面、能力、日志、数据或平台差异的场景才放在这里。

## 相关目录

- 用例：`AIot-Tests/apps/com.ugreen.iot/cases/device/ugreen-hitune-t8/`
- 正式计划：`AIot-Tests/apps/com.ugreen.iot/plans/device/ugreen-hitune-t8/ios.yaml`、`android.yaml`
- 调试计划：`AIot-Tests/apps/com.ugreen.iot/plans/debug/android-t8-*.yaml`
- 型号元素：`AIot-Tests/apps/com.ugreen.iot/elements/device/ugreen-hitune-t8.yaml`
- 通用设备打开 fragment：`AIot-Tests/apps/com.ugreen.iot/fragments/device.yaml`
- T8 状态归一 fragment：`AIot-Tests/apps/com.ugreen.iot/fragments/device/ugreen-hitune-t8.yaml`
- 共享数据：`AIot-Tests/apps/com.ugreen.iot/data/device/ugreen-hitune-t8.yaml`
- Android 覆盖数据：`AIot-Tests/apps/com.ugreen.iot/data/device/ugreen-hitune-t8.android.yaml`

## 计划入口

- iOS 正式计划跑 `TC002` 到 `TC017`。当前不包含 `TC001_NOISE_CONTROL.yaml`，噪声控制还需要把剩余位置点击收敛成稳定元素/区域点击后再纳入。
- Android 正式计划先跑 Android 独有 `TC001_HIGH_QUALITY_CODEC_ANDROID.yaml`，再跑 `TC002` 到 `TC017`，同样跳过 `TC001_NOISE_CONTROL.yaml`。
- 正式 T8 计划的 stage setup 使用 `appState.loggedInDevicePage`，每条用例前使用 `appState.restartApp`。
- 需要进入设备详情的用例统一使用 common `device.openTargetDevice`。该 fragment 按 `${device.targetMacSuffix}` 优先定位目标设备，未匹配时按 `${device.targetName}` 兜底，并通过设备卡片绿色占比确认连接态。
- T8 专属 fragment 只保留状态归一能力，例如 `t8Device.ensureGameModeOff` 和 `t8Device.ensureHighQualityCodecOff`，不要再新增重复的登录态或打开设备 fragment。

## 用例清单

| 用例 | 场景 | 计划状态 |
| --- | --- | --- |
| `TC001_NOISE_CONTROL.yaml` | 噪声控制蓝牙交互 | 暂不纳入正式计划 |
| `TC001_HIGH_QUALITY_CODEC_ANDROID.yaml` | 高音质解码开关与互斥限制 | Android 独有，Android 计划第一条 |
| `TC002_FIND_ROUTE.yaml` | 查找耳机路线 | iOS / Android |
| `TC003_MUSIC_MODE.yaml` | 音乐模式低音增强 | iOS / Android |
| `TC004_SPATIAL_AUDIO.yaml` | 空间音效 | iOS / Android |
| `TC005_GAME_MODE.yaml` | 游戏模式 | iOS / Android |
| `TC006_EQUALIZER_PRESET.yaml` | 均衡器预设 | iOS / Android |
| `TC007_DUAL_CONNECT.yaml` | 设备双连 | iOS / Android |
| `TC008_CUSTOM_EQUALIZER.yaml` | 自定义均衡器 | iOS / Android |
| `TC009_MORE_RENAME.yaml` | 更多页设备名称 | iOS / Android |
| `TC010_MORE_PROMPT_SOUND.yaml` | 提示音语言与音量 | iOS / Android |
| `TC011_MORE_USER_MANUAL.yaml` | 使用说明书 OCR | iOS / Android |
| `TC012_MORE_DELETE_CANCEL.yaml` | 删除设备取消 | iOS / Android |
| `TC013_MORE_DISCONNECT_RECONNECT.yaml` | 更多页断开并恢复连接 | iOS |
| `TC013_MORE_DISCONNECT_RECONNECT_ANDROID.yaml` | 更多页断开并恢复连接 | Android |
| `TC014_MORE_CUSTOM_CONTROL.yaml` | 自定义控制双击 | iOS / Android |
| `TC015_MORE_CUSTOM_CONTROL_SINGLE_TAP.yaml` | 自定义控制单击 | iOS / Android |
| `TC016_MORE_CUSTOM_CONTROL_TRIPLE_TAP.yaml` | 自定义控制三击 | iOS / Android |
| `TC017_MORE_CUSTOM_CONTROL_LONG_PRESS.yaml` | 自定义控制长按 | iOS / Android |

## 数据和平台差异

- T8 共享数据放在 `data/device/ugreen-hitune-t8.yaml`，包括型号名、iOS 设备 MAC 后缀、更多页名称数据、说明书校验数据、视觉模板路径、iOS BLE 写入日志关键字和高音质页面文本。
- Android 真机覆盖数据放在 `data/device/ugreen-hitune-t8.android.yaml`。当前 Android 设备 MAC 后四位是 `91:D6`，不能写回共享 T8 数据，避免影响 iOS。
- iOS BLE 断言只确认 App 触发 CoreBluetooth characteristic write。Android BLE/SPP 断言可以检查 App 日志里的下发、上报 payload，以及高音质相关 A2DP codec 行。
- 语言相关 UI 文案不要写死在 locator 中。可变文案放参数数据；语言无关结构、型号、版本号、MAC 后缀等按现有 locator policy 标注原因。

## 关键维护规则

- 打开设备详情统一使用 `device.openTargetDevice`；不要在 T8 fragment 中再维护“首卡打开设备”的重复逻辑。
- 进入详情后可能出现固件升级提示。默认打开设备 fragment 会处理一次；断连恢复、高音质恢复等路径如果可能再次弹出，需要在该路径内单独加 5 秒可选忽略按钮。
- 视觉状态优先用元素截图断言。例如开关状态用 switch 元素截图检查绿色、灰色或黑色占比，避免直接依赖不稳定属性。
- 日志断言只包围真正触发蓝牙交互的动作，不要把通用导航、弹窗处理、页面准备放进日志采集窗口。
- 调试用例时遵循 debug shell 规则，优先用 `source`、`screenshot`、`tap`、`tap-element`、`longPress`、`longPress-element`、`swipe` 等命令确认页面结构和点击目标。

## 高音质解码

`TC001_HIGH_QUALITY_CODEC_ANDROID.yaml` 是 Android 独有用例，iOS 当前没有该功能。它必须放在 Android T8 计划第一条，并且用例结束后必须保证高音质解码为关闭状态。

Android 切换高音质解码时会断开设备、自动回到详情页，再恢复连接。用例中不要在切换后点击返回按钮，应等待详情页恢复后再重新进入高音质页。

校验点包括：

- 初始关闭态、开启态、最终关闭态的 switch 属性和颜色。
- 关闭态页面显示 `AAC`，开启态页面显示 `LDAC`。
- 开启日志包含 BLE 下发、上报 payload、`highQuality: 1`、`gameMode: 0` 和 LDAC codec 证据。
- 关闭日志包含 BLE 下发、上报 payload、`highQuality: 0` 和 AAC codec 证据。
- 开启高音质后游戏模式必须为关闭。
- 高音质开启时尝试开启空间音效、游戏模式都必须弹出互斥对话框阻止。
- 不要把 Android 日志断言绑定到单一 `codeType1` 数值；实际运行中数字可能变化，但系统 A2DP codec 仍能证明 LDAC/AAC。

## 自定义均衡器

`TC008_CUSTOM_EQUALIZER.yaml` 每次执行都必须新建一个自定义均衡器，并在用例结束前删除本次新增项。不要假定开始前已经存在自定义均衡器。

Android 上点击已有自定义均衡器的整行只会选中该均衡器，只有点击名称文本才会进入编辑页。因此：

- 首次进入编辑页使用 `t8.equalizerCustomOpenTarget`，只匹配“新增自定义”卡片。
- 保存后清理时使用 `t8.equalizerCustomNameText`，点击本次新增的自定义名称文本重新进入编辑页。
- 删除图标没有稳定原生元素，保留名称行截图 ROI + `tapVisualTemplate` 的方式点击删除图标。
- 删除模板阈值和 scale 已按 Android 小图标调低，不要为了单个平台再分裂一套删除流程。

## 更多页和自定义控制

- T8 更多页从详情页右上角设置入口进入。
- 断连恢复在 iOS 和 Android 上路径不同：iOS 确认断开后停留在更多页，需要返回详情页再点击 `连接`；Android 确认断开后会自动回详情页，不能再点返回。
- 改名弹窗的输入框不是稳定原生 `TextField`。当前用例通过弹窗结构和输入区域相对位置处理清空、输入、确认和取消。
- 自定义控制拆成四条用例：双击、单击、三击、长按。每条用例覆盖左右耳，先归一到基线选项，再切到目标选项，最后恢复基线。
- 自定义控制只断言 UI 行状态和 BLE 写入日志，不覆盖真实耳机物理手势效果。

## 查找耳机路线

`TC002_FIND_ROUTE.yaml` 进入地图页后先断言原生地图元素，再保存截图，用绿色 ROI 占比确认耳机位置图标出现。道路名 OCR 暂不作为正式断言，等地图缩放和视口稳定后再考虑恢复。

## 已验证结果

- iOS 更多页批量：`t8-ios-tc010-tc017-redo-20260627-002` passed，覆盖 `TC010` 到 `TC017`，uploads `9/0/0`。
- Android 分批调试：
  - `android-t8-interaction-debug-20260628-002` passed，覆盖 `TC003`、`TC004`、`TC005`、`TC010`。
  - `android-t8-pages-debug-20260628-001` passed，覆盖 `TC002`、`TC006`、`TC007`。
  - `android-t8-disconnect-debug-20260628-002` passed，覆盖 Android 断连恢复。
  - `android-t8-custom-debug-20260628-010` passed，覆盖 `TC008`、`TC014` 到 `TC017`。
- Android 高音质 focused：`android-t8-high-quality-debug-20260628-003` passed，uploads `9/0/0`。
- common 打开设备替换后高音质 focused：`android-t8-high-quality-debug-common-open-20260628-001` passed，确认 common `device.openTargetDevice` 可按 Android MAC `91:D6` 打开 T8。
- Android 自定义均衡器 focused：`android-t8-custom-equalizer-debug-20260628-001` passed，确认“新增自定义 -> 保存 -> 点击名称文本重开 -> 删除新增项”路径。
- Android T8 全量：`android-t8-full-20260628-003` passed，覆盖 Android 高音质 + `TC002` 到 `TC017`，`TC001_NOISE_CONTROL` 跳过，uploads `44/0/0`。

## 当前暂缓

- `TC001_NOISE_CONTROL.yaml` 暂不纳入 Android 或 iOS 正式 T8 计划。
- 防风噪当前没有稳定可见入口/locator，暂未新增正式覆盖。
- 音频共享当前 T8 型号构建未暴露该功能，暂未新增正式覆盖。
