# UGREEN HiTune T8 型号设备用例

本模块记录 `UGREEN HiTune T8` 型号设备用例的目录结构、计划入口、公共装配、平台差异、日志断言和逐条用例维护规则。通用设备列表长按操作记录在 [device-actions.md](device-actions.md)，App 日志插件能力记录在 [device-app-log.md](device-app-log.md)。

## 目录结构

- 用例目录：`cases/device/ugreen-hitune-t8/`
- 正式计划：`plans/device/ugreen-hitune-t8/ios.yaml`、`plans/device/ugreen-hitune-t8/android.yaml`
- Android 调试计划：`plans/debug/android-t8-*.yaml`
- 型号元素：`elements/device/ugreen-hitune-t8.yaml`
- 型号共享数据：`data/device/ugreen-hitune-t8.yaml`
- Android 覆盖数据：`data/device/ugreen-hitune-t8.android.yaml`
- 型号状态归一 fragment：`fragments/device/ugreen-hitune-t8.yaml`
- 通用设备打开 fragment：`fragments/device.yaml`
- 已登录设备页归一 fragment：`fragments/app-state.yaml`

## 模块规则

- T8 正式计划统一在 stage setup 使用 `appState.loggedInDevicePage`，每条 case 前使用 `appState.restartApp`。
- 进入 T8 详情页统一使用 `device.openTargetDevice`，不要在 T8 fragment 中重复维护打开首个设备卡片的逻辑。
- `device.openTargetDevice` 优先按 `device.targetMacSuffix` 找设备卡片，找不到时按 `device.targetName` 兜底；打开前会用设备卡片截图绿色占比确认已连接。
- Android T8 真机 MAC 后四位为 `91:D6`，只放在 `data/device/ugreen-hitune-t8.android.yaml`，不要写回共享数据。
- `fragments/device/ugreen-hitune-t8.yaml` 只放 T8 型号状态归一能力，目前包括 `t8Device.ensureGameModeOff` 和 `t8Device.ensureHighQualityCodecOff`。
- iOS 与 Android 共用功能优先共用同一条 case，通过 plan、data、element 和日志断言参数表达平台差异。
- Android 独有能力使用平台后缀，例如 `TC001_HIGH_QUALITY_CODEC_ANDROID.yaml`。
- `TC001_NOISE_CONTROL.yaml` 暂不加入 iOS 或 Android 正式 T8 计划。
- 固件升级提示是条件弹窗。`device.openTargetDevice` 只处理打开详情页时的弹窗；断连恢复、高音质恢复、详情页中途滚动后再次出现的弹窗，必须在当前路径内单独处理。
- 日志采集只包围真正触发蓝牙交互的动作，不包围通用导航、页面准备、弹窗处理和恢复动作。
- 调试必须沿用用例调试规则，优先使用 packaged debug shell 的 `source`、`screenshot`、`tap`、`tap-element`、`longPress`、`longPress-element`、`swipe` 等命令确认页面结构和交互路径。

## 正式计划

### Android T8

计划文件：`plans/device/ugreen-hitune-t8/android.yaml`

覆盖范围：

1. `TC001_HIGH_QUALITY_CODEC_ANDROID`
2. `TC002_FIND_ROUTE`
3. `TC003_MUSIC_MODE`
4. `TC004_SPATIAL_AUDIO`
5. `TC005_GAME_MODE`
6. `TC006_EQUALIZER_PRESET`
7. `TC007_DUAL_CONNECT`
8. `TC008_CUSTOM_EQUALIZER`
9. `TC009_MORE_RENAME`
10. `TC010_MORE_PROMPT_SOUND`
11. `TC011_MORE_USER_MANUAL`
12. `TC012_MORE_DELETE_CANCEL`
13. `TC013_MORE_DISCONNECT_RECONNECT_ANDROID`
14. `TC014_MORE_CUSTOM_CONTROL`
15. `TC015_MORE_CUSTOM_CONTROL_SINGLE_TAP`
16. `TC016_MORE_CUSTOM_CONTROL_TRIPLE_TAP`
17. `TC017_MORE_CUSTOM_CONTROL_LONG_PRESS`

规则：

- `TC001_HIGH_QUALITY_CODEC_ANDROID` 必须放第一条执行。
- `TC013` 在 Android 使用专用 case，因为确认断开后 App 会自动回详情页。
- Android 全量回归命令：

