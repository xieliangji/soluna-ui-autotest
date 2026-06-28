# 资产项目合同

创建或修改 plan、case、data、element catalog、fragment 或 asset docs 前读取本文件。

## 目录

- 推荐目录
- YAML 归属
- Case 规则
- Locator 规则
- Plan 规则

## 推荐目录

```text
<asset-root>/
  soluna-project.yaml
  apps/<app-id>/
    plans/
      common/
      device/
        <model-slug>/
      debug/
    cases/
      common/
      device/
        common/
        <model-slug>/
    data/
      common/
      device/
    elements/
      common.yaml
      device/
        <model-slug>.yaml
    fragments/
    docs/
  devices/
    android/
    ios/
  artifacts/
```

runner 从 plan path 启动。device config、artifact config、parameters、cases、elements 和 fragments 必须通过 plan 直接或间接引用到。
`soluna-project.yaml` 是项目元数据和未来项目发现入口；当前 CLI 不从它推导默认 plan、device 或 artifact config，执行时仍显式传入 plan path。

## YAML 归属

- plan 负责编排：app identity、platform、device config、parameters、fragment refs、stages、case refs、diagnostics、artifact config、defaults。
- plan 内所有 `file` / config path 都按声明它的 YAML 文件位置解析；从 `plans/common/` 引用 app 内 data/fragment/case 时通常需要 `../../...`。
- 每个 plan 必须声明 `productModel`。公共 app 功能 plan 使用 app 展示名；型号相关 plan 使用具体产品型号。展示名不确定时，先通过真实设备和 Soluna/Appium extension-backed debug 证据确认。
- case 负责线性用户意图：actions、setup refs、teardown refs、data refs、element refs。
- data 文件负责输入、期望值、环境值、资源字典和模板路径。
- element catalog 负责 locator。
- fragment 负责可复用 setup/teardown 和状态收敛，可以使用 `if` / `then` / `else`。
- docs 负责用例说明、操作路径、前置条件、调试限制和数据限制。

## Case 规则

- case `actions` 必须线性，不写 `if`、循环或跳转。
- 写 action payload 前，先读 `keyword-usage.md` 和对应动作族 reference：`keyword-core-actions.md`、`keyword-visual-ocr.md` 或 `keyword-app-log.md`。
- 新 action 使用 nested keyword 形式：

```yaml
- tap:
    id: open-mine-tab
    element: common.mineTab
    desc: 打开我的 tab
```

- 修改昵称、语言、地区、登录态、app 数据、设备设置等持久状态时，必须设计 teardown 或隔离 plan。
- 破坏性或数据 reset 用例优先放 focused plan，除非 formal plan 明确把它隔离在最后阶段。
- 依赖特殊账号或设备数据的用例，在前置条件稳定前放 focused plan。
- 公共 app 功能放 `cases/common/`。
- 跨型号设备相关用例放 `cases/device/common/`。
- 型号专属用例放 `cases/device/<model-slug>/`，slug 使用稳定 ASCII，例如 `ugreen-hitune-s6-pro`。
- 新模块或新目录内重新编号 case，不要把 app-common 编号延续到 device-common 或型号目录。

## Locator 规则

- action 使用 `element: alias.name`。
- locator 只能定义在 element catalog。
- 公共 app 和跨型号设备列表 locator 放 `elements/common.yaml` 等共享 catalog。
- 型号专属设备页或能力 locator 放 `elements/device/<model-slug>.yaml`；同目录 case 需要型号 UI 时引用该 catalog。
- Android 优先使用 resource id；iOS 优先使用稳定 accessibility id。
- iOS 和 WebView 页面改 XPath/class-chain 前，先抓最新 XML。
- 不要在 locator 里硬编码业务文案。不得不用 copy 匹配时，把 copy 放 data 文件。
- 没有稳定可访问元素的非文字视觉控件，用 visual template。

## Plan 规则

- formal plan 可以使用 `continue-case`，让后续 case 在失败后继续收集证据。
- focused debug plan 通常使用 `stop-case` 并保留本地产物。
- 公共 app 和跨型号 formal plan 放 `plans/common/`。
- 型号 formal plan 放 `plans/device/<model-slug>/`。
- 临时、focused、探索性 plan 放 `plans/debug/`；不要把 `*-debug.yaml` 留在 `plans/common/` 或 formal plan 目录。
- stage setup 做一次性起始状态收敛，例如登录、进入设备列表、打开目标设备页。
- case setup 在每个 case 前执行，只做 per-case 归一化，例如 `restartApp`、轻量清理、回到前台。
- 缩小 formal plan 为 focused plan 时，不要把 stage 初始化 fragment 复制到 `caseSetupFragments`。保留原 `setupFragments`，除非完整 plan 证明 case setup 本来就应不同。
- 如果每个 focused case 都需要 fresh foreground app，优先在 `caseSetupFragments` 使用 `appState.restartApp`，不要用页面收敛 fragment 替代。
- 运行临时 plan 前，在工作说明或用户更新里写清楚：`setupFragments` 是 once-per-stage convergence，`caseSetupFragments` 是 per-case reset。
- plan/stage/case 的 setup 和 teardown action 使用与 case action 相同的关键字规则。
- 不要通过 CLI 传 device/data/element 路径；这些必须写在 YAML 引用链里。
- 从目标设备详情页开始的 case，应在 stage setup 组合 fragment：先到设备列表，再打开目标设备。例如 `appState.loggedInDevicePage` / `appState.guestDevicePage` 后接 `device.openTargetDevice`。
- 目标设备打开 fragment 必须等待目标设备处于 connected/online 状态后再点击；需要在线设备的场景不能只判断设备项存在。
- plan `app.reset` 当前只 seed `${app.reset}`，不会自动清理 app 数据。reset 行为必须通过 lifecycle fragment/action 显式表达，例如 `restartApp` 或 Android-only `clearAppData`。

focused plan setup 示例：

```yaml
stages:
  - id: logged-in-device-page
    setupFragments:
      - appState.loggedInDevicePage
    caseSetupFragments:
      - appState.restartApp
    caseRefs:
      - file: cases/common/TC001_MINE_ABOUT.yaml
      - file: cases/common/TC002_MINE_LANGUAGE_SETTINGS.yaml
```

设备详情页 setup 示例：

```yaml
fragmentRefs:
  - id: appState
    file: fragments/app-state.yaml
  - id: device
    file: fragments/device.yaml

parameters:
  - id: targetDevice
    file: data/device/ugreen-hitune-s6-pro.yaml

stages:
  - id: target-device-detail
    setupFragments:
      - appState.loggedInDevicePage
      - device.openTargetDevice
    caseRefs:
      - file: cases/device/ugreen-hitune-s6-pro/TC001_MODEL_FEATURE.yaml
```
