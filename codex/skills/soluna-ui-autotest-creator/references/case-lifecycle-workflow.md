# 用例生命周期流程

创建、修改、调试、执行、移动或废弃 Soluna case/plan 时读取本文件。

## 目录

- 任务分流
- 编辑前检查
- 编写 Case
- Focused 调试计划
- 真实设备调试
- 执行流程
- 晋级和清理
- 何时停下来问用户

## 任务分流

- 新建 case：先读目标 plan、最相近的已有 case、相关 data、elements、fragments 和 app docs。
- 修改 case：读完整引用链和最近记录；除非用户要求，否则保持 setup/teardown 和 artifact 行为。
- 调试失败 case：先理解 formal plan 的 setup 和 data 链，再创建 focused debug plan。
- 执行 plan：使用打包的 `soluna run`；改资产前先看 report JSON/HTML、trace artifacts 和 resource manifest。
- debug case 晋级 formal plan：移除 debug-only 路径，保留稳定 data/locator，并更新 docs。

## 编辑前检查

1. 确认 asset root、app id、platform、plan path、target stage、case file、model slug 和 run id 约定。
2. 读取目标 stage/case 需要的引用文件：
   - `parameters`
   - `fragmentRefs`
   - `caseRefs`
   - case `dataRefs`
   - case/fragment `elementRefs`
   - device config
   - 上传或报告相关时读取 artifact config
3. 读取 `apps/<app-id>/docs/` 下的前置条件、通过路径、账号/设备限制和已知不稳定 UI。
4. 判断 case 属于 app-common、device-common 还是 model-specific。不要为了 focused run 方便而改变归属边界。

## 编写 Case

1. 明确 case 的稳定起点和结束清理。
2. 起点收敛放在 fragment 或 plan/stage setup，不放进业务 action list。
3. case `actions` 保持线性，不写分支或循环。
4. 文案、账号引用、产品/型号名、期望值、regex、template path、设备事实放 data 文件。
5. locator 放 element catalog，不在 action 中 inline locator。
6. 选择最小动作族：
   - 基础 UI 和断言：`keyword-core-actions.md`
   - screenshot/template/ROI/OCR/recording：`keyword-visual-ocr.md`
   - App log capture/assertion：`keyword-app-log.md`
7. 修改昵称、语言、地区、配对设备状态、登录态、app 数据或开关设置时增加 teardown。
8. 更新模块 docs，记录操作路径、前置条件、数据依赖，以及 formal/debug 归属。

## Focused 调试计划

1. 从最接近的完整 formal plan 开始。
2. 保留 stage-level convergence 在 `setupFragments`。
3. 保留 per-case reset 在 `caseSetupFragments`。
4. 只缩小 `caseRefs`、stage id/name、run metadata 和 failure strategy。
5. focused plan 优先使用 `failureStrategy: stop-case`。
6. focused plan 放在 `plans/debug/`。
7. 不要因为只调一个 case，就把页面导航 fragment 从 `setupFragments` 挪到 `caseSetupFragments`。

运行 focused plan 前，在工作更新里写清楚：

```text
stage setup: <once-per-stage convergence>
case setup: <per-case reset>
target cases: <case ids/files>
```

## 真实设备调试

1. 多步调试优先使用 `soluna debug <plan.yaml> shell`。
2. 除非场景依赖脏状态，否则从 `restart-app` 后的状态开始收集 baseline。
3. 信任 locator 或 template 前，先抓 source 和 screenshot。
4. 页面跳转、弹窗、键盘变化、滑动、template tap、restart、WebView transition 后，重新抓 source 和 screenshot。
5. 将 debug 结果转换到资产边界：
   - debug locator -> element catalog
   - debug template path -> data file parameter
   - debug coordinate -> element-relative `tapPosition` 或 template
   - debug copy -> parameter data 和 assertion/input，不进 locator
6. 转成 DSL 后重新运行 focused plan。

## 执行流程

当前打包 CLI 没有独立 `validate` 命令。使用 `soluna run` 触发 schema、policy、reference、session、execution、report 和 upload 行为。

推荐顺序：

1. 用稳定 run id 运行最窄 focused plan。
2. 如果启动阶段失败，先修 schema/policy/reference/device/artifact config，不急着改业务 action。
3. 如果执行阶段失败，检查：
   - `execution-result.json` 的 `summary`
   - `execution-result.json` 的 `failures`
   - HTML report 的 case overview 和 action detail
   - `traceArtifacts` 中的失败截图/source
   - `plan-resource-manifest.json` 中的显式 screenshot/video/OCR/log 资源
4. focused pass 后，再把 case 加入或保留在更大的 formal plan。
5. formal plan 回归可用 `failureStrategy: continue-case`，让后续 case 继续收集证据。

稳定 run id 示例：

```text
<module>-<platform>-<case-id>-debug-001
<model-slug>-<platform>-formal-YYYYMMDD-001
```

## 晋级和清理

- 只有稳定 case 进入 formal plan。
- 探索性 plan 留在 `plans/debug/`；过期 debug plan 要删除或明确标记被替代。
- 移除临时 wait、宽泛 viewport tap、一次性 source regex probe 和 debug-only screenshot，除非它们属于证据合同。
- 显式 screenshot 只在其他服务、模块或报告消费者需要时保留。
- docs 中记录最终通过路径和作为证据的 focused run/report。

## 何时停下来问用户

以下情况不要自行编造行为，应询问用户：

- 缺少必要账号、设备、固件、硬件、权限、网络或后端状态。
- 操作会清理数据或修改请求范围外的持久设置。
- 目标 app 行为不明确，且没有真实设备证据。
- 看起来需要新增框架能力，但 capability-gap gate 尚未完成。
