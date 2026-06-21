package com.soluna.ui.autotest.cli

import com.soluna.ui.autotest.appium.action.KtVisualTemplateMatcher
import com.soluna.ui.autotest.appium.driver.AppiumJavaClientWebDriverAdapter
import com.soluna.ui.autotest.appium.driver.AppiumSessionRequestFactory
import com.soluna.ui.autotest.appium.driver.DriverSession
import com.soluna.ui.autotest.appium.driver.WebDriverAdapter
import com.soluna.ui.autotest.appium.server.AppiumServerHandle
import com.soluna.ui.autotest.appium.server.AppiumServerManager
import com.soluna.ui.autotest.appium.server.LocalProcessAppiumServerManager
import com.soluna.ui.autotest.appium.wda.LocalGoIosWdaManager
import com.soluna.ui.autotest.appium.wda.SolunaExtWdaBundleResolver
import com.soluna.ui.autotest.appium.wda.WdaBundleResolveRequest
import com.soluna.ui.autotest.appium.wda.WdaBundleResolver
import com.soluna.ui.autotest.appium.wda.WdaHandle
import com.soluna.ui.autotest.appium.wda.WdaManager
import com.soluna.ui.autotest.config.DeviceConfigDefinition
import com.soluna.ui.autotest.config.DeviceConfigResolver
import com.soluna.ui.autotest.config.YamlDeviceConfigParser
import com.soluna.ui.autotest.config.toAppiumServerConfig
import com.soluna.ui.autotest.config.toWdaConfig
import com.soluna.ui.autotest.core.model.LocatorDefinition
import com.soluna.ui.autotest.core.model.PlanDefinition
import com.soluna.ui.autotest.dsl.DslParser
import com.soluna.ui.autotest.dsl.YamlPlanParser
import com.soluna.ktvisual.model.Region
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class AppiumDebugManager(
    private val planParser: DslParser<PlanDefinition> = YamlPlanParser(),
    private val deviceConfigParser: DslParser<DeviceConfigDefinition> = YamlDeviceConfigParser(),
    private val appiumServerManager: AppiumServerManager = LocalProcessAppiumServerManager(),
    private val wdaManager: WdaManager = LocalGoIosWdaManager(),
    private val wdaBundleResolver: WdaBundleResolver = SolunaExtWdaBundleResolver(),
    private val deviceConfigResolver: DeviceConfigResolver = DeviceConfigResolver(),
    private val webDriverAdapter: WebDriverAdapter = AppiumJavaClientWebDriverAdapter(),
    private val sessionRequestFactory: AppiumSessionRequestFactory = AppiumSessionRequestFactory(),
) {
    fun run(request: AppiumDebugRequest): AppiumDebugResult {
        return withDebugSession(
            planPath = request.planPath,
            keepInfrastructure = request.keepInfrastructure,
        ) { active ->
            val output = executeAction(
                action = request.action,
                sessionId = active.session.sessionId,
            )
            AppiumDebugResult(
                action = request.action.name,
                serverUrl = active.server.url,
                wdaUrl = active.wda?.url,
                sessionId = active.session.sessionId,
                output = output,
            )
        }
    }

    fun runShell(
        request: AppiumDebugShellRequest,
        stdout: Appendable = System.out,
        readCommand: () -> String? = { readLine() },
    ): AppiumDebugShellResult {
        return withDebugSession(
            planPath = request.planPath,
            keepInfrastructure = request.keepInfrastructure,
        ) { active ->
            stdout.appendLine("Soluna debug shell started")
            stdout.appendLine("server: ${active.server.url}")
            active.wda?.url?.let { stdout.appendLine("wda: $it") }
            stdout.appendLine("session: ${active.session.sessionId}")
            stdout.appendLine("commands: restart-app [--app-id id], source [--out file], screenshot --out file, tap --x-ratio n --y-ratio n, tap-element --strategy s --locator v, swipe --start-x-ratio n --start-y-ratio n --end-x-ratio n --end-y-ratio n, swipe-element --strategy s --locator v --start-x-ratio n --start-y-ratio n --end-x-ratio n --end-y-ratio n, input --strategy s --locator v --text text, tap-template --template file [--roi x,y,w,h], help, exit")
            stdout.flushIfPossible()

            var executed = 0
            while (true) {
                stdout.append("soluna-debug> ")
                stdout.flushIfPossible()
                val line = readCommand() ?: break
                val trimmed = line.trim()
                if (trimmed.isBlank()) {
                    continue
                }
                when (trimmed) {
                    "exit", "quit" -> break
                    "help" -> {
                        stdout.appendLine("commands:")
                        stdout.appendLine("  restart-app [--app-id id]")
                        stdout.appendLine("  source [--out file]")
                        stdout.appendLine("  screenshot --out file")
                        stdout.appendLine("  tap --x-ratio <0..1> --y-ratio <0..1>")
                        stdout.appendLine("  tap-element --strategy <strategy> --locator <value> [--element-x-ratio <0..1>] [--element-y-ratio <0..1>]")
                        stdout.appendLine("  swipe --start-x-ratio <0..1> --start-y-ratio <0..1> --end-x-ratio <0..1> --end-y-ratio <0..1> [--duration-ms n]")
                        stdout.appendLine("  swipe-element --strategy <strategy> --locator <value> --start-x-ratio <0..1> --start-y-ratio <0..1> --end-x-ratio <0..1> --end-y-ratio <0..1> [--duration-ms n]")
                        stdout.appendLine("  input --strategy <strategy> --locator <value> --text <text> [--clear-first true|false]")
                        stdout.appendLine("  tap-template --template <png> [--roi x,y,w,h] [--threshold <0..1>] [--scales a,b,c]")
                        stdout.appendLine("  exit")
                        stdout.flushIfPossible()
                        continue
                    }
                }
                val action = runCatching { parseShellAction(trimmed) }.getOrElse { err ->
                    stdout.appendLine("error: ${err.message ?: err::class.simpleName}")
                    stdout.flushIfPossible()
                    continue
                }
                val output = runCatching { executeAction(action, active.session.sessionId, active.plan.app?.id) }.getOrElse { err ->
                    "error: ${err.message ?: err::class.simpleName}"
                }
                executed += 1
                stdout.appendLine(output)
                stdout.flushIfPossible()
            }
            AppiumDebugShellResult(
                serverUrl = active.server.url,
                wdaUrl = active.wda?.url,
                sessionId = active.session.sessionId,
                commandCount = executed,
            )
        }
    }

    private fun <T> withDebugSession(
        planPath: Path,
        keepInfrastructure: Boolean,
        block: (ActiveDebugSession) -> T,
    ): T {
        val resolvedPlanPath = planPath.toAbsolutePath().normalize()
        val plan = planParser.parse(Files.readString(resolvedPlanPath))
        val deviceConfigPath = resolveDeviceConfigPath(resolvedPlanPath, plan)
        val parsedDeviceConfig = deviceConfigParser.parse(Files.readString(deviceConfigPath))
        val serverConfig = parsedDeviceConfig.appium.server.toAppiumServerConfig()

        var serverHandle: AppiumServerHandle? = null
        var wdaHandle: WdaHandle? = null
        var session: DriverSession? = null
        return try {
            serverHandle = appiumServerManager.ensureRunning(serverConfig)
            val activeServer = serverHandle
            val resolvedDeviceConfig = deviceConfigResolver.resolve(
                config = parsedDeviceConfig,
                appiumServerUrl = activeServer.url,
            )
            val deviceConfig = resolveWdaBundleIfNeeded(resolvedDeviceConfig, activeServer.url)
            wdaHandle = ensureWdaIfNeeded(deviceConfig)
            session = webDriverAdapter.startSession(
                sessionRequestFactory.create(
                    serverUrl = activeServer.url,
                    deviceConfig = deviceConfig,
                    plan = plan,
                    webDriverAgentUrl = wdaHandle?.url,
                ),
            )
            block(
                ActiveDebugSession(
                    server = activeServer,
                    wda = wdaHandle,
                    plan = plan,
                    session = session,
                ),
            )
        } finally {
            session?.let { runCatching { webDriverAdapter.stopSession(it.sessionId) } }
            if (!keepInfrastructure) {
                wdaHandle?.takeIf { it.managed }?.let { runCatching { wdaManager.stop(it) } }
                serverHandle?.takeIf { it.managed }?.let { runCatching { appiumServerManager.stop(it) } }
            }
        }
    }

    private fun executeAction(
        action: AppiumDebugAction,
        sessionId: String,
        defaultAppId: String? = null,
    ): String {
        return when (action) {
            is AppiumDebugAction.RestartApp -> {
                val appId = action.appId ?: defaultAppId ?: error("restart-app requires --app-id when plan.app.id is not set")
                webDriverAdapter.restartApp(sessionId, appId)
                "restart-app: $appId"
            }
            is AppiumDebugAction.Source -> {
                val source = webDriverAdapter.getPageSource(sessionId)
                action.output?.let { path ->
                    Files.createDirectories(path.toAbsolutePath().normalize().parent)
                    Files.writeString(path.toAbsolutePath().normalize(), source)
                    "source: ${path.toAbsolutePath().normalize()}"
                } ?: source
            }
            is AppiumDebugAction.Screenshot -> {
                val screenshot = webDriverAdapter.takeScreenshot(sessionId)
                val output = action.output.toAbsolutePath().normalize()
                Files.createDirectories(output.parent)
                Files.write(output, screenshot.bytes)
                "screenshot: $output"
            }
            is AppiumDebugAction.Tap -> {
                webDriverAdapter.tapViewport(
                    sessionId = sessionId,
                    xRatio = action.xRatio,
                    yRatio = action.yRatio,
                )
                "tap: xRatio=${action.xRatio}, yRatio=${action.yRatio}"
            }
            is AppiumDebugAction.TapElement -> {
                val element = webDriverAdapter.findElement(
                    sessionId = sessionId,
                    locator = action.locator,
                )
                webDriverAdapter.tap(
                    sessionId = sessionId,
                    element = element,
                    xRatio = action.xRatio,
                    yRatio = action.yRatio,
                )
                "tap-element: ${action.locator.strategy}=${action.locator.value}, xRatio=${action.xRatio}, yRatio=${action.yRatio}"
            }
            is AppiumDebugAction.Swipe -> {
                webDriverAdapter.swipeViewport(
                    sessionId = sessionId,
                    durationMs = action.durationMs,
                    startXRatio = action.startXRatio,
                    startYRatio = action.startYRatio,
                    endXRatio = action.endXRatio,
                    endYRatio = action.endYRatio,
                )
                "swipe: start=${action.startXRatio},${action.startYRatio}, end=${action.endXRatio},${action.endYRatio}, durationMs=${action.durationMs}"
            }
            is AppiumDebugAction.SwipeElement -> {
                val element = webDriverAdapter.findElement(
                    sessionId = sessionId,
                    locator = action.locator,
                )
                webDriverAdapter.swipe(
                    sessionId = sessionId,
                    element = element,
                    durationMs = action.durationMs,
                    startXRatio = action.startXRatio,
                    startYRatio = action.startYRatio,
                    endXRatio = action.endXRatio,
                    endYRatio = action.endYRatio,
                )
                "swipe-element: ${action.locator.strategy}=${action.locator.value}, start=${action.startXRatio},${action.startYRatio}, end=${action.endXRatio},${action.endYRatio}, durationMs=${action.durationMs}"
            }
            is AppiumDebugAction.Input -> {
                val element = webDriverAdapter.findElement(
                    sessionId = sessionId,
                    locator = action.locator,
                )
                webDriverAdapter.inputText(
                    sessionId = sessionId,
                    element = element,
                    text = action.text,
                    clearFirst = action.clearFirst,
                )
                "input: ${action.locator.strategy}=${action.locator.value}, chars=${action.text.length}, clearFirst=${action.clearFirst}"
            }
            is AppiumDebugAction.TapTemplate -> tapTemplate(sessionId, action)
        }
    }

    private fun tapTemplate(
        sessionId: String,
        action: AppiumDebugAction.TapTemplate,
    ): String {
        val templatePath = action.template.toAbsolutePath().normalize()
        require(Files.isRegularFile(templatePath)) {
            "Template file does not exist: $templatePath"
        }
        val screenshot = webDriverAdapter.takeScreenshot(sessionId)
        val image = ImageIO.read(ByteArrayInputStream(screenshot.bytes))
            ?: error("Could not decode current screenshot")
        val match = KtVisualTemplateMatcher.find(
            screenshot = screenshot.bytes,
            template = Files.readAllBytes(templatePath),
            targetName = templatePath.fileName.toString(),
            threshold = action.threshold,
            scales = action.scales,
            roi = action.roi?.toRegion(image.width, image.height),
        ) ?: error("Template was not found: $templatePath")
        val targetX = match.bounds.x + match.bounds.width * action.targetXRatio
        val targetY = match.bounds.y + match.bounds.height * action.targetYRatio
        webDriverAdapter.tapViewport(
            sessionId = sessionId,
            xRatio = targetX / image.width,
            yRatio = targetY / image.height,
        )
        return "tap-template: score=${match.score}, bounds=${match.bounds}, xRatio=${targetX / image.width}, yRatio=${targetY / image.height}"
    }

    private fun parseShellAction(line: String): AppiumDebugAction {
        val args = tokenizeShellLine(line)
        val action = args.firstOrNull() ?: error("Debug command is required")
        var output: Path? = null
        var xRatio: Double? = null
        var yRatio: Double? = null
        var startXRatio: Double? = null
        var startYRatio: Double? = null
        var endXRatio: Double? = null
        var endYRatio: Double? = null
        var durationMs: Long = 500
        var strategy: String? = null
        var locatorValue: String? = null
        var text: String? = null
        var clearFirst = true
        var elementXRatio = 0.5
        var elementYRatio = 0.5
        var template: Path? = null
        var appId: String? = null
        var threshold = 0.72
        var scales = listOf(0.8, 1.0, 1.2, 1.4)
        var roi: DebugRoi? = null
        var targetXRatio = 0.5
        var targetYRatio = 0.5

        var index = 1
        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "--out" || arg == "--output" -> {
                    output = Path.of(args.valueAfter(index, arg))
                    index += 1
                }
                arg.startsWith("--out=") || arg.startsWith("--output=") -> output = Path.of(arg.substringAfter("="))
                arg == "--x-ratio" -> {
                    xRatio = parseRatio(args.valueAfter(index, arg), arg)
                    index += 1
                }
                arg.startsWith("--x-ratio=") -> xRatio = parseRatio(arg.substringAfter("="), "--x-ratio")
                arg == "--y-ratio" -> {
                    yRatio = parseRatio(args.valueAfter(index, arg), arg)
                    index += 1
                }
                arg.startsWith("--y-ratio=") -> yRatio = parseRatio(arg.substringAfter("="), "--y-ratio")
                arg == "--start-x-ratio" -> {
                    startXRatio = parseRatio(args.valueAfter(index, arg), arg)
                    index += 1
                }
                arg.startsWith("--start-x-ratio=") -> startXRatio = parseRatio(arg.substringAfter("="), "--start-x-ratio")
                arg == "--start-y-ratio" -> {
                    startYRatio = parseRatio(args.valueAfter(index, arg), arg)
                    index += 1
                }
                arg.startsWith("--start-y-ratio=") -> startYRatio = parseRatio(arg.substringAfter("="), "--start-y-ratio")
                arg == "--end-x-ratio" -> {
                    endXRatio = parseRatio(args.valueAfter(index, arg), arg)
                    index += 1
                }
                arg.startsWith("--end-x-ratio=") -> endXRatio = parseRatio(arg.substringAfter("="), "--end-x-ratio")
                arg == "--end-y-ratio" -> {
                    endYRatio = parseRatio(args.valueAfter(index, arg), arg)
                    index += 1
                }
                arg.startsWith("--end-y-ratio=") -> endYRatio = parseRatio(arg.substringAfter("="), "--end-y-ratio")
                arg == "--duration-ms" -> {
                    durationMs = parseNonNegativeLong(args.valueAfter(index, arg), arg)
                    index += 1
                }
                arg.startsWith("--duration-ms=") -> durationMs = parseNonNegativeLong(arg.substringAfter("="), "--duration-ms")
                arg == "--strategy" || arg == "--by" -> {
                    strategy = args.valueAfter(index, arg)
                    index += 1
                }
                arg.startsWith("--strategy=") || arg.startsWith("--by=") -> strategy = arg.substringAfter("=")
                arg == "--locator" -> {
                    locatorValue = args.valueAfter(index, arg)
                    index += 1
                }
                arg.startsWith("--locator=") -> locatorValue = arg.substringAfter("=")
                arg == "--text" -> {
                    text = args.valueAfter(index, arg)
                    index += 1
                }
                arg.startsWith("--text=") -> text = arg.substringAfter("=")
                arg == "--clear-first" -> {
                    clearFirst = parseBoolean(args.valueAfter(index, arg), arg)
                    index += 1
                }
                arg.startsWith("--clear-first=") -> clearFirst = parseBoolean(arg.substringAfter("="), "--clear-first")
                arg == "--element-x-ratio" -> {
                    elementXRatio = parseRatio(args.valueAfter(index, arg), arg)
                    index += 1
                }
                arg.startsWith("--element-x-ratio=") -> elementXRatio = parseRatio(arg.substringAfter("="), "--element-x-ratio")
                arg == "--element-y-ratio" -> {
                    elementYRatio = parseRatio(args.valueAfter(index, arg), arg)
                    index += 1
                }
                arg.startsWith("--element-y-ratio=") -> elementYRatio = parseRatio(arg.substringAfter("="), "--element-y-ratio")
                arg == "--template" -> {
                    template = Path.of(args.valueAfter(index, arg))
                    index += 1
                }
                arg.startsWith("--template=") -> template = Path.of(arg.substringAfter("="))
                arg == "--app-id" -> {
                    appId = args.valueAfter(index, arg)
                    index += 1
                }
                arg.startsWith("--app-id=") -> appId = arg.substringAfter("=")
                arg == "--threshold" -> {
                    threshold = parseRatio(args.valueAfter(index, arg), arg)
                    index += 1
                }
                arg.startsWith("--threshold=") -> threshold = parseRatio(arg.substringAfter("="), "--threshold")
                arg == "--scales" -> {
                    scales = parseScales(args.valueAfter(index, arg))
                    index += 1
                }
                arg.startsWith("--scales=") -> scales = parseScales(arg.substringAfter("="))
                arg == "--roi" -> {
                    roi = parseRoi(args.valueAfter(index, arg))
                    index += 1
                }
                arg.startsWith("--roi=") -> roi = parseRoi(arg.substringAfter("="))
                arg == "--target-x-ratio" -> {
                    targetXRatio = parseRatio(args.valueAfter(index, arg), arg)
                    index += 1
                }
                arg.startsWith("--target-x-ratio=") -> targetXRatio = parseRatio(arg.substringAfter("="), "--target-x-ratio")
                arg == "--target-y-ratio" -> {
                    targetYRatio = parseRatio(args.valueAfter(index, arg), arg)
                    index += 1
                }
                arg.startsWith("--target-y-ratio=") -> targetYRatio = parseRatio(arg.substringAfter("="), "--target-y-ratio")
                !arg.startsWith("-") && output == null && action in setOf("source", "screenshot") -> output = Path.of(arg)
                !arg.startsWith("-") && template == null && action in setOf("tap-template", "tapTemplate") -> template = Path.of(arg)
                else -> error("Unknown debug shell argument '$arg'")
            }
            index += 1
        }

        return when (action) {
            "restart-app", "restartApp" -> AppiumDebugAction.RestartApp(appId = appId)
            "source" -> AppiumDebugAction.Source(output = output)
            "screenshot" -> AppiumDebugAction.Screenshot(
                output = output ?: error("screenshot requires --out <file>"),
            )
            "tap" -> AppiumDebugAction.Tap(
                xRatio = xRatio ?: error("tap requires --x-ratio"),
                yRatio = yRatio ?: error("tap requires --y-ratio"),
            )
            "swipe" -> AppiumDebugAction.Swipe(
                startXRatio = startXRatio ?: error("swipe requires --start-x-ratio"),
                startYRatio = startYRatio ?: error("swipe requires --start-y-ratio"),
                endXRatio = endXRatio ?: error("swipe requires --end-x-ratio"),
                endYRatio = endYRatio ?: error("swipe requires --end-y-ratio"),
                durationMs = durationMs,
            )
            "tap-element", "tapElement" -> AppiumDebugAction.TapElement(
                locator = LocatorDefinition(
                    strategy = strategy ?: error("tap-element requires --strategy"),
                    value = locatorValue ?: error("tap-element requires --locator"),
                ),
                xRatio = elementXRatio,
                yRatio = elementYRatio,
            )
            "swipe-element", "swipeElement" -> AppiumDebugAction.SwipeElement(
                locator = LocatorDefinition(
                    strategy = strategy ?: error("swipe-element requires --strategy"),
                    value = locatorValue ?: error("swipe-element requires --locator"),
                ),
                startXRatio = startXRatio ?: error("swipe-element requires --start-x-ratio"),
                startYRatio = startYRatio ?: error("swipe-element requires --start-y-ratio"),
                endXRatio = endXRatio ?: error("swipe-element requires --end-x-ratio"),
                endYRatio = endYRatio ?: error("swipe-element requires --end-y-ratio"),
                durationMs = durationMs,
            )
            "input" -> AppiumDebugAction.Input(
                locator = LocatorDefinition(
                    strategy = strategy ?: error("input requires --strategy"),
                    value = locatorValue ?: error("input requires --locator"),
                ),
                text = text ?: error("input requires --text"),
                clearFirst = clearFirst,
            )
            "tap-template", "tapTemplate" -> AppiumDebugAction.TapTemplate(
                template = template ?: error("tap-template requires --template <file>"),
                threshold = threshold,
                scales = scales,
                roi = roi,
                targetXRatio = targetXRatio,
                targetYRatio = targetYRatio,
            )
            else -> error("Unsupported debug shell command '$action'")
        }
    }

    private fun tokenizeShellLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaping = false
        for (char in line) {
            when {
                escaping -> {
                    current.append(char)
                    escaping = false
                }
                char == '\\' -> escaping = true
                quote != null && char == quote -> quote = null
                quote != null -> current.append(char)
                char == '\'' || char == '"' -> quote = char
                char.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        tokens += current.toString()
                        current.clear()
                    }
                }
                else -> current.append(char)
            }
        }
        require(quote == null) {
            "Unclosed quote in debug shell command"
        }
        if (escaping) {
            current.append('\\')
        }
        if (current.isNotEmpty()) {
            tokens += current.toString()
        }
        return tokens
    }

    private fun parseRatio(
        raw: String,
        option: String,
    ): Double {
        val value = raw.toDoubleOrNull()
            ?: error("$option requires a number")
        require(value in 0.0..1.0) {
            "$option must be between 0 and 1"
        }
        return value
    }

    private fun parseNonNegativeLong(
        raw: String,
        option: String,
    ): Long {
        val value = raw.toLongOrNull()
            ?: error("$option requires an integer")
        require(value >= 0) {
            "$option must be >= 0"
        }
        return value
    }

    private fun parseScales(raw: String): List<Double> {
        val values = raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { value -> value.toDoubleOrNull() ?: error("--scales requires comma-separated numbers") }
        require(values.isNotEmpty() && values.all { it > 0.0 }) {
            "--scales requires positive comma-separated numbers"
        }
        return values
    }

    private fun parseRoi(raw: String): DebugRoi {
        val values = raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { value -> value.toDoubleOrNull() ?: error("--roi requires x,y,width,height numbers") }
        require(values.size == 4) {
            "--roi requires x,y,width,height"
        }
        return DebugRoi(values[0], values[1], values[2], values[3])
    }

    private fun parseBoolean(
        raw: String,
        option: String,
    ): Boolean {
        return when (raw.lowercase()) {
            "true", "yes", "1" -> true
            "false", "no", "0" -> false
            else -> error("$option requires true or false")
        }
    }

    private fun List<String>.valueAfter(
        index: Int,
        option: String,
    ): String {
        return getOrNull(index + 1)
            ?.takeUnless { it.startsWith("-") }
            ?: error("$option requires a value")
    }

    private fun resolveDeviceConfigPath(
        planPath: Path,
        plan: PlanDefinition,
    ): Path {
        val ref = plan.deviceConfig ?: error("Plan '${plan.id}' does not define deviceConfig")
        return resolveReferencePath(planPath, ref)
    }

    private fun resolveReferencePath(
        ownerPath: Path,
        reference: String,
    ): Path {
        val path = Path.of(reference)
        return if (path.isAbsolute) {
            path.normalize()
        } else {
            (ownerPath.parent ?: Path.of(".").toAbsolutePath().normalize()).resolve(path).normalize()
        }
    }

    private fun resolveWdaBundleIfNeeded(
        deviceConfig: DeviceConfigDefinition,
        appiumServerUrl: String,
    ): DeviceConfigDefinition {
        if (deviceConfig.device.platform?.equals("ios", ignoreCase = true) != true) {
            return deviceConfig
        }
        val wda = deviceConfig.ios.wda
        if (!wda.enabled || !wda.managed) {
            return deviceConfig
        }
        val resolution = wdaBundleResolver.resolve(
            WdaBundleResolveRequest(
                appiumServerUrl = appiumServerUrl,
                udid = deviceConfig.device.udid,
                bundleId = wda.bundleId,
                testRunnerBundleId = wda.testRunnerBundleId,
                xctestConfig = wda.xctestConfig,
            ),
        )
        return deviceConfig.copy(
            ios = deviceConfig.ios.copy(
                wda = wda.copy(
                    bundleId = resolution.bundleId,
                    testRunnerBundleId = resolution.testRunnerBundleId,
                    xctestConfig = resolution.xctestConfig,
                ),
            ),
        )
    }

    private fun ensureWdaIfNeeded(deviceConfig: DeviceConfigDefinition): WdaHandle? {
        if (deviceConfig.device.platform?.equals("ios", ignoreCase = true) != true) {
            return null
        }
        if (!deviceConfig.ios.wda.enabled) {
            return null
        }
        return wdaManager.ensureRunning(deviceConfig.toWdaConfig())
    }
}

