# soluna-appium-ext

`soluna-appium-ext` is an Appium plugin that provides:

- Appium startup preflight checks for host CLI dependencies (`adb` and `go-ios`/`ios`)
- A custom HTTP endpoint to query device info by `udid` with a unified Android/iOS response model
- A unified log session API to collect Android/iOS logs for a target `udid` (disk-first with small memory cache)

Preflight command discovery is cross-platform:

- macOS/Linux: uses `which`
- Windows: uses `where`

## Features

### 1) Preflight checks before Appium startup

When the plugin is enabled, it verifies:

- `adb` is installed
- `go-ios` or `ios` (alias) is installed

If any required command is missing, it logs an error and throws, blocking Appium startup.

### 2) Device lookup endpoint

The plugin exposes:

- `GET /soluna/device?udid=<UDID>`
- `GET /soluna/devices`

Behavior:

- Detects whether the UDID belongs to an Android or iOS device connected to the host
- Returns normalized device info
- Returns HTTP 404 when device does not exist
- `GET /soluna/devices` returns all currently connected Android+iOS devices

### 3) Execute adb / go-ios (ios) commands

The plugin exposes a command execution endpoint:

- `POST /soluna/command`

Example request body:

```json
{
  "tool": "adb",
  "args": ["devices"],
  "timeoutMs": 5000,
  "maxOutputBytes": 65536
}
```

Fields:

- `tool`: only `adb`, `go-ios`, `ios`
- `args`: command argument array
- `timeoutMs`: capture window in ms (default 5000, range 100~60000)
- `maxOutputBytes`: max output bytes (default 65536, range 1024~2097152)

Response includes:

- `exitCode`: process exit code
- `timedOut`: whether process was stopped due to timeout
- `truncated`: whether output was truncated
- `stdout` / `stderr`: captured output

Logging notes:

- The plugin logs command summary metadata (args, duration, exit code)
- Debug `stdout/stderr` logs are preview-truncated to avoid log pollution from huge output

Logging behavior:

- Request/result summaries are logged at Appium `info` level
- Full command `stdout` / `stderr` are logged at `debug` level

For long-running commands that keep streaming output, this plugin uses controlled execution:

- captures output within `timeoutMs`
- sends `SIGTERM` first, then `SIGKILL` if needed
- returns captured output to avoid hanging HTTP requests

### 4) Unified log session API (by UDID)

The plugin exposes log session APIs:

- `POST /soluna/logs/sessions`
- `GET /soluna/logs/sessions/:sessionId?cursor=<n>&limit=<n>`
- `DELETE /soluna/logs/sessions/:sessionId`

#### Create session

Example body:

```json
{
  "udid": "abc123",
  "maxBufferEntries": 1000,
  "maxSessionBytes": 104857600,
  "ttlMs": 600000
}
```

Fields:

- `udid`: target device UDID (required)
- `maxBufferEntries`: in-memory ring buffer size (default 1000, range 100~10000)
- `maxSessionBytes`: per-session disk file cap (default 100MB, range 1MB~500MB)
- `ttlMs`: idle timeout for automatic cleanup (default 10m, range 1m~24h)

The create response returns `sessionId`. Use this id + cursor to fetch logs incrementally.

#### Read logs

`GET /soluna/logs/sessions/:sessionId?cursor=0&limit=200`

Important response fields:

- `entries`: unified log entries for Android/iOS
- `nextCursor`: cursor for next pull
- `cursorAdjusted`: `true` when requested cursor is too old and was auto-adjusted

#### Delete session

`DELETE /soluna/logs/sessions/:sessionId`

This stops the background log process and removes the session disk file.

#### Storage strategy (to avoid memory blow-up)

This feature uses a disk-first model with a small in-memory cache:

- every log entry is persisted to a temporary JSONL file
- only recent N entries are kept in memory for fast reads
- when file size reaches cap, oldest logs are trimmed and `droppedCount` increases
- idle sessions are cleaned by TTL to avoid long-term disk usage

## How to use this plugin when starting Appium

### Prerequisites

Install required host tools first:

- `adb`
- `go-ios` or `ios` (alias)

### 1) Build the plugin

```bash
npm install
npm run build
```

### 2) Install plugin into Appium (local source)

```bash
appium plugin install --source=local .
```

Verify installation:

```bash
appium plugin list
```

You should see plugin name `soluna-ext`.

### 3) Start Appium with plugin enabled

```bash
appium --use-plugins=soluna-ext
```

If preflight fails, startup is blocked and logs an error like:

```text
Preflight failed: missing required CLI tool(s): adb, go-ios (or alias: ios). Install them before starting Appium.
```

