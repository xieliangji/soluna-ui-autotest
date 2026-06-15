# soluna-appium-ext

`soluna-appium-ext` 是一个 Appium 插件，提供以下能力：

- 在 Appium 启动前检查宿主机依赖（`adb`、`go-ios`/`ios`）
- 通过 HTTP 接口按 `udid` 查询设备，并以统一模型返回 Android/iOS 设备信息
- 通过日志会话接口统一采集指定 `udid` 的 Android/iOS 日志（落盘 + 小内存缓存）

预检中的命令发现已做跨平台兼容：

- macOS/Linux：使用 `which`
- Windows：使用 `where`

English documentation is available in [`README.en.md`](./README.en.md).

## 功能说明

### 1）Appium 启动前依赖检查

插件启用时会检查：

- `adb` 是否已安装
- `go-ios` 或 `ios`（别名）是否已安装

如果有任一依赖缺失，会在控制台输出错误并抛出异常，从而阻止 Appium 继续启动。

### 2）设备查询接口

插件暴露接口：

- `GET /soluna/device?udid=<UDID>`
- `GET /soluna/devices`

行为：

- 根据 `udid` 判断当前机器连接的是 Android 设备还是 iOS 设备
- 按统一结构返回设备信息
- 设备不存在时返回 404
- `GET /soluna/devices` 返回当前接入主机的全部 Android+iOS 设备

### 3）执行 adb / go-ios（ios）命令

插件暴露命令执行接口：

- `POST /soluna/command`

请求体示例：

```json
{
  "tool": "adb",
  "args": ["devices"],
  "timeoutMs": 5000,
  "maxOutputBytes": 65536
}
```

字段说明：

- `tool`：只允许 `adb`、`go-ios`、`ios`
- `args`：命令参数数组
- `timeoutMs`：采集窗口（毫秒），超时会主动结束进程（默认 5000，范围 100~60000）
- `maxOutputBytes`：最大输出字节数（默认 65536，范围 1024~2097152）

返回说明：

- `exitCode`：进程退出码
- `timedOut`：是否因超时被主动结束
- `truncated`：输出是否被截断
- `stdout` / `stderr`：采集到的输出

日志说明：

- 接口会记录命令摘要日志（参数、耗时、退出码等）
- 为避免日志被超长输出污染，调试日志中的 `stdout/stderr` 会做预览截断

日志行为：

- 请求与执行结果摘要输出到 Appium `info` 级别日志
- 完整 `stdout` / `stderr` 输出到 `debug` 级别日志

对于不会立即退出、会持续输出的命令（例如某些日志类命令），本插件不会无限等待：

- 在 `timeoutMs` 窗口内持续采集输出
- 到时先发 `SIGTERM`，必要时再升级为 `SIGKILL`
- 返回当前已采集结果，避免接口阻塞

### 4）统一日志会话接口（指定 UDID）

插件暴露日志会话接口：

- `POST /soluna/logs/sessions`
- `GET /soluna/logs/sessions/:sessionId?cursor=<n>&limit=<n>`
- `DELETE /soluna/logs/sessions/:sessionId`

#### 创建会话

请求体示例：

```json
{
  "udid": "abc123",
  "maxBufferEntries": 1000,
  "maxSessionBytes": 104857600,
  "ttlMs": 600000
}
```

字段说明：

- `udid`：目标设备 UDID（必填）
- `maxBufferEntries`：内存环形缓存条数（默认 1000，范围 100~10000）
- `maxSessionBytes`：单会话落盘文件上限（默认 100MB，范围 1MB~500MB）
- `ttlMs`：会话空闲超时自动清理（默认 10 分钟，范围 1 分钟~24 小时）

返回 `sessionId` 后，调用方可按 cursor 增量拉取日志。

#### 读取日志

`GET /soluna/logs/sessions/:sessionId?cursor=0&limit=200`

返回字段重点：

- `entries`：统一格式日志数组（Android/iOS 抽象一致）
- `nextCursor`：下一次拉取起点
- `cursorAdjusted`：若传入 cursor 已过期（旧日志被淘汰），会自动修正并标记为 `true`

#### 结束会话

`DELETE /soluna/logs/sessions/:sessionId`

会停止日志子进程并删除该会话的落盘文件。

#### 存储策略（避免内存爆）

日志采用“落盘为主 + 小内存缓存”：

- 每条日志写入临时 JSONL 文件
- 同时保留最近 N 条在内存中，保证热读取性能
- 文件超上限后会裁剪最旧日志，并在会话元数据中累计 `droppedCount`
- 会话到期（TTL）自动清理，避免长期占用磁盘

成功响应示例：

```json
{
  "value": {
    "exists": true,
    "device": {
      "platform": "android",
      "udid": "abc123",
      "name": "Pixel 8",
      "model": "Pixel 8",
      "osVersion": "14"
    }
  }
}
```

设备不存在示例：

```json
{
  "value": {
    "exists": false,
    "message": "Device '<udid>' not found on this host"
  }
}
```

## 如何在启动 Appium 时使用该插件

下面是从本地开发到启动 Appium 的完整流程。

### 前置条件

请先安装宿主机依赖：

- `adb`
- `go-ios` 或 `ios`（别名）

如果缺失，插件会阻止 Appium 启动。

### 1）构建插件

在本仓库根目录执行：

```bash
npm install
npm run build
```

### 2）将插件安装到 Appium（本地源码方式）

```bash
appium plugin install --source=local .
```

查看是否安装成功：

```bash
appium plugin list
```

你应该能看到插件名 `soluna-ext`。

### 3）启用插件并启动 Appium

```bash
appium --use-plugins=soluna-ext
```

