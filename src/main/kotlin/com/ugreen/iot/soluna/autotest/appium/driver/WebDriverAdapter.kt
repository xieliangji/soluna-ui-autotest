package com.ugreen.iot.soluna.autotest.appium.driver

import com.ugreen.iot.soluna.autotest.core.model.LocatorDefinition

interface WebDriverAdapter {
    fun startSession(request: StartSessionRequest): DriverSession

    fun getSession(sessionId: String): DriverSession?

    fun stopSession(sessionId: String)

    fun findElement(
        sessionId: String,
        locator: LocatorDefinition,
        wait: DriverWaitOptions? = null,
    ): DriverElement

    fun tap(
        sessionId: String,
        element: DriverElement,
    )

    fun tapViewport(
        sessionId: String,
        xRatio: Double,
        yRatio: Double,
    ) {
        error("tapViewport is not supported by this WebDriverAdapter")
    }

    fun inputText(
        sessionId: String,
        element: DriverElement,
        text: String,
        clearFirst: Boolean = true,
    )

    fun getElementText(
        sessionId: String,
        element: DriverElement,
    ): String {
        error("getElementText is not supported by this WebDriverAdapter")
    }

    fun getElementAttribute(
        sessionId: String,
        element: DriverElement,
        name: String,
    ): String? {
        error("getElementAttribute is not supported by this WebDriverAdapter")
    }

    fun getPageSource(sessionId: String): String {
        error("getPageSource is not supported by this WebDriverAdapter")
    }

    fun restartApp(
        sessionId: String,
        appId: String,
    ) {
        error("restartApp is not supported by this WebDriverAdapter")
    }

    fun takeScreenshot(sessionId: String): ScreenshotData

    fun isSessionHealthy(sessionId: String): Boolean
}

data class StartSessionRequest(
    val serverUrl: String,
    val capabilities: Map<String, Any?>,
)

data class DriverSession(
    val sessionId: String,
    val serverUrl: String,
    val capabilities: Map<String, Any?>,
)

data class DriverElement(
    val elementId: String,
)

data class DriverWaitOptions(
    val timeoutMs: Long,
    val intervalMs: Long? = null,
)

data class ScreenshotData(
    val bytes: ByteArray,
    val contentType: String = "image/png",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScreenshotData

        if (!bytes.contentEquals(other.bytes)) return false
        if (contentType != other.contentType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + contentType.hashCode()
        return result
    }
}
