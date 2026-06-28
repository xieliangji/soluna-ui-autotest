package io.soluna.ui.autotest.appium.driver

import io.soluna.ui.autotest.core.model.LocatorDefinition

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
        xRatio: Double = 0.5,
        yRatio: Double = 0.5,
    )

    fun tapViewport(
        sessionId: String,
        xRatio: Double,
        yRatio: Double,
    ) {
        error("tapViewport is not supported by this WebDriverAdapter")
    }

    fun longPress(
        sessionId: String,
        element: DriverElement,
        durationMs: Long = 1000,
        xRatio: Double = 0.5,
        yRatio: Double = 0.5,
    ) {
        error("longPress is not supported by this WebDriverAdapter")
    }

    fun longPressViewport(
        sessionId: String,
        durationMs: Long = 1000,
        xRatio: Double,
        yRatio: Double,
    ) {
        error("longPressViewport is not supported by this WebDriverAdapter")
    }

    fun swipe(
        sessionId: String,
        element: DriverElement,
        durationMs: Long = 500,
        startXRatio: Double,
        startYRatio: Double,
        endXRatio: Double,
        endYRatio: Double,
    ) {
        error("swipe is not supported by this WebDriverAdapter")
    }

    fun swipeViewport(
        sessionId: String,
        durationMs: Long = 500,
        startXRatio: Double,
        startYRatio: Double,
        endXRatio: Double,
        endYRatio: Double,
    ) {
        error("swipeViewport is not supported by this WebDriverAdapter")
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

    fun getElementRect(
        sessionId: String,
        element: DriverElement,
    ): ElementRect {
        error("getElementRect is not supported by this WebDriverAdapter")
    }

    fun getPageSource(sessionId: String): String {
        error("getPageSource is not supported by this WebDriverAdapter")
    }

    fun restartApp(
        sessionId: String,
        appId: String,
        wait: DriverWaitOptions? = null,
    ) {
        error("restartApp is not supported by this WebDriverAdapter")
    }

    fun clearAppData(
        sessionId: String,
        appId: String,
        wait: DriverWaitOptions? = null,
    ) {
        error("clearAppData is not supported by this WebDriverAdapter")
    }

    fun takeScreenshot(sessionId: String): ScreenshotData

    fun takeElementScreenshot(
        sessionId: String,
        element: DriverElement,
    ): ScreenshotData {
        error("takeElementScreenshot is not supported by this WebDriverAdapter")
    }

    fun startScreenRecording(
        sessionId: String,
        options: ScreenRecordingOptions = ScreenRecordingOptions(),
    ) {
        error("startScreenRecording is not supported by this WebDriverAdapter")
    }

    fun stopScreenRecording(sessionId: String): ScreenRecordingData {
        error("stopScreenRecording is not supported by this WebDriverAdapter")
    }

    fun isSessionHealthy(sessionId: String): Boolean
}

data class StartSessionRequest(
    val serverUrl: String,
    val capabilities: Map<String, Any?>,
    val connectionTimeoutMs: Long = 10_000,
    val readTimeoutMs: Long = 30_000,
)

data class DriverSession(
    val sessionId: String,
    val serverUrl: String,
    val capabilities: Map<String, Any?>,
)

data class DriverElement(
    val elementId: String,
    val locator: LocatorDefinition? = null,
    val wait: DriverWaitOptions? = null,
)

data class ElementRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val viewportWidth: Int,
    val viewportHeight: Int,
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

data class ScreenRecordingOptions(
    val timeLimitMs: Long = 10_000,
)

data class ScreenRecordingData(
    val bytes: ByteArray,
    val contentType: String = "video/mp4",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScreenRecordingData

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
