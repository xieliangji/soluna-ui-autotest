package com.ugreen.iot.soluna.autotest.appium.driver

import com.ugreen.iot.soluna.autotest.appium.server.AppiumServerConfig
import com.ugreen.iot.soluna.autotest.appium.server.AppiumServerHandle
import com.ugreen.iot.soluna.autotest.appium.server.AppiumServerManager
import com.ugreen.iot.soluna.autotest.core.model.LocatorDefinition
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class RecoveringWebDriverAdapter(
    private val delegate: WebDriverAdapter,
    private val appiumServerManager: AppiumServerManager,
    private val serverConfig: AppiumServerConfig,
    initialServerHandle: AppiumServerHandle,
    private val recoveryPolicy: AppiumSessionRecoveryPolicy = AppiumSessionRecoveryPolicy(),
) : WebDriverAdapter {
    @Volatile
    private var currentServerHandle: AppiumServerHandle = initialServerHandle

    private val sessions = ConcurrentHashMap<String, RecoveringSession>()
    private val elements = ConcurrentHashMap<String, RecoveringElement>()

    fun currentServerHandle(): AppiumServerHandle {
        return currentServerHandle
    }

    override fun startSession(request: StartSessionRequest): DriverSession {
        val logicalSessionId = "logical-${UUID.randomUUID()}"
        val normalizedRequest = request.copy(serverUrl = currentServerHandle.url)
        val physicalSession = delegate.startSession(normalizedRequest)
        sessions[logicalSessionId] = RecoveringSession(
            logicalSessionId = logicalSessionId,
            originalRequest = normalizedRequest,
            physicalSessionId = physicalSession.sessionId,
        )
        return DriverSession(
            sessionId = logicalSessionId,
            serverUrl = currentServerHandle.url,
            capabilities = physicalSession.capabilities,
        )
    }

    override fun getSession(sessionId: String): DriverSession? {
        val session = sessions[sessionId] ?: return null
        return DriverSession(
            sessionId = session.logicalSessionId,
            serverUrl = currentServerHandle.url,
            capabilities = session.originalRequest.capabilities,
        )
    }

    override fun stopSession(sessionId: String) {
        val session = sessions.remove(sessionId) ?: return
        elements.entries.removeIf { it.value.logicalSessionId == sessionId }
        runCatching {
            delegate.stopSession(session.physicalSessionId)
        }
    }

    override fun findElement(
        sessionId: String,
        locator: LocatorDefinition,
        wait: DriverWaitOptions?,
    ): DriverElement {
        val session = requireSession(sessionId)
        withSessionRecovery(session) {
            delegate.findElement(session.physicalSessionId, locator, wait)
        }
        val logicalElementId = "$sessionId:${UUID.randomUUID()}"
        elements[logicalElementId] = RecoveringElement(
            logicalElementId = logicalElementId,
            logicalSessionId = sessionId,
            locator = locator,
            wait = wait,
        )
        return DriverElement(logicalElementId)
    }

    override fun tap(
        sessionId: String,
        element: DriverElement,
        xRatio: Double,
        yRatio: Double,
    ) {
        val session = requireSession(sessionId)
        val recoveringElement = requireElement(sessionId, element)
        withElementRecovery(session, recoveringElement) { physicalElement ->
            delegate.tap(session.physicalSessionId, physicalElement, xRatio, yRatio)
        }
    }

    override fun tapViewport(
        sessionId: String,
        xRatio: Double,
        yRatio: Double,
    ) {
        val session = requireSession(sessionId)
        withSessionRecovery(session) {
            delegate.tapViewport(session.physicalSessionId, xRatio, yRatio)
        }
    }

    override fun inputText(
        sessionId: String,
        element: DriverElement,
        text: String,
        clearFirst: Boolean,
    ) {
        val session = requireSession(sessionId)
        val recoveringElement = requireElement(sessionId, element)
        withElementRecovery(session, recoveringElement) { physicalElement ->
            delegate.inputText(
                sessionId = session.physicalSessionId,
                element = physicalElement,
                text = text,
                clearFirst = clearFirst,
            )
        }
    }

    override fun getElementText(
        sessionId: String,
        element: DriverElement,
    ): String {
        val session = requireSession(sessionId)
        val recoveringElement = requireElement(sessionId, element)
        var text: String? = null
        withElementRecovery(session, recoveringElement) { physicalElement ->
            text = delegate.getElementText(session.physicalSessionId, physicalElement)
        }
        return text ?: ""
    }

    override fun getElementAttribute(
        sessionId: String,
        element: DriverElement,
        name: String,
    ): String? {
        val session = requireSession(sessionId)
        val recoveringElement = requireElement(sessionId, element)
        var attribute: String? = null
        withElementRecovery(session, recoveringElement) { physicalElement ->
            attribute = delegate.getElementAttribute(session.physicalSessionId, physicalElement, name)
        }
        return attribute
    }

    override fun getPageSource(sessionId: String): String {
        val session = requireSession(sessionId)
        return withSessionRecovery(session) {
            delegate.getPageSource(session.physicalSessionId)
        }
    }

    override fun restartApp(
        sessionId: String,
        appId: String,
        wait: DriverWaitOptions?,
    ) {
        val session = requireSession(sessionId)
        withSessionRecovery(session) {
            delegate.restartApp(
                sessionId = session.physicalSessionId,
                appId = appId,
                wait = wait,
            )
        }
    }

    override fun takeScreenshot(sessionId: String): ScreenshotData {
        val session = requireSession(sessionId)
        return withSessionRecovery(session) {
            delegate.takeScreenshot(session.physicalSessionId)
        }
    }

    override fun startScreenRecording(
        sessionId: String,
        options: ScreenRecordingOptions,
    ) {
        val session = requireSession(sessionId)
        withSessionRecovery(session) {
            delegate.startScreenRecording(session.physicalSessionId, options)
        }
    }

    override fun stopScreenRecording(sessionId: String): ScreenRecordingData {
        val session = requireSession(sessionId)
        return withSessionRecovery(session) {
            delegate.stopScreenRecording(session.physicalSessionId)
        }
    }

    override fun isSessionHealthy(sessionId: String): Boolean {
        val session = sessions[sessionId] ?: return false
        return isPhysicalSessionHealthy(session)
    }

    private fun <T> withSessionRecovery(
        session: RecoveringSession,
        operation: () -> T,
    ): T {
        ensureActivePhysicalSession(session)
        return try {
            operation()
        } catch (err: RuntimeException) {
            if (!shouldRecover(session, err)) {
                throw err
            }
            recoverSession(session, err)
            operation()
        }
    }

    private fun withElementRecovery(
        session: RecoveringSession,
        element: RecoveringElement,
        operation: (DriverElement) -> Unit,
    ) {
        ensureActivePhysicalSession(session)
        val physicalElement = delegate.findElement(
            sessionId = session.physicalSessionId,
            locator = element.locator,
            wait = element.wait,
        )
        try {
            operation(physicalElement)
        } catch (err: RuntimeException) {
            if (shouldRecover(session, err)) {
                recoverSession(session, err)
            } else if (!isStaleElementReference(err)) {
                throw err
            }
            val refreshedElement = delegate.findElement(
                sessionId = session.physicalSessionId,
                locator = element.locator,
                wait = element.wait,
            )
            operation(refreshedElement)
        }
    }

    private fun isStaleElementReference(err: RuntimeException): Boolean {
        return err::class.simpleName == "StaleElementReferenceException" ||
            err.message?.contains("staleelementreference", ignoreCase = true) == true ||
            err.message?.contains("stale element reference", ignoreCase = true) == true ||
            err.message?.contains("not linked to the same object", ignoreCase = true) == true
    }

    private fun ensureActivePhysicalSession(session: RecoveringSession) {
        if (!isPhysicalSessionHealthy(session)) {
            recoverSession(session, null)
        }
    }

    private fun isPhysicalSessionHealthy(session: RecoveringSession): Boolean {
        return appiumServerManager.isRunning(currentServerHandle) &&
            delegate.isSessionHealthy(session.physicalSessionId)
    }

    private fun shouldRecover(session: RecoveringSession): Boolean {
        return !appiumServerManager.isRunning(currentServerHandle) ||
            !delegate.isSessionHealthy(session.physicalSessionId)
    }

    private fun shouldRecover(
        session: RecoveringSession,
        cause: RuntimeException,
    ): Boolean {
        return isWebDriverCommandTimeout(cause) || shouldRecover(session)
    }

    private fun isWebDriverCommandTimeout(err: RuntimeException): Boolean {
        return err is WebDriverCommandTimeoutException ||
            err.message?.contains("WebDriver command", ignoreCase = true) == true &&
            err.message?.contains("timed out", ignoreCase = true) == true
    }

    private fun recoverSession(
        session: RecoveringSession,
        cause: RuntimeException?,
    ) {
        if (session.recoveryCount >= recoveryPolicy.maxRecoveriesPerSession) {
            throw AppiumSessionRecoveryException(
                "Appium session '${session.logicalSessionId}' exceeded max recoveries ${recoveryPolicy.maxRecoveriesPerSession}",
                cause,
            )
        }

        session.recoveryCount += 1
        runCatching {
            delegate.stopSession(session.physicalSessionId)
        }
        if (currentServerHandle.managed) {
            runCatching {
                appiumServerManager.stop(currentServerHandle)
            }
        }

        currentServerHandle = appiumServerManager.ensureRunning(serverConfig)
        val physicalSession = delegate.startSession(
            session.originalRequest.copy(serverUrl = currentServerHandle.url),
        )
        session.physicalSessionId = physicalSession.sessionId
    }

    private fun requireSession(sessionId: String): RecoveringSession {
        return sessions[sessionId] ?: error("Recovering Appium session '$sessionId' is not active")
    }

    private fun requireElement(
        sessionId: String,
        element: DriverElement,
    ): RecoveringElement {
        val recoveringElement = elements[element.elementId]
            ?: error("Recovering element '${element.elementId}' is not cached")
        require(recoveringElement.logicalSessionId == sessionId) {
            "Element '${element.elementId}' does not belong to session '$sessionId'"
        }
        return recoveringElement
    }
}

data class AppiumSessionRecoveryPolicy(
    val maxRecoveriesPerSession: Int = 3,
) {
    init {
        require(maxRecoveriesPerSession >= 0) { "maxRecoveriesPerSession must be greater than or equal to 0" }
    }
}

class AppiumSessionRecoveryException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

private data class RecoveringSession(
    val logicalSessionId: String,
    val originalRequest: StartSessionRequest,
    var physicalSessionId: String,
    var recoveryCount: Int = 0,
)

private data class RecoveringElement(
    val logicalElementId: String,
    val logicalSessionId: String,
    val locator: LocatorDefinition,
    val wait: DriverWaitOptions?,
)