private data class ActiveDebugSession(
    val server: AppiumServerHandle,
    val wda: WdaHandle?,
    val plan: PlanDefinition,
    val session: DriverSession,
)

private fun Appendable.flushIfPossible() {
    if (this is java.io.Flushable) {
        flush()
    }
}

data class AppiumDebugRequest(
    val planPath: Path,
    val action: AppiumDebugAction,
    val keepInfrastructure: Boolean = false,
)

data class AppiumDebugShellRequest(
    val planPath: Path,
    val keepInfrastructure: Boolean = false,
)

sealed class AppiumDebugAction(val name: String) {
    data class RestartApp(
        val appId: String? = null,
    ) : AppiumDebugAction("restart-app")

    data class Source(
        val output: Path?,
    ) : AppiumDebugAction("source")

    data class Screenshot(
        val output: Path,
    ) : AppiumDebugAction("screenshot")

    data class Tap(
        val xRatio: Double,
        val yRatio: Double,
    ) : AppiumDebugAction("tap")

    data class TapElement(
        val locator: LocatorDefinition,
        val xRatio: Double = 0.5,
        val yRatio: Double = 0.5,
    ) : AppiumDebugAction("tap-element")

    data class Swipe(
        val startXRatio: Double,
        val startYRatio: Double,
        val endXRatio: Double,
        val endYRatio: Double,
        val durationMs: Long = 500,
    ) : AppiumDebugAction("swipe")

