package com.ugreen.iot.soluna.autotest.appium.wda

import java.net.URI
import java.net.ServerSocket
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

interface WdaManager {
    fun ensureRunning(config: WdaConfig): WdaHandle

    fun restart(
        handle: WdaHandle,
        config: WdaConfig,
    ): WdaHandle {
        stop(handle)
        return ensureRunning(config)
    }

    fun stop(handle: WdaHandle)

    fun isRunning(handle: WdaHandle): Boolean
}

data class WdaConfig(
    val udid: String,
    val osVersion: String?,
    val enabled: Boolean = true,
    val managed: Boolean = true,
    val url: String? = null,
    val host: String = "127.0.0.1",
    val hostPort: Int? = null,
    val devicePort: Int = 8100,
    val executable: String = "ios",
    val tunnelMode: String = "userspace",
    val tunnelInfoHost: String = "127.0.0.1",
    val tunnelInfoPort: Int? = null,
    val userspaceTunnelPort: Int? = null,
    val bundleId: String? = null,
    val testRunnerBundleId: String? = null,
    val xctestConfig: String? = null,
    val extraArgs: List<String> = emptyList(),
    val environment: Map<String, String> = emptyMap(),
    val startupTimeoutMs: Long = 60_000,
    val tunnelStartupDelayMs: Long = 2_000,
    val runwdaStartupDelayMs: Long = 2_000,
    val logDirectory: Path? = null,
) {
    init {
        hostPort?.let { require(it in 1..65535) { "hostPort must be in 1..65535" } }
        require(devicePort in 1..65535) { "devicePort must be in 1..65535" }
        require(tunnelMode in setOf("userspace", "system")) { "tunnelMode must be one of: userspace, system" }
        require(tunnelInfoHost.isNotBlank()) { "tunnelInfoHost must not be blank" }
        tunnelInfoPort?.let { require(it in 1..65535) { "tunnelInfoPort must be in 1..65535" } }
        userspaceTunnelPort?.let { require(it in 1..65535) { "userspaceTunnelPort must be in 1..65535" } }
        require(startupTimeoutMs >= 0) { "startupTimeoutMs must be greater than or equal to 0" }
        require(tunnelStartupDelayMs >= 0) { "tunnelStartupDelayMs must be greater than or equal to 0" }
        require(runwdaStartupDelayMs >= 0) { "runwdaStartupDelayMs must be greater than or equal to 0" }
        val runWdaIdentity = listOf(bundleId, testRunnerBundleId, xctestConfig)
        require(runWdaIdentity.all { it.isNullOrBlank() } || runWdaIdentity.all { !it.isNullOrBlank() }) {
            "bundleId, testRunnerBundleId and xctestConfig must be configured together for go-ios runwda"
        }
        if (!managed && enabled) {
            require(!url.isNullOrBlank()) { "url is required when WDA is unmanaged" }
        }
    }

    fun requiresTunnel(): Boolean {
        val major = osVersion?.substringBefore(".")?.toIntOrNull()
            ?: error("iOS device '$udid' must have osVersion resolved from soluna-ext before WDA startup")
        return major >= 17
    }
}

data class WdaHandle(
    val url: String,
    val udid: String,
    val managed: Boolean,
    val usesTunnel: Boolean,
    val hostPort: Int?,
    val devicePort: Int?,
    val tunnelInfoPort: Int? = null,
    val userspaceTunnelPort: Int? = null,
    val tunnelProcessId: Long? = null,
    val runwdaProcessId: Long? = null,
    val forwardProcessId: Long? = null,
)