```bash
SOLUNA_VISUAL_OCR_MULTIMODAL_BASE_URL='http://47.128.186.61:8317/v1' SOLUNA_VISUAL_OCR_MULTIMODAL_API_KEY="$SOLUNA_VISUAL_OCR_MULTIMODAL_API_KEY" SOLUNA_VISUAL_OCR_MULTIMODAL_MODEL='gpt-5.5' SOLUNA_VISUAL_OCR_MULTIMODAL_REASONING_EFFORT='high' SOLUNA_OPTS='-Dorg.slf4j.simpleLogger.log.io.soluna.ui.autotest.appium.server=info -Dorg.slf4j.simpleLogger.log.io.soluna.ui.autotest.appium.wda=info' build/install/soluna/bin/soluna run AIot-Tests/apps/com.ugreen.iot/plans/device/ugreen-hitune-t8/android.yaml --run-id android-t8-full-$(date +%Y%m%d-%H%M%S)
```

### iOS T8

计划文件：`plans/device/ugreen-hitune-t8/ios.yaml`

覆盖范围：

1. `TC002_FIND_ROUTE`
2. `TC003_MUSIC_MODE`
3. `TC004_SPATIAL_AUDIO`
4. `TC005_GAME_MODE`
5. `TC006_EQUALIZER_PRESET`
6. `TC007_DUAL_CONNECT`
7. `TC008_CUSTOM_EQUALIZER`
8. `TC009_MORE_RENAME`
9. `TC010_MORE_PROMPT_SOUND`
10. `TC011_MORE_USER_MANUAL`
11. `TC012_MORE_DELETE_CANCEL`
12. `TC013_MORE_DISCONNECT_RECONNECT`
13. `TC014_MORE_CUSTOM_CONTROL`
14. `TC015_MORE_CUSTOM_CONTROL_SINGLE_TAP`
15. `TC016_MORE_CUSTOM_CONTROL_TRIPLE_TAP`
16. `TC017_MORE_CUSTOM_CONTROL_LONG_PRESS`

规则：

- iOS 不覆盖高音质解码，当前 App 未暴露该 Android 独有功能。
- iOS `TC013_MORE_DISCONNECT_RECONNECT` 确认断开后停留在更多页，需要显式返回详情页再恢复连接。

## 调试计划

- `plans/debug/android-t8-high-quality-debug.yaml`：高音质解码 focused 调试。
- `plans/debug/android-t8-pages-debug.yaml`：查找路线、均衡器预设、设备双连 focused 调试。
- `plans/debug/android-t8-interaction-debug.yaml`：音乐模式、空间音效、游戏模式、提示音 focused 调试。
- `plans/debug/android-t8-custom-equalizer-debug.yaml`：自定义均衡器 focused 调试。
- `plans/debug/android-t8-custom-debug.yaml`：自定义均衡器和自定义控制批量 focused 调试。
- `plans/debug/android-t8-more-debug.yaml`：更多页改名、说明书、删除取消、断连恢复 focused 调试。
- `plans/debug/android-t8-disconnect-debug.yaml`：Android 断连恢复 focused 调试。

调试建议：

- 先用 debug shell 的 `source` 看元素树，确认目标控件是否是稳定原生元素。
- 原生元素稳定时优先补 element locator；没有稳定元素时再用截图 ROI、颜色占比或视觉模板。
- Android BLE 相关用例需要先抓日志再写断言，不要套用 iOS 的日志特征。
- 自定义均衡器删除图标、说明书页面、地图图标这类入口没有稳定元素时，保留截图、ROI 或 OCR 证据。

## 日志断言规则

- iOS 侧日志断言主要确认 CoreBluetooth 写入证据，例如 BLE characteristic write 相关行。
- Android 侧日志断言需要兼容蓝牙下发和上报 payload，不能只找 iOS 风格的写入行。
- Android 高音质解码必须同时断言 BLE payload、`highQuality` 状态、关联模式状态和 codec 证据。
- 蓝牙交互用例的日志断言参数放在 T8 data 文件里，case 只表达“采集 -> 触发 -> 停止 -> 断言”的执行顺序。
- 日志插件扩展后，同一条共用 case 可以在 iOS/Android 下使用不同 pattern，减少维护量。

