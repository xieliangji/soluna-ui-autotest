package com.ugreen.iot.soluna.autotest.appium.driver

import com.ugreen.iot.soluna.autotest.core.model.LocatorDefinition
import io.appium.java_client.AppiumClientConfig
import io.appium.java_client.AppiumBy
import io.appium.java_client.AppiumDriver
import io.appium.java_client.CommandExecutionHelper
import io.appium.java_client.MobileCommand
import io.appium.java_client.ios.IOSStartScreenRecordingOptions
import org.openqa.selenium.By
import org.openqa.selenium.MutableCapabilities
import org.openqa.selenium.NoSuchElementException
import org.openqa.selenium.OutputType
import org.openqa.selenium.WebElement
import org.openqa.selenium.interactions.Pause
import org.openqa.selenium.interactions.PointerInput
import org.openqa.selenium.interactions.Sequence
import org.openqa.selenium.support.ui.WebDriverWait
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.math.roundToInt

class AppiumJavaClientWebDriverAdapter(
    private val driverFactory: DriverFactory = DefaultAppiumDriverFactory,
) : WebDriverAdapter {
    private val sessions = ConcurrentHashMap<String, AppiumDriver>()
    private val sessionViews = ConcurrentHashMap<String, DriverSession>()
    private val sessionImplicitWaits = ConcurrentHashMap<String, Duration>()
    private val sessionReadTimeouts = ConcurrentHashMap<String, Duration>()
    private val sessionWindowSizes = ConcurrentHashMap<String, org.openqa.selenium.Dimension>()
    private val androidScreenRecordings = ConcurrentHashMap<String, AndroidScreenRecordingProcess>()

    override fun startSession(request: StartSessionRequest): DriverSession {
        val driver = driverFactory.create(request)
        val implicitWait = request.implicitWaitDuration()
        driver.manage().timeouts().implicitlyWait(implicitWait)
        val sessionId = driver.sessionId.toString()
        val session = DriverSession(
            sessionId = sessionId,
            serverUrl = request.serverUrl,
            capabilities = request.capabilities,
        )
        sessions[sessionId] = driver
        sessionViews[sessionId] = session
        sessionImplicitWaits[sessionId] = implicitWait
        sessionReadTimeouts[sessionId] = Duration.ofMillis(request.readTimeoutMs)
        return session
    }

    override fun getSession(sessionId: String): DriverSession? {
        return sessionViews[sessionId]
    }

    override fun stopSession(sessionId: String) {
        androidScreenRecordings.remove(sessionId)?.stop()
        val driver = sessions.remove(sessionId) ?: return
        val readTimeout = sessionReadTimeouts[sessionId] ?: Duration.ofSeconds(30)
        sessionViews.remove(sessionId)
        sessionImplicitWaits.remove(sessionId)
        sessionReadTimeouts.remove(sessionId)
        sessionWindowSizes.remove(sessionId)
        runCommandWithTimeout(
            sessionId = sessionId,
            commandName = "quit",
            timeoutOverride = readTimeout.coerceAtMost(Duration.ofMillis(5_000)),
        ) {
            driver.quit()
        }
    }

    override fun findElement(
        sessionId: String,
        locator: LocatorDefinition,
        wait: DriverWaitOptions?,
    ): DriverElement {
        val driver = requireDriver(sessionId)
        findViewportVisibleElement(sessionId, driver, locator, wait)
        val elementId = "$sessionId:${UUID.randomUUID()}"
        return DriverElement(
            elementId = elementId,
            locator = locator,
            wait = wait,
        )
    }

    override fun tap(
        sessionId: String,
        element: DriverElement,
        xRatio: Double,
        yRatio: Double,
    ) {
        require(xRatio in 0.0..1.0) { "xRatio must be between 0.0 and 1.0" }
        require(yRatio in 0.0..1.0) { "yRatio must be between 0.0 and 1.0" }
        val driver = requireDriver(sessionId)
        val webElement = resolveElement(sessionId, element)
        val visibleRect = webElement.visibleRect(sessionId, driver, clipKeyboard = true)
        val clickPoint = visibleRect.pointAt(xRatio, yRatio)
        driver.performTap(clickPoint.x, clickPoint.y)
    }

    override fun tapViewport(
        sessionId: String,
        xRatio: Double,
        yRatio: Double,
    ) {
        require(xRatio in 0.0..1.0) { "xRatio must be between 0.0 and 1.0" }
        require(yRatio in 0.0..1.0) { "yRatio must be between 0.0 and 1.0" }
        val driver = requireDriver(sessionId)
        val size = driver.manage().window().size
        val x = (size.width * xRatio).roundToInt().coerceIn(0, size.width - 1)
        val y = (size.height * yRatio).roundToInt().coerceIn(0, size.height - 1)
        driver.performTap(x, y)
    }

    private fun AppiumDriver.performTap(
        x: Int,
        y: Int,
    ) {
        val finger = PointerInput(PointerInput.Kind.TOUCH, "finger")
        val tap = Sequence(finger, 0).apply {
            addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), x, y))
            addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
            addAction(Pause(finger, Duration.ofMillis(50)))
            addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()))
        }
        perform(listOf(tap))
    }

    override fun inputText(
        sessionId: String,
        element: DriverElement,
        text: String,
        clearFirst: Boolean,
    ) {
        val webElement = resolveElement(sessionId, element)
        if (clearFirst) {
            runCommandWithTimeout(
                sessionId = sessionId,
                commandName = "clearElement",
                timeoutOverride = Duration.ofMillis(10_000),
            ) {
                webElement.clear()
            }
        }
        runCommandWithTimeout(
            sessionId = sessionId,
            commandName = "sendKeysToElement",
            timeoutOverride = Duration.ofMillis(20_000),
        ) {
            webElement.sendKeys(text)
        }
    }

    override fun getElementText(
        sessionId: String,
        element: DriverElement,
    ): String {
        return resolveElement(sessionId, element).text
    }

    override fun getElementAttribute(
        sessionId: String,
        element: DriverElement,
        name: String,
    ): String? {
        val webElement = resolveElement(sessionId, element)
        return if (name.equals("text", ignoreCase = true)) {
            webElement.text.takeIf { it.isNotBlank() } ?: webElement.getAttribute(name)
        } else {
            webElement.getAttribute(name)
        }
    }

    override fun getPageSource(sessionId: String): String {
        return runCommandWithTimeout(sessionId, "getPageSource") {
            requireDriver(sessionId).pageSource.orEmpty()
        }
    }

    override fun restartApp(
        sessionId: String,
        appId: String,
        wait: DriverWaitOptions?,
    ) {
        val driver = requireDriver(sessionId)
        driver.executeScript(
            "mobile: terminateApp",
            mapOf(
                "appId" to appId,
                "bundleId" to appId,
            ),
        )
        driver.executeScript(
            "mobile: activateApp",
            mapOf(
                "appId" to appId,
                "bundleId" to appId,
            ),
        )
        driver.waitForAppForeground(appId, wait ?: defaultAppForegroundWait)
    }

    override fun takeScreenshot(sessionId: String): ScreenshotData {
        val bytes = runCommandWithTimeout(sessionId, "screenshot") {
            requireDriver(sessionId).getScreenshotAs(OutputType.BYTES)
        }
        return ScreenshotData(bytes = bytes)
    }

    override fun startScreenRecording(
        sessionId: String,
        options: ScreenRecordingOptions,
    ) {
        val driver = requireDriver(sessionId)
        val limit = Duration.ofMillis(options.timeLimitMs.coerceAtLeast(1))
        when (sessionViews[sessionId].platformName()) {
            "android" -> {
                androidScreenRecordings.remove(sessionId)?.stop()
                androidScreenRecordings[sessionId] = AndroidScreenRecordingProcess.start(
                    sessionId = sessionId,
                    udid = sessionViews[sessionId]?.udid()
                        ?: error("Android screen recording requires appium:udid capability"),
                    timeLimit = limit,
                )
            }
            "ios" -> CommandExecutionHelper.execute<Any>(
                driver,
                MobileCommand.startRecordingScreenCommand(
                    IOSStartScreenRecordingOptions.startScreenRecordingOptions()
                        .withTimeLimit(limit)
                        .enableForcedRestart(),
                ),
            )
            else -> error("Screen recording is not supported for platform '${sessionViews[sessionId].platformName()}'")
        }
    }

    override fun stopScreenRecording(sessionId: String): ScreenRecordingData {
        androidScreenRecordings.remove(sessionId)?.let { recording ->
            return ScreenRecordingData(
                bytes = recording.stopAndRead(),
                contentType = "video/mp4",
            )
        }
        val driver = requireDriver(sessionId)
        val encoded = CommandExecutionHelper.execute<Any>(
            driver,
            MobileCommand.STOP_RECORDING_SCREEN,
        ).toString()
        return ScreenRecordingData(
            bytes = Base64.getDecoder().decode(encoded),
            contentType = "video/mp4",
        )
    }

    override fun isSessionHealthy(sessionId: String): Boolean {
        sessions[sessionId] ?: return false
        return runCatching {
            runCommandWithTimeout(
                sessionId = sessionId,
                commandName = "status",
                timeoutOverride = Duration.ofMillis(3_000),
            ) {
                requireDriver(sessionId).status
            }
            true
        }.getOrDefault(false)
    }

    private fun requireDriver(sessionId: String): AppiumDriver {
        return sessions[sessionId] ?: error("Appium session '$sessionId' is not active")
    }

    private fun resolveElement(
        sessionId: String,
        element: DriverElement,
    ): WebElement {
        val locator = element.locator
            ?: error("Element '${element.elementId}' has no locator and cannot be resolved")
        return findViewportVisibleElement(
            sessionId = sessionId,
            driver = requireDriver(sessionId),
            locator = locator,
            wait = element.wait,
        )
    }

    private fun findViewportVisibleElement(
        sessionId: String,
        driver: AppiumDriver,
        locator: LocatorDefinition,
        wait: DriverWaitOptions?,
    ): WebElement {
        val by = locator.toBy()
        return if (wait == null) {
            driver.findViewportVisibleElement(sessionId, by, locator)
        } else {
            withImplicitWait(driver, Duration.ZERO, sessionImplicitWaits[sessionId] ?: Duration.ZERO) {
                WebDriverWait(driver, Duration.ofMillis(wait.timeoutMs)).apply {
                    wait.intervalMs?.let { pollingEvery(Duration.ofMillis(it)) }
                }.until { currentDriver ->
                    (currentDriver as AppiumDriver).findViewportVisibleElementOrNull(sessionId, by)
                }
            }
        }
    }

    private fun <T> withImplicitWait(
        driver: AppiumDriver,
        temporary: Duration,
        restore: Duration,
        block: () -> T,
    ): T {
        driver.manage().timeouts().implicitlyWait(temporary)
        return try {
            block()
        } finally {
            driver.manage().timeouts().implicitlyWait(restore)
        }
    }

    private fun AppiumDriver.findViewportVisibleElement(
        sessionId: String,
        by: By,
        locator: LocatorDefinition,
    ): WebElement {
        return findViewportVisibleElementOrNull(sessionId, by)
            ?: throw NoSuchElementException("No viewport-visible element found for locator ${locator.strategy}=${locator.value}")
    }

    private fun AppiumDriver.findViewportVisibleElementOrNull(
        sessionId: String,
        by: By,
    ): WebElement? {
        val elements = runCommandWithTimeout(
            sessionId = sessionId,
            commandName = "findElements",
            timeoutOverride = Duration.ofMillis(10_000),
        ) {
            findElements(by)
        }
        return elements.firstOrNull { element ->
            element.isDisplayedInViewport(sessionId, this)
        }
    }

    private fun AppiumDriver.waitForAppForeground(
        appId: String,
        wait: DriverWaitOptions,
    ) {
        WebDriverWait(this, Duration.ofMillis(wait.timeoutMs)).apply {
            wait.intervalMs?.let { pollingEvery(Duration.ofMillis(it)) }
        }.until { currentDriver ->
            (currentDriver as AppiumDriver).isAppForeground(appId)
        }
    }

    private fun AppiumDriver.isAppForeground(appId: String): Boolean {
        val state = executeScript(
            "mobile: queryAppState",
            mapOf(
                "appId" to appId,
                "bundleId" to appId,
            ),
        )
        return state.isForegroundAppState()
    }

    private fun WebElement.isDisplayedInViewport(
        sessionId: String,
        driver: AppiumDriver,
    ): Boolean {
        return runCatching {
            runCommandWithTimeout(sessionId, "isElementDisplayed") { isDisplayed } &&
                visibleRect(sessionId, driver, clipKeyboard = false).hasArea()
        }.getOrDefault(false)
    }

    private fun WebElement.visibleRect(
        sessionId: String,
        driver: AppiumDriver,
        clipKeyboard: Boolean,
    ): ViewportRect {
        val elementRect = runCommandWithTimeout(
            sessionId = sessionId,
            commandName = "getElementRect",
            timeoutOverride = Duration.ofMillis(3_000),
        ) {
            rect
        }
        val viewport = sessionWindowSizes[sessionId]
            ?: runCommandWithTimeout(
                sessionId = sessionId,
                commandName = "getWindowSize",
                timeoutOverride = Duration.ofMillis(3_000),
            ) {
                driver.manage().window().size
            }.also { sessionWindowSizes[sessionId] = it }
        val visibleBottom = if (clipKeyboard) {
            driver.visibleViewportBottom(sessionId, viewport.height)
        } else {
            viewport.height
        }
        val left = elementRect.x.coerceAtLeast(0)
        val top = elementRect.y.coerceAtLeast(0)
        val right = (elementRect.x + elementRect.width).coerceAtMost(viewport.width)
        val bottom = (elementRect.y + elementRect.height).coerceAtMost(visibleBottom)
        return ViewportRect(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
        )
    }

    private fun AppiumDriver.visibleViewportBottom(
        sessionId: String,
        defaultBottom: Int,
    ): Int {
        val keyboardTop = keyboardOverlayTop(sessionId) ?: return defaultBottom
        return keyboardTop.coerceIn(0, defaultBottom)
    }

    private fun AppiumDriver.keyboardOverlayTop(sessionId: String): Int? {
        return runCatching {
            val source = runCommandWithTimeout(
                sessionId = sessionId,
                commandName = "getPageSourceForKeyboardOverlay",
                timeoutOverride = Duration.ofMillis(1_000),
            ) {
                pageSource.orEmpty()
            }
            KeyboardOverlaySourceParser.topFromPageSource(source)
        }.getOrNull()
    }

    private fun <T> runCommandWithTimeout(
        sessionId: String,
        commandName: String,
        timeoutOverride: Duration? = null,
        block: () -> T,
    ): T {
        val timeout = timeoutOverride ?: sessionReadTimeouts[sessionId] ?: Duration.ofSeconds(30)
        val future = webdriverCommandExecutor.submit<T> { block() }
        return try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (err: TimeoutException) {
            future.cancel(true)
            throw WebDriverCommandTimeoutException(
                "WebDriver command '$commandName' timed out after ${timeout.toMillis()}ms for session '$sessionId'",
                err,
            )
        } catch (err: ExecutionException) {
            val cause = err.cause
            when (cause) {
                is RuntimeException -> throw cause
                is Error -> throw cause
                else -> throw RuntimeException(cause)
            }
        }
    }

    private companion object {
        val defaultAppForegroundWait = DriverWaitOptions(timeoutMs = 8_000, intervalMs = 200)
        val webdriverCommandExecutor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "soluna-webdriver-command-timeout").apply {
                isDaemon = true
            }
        }
    }
}

