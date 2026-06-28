# 分发和执行流程

处理打包 Soluna distribution、执行 asset-project plan、阅读报告或打包 App log assertion plugin 时读取本文件。

## CLI 边界

优先使用当前 distribution 的可执行文件：

```bash
soluna run <plan.yaml> --run-id <run-id>
soluna debug <plan.yaml> shell
soluna scaffold app-log-plugin <output> --plugin-id <id> --package <package>
```

不要让外部 asset project 依赖框架源码路径。

当前 CLI 没有独立 `validate` 命令。`soluna run` 是验证和执行入口：启动阶段会做 schema、policy、引用、device、Appium、artifact 和 plugin 检查。未来 distribution 如果提供 `validate`，可以先用它，但不要假设它存在。
当前 CLI 也不强制读取 `soluna-project.yaml`；该文件服务 asset project 元数据和未来项目发现，不能替代 `soluna run <plan.yaml>` 的显式 plan path。

## 分发包内容

distribution 应包含：

- `bin/soluna`：CLI 入口。
- `tools/`：FFmpeg 等运行时工具。
- `plugins/soluna-appium-ext/`：打包的 Appium extension source。
- `plugins/app-log/`：`customAssertAppLog` 使用的独立 app-log assertion plugin JAR。
- `codex/skills/soluna-ui-autotest-creator/`：本 Codex skill 和 scaffold 资源。

asset project 只能引用自身文件，并通过 distribution CLI 执行。

App-specific log parser/matcher 应构建为独立 JVM plugin JAR。将 JAR 和依赖 JAR 放到当前 distribution 的 `plugins/app-log/`，或通过 `-Dsoluna.appLogPluginDirs=<paths>` / `SOLUNA_APP_LOG_PLUGIN_DIRS=<paths>` 传入目录。不要把 parser 代码写进 case、data、element 或 fragment 资产。

创建 App log assertion plugin：

```bash
soluna scaffold app-log-plugin ./ugreen-audio-log-plugin \
  --plugin-id ugreen-audio \
  --package com.ugreen.soluna.applog \
  --assertion ble-command-ack
```

构建生成项目：

```bash
SOLUNA_HOME=/path/to/soluna gradle test jar
```

或：

```bash
gradle test jar -PsolunaHome=/path/to/soluna
```

然后把 JAR 复制到 `plugins/app-log/`。

## 执行顺序

1. 用 `failureStrategy: stop-case` 运行最窄 focused plan。
2. 如果启动失败，先修 schema/policy/reference/device/artifact/plugin 配置，不急着改业务 action。
3. 如果 action 执行失败，检查 report JSON、HTML、trace diagnostics 和 `plan-resource-manifest.json`。
4. focused plan 通过后，再运行更大的 formal plan。
5. 只有框架或负向路径测试期望失败时，才用 `--expect failed`。

阅读 `execution-result.json` 时，先看 `summary` 中的 stage/case/action 统计，再看 `failures` 中的扁平失败位置。使用 `actionId`、`actionKeyword`、`attempt`、`durationMs` 等 metadata 将报告行和 case YAML 对齐。`traceArtifacts` 用于失败动作 screenshot/page-source 证据；`plan-resource-manifest.json` 用于显式 screenshot、recording、OCR 和当前 runtime 写出的 log 资源。

HTML report 是调试证据，不是编辑入口。先看 case overview 和 failure summary；需要动作级证据时再打开对应 case 的 action detail。`productModel` 是 report/通知展示标题；app/device 展示名应优先来自 Soluna/Appium extension 元数据，不要硬编码。

调试 run 使用稳定 run id，方便关联报告和 MinIO 路径。

## 报告和产物

本地报告默认在：

```text
build/soluna-runs/<runId>/report/
```

上传 object key 使用配置的 artifact prefix 和 run id：

```text
runs/<runId>/report/index.html
runs/<runId>/report/execution-result.json
runs/<runId>/report/plan-resource-manifest.json
runs/<runId>/resources/<explicit-resource>
runs/<runId>/diagnostics/<failure-trace>
```

`plan-resource-manifest.json` 记录 DSL 显式资源，例如 screenshot、recording、OCR match frame，以及当前 runtime 可写出的 App log JSONL 资源。失败 trace screenshot/page source 属于 diagnostics，通过 report `traceArtifacts` 暴露，不进入 manifest。
如果外部服务按 v1 schema 严格校验 manifest，要注意当前 schema 仍只枚举 image/video；包含 App log JSONL 的 manifest 属于已知合同缺口，按 `capability-gap-gate.md` 处理。