## 用例记录

### TC001 高音质解码开关与互斥限制 Android

状态：Android 独有，已实现，必须作为 Android 正式计划第一条。

前置条件：

- 设备已连接并进入 T8 详情页。
- 用例 setup 使用 `t8Device.ensureHighQualityCodecOff`，确保正式步骤开始前高音质解码关闭。

操作路径：

1. 从详情页打开高音质解码页。
2. 校验初始关闭态：开关 `value=0`，开关截图为灰色，页面显示 AAC。
3. 开始采集 Android App 日志。
4. 点击高音质解码开关打开。
5. 等待设备断开、App 自动回到详情页、设备恢复连接。
6. 停止日志采集，断言日志包含 BLE 下发/上报 payload、`highQuality=1`、`gameMode=0` 和 LDAC codec 证据。
7. 在详情页校验游戏模式为关闭。
8. 尝试打开空间音效，校验弹出互斥对话框并关闭弹窗。
9. 尝试打开游戏模式，校验弹出互斥对话框，关闭弹窗后游戏模式仍为关闭。
10. 重新进入高音质解码页，校验开启态：开关 `value=1`，开关截图为黑色，页面显示 LDAC。
11. 开始采集 Android App 日志。
12. 点击高音质解码开关关闭。
13. 等待设备断开、App 自动回详情页、设备恢复连接。
14. 停止日志采集，断言日志包含 BLE 下发/上报 payload、`highQuality=0` 和 AAC codec 证据。
15. 再次进入高音质解码页，校验最终关闭态。

验证点：

- 高音质解码开关可以从关切到开，再从开切回关。
- 每次切换都会触发断开并恢复连接，Android 不需要手动点返回。
- 开启高音质后游戏模式被置为关闭。
- 高音质开启时空间音效、游戏模式都被互斥弹窗阻止。
- 用例结束后高音质解码必须为关闭。

注意事项：

- 不要在切换高音质后点击返回按钮，Android 会自动回详情页。
- 不要断言单一 `codeType1` 数值；以 `highQuality`、`gameMode`、BLE payload 和 A2DP codec 行共同作为证据。

### TC001 噪声控制蓝牙交互

状态：暂缓执行。

说明：

- 当前 `TC001_NOISE_CONTROL.yaml` 保留在目录中，但未加入 iOS/Android T8 正式计划。
- 后续恢复前需要重新确认入口、状态文案、Android/iOS 日志断言差异和默认状态归一方式。

### TC002 查找耳机查找路线

状态：iOS/Android 正式用例，已实现。

操作路径：

1. 打开 T8 详情页。
2. 在详情页滚动露出查找耳机入口。
3. 处理可能出现的固件升级提示。
4. 进入查找耳机页。
5. 点击查找路线卡片进入地图页。
6. 等待地图瓦片和文字稳定。
7. 保存地图页截图。
8. 用绿色 ROI 占比确认耳机位置图标出现。

验证点：

- 查找耳机页可进入。
- 查找路线地图可加载。
- 地图上存在绿色耳机位置图标。

注意事项：

- 道路名 OCR 当前不作为正式断言，避免地图缩放、瓦片加载和视口差异导致误判。

### TC003 音乐模式低音增强蓝牙交互

状态：iOS/Android 正式用例，已实现。

操作路径：

1. 打开 T8 详情页。
2. 开始采集 App 日志。
3. 先切换到空间音效，制造后续低音增强点击的真实状态变化。
4. 切换到低音增强。
5. 等待日志输出。
6. 保存低音增强按钮截图。
7. 通过绿色占比确认低音增强处于选中态。
8. 停止日志采集并执行平台化日志断言。

验证点：

- 音乐模式可以切换到低音增强。
- UI 选中态变更可见。
- BLE 日志包含对应交互证据。

### TC004 空间音效蓝牙交互

状态：iOS/Android 正式用例，已实现。

操作路径：

1. 打开 T8 详情页。
2. 开始采集 App 日志。
3. 点击空间音效。
4. 等待日志输出。
5. 保存空间音效按钮截图。
6. 通过绿色占比确认空间音效处于选中态。
7. 停止日志采集并执行平台化日志断言。
8. 将音乐模式恢复为低音增强。

验证点：

- 空间音效可以被选中。
- UI 选中态变更可见。
- BLE 日志包含对应交互证据。

