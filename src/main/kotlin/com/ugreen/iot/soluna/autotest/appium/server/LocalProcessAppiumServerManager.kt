package com.ugreen.iot.soluna.autotest.appium.server

import java.net.URI
import java.net.ServerSocket
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class LocalProcessAppiumServerManager(
    private val processLauncher: AppiumProcessLauncher = ProcessBuilderAppiumProcessLauncher,
    private val statusProbe: AppiumServerStatusProbe = JavaNetAppiumServerStatusProbe(),
    private val portAllocator: AppiumPortAllocator = ServerSocketAppiumPortAllocator,
    private val probeIntervalMs: Long = 250,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
) : AppiumServerManager {
    private val processes = ConcurrentHashMap<Long, AppiumServerProcess>()

    override fun ensureRunning(config: AppiumServerConfig): AppiumServerHandle {
        if (!config.managed) {
            waitUntilReady(
                url = config.url,
                timeoutMs = config.startupTimeoutMs,
                process = null,
            )
            return AppiumServerHandle(
                url = config.url,
                managed = false,
            )
        }

        val launchConfig = config.copy(
            host = config.host ?: AppiumServerConfig.DEFAULT_HOST,
            port = config.port ?: portAllocator.findAvailablePort(),
        )
        val launchUrl = launchConfig.url
        val process = processLauncher.launch(
            command = buildCommand(launchConfig),
            environment = launchConfig.environment,
        )
        processes[process.pid] = process

        try {
            waitUntilReady(
                url = launchUrl,
                timeoutMs = launchConfig.startupTimeoutMs,
                process = process,
            )
        } catch (err: RuntimeException) {
            stopProcess(process)
            processes.remove(process.pid)
            throw err
        }

        return AppiumServerHandle(
            url = launchUrl,
            managed = true,
            processId = process.pid,
        )
    }

    override fun stop(handle: AppiumServerHandle) {
        val process = handle.processId?.let { processes.remove(it) } ?: return
        stopProcess(process)
    }

    override fun isRunning(handle: AppiumServerHandle): Boolean {
        val processAlive = handle.processId
            ?.let { processes[it]?.isAlive() }
            ?: true
        return processAlive && statusProbe.isReady(handle.url)
    }

    internal fun buildCommand(config: AppiumServerConfig): List<String> {
        val host = config.host ?: AppiumServerConfig.DEFAULT_HOST
        val port = config.port ?: AppiumServerConfig.DEFAULT_PORT
        return buildList {
            add(config.executable)
            add("--address")
            add(host)
            add("--port")
            add(port.toString())
            if (config.usePlugins.isNotEmpty()) {
                add("--use-plugins=${config.usePlugins.joinToString(separator = ",")}")
            }
            addAll(config.extraArgs)
        }
    }

    private fun waitUntilReady(
        url: String,
        timeoutMs: Long,
        process: AppiumServerProcess?,
    ) {
        val start = System.nanoTime()
        val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (true) {
            if (statusProbe.isReady(url)) {
                return
            }
            if (process?.isAlive() == false) {
                throw AppiumServerStartupException("Appium server process ${process.pid} exited before becoming ready")
            }
            val elapsed = System.nanoTime() - start
            if (elapsed >= timeoutNanos) {
                throw AppiumServerStartupException("Appium server at '$url' was not ready within ${timeoutMs}ms")
            }
            val remainingMs = TimeUnit.NANOSECONDS.toMillis(timeoutNanos - elapsed).coerceAtLeast(1)
            sleeper(minOf(probeIntervalMs, remainingMs))
        }
    }

    private fun stopProcess(process: AppiumServerProcess) {
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

class AppiumServerStartupException(
    message: String,
) : RuntimeException(message)

interface AppiumProcessLauncher {
    fun launch(
        command: List<String>,
        environment: Map<String, String>,
    ): AppiumServerProcess
}

interface AppiumServerProcess {
    val pid: Long

    fun isAlive(): Boolean

    fun destroy()

    fun destroyForcibly()

    fun waitFor(timeoutMs: Long): Boolean
}

interface AppiumPortAllocator {
    fun findAvailablePort(): Int
}

object ServerSocketAppiumPortAllocator : AppiumPortAllocator {
    override fun findAvailablePort(): Int {
        return ServerSocket(0).use { socket ->
            socket.reuseAddress = true
            socket.localPort
        }
    }
}

object ProcessBuilderAppiumProcessLauncher : AppiumProcessLauncher {
    override fun launch(
        command: List<String>,
        environment: Map<String, String>,
    ): AppiumServerProcess {
        val processBuilder = ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        val processEnvironment = processBuilder.environment()
        processEnvironment.putAll(environment)
        return JavaLangAppiumServerProcess(processBuilder.start())
    }
}

class JavaLangAppiumServerProcess(
    private val process: Process,
) : AppiumServerProcess {
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

interface AppiumServerStatusProbe {
    fun isReady(url: String): Boolean
}

class JavaNetAppiumServerStatusProbe(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build(),
    private val requestTimeout: Duration = Duration.ofSeconds(2),
) : AppiumServerStatusProbe {
    override fun isReady(url: String): Boolean {
        return runCatching {
            val request = HttpRequest.newBuilder(URI.create("${url.trimEnd('/')}/status"))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200 && response.body().contains(READY_TRUE_PATTERN)
        }.getOrDefault(false)
    }

    companion object {
        private val READY_TRUE_PATTERN = Regex(""""ready"\s*:\s*true""")
    }
}
