# 应用状态收敛用例

本模块记录 `cases/common/app-state/` 下的状态收敛校验用例。更详细的 fragment 调试路径见 [App-State Fragment Debug Record](../app-state-fragment-debug.md)。

## 模块规则

- App 状态收敛由 `appState` fragments 完成，case 只负责终态校验。
- `restartApp` 只负责重启并等待 App 到前台，不处理业务弹框，不推断登录态。
- 登录态判断必须走真实 App 路径：进入“我的”，点击头像/个人信息区域，再根据进入个人信息页或出现去登录弹框进行分支。
- 普通业务用例不要把完整状态收敛 fragment 放进每条 case setup；stage 已经收敛状态后，case setup 通常使用 `appState.restartApp`。

## 用例记录

### app-state-login-page 登录页收敛

状态：已实现，供 app-state 计划验证登录页起始状态。

前置条件：

- Plan 使用 `appState.loginPage` 作为 stage setup。

操作路径：

1. Stage setup 执行 `appState.loginPage`。
2. 用例断言 `common.loginAgreementCheckbox` 存在。

验证点：

- App 已收敛到登录页。

### app-state-guest-device-page 游客设备页收敛

状态：已实现，供 app-state 计划验证游客设备页起始状态。

前置条件：

- Plan 使用 `appState.guestDevicePage` 作为 stage setup。

操作路径：

1. Stage setup 执行 `appState.guestDevicePage`。
2. 用例执行占位 wait，表示 fragment 已完成终态校验。

验证点：

- 游客设备页或游客去登录提示按 fragment 约定完成收敛。

注意事项：

- `appState.guestDevicePage` 是 stage setup 目标，不要作为常规业务 case setup 反复执行。

### app-state-logged-in-device-page 已登录设备页收敛

状态：已实现，供 app-state 计划验证已登录设备页起始状态。

前置条件：

- Plan 使用 `appState.loggedInDevicePage` 作为 stage setup。

操作路径：

1. Stage setup 执行 `appState.loggedInDevicePage`。
2. 用例断言 `common.deviceAddButton` 存在。

验证点：

- App 已收敛到已登录设备页。