注意事项：

- Android 高音质解码开启时空间音效会被互斥阻止，所以 Android 正式计划必须先执行高音质用例并恢复为关闭。

### TC005 游戏模式蓝牙交互

状态：iOS/Android 正式用例，已实现。

前置条件：

- setup 使用 `t8Device.ensureGameModeOff`，确保用例开始前游戏模式关闭。

操作路径：

1. 打开 T8 详情页并归一游戏模式关闭。
2. 开始采集 App 日志。
3. 点击游戏模式开关打开。
4. 保存游戏模式开关截图，确认开关 `value=1`。
5. 等待日志输出。
6. 停止日志采集并执行平台化日志断言。
7. 点击游戏模式开关关闭，恢复默认状态。

验证点：

- 游戏模式可以从关闭切换为开启。
- UI 开关状态变化可见。
- BLE 日志包含对应交互证据。
- 用例结束后游戏模式恢复关闭。

### TC006 均衡器预设蓝牙交互

状态：iOS/Android 正式用例，已实现。

操作路径：

1. 打开 T8 详情页。
2. 打开均衡器页。
3. 开始采集 App 日志。
4. 点击目标均衡器预设。
5. 保存目标预设截图，用绿色占比确认选中态。
6. 停止日志采集并执行平台化日志断言。
7. 恢复默认预设，保证后续用例起始状态稳定。

验证点：

- 均衡器页可进入。
- 目标预设可选中。
- UI 选中态和 BLE 日志都能证明切换成功。

### TC007 设备双连蓝牙交互

状态：iOS/Android 正式用例，已实现。

操作路径：

1. 打开 T8 详情页。
2. 滚动露出设备双连入口。
3. 处理可能出现的固件升级提示。
4. 进入设备双连页。
5. 开始采集 App 日志。
6. 打开设备双连开关。
7. 保存开关截图，用绿色占比确认开启。
8. 等待日志输出。
9. 停止日志采集并执行平台化日志断言。
10. 关闭设备双连，恢复默认状态。

验证点：

- 设备双连页可进入。
- 设备双连开关可以开启。
- UI 开关状态和 BLE 日志都能证明交互成功。
- 用例结束后设备双连恢复关闭。

### TC008 自定义均衡器蓝牙交互

状态：iOS/Android 正式用例，已实现。

操作路径：

1. 打开 T8 详情页。
2. 滚动露出均衡器入口。
3. 打开均衡器页。
4. 点击新增自定义入口，进入自定义均衡器编辑页。
5. 校验自定义均衡器标题可见。
6. 点击重置，归一均衡器曲线。
7. 开始采集 App 日志。
8. 上调 62Hz 频段。
9. 保存自定义均衡器。
10. 等待保存和日志输出。
11. 停止日志采集并执行平台化日志断言。
12. 回到均衡器列表后，点击本次新增项的名称文本重新进入编辑页。
13. 保存名称行截图和 ROI。
14. 在名称行 ROI 中用删除图标模板点击删除。
15. 确认删除，恢复可重复执行状态。

验证点：

- 每次用例都会新增一个自定义均衡器。
- 62Hz 调整和保存会产生 BLE 日志。
- 保存后可以重新打开本次新增项并删除。
- 用例结束后不遗留本次新增的自定义均衡器。

注意事项：

- 不要假定开始前已有自定义均衡器。
- Android 点击已有自定义均衡器整行只是选中均衡器，只有点击名称文本才进入编辑页。
- `t8.equalizerCustomOpenTarget` 只用于首次新增入口。
- `t8.equalizerCustomNameText` 只用于保存后重新打开本次新增项做清理。
- 删除图标没有稳定原生元素，保留名称行截图 ROI 加 `tapVisualTemplate` 方案。

### TC009 更多页修改耳机名称

状态：iOS/Android 正式用例，已实现。

操作路径：

1. 打开 T8 更多页。
2. 先打开并关闭一次重命名弹窗，降低首次弹窗渲染抖动影响。
3. 再打开重命名弹窗。
4. 输入短名称并确认。
5. 校验更多页显示短名称。
6. 打开重命名弹窗。
7. 输入长名称并确认。
8. 校验长名称展示或截断行为符合页面表现。
9. 打开重命名弹窗。
10. 输入待取消名称，点击取消。
11. 校验取消后名称未被修改。
12. 最后恢复默认 T8 名称。