class LocalGoIosWdaManager(
    private val processLauncher: WdaProcessLauncher = ProcessBuilderWdaProcessLauncher,
    private val statusProbe: WdaStatusProbe = JavaNetWdaStatusProbe(),
    private val portAllocator: WdaPortAllocator = ServerSocketWdaPortAllocator,
    private val probeIntervalMs: Long = 250,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
) : WdaManager {
    private val processes = ConcurrentHashMap<Long, WdaProcess>()

    override fun ensureRunning(config: WdaConfig): WdaHandle {
        if (!config.enabled) {
            error("WDA is disabled for device '${config.udid}'")
        }
        if (!config.managed) {
            val url = config.url ?: error("url is required when WDA is unmanaged")
            waitUntilReady(url, config.startupTimeoutMs, emptyList())
            return WdaHandle(
                url = url,
                udid = config.udid,
                managed = false,
                usesTunnel = false,
                hostPort = null,
                devicePort = null,
            )
        }

        val usesTunnel = config.requiresTunnel()
        val runtimeConfig = config.withRuntimePorts(usesTunnel)
        val hostPort = runtimeConfig.hostPort ?: error("WDA host port was not allocated")
        val url = "http://${runtimeConfig.host}:$hostPort"
        val startedProcesses = mutableListOf<WdaProcess>()

        try {
            val tunnel = if (usesTunnel) {
                launch(buildTunnelCommand(runtimeConfig), runtimeConfig.environment, runtimeConfig.logDirectory?.resolve("go-ios-tunnel.log")).also { process ->
                    startedProcesses += process
                    waitForAlive(process, "go-ios tunnel")
                    sleeper(runtimeConfig.tunnelStartupDelayMs)
                }
            } else {
                null
            }

            val runwda = launch(buildRunWdaCommand(runtimeConfig), runtimeConfig.environment, runtimeConfig.logDirectory?.resolve("go-ios-runwda.log")).also { process ->
                startedProcesses += process
                waitForAlive(process, "go-ios runwda")
            }
            sleeper(runtimeConfig.runwdaStartupDelayMs)

            val forward = launch(buildForwardCommand(runtimeConfig, hostPort), runtimeConfig.environment, runtimeConfig.logDirectory?.resolve("go-ios-forward.log")).also { process ->
                startedProcesses += process
                waitForAlive(process, "go-ios forward")
            }

            waitUntilReady(url, runtimeConfig.startupTimeoutMs, startedProcesses)

            return WdaHandle(
                url = url,
                udid = runtimeConfig.udid,
                managed = true,
                usesTunnel = usesTunnel,
                hostPort = hostPort,
                devicePort = runtimeConfig.devicePort,
                tunnelInfoPort = runtimeConfig.tunnelInfoPort,
                userspaceTunnelPort = runtimeConfig.userspaceTunnelPort,
                tunnelProcessId = tunnel?.pid,
                runwdaProcessId = runwda.pid,
                forwardProcessId = forward.pid,
            )
        } catch (err: RuntimeException) {
            startedProcesses.asReversed().forEach { process ->
                stopProcess(process)
                processes.remove(process.pid)
            }
            throw err
        }
    }

    override fun stop(handle: WdaHandle) {
        if (!handle.managed) {
            return
        }
        listOf(handle.forwardProcessId, handle.runwdaProcessId, handle.tunnelProcessId)
            .mapNotNull { processId -> processId?.let { processes.remove(it) } }
            .forEach { process -> stopProcess(process) }
    }

    override fun isRunning(handle: WdaHandle): Boolean {
        if (!handle.managed) {
            return statusProbe.isReady(handle.url)
        }
        val processIds = listOfNotNull(
            handle.runwdaProcessId,
            handle.forwardProcessId,
            handle.tunnelProcessId.takeIf { handle.usesTunnel },
        )
        val processesAlive = processIds.all { processId ->
            processes[processId]?.isAlive() == true
        }
        return processesAlive && statusProbe.isReady(handle.url)
    }

    internal fun buildTunnelCommand(config: WdaConfig): List<String> {
        return buildList {
            add(config.executable)
            add("--udid=${config.udid}")
            addTunnelInfoArgs(config)
            add("tunnel")
            add("start")
            if (config.tunnelMode == "userspace") {
                add("--userspace")
            }
            config.userspaceTunnelPort?.let { add("--userspace-port=$it") }
        }
    }

    internal fun buildRunWdaCommand(config: WdaConfig): List<String> {
        return buildList {
            add(config.executable)
            add("--udid=${config.udid}")
            addTunnelInfoArgs(config)
            add("runwda")
            config.bundleId?.let { add("--bundleid=$it") }
            config.testRunnerBundleId?.let { add("--testrunnerbundleid=$it") }
            config.xctestConfig?.let { add("--xctestconfig=$it") }
            addAll(config.extraArgs)
        }
    }

    internal fun buildForwardCommand(
        config: WdaConfig,
        hostPort: Int,
    ): List<String> {
        return listOf(
            config.executable,
            "--udid=${config.udid}",
            *tunnelInfoArgs(config).toTypedArray(),
            "forward",
            hostPort.toString(),
            config.devicePort.toString(),
        )
    }

    private fun WdaConfig.withRuntimePorts(usesTunnel: Boolean): WdaConfig {
        val allocatedHostPort = hostPort ?: portAllocator.findAvailablePort()
        if (!usesTunnel) {
            return copy(hostPort = allocatedHostPort)
        }
        val allocatedTunnelInfoPort = tunnelInfoPort ?: portAllocator.findAvailablePort()
        val allocatedUserspaceTunnelPort = if (tunnelMode == "userspace") {
            userspaceTunnelPort ?: portAllocator.findAvailablePort()
        } else {
            userspaceTunnelPort
        }
        return copy(
            hostPort = allocatedHostPort,
            tunnelInfoPort = allocatedTunnelInfoPort,
            userspaceTunnelPort = allocatedUserspaceTunnelPort,
        )
    }

    private fun MutableList<String>.addTunnelInfoArgs(config: WdaConfig) {
        addAll(tunnelInfoArgs(config))
    }

    private fun tunnelInfoArgs(config: WdaConfig): List<String> {
        val port = config.tunnelInfoPort ?: return emptyList()
        return listOf(
            "--tunnel-info-host=${config.tunnelInfoHost}",
            "--tunnel-info-port=$port",
        )
    }

    private fun launch(
        command: List<String>,
        environment: Map<String, String>,
        logFile: Path?,
    ): WdaProcess {
        val process = processLauncher.launch(command, environment, logFile)
        processes[process.pid] = process
        return process
    }

    private fun waitForAlive(
        process: WdaProcess,
        label: String,
    ) {
        if (!process.isAlive()) {
            throw WdaStartupException("$label process ${process.pid} exited during startup")
        }
    }

    private fun waitUntilReady(
        url: String,
        timeoutMs: Long,
        processes: List<WdaProcess>,
    ) {
        val start = System.nanoTime()
        val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (true) {
            if (statusProbe.isReady(url)) {
                return
            }
            val exited = processes.firstOrNull { !it.isAlive() }
            if (exited != null) {
                throw WdaStartupException("WDA process ${exited.pid} exited before '$url' became ready")
            }
            val elapsed = System.nanoTime() - start
            if (elapsed >= timeoutNanos) {
                throw WdaStartupException("WDA at '$url' was not ready within ${timeoutMs}ms")
            }
            val remainingMs = TimeUnit.NANOSECONDS.toMillis(timeoutNanos - elapsed).coerceAtLeast(1)
            sleeper(minOf(probeIntervalMs, remainingMs))
        }
    }

    private fun stopProcess(process: WdaProcess) {
        if (!process.isAlive()) {
            return
        }
        process.destroy()
        if (!process.waitFor(timeoutMs = 5_000)) {
            process.destroyForcibly()
            process.waitFor(timeoutMs = 2_000)
        }
    }
}

