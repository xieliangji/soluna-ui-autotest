# {{APP_NAME}} 用例维护记录

本文档属于 `{{APP_ID}}` 的 Soluna asset project。

在这里记录 App 专属用例规则、操作路径、测试数据前置条件、locator 调试结论和真实设备限制。不要写入账号密码、token、MinIO 凭据、DingTalk secret 或 multimodal API key。

## 基本规则

- case `actions` 保持线性；分支和可复用状态收敛放在 fragment。
- locator 放 `elements/`，测试值和文案放 `data/`，编排放 `plans/`。
- formal app-common 或跨型号 plan 放 `plans/common/`；型号 formal plan 放 `plans/device/<model-slug>/`；focused debug plan 放 `plans/debug/`。
- 公共 App case 放 `cases/common/`；跨型号设备 case 放 `cases/device/common/`；型号专属 case 放 `cases/device/<model-slug>/`。
- 公共 App 和跨型号设备列表 locator 放 `elements/common.yaml`；型号专属 locator 放 `elements/device/<model-slug>.yaml`。
- 每个 case 模块或目录内重新编号，不跨模块延续编号。
- 接受新 locator 前，先抓最新 source 和 screenshot。
- case 调试通过并进入 plan 后，更新对应模块 docs。

## 记录模板

- 前置条件：
- 操作路径：
- 数据依赖：
- 设备/固件/账号限制：
- 最近通过 run/report：
- 已知不稳定点：
