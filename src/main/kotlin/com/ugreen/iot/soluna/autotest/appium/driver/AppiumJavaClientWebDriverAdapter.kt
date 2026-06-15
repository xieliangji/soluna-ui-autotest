package com.ugreen.iot.soluna.autotest.appium.driver

import com.ugreen.iot.soluna.autotest.core.model.LocatorDefinition
import io.appium.java_client.AppiumBy
import io.appium.java_client.AppiumDriver
import org.openqa.selenium.By
import org.openqa.selenium.MutableCapabilities
import org.openqa.selenium.OutputType
import org.openqa.selenium.WebElement
import org.openqa.selenium.interactions.Pause
import org.openqa.selenium.interactions.PointerInput
import org.openqa.selenium.interactions.Sequence
import org.openqa.selenium.support.ui.WebDriverWait
import java.net.URI
import java.time.Duration
import kotlin.math.roundToInt
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AppiumJavaClientWebDriverAdapter(
    private val driverFactory: DriverFactory = DefaultAppiumDriverFactory,
) : WebDriverAdapter {
    private val sessions = ConcurrentHashMap<String, AppiumDriver>()
    private val sessionViews = ConcurrentHashMap<String, DriverSession>()
    private val elements = ConcurrentHashMap<String, WebElement>()

    override fun startSession(request: StartSessionRequest): DriverSession {
        val driver = driverFactory.create(request)
        val sessionId = driver.sessionId.toString()
        val session = DriverSession(
            sessionId = sessionId,
            serverUrl = request.serverUrl,
            capabilities = request.capabilities,
        )
        sessions[sessionId] = driver
        sessionViews[sessionId] = session
        return session
    }

    override fun getSession(sessionId: String): DriverSession? {
        return sessionViews[sessionId]
    }

    override fun stopSession(sessionId: String) {
        val driver = sessions.remove(sessionId) ?: return
        sessionViews.remove(sessionId)
        elements.entries.removeIf { it.key.startsWith("$sessionId:") }
        driver.quit()
    }

    override fun findElement(
        sessionId: String,
        locator: LocatorDefinition,
        wait: DriverWaitOptions?,
    ): DriverElement {
        val driver = requireDriver(sessionId)
        val by = locator.toBy()
        val element = if (wait == null) {
            driver.findElement(by)
        } else {
            WebDriverWait(driver, Duration.ofMillis(wait.timeoutMs)).apply {
                wait.intervalMs?.let { pollingEvery(Duration.ofMillis(it)) }
            }.until { it.findElement(by) }
        }
        val elementId = "$sessionId:${UUID.randomUUID()}"
        elements[elementId] = element
        return DriverElement(elementId)
    }

    override fun tap(
        sessionId: String,
        element: DriverElement,
    ) {
        requireElement(sessionId, element).click()
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
        val finger = PointerInput(PointerInput.Kind.TOUCH, "finger")
        val tap = Sequence(finger, 0).apply {
            addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), x, y))
            addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
            addAction(Pause(finger, Duration.ofMillis(50)))
            addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()))
        }
        driver.perform(listOf(tap))
    }

    override fun inputText(
        sessionId: String,
        element: DriverElement,
        text: String,
        clearFirst: Boolean,
    ) {
        val webElement = requireElement(sessionId, element)
        if (clearFirst) {
            webElement.clear()
        }
        webElement.sendKeys(text)
    }

    override fun getElementText(
        sessionId: String,
        element: DriverElement,
    ): String {
        return requireElement(sessionId, element).text
    }

    override fun getElementAttribute(
        sessionId: String,
        element: DriverElement,
        name: String,
    ): String? {
        val webElement = requireElement(sessionId, element)
        return if (name.equals("text", ignoreCase = true)) {
            webElement.text.takeIf { it.isNotBlank() } ?: webElement.getAttribute(name)
        } else {
            webElement.getAttribute(name)
        }
    }

    override fun getPageSource(sessionId: String): String {
        return requireDriver(sessionId).pageSource.orEmpty()
    }

    override fun restartApp(
        sessionId: String,
        appId: String,
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
    }

    override fun takeScreenshot(sessionId: String): ScreenshotData {
        val bytes = requireDriver(sessionId).getScreenshotAs(OutputType.BYTES)
        return ScreenshotData(bytes = bytes)
    }

    override fun isSessionHealthy(sessionId: String): Boolean {
        val driver = sessions[sessionId] ?: return false
        return runCatching {
            driver.status
            true
        }.getOrDefault(false)
    }

    private fun requireDriver(sessionId: String): AppiumDriver {
        return sessions[sessionId] ?: error("Appium session '$sessionId' is not active")
    }

    private fun requireElement(
        sessionId: String,
        element: DriverElement,
    ): WebElement {
        requireDriver(sessionId)
        return elements[element.elementId] ?: error("Element '${element.elementId}' is not cached")
    }
}

interface DriverFactory {
    fun create(request: StartSessionRequest): AppiumDriver
}

object DefaultAppiumDriverFactory : DriverFactory {
    override fun create(request: StartSessionRequest): AppiumDriver {
        return AppiumDriver(
            URI.create(request.serverUrl).toURL(),
            MutableCapabilities(request.capabilities),
        )
    }
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