### Plugin upgrade steps (by install mode)

If you run `appium` inside this plugin repository and the project depends on `appium`, Appium may mark the plugin as `dev` install type.
In `dev` mode, uninstall is blocked with: `Cannot uninstall ... because it is in development`.

#### A) `dev` mode

Upgrade flow:

1. Rebuild in plugin repository:

```bash
npm install
npm run build
```

2. Restart Appium (no uninstall/reinstall needed):

```bash
appium --use-plugins=soluna-ext
```

#### B) normal `local`/`npm` install mode

Upgrade flow:

1. Rebuild (for local source flow):

```bash
npm install
npm run build
```

2. Uninstall old plugin:

```bash
appium plugin uninstall soluna-ext
```

3. Install new version:

```bash
# local source
appium plugin install --source=local .

# or npm package
# appium plugin install --source=npm <package-name>
```

4. Restart Appium with plugin enabled:

```bash
appium --use-plugins=soluna-ext
```

### 4) Call the device endpoint

```bash
curl "http://127.0.0.1:4723/soluna/device?udid=<YOUR_UDID>"
```

### 5) Call the all-devices endpoint

```bash
curl "http://127.0.0.1:4723/soluna/devices"
```

### 6) Call the command execution endpoint

```bash
curl -X POST "http://127.0.0.1:4723/soluna/command" \
  -H "Content-Type: application/json" \
  -d '{"tool":"adb","args":["devices"],"timeoutMs":5000}'
```

### 7) Log session APIs (curl commands)

Prepare variables:

```bash
APPIUM_URL="http://127.0.0.1:4723"
UDID="<YOUR_UDID>"
```

1) Create a log session (with optional limits):

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

The response includes:

- `value.session.sessionId`
- `value.session.nextSeq`

2) First pull (`cursor=0`):

```bash
curl -sS "$APPIUM_URL/soluna/logs/sessions/<SESSION_ID>?cursor=0&limit=200"
```

3) Incremental pull (reuse previous `nextCursor`):

```bash
curl -sS "$APPIUM_URL/soluna/logs/sessions/<SESSION_ID>?cursor=<NEXT_CURSOR>&limit=200"
```

4) Delete session:

```bash
curl -sS -X DELETE "$APPIUM_URL/soluna/logs/sessions/<SESSION_ID>"
```

#### High-volume logs / long pause: pull latest only

Option A (keep current session):

1) Use a very large cursor once to jump to the current tail:

```bash
curl -sS "$APPIUM_URL/soluna/logs/sessions/<SESSION_ID>?cursor=999999999&limit=1"
```

2) Read `value.nextCursor` from the response, then keep polling with it:

```bash
curl -sS "$APPIUM_URL/soluna/logs/sessions/<SESSION_ID>?cursor=<NEXT_CURSOR>&limit=200"
```

Option B (clean restart):

- Delete the old session and create a new one. New session starts from “now”.

Notes:

- `cursorAdjusted=true`: your requested cursor was auto-adjusted by server
- `droppedCount>0`: some historical logs were already evicted by retention/trimming

### 8) Optional custom host/port

```bash
appium --address 0.0.0.0 --port 4725 --use-plugins=soluna-ext
curl "http://127.0.0.1:4725/soluna/device?udid=<YOUR_UDID>"
curl "http://127.0.0.1:4725/soluna/devices"
```

### 9) Python client (repository root)

A lightweight Python client is provided at `soluna_client.py` in the repository root (stdlib only, no third-party deps).

Example:

```python
from soluna_client import SolunaClient

client = SolunaClient(base_url='http://127.0.0.1:4723')

# Device lookup
device = client.get_device_info('abc123')
# Or list all connected devices
devices = client.list_devices()

# Command execution
cmd = client.execute_command('adb', ['devices'])

# Log session APIs
created = client.create_log_session('abc123')
sid = created['session']['sessionId']
batch = client.read_log_session(sid, cursor=0, limit=200)
client.delete_log_session(sid)
```

It also supports context-managed auto cleanup:

```python
from soluna_client import SolunaClient

client = SolunaClient()
with client.log_session('abc123') as (sid, _):
    logs = client.read_log_session(sid, cursor=0, limit=200)
    print(logs['entries'])
```

## Development

```bash
npm run lint
npm run test:unit
npm run build
```

Run a single test file:

```bash
npm run test:single test/unit/preflight.spec.ts
```

## Appium metadata

Defined in `package.json`:

- `appium.pluginName`: `soluna-ext`
- `appium.mainClass`: `SolunaExtPlugin`