class WebDriverCommandTimeoutException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)

internal object KeyboardOverlaySourceParser {
    private val yAttribute = Regex("""\sy="(-?\d+)"""")
    private val heightAttribute = Regex("""\sheight="(\d+)"""")
    private val keyboardMarkers = listOf(
        "XCUIElementTypeKeyboard",
        "android.inputmethodservice.KeyboardView",
    )

    fun topFromPageSource(source: String): Int? {
        return source.lineSequence()
            .filter { line ->
                keyboardMarkers.any { marker -> line.contains(marker) } &&
                    line.contains("""visible="true"""")
            }
            .mapNotNull { line ->
                val y = yAttribute.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val height = heightAttribute.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (y != null && height != null && height > 0) y.coerceAtLeast(0) else null
            }
            .minOrNull()
    }
}

private fun Any?.isForegroundAppState(): Boolean {
    return when (this) {
        is Number -> toInt() == 4
        is String -> this == "4" || contains("RUNNING_IN_FOREGROUND", ignoreCase = true)
        else -> toString().contains("RUNNING_IN_FOREGROUND", ignoreCase = true)
    }
}

private fun DriverSession?.platformName(): String {
    val platformName = this?.capabilities?.entries
        ?.firstOrNull { (key, _) -> key.equals("platformName", ignoreCase = true) }
        ?.value
        ?.toString()
        ?.lowercase()
    return platformName.orEmpty()
}

private fun DriverSession.udid(): String? {
    return capabilities.entries
        .firstOrNull { (key, _) -> key.equals("appium:udid", ignoreCase = true) || key.equals("udid", ignoreCase = true) }
        ?.value
        ?.toString()
        ?.takeIf { it.isNotBlank() }
}

private class AndroidScreenRecordingProcess(
    private val adb: String,
    private val udid: String,
    private val remotePath: String,
    private val localPath: Path,
    private val process: Process,
) {
    fun stopAndRead(): ByteArray {
        stop()
        runAdb(adb, udid, listOf("pull", remotePath, localPath.toString()), timeoutMs = 15_000)
        runCatching {
            runAdb(adb, udid, listOf("shell", "rm", "-f", remotePath), timeoutMs = 5_000)
        }
        return Files.readAllBytes(localPath)
    }

    fun stop() {
        if (process.isAlive) {
            runCatching {
                runAdb(
                    adb = adb,
                    udid = udid,
                    args = listOf("shell", "sh", "-c", "pkill -2 screenrecord || kill -2 $(pidof screenrecord)"),
                    timeoutMs = 5_000,
                )
            }
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
            }
        } else {
            process.waitFor(1, TimeUnit.SECONDS)
        }
    }

    companion object {
        fun start(
            sessionId: String,
            udid: String,
            timeLimit: Duration,
        ): AndroidScreenRecordingProcess {
            val adb = resolveAdb()
            val remotePath = "/sdcard/soluna-recording-${sessionId.filter { it.isLetterOrDigit() }.take(16)}.mp4"
            val localPath = Files.createTempFile("soluna-android-recording-", ".mp4")
            val seconds = timeLimit.seconds.coerceAtLeast(1).coerceAtMost(180)
            val process = ProcessBuilder(
                adb,
                "-s",
                udid,
                "shell",
                "screenrecord",
                "--time-limit",
                seconds.toString(),
                remotePath,
            )
                .redirectErrorStream(true)
                .start()
            Thread.sleep(300)
            check(process.isAlive) {
                "Failed to start adb screenrecord: ${process.inputStream.bufferedReader().readText()}"
            }
            return AndroidScreenRecordingProcess(adb, udid, remotePath, localPath, process)
        }

        private fun resolveAdb(): String {
            val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
            val candidates = listOfNotNull(
                androidHome?.let { Path.of(it, "platform-tools", "adb").toString() },
                Path.of(System.getProperty("user.home"), "Library", "Android", "sdk", "platform-tools", "adb").toString(),
                "adb",
            )
            return candidates.firstOrNull { candidate ->
                runCatching {
                    val process = ProcessBuilder(candidate, "version")
                        .redirectErrorStream(true)
                        .start()
                    process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0
                }.getOrDefault(false)
            } ?: error("adb executable was not found for Android screen recording")
        }

        private fun runAdb(
            adb: String,
            udid: String,
            args: List<String>,
            timeoutMs: Long,
        ) {
            val process = ProcessBuilder(listOf(adb, "-s", udid) + args)
                .redirectErrorStream(true)
                .start()
            val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            val output = process.inputStream.bufferedReader().readText()
            check(completed && process.exitValue() == 0) {
                "adb ${args.joinToString(" ")} failed: $output"
            }
        }
    }
}

private data class ViewportRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    private val width: Int = right - left
    private val height: Int = bottom - top

    fun hasArea(): Boolean {
        return width > 0 && height > 0
    }

    fun pointAt(
        xRatio: Double,
        yRatio: Double,
    ): ViewportPoint {
        require(hasArea()) { "Element has no visible viewport area" }
        val x = (left + width * xRatio).roundToInt().coerceIn(left, right - 1)
        val y = (top + height * yRatio).roundToInt().coerceIn(top, bottom - 1)
        return ViewportPoint(x, y)
    }
}

private data class ViewportPoint(
    val x: Int,
    val y: Int,
)

interface DriverFactory {
    fun create(request: StartSessionRequest): AppiumDriver
}

object DefaultAppiumDriverFactory : DriverFactory {
    override fun create(request: StartSessionRequest): AppiumDriver {
        val clientConfig = AppiumClientConfig.defaultConfig()
            .baseUri(URI.create(request.serverUrl))
            .connectionTimeout(Duration.ofMillis(request.connectionTimeoutMs))
            .readTimeout(Duration.ofMillis(request.readTimeoutMs))
        return AppiumDriver(
            clientConfig,
            MutableCapabilities(request.capabilities),
        )
    }
}

private fun StartSessionRequest.implicitWaitDuration(): Duration {
    val timeouts = capabilities["timeouts"] as? Map<*, *> ?: return Duration.ZERO
    val implicit = timeouts["implicit"] ?: return Duration.ZERO
    val millis = when (implicit) {
        is Number -> implicit.toLong()
        is String -> implicit.toLongOrNull()
        else -> null
    } ?: return Duration.ZERO
    return Duration.ofMillis(millis)
}

internal fun LocatorDefinition.toBy(): By {
    return when (strategy.trim().lowercase()) {
        "id" -> AppiumBy.id(value)
        "accessibility", "accessibilityid", "accessibility id", "accessibility_id" -> AppiumBy.accessibilityId(value)
        "xpath" -> By.xpath(value)
        "class", "classname", "class name", "class_name" -> AppiumBy.className(value)
        "name" -> AppiumBy.name(value)
        "text" -> By.xpath(
            "//*[@text=${value.toXPathLiteral()} or @label=${value.toXPathLiteral()} or @name=${value.toXPathLiteral()} or @value=${value.toXPathLiteral()}]",
        )
        "predicate", "iospredicate", "ios predicate", "ios_predicate" -> AppiumBy.iOSNsPredicateString(value)
        "classchain", "class chain", "class_chain", "iosclasschain", "ios class chain" -> AppiumBy.iOSClassChain(value)
        "uiautomator", "androiduiautomator", "android uiautomator", "android_ui_automator" -> AppiumBy.androidUIAutomator(value)
        "image" -> AppiumBy.image(value)
        else -> error("Unsupported locator strategy '$strategy'")
    }
}

private fun String.toXPathLiteral(): String {
    if (!contains("'")) {
        return "'$this'"
    }
    if (!contains("\"")) {
        return "\"$this\""
    }
    return split("'").joinToString(
        separator = """, "'", """,
        prefix = "concat('",
        postfix = "')",
    )
}