    data class SwipeElement(
        val locator: LocatorDefinition,
        val startXRatio: Double,
        val startYRatio: Double,
        val endXRatio: Double,
        val endYRatio: Double,
        val durationMs: Long = 500,
    ) : AppiumDebugAction("swipe-element")

    data class Input(
        val locator: LocatorDefinition,
        val text: String,
        val clearFirst: Boolean = true,
    ) : AppiumDebugAction("input")

    data class TapTemplate(
        val template: Path,
        val threshold: Double = 0.72,
        val scales: List<Double> = listOf(0.8, 1.0, 1.2, 1.4),
        val roi: DebugRoi? = null,
        val targetXRatio: Double = 0.5,
        val targetYRatio: Double = 0.5,
    ) : AppiumDebugAction("tap-template")
}

data class DebugRoi(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
) {
    init {
        require(x in 0.0..1.0) { "roi.x must be between 0 and 1" }
        require(y in 0.0..1.0) { "roi.y must be between 0 and 1" }
        require(width > 0.0 && width <= 1.0) { "roi.width must be in (0, 1]" }
        require(height > 0.0 && height <= 1.0) { "roi.height must be in (0, 1]" }
        require(x + width <= 1.0) { "roi.x + roi.width must be <= 1" }
        require(y + height <= 1.0) { "roi.y + roi.height must be <= 1" }
    }

    fun toRegion(
        imageWidth: Int,
        imageHeight: Int,
    ): Region {
        return Region(
            x = (x * imageWidth).toInt().coerceIn(0, imageWidth - 1),
            y = (y * imageHeight).toInt().coerceIn(0, imageHeight - 1),
            width = (width * imageWidth).toInt().coerceAtLeast(1),
            height = (height * imageHeight).toInt().coerceAtLeast(1),
        )
    }
}

data class AppiumDebugResult(
    val action: String,
    val serverUrl: String,
    val wdaUrl: String?,
    val sessionId: String,
    val output: String,
)

data class AppiumDebugShellResult(
    val serverUrl: String,
    val wdaUrl: String?,
    val sessionId: String,
    val commandCount: Int,
)
