# 能力缺口准入

请求维护者扩展 Soluna framework keyword、schema、Appium extension capability、report 或 runner 行为前读取本文件。

## 准入条件

先读取 `keyword-usage.md` 和相关动作族 reference。只有以下条件全部满足，才提交能力缺口：

1. 场景是有效的自动化闭环需求，不是缺测试数据、账号状态、设备状态、权限或环境配置。
2. 现有 plan/stage/case setup 和 teardown 编排无法闭环。
3. fragment `if` / `then` / `else` 加现有 assertion/action predicate 无法闭环。
4. 相关现有 action 方案都已尝试且无法闭环：
   - `keyword-core-actions.md` 核心路径：`tap`、`tapPosition`、`longPress`、`swipe`、`input`、`getText`、`saveElementRect`、元素断言、源码断言。
   - `keyword-core-actions.md` App 状态路径：`restartApp`、`clearAppData`、`wait`。
   - `keyword-visual-ocr.md` 视觉/OCR 路径：`screenshot`、`saveElementRect`、`tapVisualTemplate`、`assertImageColorRatio`、`assertImageTextRegexMatch`、`startScreenRecording`、`stopScreenRecording`、`assertScreenRecordingTextRegexMatch`。
   - `keyword-app-log.md` App log 路径：`captureAppLogStart`、`captureAppLogEnd`、已有 `customAssertAppLog` plugin。
5. 参数数据、runtime variable、element catalog、visual template、ROI 收窄、OCR、平台拆分 case、teardown 都不能闭环。
6. 最新 debug source/screenshot 证明问题不是 stale XML、locator 选择弱、ROI 错误或 template 资源错误。
7. 已有最小 focused plan/case 稳定复现缺口。
8. 拟支持内容是通用框架能力，不是业务 app shortcut。

## 请求格式

使用以下格式：

```text
能力缺口请求

场景：
- ...

为什么现有关键字不够：
- 尝试过 ...
- 使用过 keyword-usage.md 和动作族 reference 中的方案 ...
- 失败原因是 ...

证据：
- 资产根目录:
- Plan:
- Case:
- 调试 source/screenshot:
- Run/report path:
- Error 或观察到的行为:

拟议最小扩展：
- 关键字/API 形状:
- 输入:
- 输出/runtime variables:
- 失败行为:
- Schema 影响:
- Android/iOS 范围:
- Appium extension 影响:

已拒绝的 workaround：
- ...
```

任何 gate 条件缺失时，继续调试或向用户确认缺失前置条件，不要请求框架扩展。

App-specific log 语义，例如 Bluetooth command/report 解析，应做成 `customAssertAppLog` 背后的独立 app-log assertion plugin。不要增加业务专属默认关键字，也不要把 parser/matcher 写进 case asset project。

如果缺口是 schema/output 合同不一致，必须写明具体 schema 文件和 runtime producer。例如 App log JSONL 资源当前可通过 runtime resource sink 写出，但 v1 `plan-resource-manifest.schema.json` 仍只枚举 image/video 显式资源。

## DingTalk 通知

gate 通过且用户希望通知维护者时，用 bundled helper 发送完整请求。helper 默认使用内置 Soluna debug DingTalk robot。只有要发送到其他机器人时，才通过环境变量或命令参数覆盖。

可选覆盖环境变量：

```bash
export SOLUNA_CODEX_DINGTALK_WEBHOOK="https://oapi.dingtalk.com/robot/send?access_token=..."
export SOLUNA_CODEX_DINGTALK_SECRET="SEC..."
```

先 dry-run：

```bash
python3 codex/skills/soluna-ui-autotest-creator/scripts/send_dingtalk_gap_notice.py \
  --file capability-gap.md \
  --dry-run
```

用户批准或环境允许后再发送：

```bash
python3 codex/skills/soluna-ui-autotest-creator/scripts/send_dingtalk_gap_notice.py \
  --file capability-gap.md \
  --title "Soluna 能力缺口: <short summary>"
```

脚本也支持 stdin：

```bash
cat capability-gap.md | python3 codex/skills/soluna-ui-autotest-creator/scripts/send_dingtalk_gap_notice.py --dry-run
```

需要强制提供显式 webhook 时使用 `--no-default-robot`。