验证点：

- 耳机名称可以修改。
- 取消修改不会生效。
- 用例结束后名称恢复默认，避免影响设备列表匹配。

### TC010 更多页提示音语言与音量蓝牙交互

状态：iOS/Android 正式用例，已实现。

操作路径：

1. 打开 T8 更多页。
2. 打开提示音设置页。
3. 校验铃声选项可见。
4. 开始采集 App 日志。
5. 切换提示音为铃声。
6. 点击音量条高音量位置，将提示声音量设为 10。
7. 等待日志输出。
8. 停止日志采集并执行平台化日志断言。
9. 恢复提示音为中文。

验证点：

- 提示音设置页可进入。
- 提示音语言和提示声音量可以修改。
- BLE 日志包含对应交互证据。
- 用例结束后提示音语言恢复为中文。

### TC011 更多页使用说明书 OCR 校验

状态：iOS/Android 正式用例，已实现。

操作路径：

1. 打开 T8 更多页。
2. 打开使用说明书页面。
3. 等待页面或 PDF 内容渲染。
4. 保存页面截图。
5. OCR 校验截图包含产品说明书关键字。
6. OCR 校验截图包含 T8 产品型号信息。
7. 返回更多页。

验证点：

- 使用说明书入口可打开。
- 说明书内容完成渲染。
- 页面内容能识别到说明书和 T8 型号信息。

注意事项：

- 说明书依赖网络加载，调试失败时先区分网络加载失败和 OCR 误判。

### TC012 更多页删除设备取消

状态：iOS/Android 正式用例，已实现。

操作路径：

1. 打开 T8 更多页。
2. 点击删除设备入口，打开确认弹窗。
3. 校验取消按钮可见。
4. 点击取消关闭弹窗。
5. 校验仍停留在更多页。

验证点：

- 删除设备确认弹窗可打开。
- 点击取消不会删除设备。
- 当前用例不执行确认删除。

注意事项：

- 确认删除属于破坏性动作，目前只覆盖取消路径。

### TC013 更多页断开连接并恢复连接 iOS

状态：iOS 正式用例，已实现。

操作路径：

1. 打开 T8 更多页。
2. 点击断开连接入口。
3. 校验断开连接确认弹窗可见。
4. 确认断开。
5. iOS 停留在更多页，点击返回回到详情页。
6. 校验详情页显示未连接状态。
7. 点击详情页连接按钮恢复连接。
8. 校验恢复连接后仍在 T8 详情页。

验证点：

- iOS 可以从更多页断开 T8。
- 断开后详情页显示未连接。
- 点击连接后能恢复到已连接详情页。

### TC013 更多页断开连接并恢复连接 Android

状态：Android 正式用例，已实现，使用 `TC013_MORE_DISCONNECT_RECONNECT_ANDROID.yaml`。

操作路径：

1. 打开 T8 更多页。
2. 点击断开连接入口。
3. 校验断开连接确认弹窗可见。
4. 确认断开。
5. Android 自动回到详情页。
6. 校验详情页显示未连接状态。
7. 处理断开后可能出现的固件升级提示。
8. 点击详情页连接按钮恢复连接。
9. 校验恢复连接后仍在 T8 详情页。

验证点：

- Android 可以从更多页断开 T8。
- 断开确认后 App 自动回详情页。
- 点击连接后能恢复到已连接详情页。

注意事项：

- Android 路径不要点击返回按钮，否则会离开详情页。

### TC014 更多页自定义控制双击蓝牙交互

状态：iOS/Android 正式用例，已实现。

操作路径：

1. 打开 T8 更多页。
2. 打开自定义控制页。
3. 开始采集 App 日志。
4. 左耳双击先归一到基线选项，再切换到目标选项。
5. 切换到右耳。
6. 右耳双击先归一到基线选项，再切换到目标选项。
7. 等待日志输出。
8. 停止日志采集并执行平台化日志断言。
9. 依次恢复右耳和左耳双击基线选项。

验证点：

- 左右耳双击动作都可以切换。
- 每次选择后回到自定义控制设置面板。
- BLE 日志包含双击自定义控制设置证据。
- 用例结束后恢复基线选项。

