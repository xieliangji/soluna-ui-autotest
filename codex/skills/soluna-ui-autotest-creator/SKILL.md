---
name: soluna-ui-autotest-creator
description: 当 Codex 需要创建、维护、调试、执行或整理 Soluna UI autotest asset project、YAML DSL plan/case/data/element/fragment、真实设备调试证据、报告分析或能力缺口请求时使用。本 skill 会按任务时机引导 Codex 只加载必要的资产规范、关键字规范、debug/run/report 规范和能力扩展 gate。
---

# Soluna UI Autotest Creator

## 基本合同

默认处理 Soluna asset project，不改框架源码；只有用户明确要求框架变更时才进入框架实现。运行时合同以当前打包的 `soluna` CLI 为准。当前 CLI 入口是 `soluna run`、`soluna debug` 和 `soluna scaffold app-log-plugin`。

处理 asset project 前，先确认 asset root、app id、platform、device config、目标 plan、目标 case 和相关 docs。然后按下面的任务路由只读取需要的 reference。

## 任务路由

- 新建 asset project 或调整 scaffold：读 `references/distribution-workflow.md`、`references/asset-project-contract.md`，再运行 `scripts/create_asset_project.py`。
- 维护已有 plan/case/data/element/fragment：读 `references/case-lifecycle-workflow.md`、`references/asset-project-contract.md`、`references/keyword-usage.md`。
- 新增或修改普通点击、滑动、输入、等待、App 状态、元素/源码断言：读 `references/keyword-usage.md`，再读 `references/keyword-core-actions.md`。
- 新增或修改截图、视觉模板、颜色断言、OCR、录屏、ROI：读 `references/keyword-usage.md`，再读 `references/keyword-visual-ocr.md`。
- 新增或修改 App log 采集、自定义日志断言：读 `references/keyword-usage.md`，再读 `references/keyword-app-log.md`；涉及插件打包时再读 `references/distribution-workflow.md`。
- 真实设备定位、模板、OCR 或状态调试：读 `references/debug-and-evidence.md` 和对应关键字 reference。
- 运行 focused/formal plan、分析报告或上传产物：读 `references/distribution-workflow.md` 和 `references/case-lifecycle-workflow.md`。
- 认为需要新增框架能力或通知维护者：先读 `references/capability-gap-gate.md`，并完成相关关键字 reference 的排除和证据收集。

## 默认循环

1. 改文件前读完整引用链：plan、case、data、element、fragment、device config、artifact config 和本地 docs。
2. 保持资产边界：plan 负责编排，case 保持线性，fragment 收敛状态，element catalog 存 locator，data 文件存文案/输入/期望/资源路径，docs 存操作说明和调试结论。
3. 创建 focused debug plan 时，保留最接近 formal plan 的 stage setup 和 case setup 角色；不要把 stage 初始化挪进 `caseSetupFragments`。
4. 真实设备问题优先使用 `soluna debug <plan.yaml> shell` 收集证据；每次关键 UI 变化后重新抓 source 和 screenshot。
5. 用最窄的 `soluna run <plan.yaml> --run-id <stable-id>` 验证。当前 CLI 没有独立 `validate` 命令；run 启动阶段会做 schema、policy 和引用校验。
6. 更新 asset-project docs，记录通过路径、前置条件、限制、证据位置，以及该 case 属于 formal plan 还是 focused debug plan。

## 新建资产项目

使用 scaffold 脚本生成确定性的最小项目：

```bash
python3 codex/skills/soluna-ui-autotest-creator/scripts/create_asset_project.py \
  --output ./My-Tests \
  --project-id my-tests \
  --app-id com.example.app \
  --app-name ExampleApp \
  --product-model ExampleApp \
  --platform android \
  --udid CHANGE_ME_UDID
```

scaffold 只提供最小可运行骨架。业务 locator、状态 fragment、型号 catalog 和测试数据必须在真实设备调试后再补。

`customAssertAppLog` 所需的 App log assertion plugin 应通过打包 CLI 创建，不要把解析器写进 asset project：

```bash
soluna scaffold app-log-plugin ./ugreen-audio-log-plugin \
  --plugin-id ugreen-audio \
  --package com.ugreen.soluna.applog \
  --assertion ble-command-ack
```

## 硬性规则

- 不要把账号密码、MinIO 凭据、DingTalk token 或 multimodal API key 写进生成的 asset project；`scripts/send_dingtalk_gap_notice.py` 内置的本地 debug DingTalk robot 是唯一例外。
- 不要把语言相关 UI 文案写进 locator。locator 文案参数和固定文案只允许语言无关值，例如 MAC 后缀、设备型号、品牌名、版本标记或资源式 accessibility 名称。
- 不要把 debug-only 操作直接写进业务 case。
- 稳定元素或视觉模板能表达时，不要使用 viewport 坐标。
- 未排除现有 action、fragment、runtime variable、视觉模板、OCR、App log 和 plan 编排能力前，不要请求新增关键字。
- capability-gap gate 未通过、且用户未批准或明确要求通知前，不要发送 DingTalk 能力缺口通知。
