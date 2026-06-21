package com.soluna.ui.autotest.appium.server

interface AppiumServerManager {
    fun ensureRunning(config: AppiumServerConfig): AppiumServerHandle

    fun stop(handle: AppiumServerHandle)

    fun isRunning(handle: AppiumServerHandle): Boolean
}

data class AppiumServerConfig(
    val managed: Boolean,
    val baseUrl: String? = null,
    val host: String? = null,
    val port: Int? = null,
    val usePlugins: List<String> = listOf("soluna-ext"),
    val ensureDrivers: List<String> = listOf("uiautomator2", "xcuitest"),
    val executable: String = "appium",
    val extraArgs: List<String> = emptyList(),
    val environment: Map<String, String> = emptyMap(),
    val startupTimeoutMs: Long = 30_000,
) {
    init {
        port?.let { require(it in 1..65535) { "port must be in 1..65535" } }
        require(startupTimeoutMs >= 0) { "startupTimeoutMs must be greater than or equal to 0" }
    }

    val url: String
        get() = baseUrl ?: "http://${host ?: DEFAULT_HOST}:${port ?: DEFAULT_PORT}"

    companion object {
        const val DEFAULT_HOST: String = "127.0.0.1"
        const val DEFAULT_PORT: Int = 4723
    }
}

data class AppiumServerHandle(
    val url: String,
    val managed: Boolean,
    val processId: Long? = null,
)