### TC015 更多页自定义控制单击蓝牙交互

状态：iOS/Android 正式用例，已实现。

操作路径：

1. 打开 T8 更多页。
2. 打开自定义控制页。
3. 开始采集 App 日志。
4. 左耳单击先归一为播放/暂停，再切换为调大音量。
5. 切换到右耳。
6. 右耳单击先归一为播放/暂停，再切换为调大音量。
7. 等待日志输出。
8. 停止日志采集并执行平台化日志断言。
9. 依次恢复右耳和左耳单击为播放/暂停。

验证点：

- 左右耳单击动作都可以切换。
- 每次选择后回到自定义控制设置面板。
- BLE 日志包含单击自定义控制设置证据。
- 用例结束后恢复基线选项。

### TC016 更多页自定义控制三击蓝牙交互

状态：iOS/Android 正式用例，已实现。

操作路径：

1. 打开 T8 更多页。
2. 打开自定义控制页。
3. 开始采集 App 日志。
4. 左耳三击先归一到基线选项，再切换到目标选项。
5. 切换到右耳。
6. 右耳三击先归一到基线选项，再切换到目标选项。
7. 等待日志输出。
8. 停止日志采集并执行平台化日志断言。
9. 依次恢复右耳和左耳三击基线选项。

验证点：

- 左右耳三击动作都可以切换。
- 每次选择后回到自定义控制设置面板。
- BLE 日志包含三击自定义控制设置证据。
- 用例结束后恢复基线选项。

### TC017 更多页自定义控制长按蓝牙交互

状态：iOS/Android 正式用例，已实现。

操作路径：

1. 打开 T8 更多页。
2. 打开自定义控制页。
3. 开始采集 App 日志。
4. 左耳长按先归一为降噪关/环境音/降噪开，再切换为语音助手。
5. 切换到右耳；必要时再次点击右耳页签降低页签偶发未切换影响。
6. 右耳长按先归一为降噪关/环境音/降噪开，再切换为语音助手。
7. 等待日志输出。
8. 停止日志采集并执行平台化日志断言。
9. 依次恢复右耳和左耳长按为降噪关/环境音/降噪开。

验证点：

- 左右耳长按动作都可以切换。
- 每次选择后回到自定义控制设置面板。
- BLE 日志包含长按自定义控制设置证据。
- 用例结束后恢复基线选项。

注意事项：

- debug shell 已支持 `longPress` 和 `longPress-element`，调试长按相关路径时不要绕开用例调试规则。

## 平台差异汇总

- 高音质解码：Android 独有；开启和关闭都会断开再恢复连接，并自动回详情页。
- 断连恢复：iOS 确认断开后停留更多页，需要手动返回详情页；Android 确认断开后自动回详情页。
- BLE 日志：iOS 主要看 CoreBluetooth 写入；Android 需要看下发/上报 payload 和业务字段。
- 自定义均衡器：Android 点击已有自定义均衡器整行是选中，点击名称文本才进入编辑页。
- 地图、说明书、删除图标等视觉路径在不同平台渲染有差异，优先使用 ROI、颜色占比、OCR 或视觉模板做平台兼容。

## 验证记录

- `android-t8-high-quality-debug-common-open-20260628-001`：切换到 common `device.openTargetDevice` 后，高音质 focused 通过，确认可按 Android MAC `91:D6` 打开 T8。
- `android-t8-custom-equalizer-debug-20260628-001`：自定义均衡器 focused 通过，确认“新增自定义 -> 保存 -> 点击名称文本重开 -> 删除新增项”路径。
- `android-t8-custom-debug-20260628-011`：自定义均衡器与自定义控制批量 focused 通过，uploads `14/0/0`。
- `t8-ios-tc010-tc017-redo-20260627-002`：iOS 更多页批量通过，覆盖 `TC010` 到 `TC017`。
- `android-t8-full-20260628-003`：Android T8 曾全量通过；后续自定义均衡器又修过“必须新增并点击名称文本重开”的路径，因此最新代码仍需要重新跑一次 Android T8 全量作为最终回归记录。

## 暂缓覆盖

- `TC001_NOISE_CONTROL.yaml`：暂不纳入正式 T8 计划。
- 防风噪：当前没有稳定可见入口或 locator。
- 音频共享：当前 T8 型号构建未暴露该功能。
