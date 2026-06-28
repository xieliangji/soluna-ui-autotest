# 设备列表长按操作窗用例

本模块记录设备列表第一个设备项长按后的操作窗用例，包括重命名、删除取消和断开连接。

用例文件位于 `cases/device/common/`，适用于所有支持设备列表长按操作窗的设备，不绑定具体型号。

调试计划位于 `plans/debug/ios-device-actions-debug.yaml`、`plans/debug/ios-device-actions-guest-debug.yaml` 和 `plans/debug/android-device-common-debug.yaml`；正式聚合计划引用这些通用设备用例时放在 `plans/common/` 或型号正式计划目录中。

## 模块规则

- 长按设备项使用 `longPress`，目标为第一个可见设备 cell。
- 操作窗菜单项不能全局按固定第二项、第三项理解：设备非已连接状态只有“重命名”和“删除设备”两个功能项。
- 重命名始终取操作窗第一项。
- 删除设备始终取操作窗最后一项，避免已连接和未连接状态下菜单项数量不同导致误点。
- 断开连接只在已连接设备场景执行，执行前必须先断言设备状态为“已连接”。
- iOS 重启后首次点击重命名输入框可能出现弹窗被系统键盘顶走的问题。用例需要先打开一次重命名弹窗，点击输入框触发问题，再点击页面顶部非弹窗区域收起键盘并关闭弹窗，然后重新打开重命名弹窗做实际操作。
- 关闭重命名弹窗时点击页面顶部安全区域，不点击设备列表区域，避免误触设备项。
- 重命名用例必须在 teardown 中恢复原设备名。
- Android 设备列表卡片使用 `rvDevices` 下第一个可点击且可长按的子节点；操作窗菜单项使用稳定 resource-id：`llRename`、`llBreak`、`llDelete`。

## 用例记录

### TC003 设备列表-长按设备项-断开连接

状态：已实现，iOS/Android 登录态用例。

前置条件：

- Stage 已收敛到登录态设备列表页。
- 第一个设备当前状态为“已连接”。

操作路径：

1. 校验第一个设备项可见。
2. 校验第一个设备状态为“已连接”。
3. 长按第一个设备项打开操作窗。
4. 点击操作窗第二项“断开连接”。
5. 等待断开完成。
6. 校验第一个设备状态变为“未连接”。
7. 重启 App。
8. 等待设备自动重连。
9. 校验第一个设备状态恢复为“已连接”。

验证点：

- 已连接设备的操作窗存在断开连接入口。
- 点击断开连接后设备状态变为未连接。
- 重启 App 后设备可以自动重连。

注意事项：

- iOS 当前实测点击“断开连接”后直接断开，没有稳定的确认弹框，因此用状态变化作为验证点。
- 非已连接状态下不执行该用例，避免第二项实际为删除设备。
- Android 真机断开后自动重连耗时可能超过 60 秒；最终“已连接”断言保留自动重连验证逻辑，但等待预算放宽到 180 秒。

### TC001 设备列表-长按设备项-重命名

状态：已实现，iOS/Android 用例；iOS 已在登录态和游客态调通，Android 已在登录态调通。

前置条件：

- Stage 已收敛到设备列表页。
- 设备列表存在至少一个设备项。

操作路径：

1. 校验第一个设备项可见。
2. 读取并保存第一个设备名称。
3. 长按第一个设备项打开操作窗。
4. 点击第一项“重命名”。
5. iOS 首次弹窗规避：点击重命名输入框后，点击页面顶部非弹窗区域收起键盘，再次点击顶部非弹窗区域关闭弹窗。
6. 再次长按第一个设备项打开操作窗。
7. 点击第一项“重命名”。
8. 输入临时设备名。
9. 点击确定。
10. 校验设备名已变更为临时设备名。
11. teardown 中再次打开重命名弹窗，输入原设备名并确认。
12. 校验设备名已恢复。

验证点：

- 长按设备项可打开操作窗。
- 重命名可以提交并刷新到设备列表。
- 用例结束后原设备名恢复。

注意事项：

- 不使用菜单第二项或最后一项做重命名。
- 顶部区域点击只用于关闭弹窗/键盘，不点击设备列表区域。
- Android 重命名弹窗输入框位于 `ll_dialog_container` 下的 `EditText`，确认按钮为 `btn_positive`。

### TC002 设备列表-长按设备项-删除设备取消

状态：已实现，iOS/Android 用例；iOS 已在登录态和游客态调通，Android 已在登录态调通。

前置条件：

- Stage 已收敛到设备列表页。
- 设备列表存在至少一个设备项。

操作路径：

1. 校验第一个设备项可见。
2. 读取并保存第一个设备名称。
3. 长按第一个设备项打开操作窗。
4. 点击操作窗最后一项“删除设备”。
5. 校验删除确认弹框出现。
6. 点击取消。
7. 校验第一个设备仍存在且名称未变。

验证点：

- 删除确认弹框可以正常出现。
- 点击取消不会删除设备。

注意事项：

- 删除设备必须取最后一项，兼容未连接设备只有两个菜单项的场景。

## 调试记录

- `android-device-common-debug-20260628-004` 通过 Android 真机 focused plan `plans/debug/android-device-common-debug.yaml`，覆盖 `TC001_DEVICE_RENAME`、`TC002_DEVICE_DELETE_CANCEL`、`TC003_DEVICE_DISCONNECT`；报告：`build/soluna-runs/android-device-common-debug-20260628-004/report/index.html`，上传完成：`uploaded=3, failed=0, abandoned=0`。
- Android 调试使用 packaged debug shell 的 `longPress-element` 采集 fresh source/screenshot；证据包括 `build/soluna-debug/android-device-action-menu-shell-longpress.*`、`android-device-rename-dialog-shell.xml` 和 `android-tc003-after-failure-late.*`。
- T8 Android 正式计划 `android-t8-full-20260628-002` 通过后，通用设备列表用例仍以 `plans/debug/android-device-common-debug.yaml` 的 focused run 作为本模块证据；型号详情页差异记录在 `cases/device/ugreen-hitune-t8/README.md`。