- 依赖检查通过：Appium 正常启动
- 依赖检查失败：启动被阻止，并输出类似错误

```text
Preflight failed: missing required CLI tool(s): adb, go-ios (or alias: ios). Install them before starting Appium.
```

### 插件升级步骤（按安装模式）

如果你是在插件仓库内执行 `appium` 命令，并且当前项目依赖里包含 `appium`，Appium 会把该插件标记为 `dev` 模式。
`dev` 模式下不允许 `uninstall`，会提示：`Cannot uninstall ... because it is in development`。

#### A) `dev` 模式

升级步骤：

1. 在插件仓库重新构建：

```bash
npm install
npm run build
```

2. 直接重启 Appium（无需卸载/重装）：

```bash
appium --use-plugins=soluna-ext
```

#### B) `local`/`npm` 普通安装模式

升级步骤：

1. 重新构建（本地源码场景）：

```bash
npm install
npm run build
```

2. 卸载旧版本：

```bash
appium plugin uninstall soluna-ext
```

3. 安装新版本：

```bash
# 本地源码
appium plugin install --source=local .

# 或 npm 包
# appium plugin install --source=npm <package-name>
```

4. 重启 Appium 并启用插件：

```bash
appium --use-plugins=soluna-ext
```

### 4）调用设备查询接口

Appium 启动后，可通过 `curl` 验证：

```bash
curl "http://127.0.0.1:4723/soluna/device?udid=<YOUR_UDID>"
```

### 5）调用全部设备列表接口

```bash
curl "http://127.0.0.1:4723/soluna/devices"
```

### 6）调用命令执行接口

```bash
curl -X POST "http://127.0.0.1:4723/soluna/command" \
  -H "Content-Type: application/json" \
  -d '{"tool":"adb","args":["devices"],"timeoutMs":5000}'
```

### 7）调用日志会话接口（curl 命令版）

先准备变量：

```bash
APPIUM_URL="http://127.0.0.1:4723"
UDID="<YOUR_UDID>"
```

1）创建日志会话（可带可选参数）：

```bash
curl -sS -X POST "$APPIUM_URL/soluna/logs/sessions" \
  -H "Content-Type: application/json" \
  -d "{
    \"udid\":\"$UDID\",
    \"maxBufferEntries\":1000,
    \"maxSessionBytes\":104857600,
    \"ttlMs\":600000
  }"
```

返回示例里会包含：

- `value.session.sessionId`
- `value.session.nextSeq`

2）第一次拉取（从 `cursor=0` 开始）：

```bash
curl -sS "$APPIUM_URL/soluna/logs/sessions/<SESSION_ID>?cursor=0&limit=200"
```

3）增量拉取（使用上一次返回的 `nextCursor`）：

```bash
curl -sS "$APPIUM_URL/soluna/logs/sessions/<SESSION_ID>?cursor=<NEXT_CURSOR>&limit=200"
```

4）结束并清理会话：

```bash
curl -sS -X DELETE "$APPIUM_URL/soluna/logs/sessions/<SESSION_ID>"
```

#### 日志太多/长时间没拉：如何直接拉“最新”

方案 A（保留当前会话）：

1）先用一个很大的 cursor 触发“跳到当前最新位置”：

```bash
curl -sS "$APPIUM_URL/soluna/logs/sessions/<SESSION_ID>?cursor=999999999&limit=1"
```

2）从响应里拿 `value.nextCursor`，后续都用这个值增量拉取：

```bash
curl -sS "$APPIUM_URL/soluna/logs/sessions/<SESSION_ID>?cursor=<NEXT_CURSOR>&limit=200"
```

方案 B（重新开始）：

- 直接 `DELETE` 旧会话并重新 `POST` 创建新会话，天然从“当前时刻”开始拉。

提示：

- `cursorAdjusted=true`：表示你的 cursor 已被服务端自动修正（通常是过旧或过大）
- `droppedCount>0`：表示有历史日志已被淘汰（文件裁剪或保留策略导致）

### 8）可选：自定义 Appium 地址与端口

如果你不是用默认 `127.0.0.1:4723`，请同步修改请求地址：

```bash
appium --address 0.0.0.0 --port 4725 --use-plugins=soluna-ext
curl "http://127.0.0.1:4725/soluna/device?udid=<YOUR_UDID>"
curl "http://127.0.0.1:4725/soluna/devices"
```

### 9）Python client（根目录）

仓库内提供了一个轻量 Python client：`soluna_client.py`（仓库根目录，仅标准库，无第三方依赖）。

示例：

```python
from soluna_client import SolunaClient

client = SolunaClient(base_url='http://127.0.0.1:4723')

# 设备查询
device = client.get_device_info('abc123')
# 或获取当前所有连接设备
devices = client.list_devices()

# 命令执行
cmd = client.execute_command('adb', ['devices'])

# 日志会话
created = client.create_log_session('abc123')
sid = created['session']['sessionId']
batch = client.read_log_session(sid, cursor=0, limit=200)
client.delete_log_session(sid)
```

也支持上下文自动清理：

```python
from soluna_client import SolunaClient

client = SolunaClient()
with client.log_session('abc123') as (sid, _):
    logs = client.read_log_session(sid, cursor=0, limit=200)
    print(logs['entries'])
```

## 开发说明

### 安装依赖

```bash
npm install
```

### 构建

```bash
npm run build
```

### 代码检查

```bash
npm run lint
```

### 测试

运行全部单元测试：

```bash
npm run test:unit
```

运行单个测试文件：

```bash
npm run test:single test/unit/preflight.spec.ts
```

## Appium 插件元数据

`package.json` 中的 Appium 扩展元数据：

- `appium.pluginName`: `soluna-ext`
- `appium.mainClass`: `SolunaExtPlugin`