class WdaStartupException(
    message: String,
) : RuntimeException(message)

interface WdaProcessLauncher {
    fun launch(
        command: List<String>,
        environment: Map<String, String>,
        logFile: Path? = null,
    ): WdaProcess
}

interface WdaProcess {
    val pid: Long

    fun isAlive(): Boolean

    fun destroy()

    fun destroyForcibly()

    fun waitFor(timeoutMs: Long): Boolean
}

interface WdaPortAllocator {
    fun findAvailablePort(): Int
}

object ServerSocketWdaPortAllocator : WdaPortAllocator {
    override fun findAvailablePort(): Int {
        return ServerSocket(0).use { socket ->
            socket.reuseAddress = true
            socket.localPort
        }
    }
}

object ProcessBuilderWdaProcessLauncher : WdaProcessLauncher {
    override fun launch(
        command: List<String>,
        environment: Map<String, String>,
        logFile: Path?,
    ): WdaProcess {
        val processBuilder = ProcessBuilder(command).redirectErrorStream(true)
        if (logFile != null) {
            java.nio.file.Files.createDirectories(logFile.parent)
            processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
        } else {
            processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
        }
        processBuilder.environment().putAll(environment)
        return JavaLangWdaProcess(processBuilder.start())
    }
}

class JavaLangWdaProcess(
    private val process: Process,
) : WdaProcess {
    override val pid: Long
        get() = process.pid()

    override fun isAlive(): Boolean {
        return process.isAlive
    }

    override fun destroy() {
        process.destroy()
    }

    override fun destroyForcibly() {
        process.destroyForcibly()
    }

    override fun waitFor(timeoutMs: Long): Boolean {
        return process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
    }
}

interface WdaStatusProbe {
    fun isReady(url: String): Boolean
}

class JavaNetWdaStatusProbe(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build(),
    private val requestTimeout: Duration = Duration.ofSeconds(2),
) : WdaStatusProbe {
    override fun isReady(url: String): Boolean {
        return runCatching {
            val request = HttpRequest.newBuilder(URI.create("${url.trimEnd('/')}/status"))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() in 200..299
        }.getOrDefault(false)
    }
}
