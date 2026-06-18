package com.soluna.ui.autotest.appium.server

import com.soluna.ui.autotest.tool.DefaultFfmpegToolResolver
import com.soluna.ui.autotest.tool.FfmpegToolResolver
import com.soluna.ui.autotest.tool.prependPathEntry
import org.slf4j.LoggerFactory
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
    private val ffmpegToolResolver: FfmpegToolResolver = DefaultFfmpegToolResolver(),
    private val probeIntervalMs: Long = 250,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
) : AppiumServerManager {
    private val logger = LoggerFactory.getLogger(LocalProcessAppiumServerManager::class.java)
    private val processes = ConcurrentHashMap<Long, AppiumServerProcess>()

    override fun ensureRunning(config: AppiumServerConfig): AppiumServerHandle {
        logger.debug(
            "appium.manager ensureRunning requested managed={} url={} host={} port={} executable={} plugins={} startupTimeoutMs={}",
            config.managed,
            config.baseUrl,
            config.host,
            config.port,
            config.executable,
            config.usePlugins,
            config.startupTimeoutMs,
        )
        if (!config.managed) {
            logger.debug("appium.manager using external server url={}", config.url)
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
        val launchEnvironment = environmentWithFfmpegPath(launchConfig.environment)
        val command = buildCommand(launchConfig)
        logger.debug(
            "appium.manager launching managed server url={} command={} envKeys={}",
            launchUrl,
            command.redactSensitiveCommandValues(),
            launchEnvironment.keys.sorted(),
        )
        val process = processLauncher.launch(
            command = command,
            environment = launchEnvironment,
        )
        processes[process.pid] = process
        logger.debug("appium.manager process started pid={} url={}", process.pid, launchUrl)

        try {
            waitUntilReady(
                url = launchUrl,
                timeoutMs = launchConfig.startupTimeoutMs,
                process = process,
            )
        } catch (err: RuntimeException) {
            logger.debug("appium.manager startup failed pid={} url={} error={}", process.pid, launchUrl, err.message)
            stopProcess(process)
            processes.remove(process.pid)
            throw err
        }

        logger.debug("appium.manager ready pid={} url={}", process.pid, launchUrl)
        return AppiumServerHandle(
            url = launchUrl,
            managed = true,
            processId = process.pid,
        )
    }

    override fun stop(handle: AppiumServerHandle) {
        logger.debug("appium.manager stop requested managed={} url={} pid={}", handle.managed, handle.url, handle.processId)
        val process = handle.processId?.let { processes.remove(it) } ?: return
        stopProcess(process)
    }

    override fun isRunning(handle: AppiumServerHandle): Boolean {
        val processAlive = handle.processId
            ?.let { processes[it]?.isAlive() }
            ?: true
        val ready = processAlive && statusProbe.isReady(handle.url)
        logger.debug(
            "appium.manager health checked url={} pid={} processAlive={} ready={}",
            handle.url,
            handle.processId,
            processAlive,
            ready,
        )
        return ready
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

    internal fun environmentWithFfmpegPath(environment: Map<String, String>): Map<String, String> {
        val ffmpegDirectory = ffmpegToolResolver.resolvePathEntryForManagedProcess()
            ?: run {
                logger.debug("appium.manager ffmpeg PATH injection skipped reason=no-explicit-or-bundled-ffmpeg")
                return environment
            }
        logger.debug("appium.manager prepending ffmpeg directory to PATH path={}", ffmpegDirectory)
        return prependPathEntry(environment, ffmpegDirectory)
    }

    private fun waitUntilReady(
        url: String,
        timeoutMs: Long,
        process: AppiumServerProcess?,
    ) {
        logger.debug("appium.manager waiting for readiness url={} timeoutMs={}", url, timeoutMs)
        val start = System.nanoTime()
        val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (true) {
            if (statusProbe.isReady(url)) {
                val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
                logger.debug("appium.manager readiness confirmed url={} elapsedMs={}", url, elapsedMs)
                return
            }
            if (process?.isAlive() == false) {
                logger.debug("appium.manager process exited before ready pid={} url={}", process.pid, url)
                throw AppiumServerStartupException("Appium server process ${process.pid} exited before becoming ready")
            }
            val elapsed = System.nanoTime() - start
            if (elapsed >= timeoutNanos) {
                logger.debug("appium.manager readiness timed out url={} timeoutMs={}", url, timeoutMs)
                throw AppiumServerStartupException("Appium server at '$url' was not ready within ${timeoutMs}ms")
            }
            val remainingMs = TimeUnit.NANOSECONDS.toMillis(timeoutNanos - elapsed).coerceAtLeast(1)
            sleeper(minOf(probeIntervalMs, remainingMs))
        }
    }

    private fun stopProcess(process: AppiumServerProcess) {
        if (!process.isAlive()) {
            logger.debug("appium.manager process already stopped pid={}", process.pid)
            return
        }
        logger.debug("appium.manager destroying process pid={}", process.pid)
        process.destroy()
        if (!process.waitFor(timeoutMs = 5_000)) {
            logger.debug("appium.manager process did not stop gracefully; destroying forcibly pid={}", process.pid)
            process.destroyForcibly()
            process.waitFor(timeoutMs = 2_000)
        }
        logger.debug("appium.manager process stopped pid={} alive={}", process.pid, process.isAlive())
    }

    private fun List<String>.redactSensitiveCommandValues(): List<String> {
        val sensitiveName = Regex("(?i)(pass|password|secret|token|key|credential)")
        return mapIndexed { index, value ->
            val previous = getOrNull(index - 1).orEmpty()
            when {
                value.contains("=") -> {
                    val name = value.substringBefore("=")
                    if (sensitiveName.containsMatchIn(name)) "$name=***" else value
                }
                previous.startsWith("-") && sensitiveName.containsMatchIn(previous) -> "***"
                else -> value
            }
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
