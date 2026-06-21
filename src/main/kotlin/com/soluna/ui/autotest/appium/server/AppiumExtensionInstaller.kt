package com.soluna.ui.autotest.appium.server

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

interface AppiumExtensionInstaller {
    fun ensureExtensions(
        appiumExecutable: String,
        pluginNames: List<String>,
        driverNames: List<String>,
        environment: Map<String, String> = emptyMap(),
    )
}

object NoOpAppiumExtensionInstaller : AppiumExtensionInstaller {
    override fun ensureExtensions(
        appiumExecutable: String,
        pluginNames: List<String>,
        driverNames: List<String>,
        environment: Map<String, String>,
    ) = Unit
}

class LocalAppiumExtensionInstaller(
    private val commandRunner: AppiumExtensionCommandRunner = ProcessBuilderAppiumExtensionCommandRunner(),
    private val sourceResolver: AppiumPluginSourceResolver = KnownLocalAppiumPluginSourceResolver(),
    private val objectMapper: ObjectMapper = ObjectMapper(),
) : AppiumExtensionInstaller {
    private val logger = LoggerFactory.getLogger(LocalAppiumExtensionInstaller::class.java)

    override fun ensureExtensions(
        appiumExecutable: String,
        pluginNames: List<String>,
        driverNames: List<String>,
        environment: Map<String, String>,
    ) {
        ensurePlugins(appiumExecutable, pluginNames, environment)
        ensureDrivers(appiumExecutable, driverNames, environment)
    }

    private fun ensurePlugins(
        appiumExecutable: String,
        pluginNames: List<String>,
        environment: Map<String, String>,
    ) {
        val requiredPlugins = pluginNames.distinct().filter { it.isNotBlank() }
        if (requiredPlugins.isEmpty()) {
            return
        }

        val installedPlugins = listInstalledExtensions(
            appiumExecutable = appiumExecutable,
            kind = "plugin",
            environment = environment,
        )

        for (pluginName in requiredPlugins) {
            val sourceDir = sourceResolver.sourceFor(pluginName)
            val installed = installedPlugins[pluginName]
            if (installed != null && isAcceptablePluginInstall(pluginName, installed, sourceDir)) {
                logger.debug("appium.plugin already installed name={} source={}", pluginName, installed.installPath ?: installed.installSpec)
                continue
            }

            if (installed != null) {
                logger.info(
                    "appium.plugin reinstalling name={} currentInstallType={} currentInstallPath={}",
                    pluginName,
                    installed.installType,
                    installed.installPath,
                )
                runAppiumCommand(
                    command = listOf(appiumExecutable, "plugin", "uninstall", pluginName),
                    environment = environment,
                    description = "uninstall Appium plugin '$pluginName'",
                    timeoutMs = INSTALL_TIMEOUT_MS,
                )
            }

            val resolvedSourceDir = sourceDir
                ?: throw AppiumExtensionInstallException(
                    "Appium plugin '$pluginName' is not installed and no bundled source directory was found",
                )
            ensureBuilt(pluginName, resolvedSourceDir, environment)
            logger.info("appium.plugin installing name={} source={}", pluginName, resolvedSourceDir)
            runAppiumCommand(
                command = listOf(appiumExecutable, "plugin", "install", "--source=local", resolvedSourceDir.toString()),
                environment = environment,
                description = "install Appium plugin '$pluginName'",
                timeoutMs = INSTALL_TIMEOUT_MS,
            )
        }
    }

    private fun ensureDrivers(
        appiumExecutable: String,
        driverNames: List<String>,
        environment: Map<String, String>,
    ) {
        val requiredDrivers = driverNames.distinct().filter { it.isNotBlank() }
        if (requiredDrivers.isEmpty()) {
            return
        }

        val installedDrivers = listInstalledExtensions(
            appiumExecutable = appiumExecutable,
            kind = "driver",
            environment = environment,
        )

        for (driverName in requiredDrivers) {
            if (installedDrivers.containsKey(driverName)) {
                logger.debug("appium.driver already installed name={}", driverName)
                continue
            }
            logger.info("appium.driver installing name={}", driverName)
            runAppiumCommand(
                command = listOf(appiumExecutable, "driver", "install", driverName),
                environment = environment,
                description = "install Appium driver '$driverName'",
                timeoutMs = INSTALL_TIMEOUT_MS,
            )
        }
    }

    private fun isAcceptablePluginInstall(
        pluginName: String,
        installed: AppiumExtensionMetadata,
        sourceDir: Path?,
    ): Boolean {
        if (pluginName != "soluna-ext") {
            return true
        }
        val expectedSource = sourceDir?.toAbsolutePath()?.normalize() ?: return false
        return listOfNotNull(installed.installSpec, installed.installPath)
            .mapNotNull { runCatching { Path.of(it).toAbsolutePath().normalize() }.getOrNull() }
            .any { path -> path == expectedSource || path.startsWith(expectedSource) }
    }

    private fun ensureBuilt(
        pluginName: String,
        sourceDir: Path,
        environment: Map<String, String>,
    ) {
        val packageJson = sourceDir.resolve("package.json")
        require(Files.isRegularFile(packageJson)) {
            "Appium plugin '$pluginName' source directory does not contain package.json: $sourceDir"
        }
        if (Files.isRegularFile(sourceDir.resolve("build/lib/index.js"))) {
            return
        }

        logger.info("appium.plugin building bundled source name={} source={}", pluginName, sourceDir)
        runExtensionCommand(
            command = listOf("npm", "ci"),
            workingDirectory = sourceDir,
            environment = environment,
            description = "install npm dependencies for Appium plugin '$pluginName'",
            timeoutMs = NPM_TIMEOUT_MS,
        )
        runExtensionCommand(
            command = listOf("npm", "run", "build"),
            workingDirectory = sourceDir,
            environment = environment,
            description = "build Appium plugin '$pluginName'",
            timeoutMs = NPM_TIMEOUT_MS,
        )
    }

    private fun listInstalledExtensions(
        appiumExecutable: String,
        kind: String,
        environment: Map<String, String>,
    ): Map<String, AppiumExtensionMetadata> {
        val output = runAppiumCommand(
            command = listOf(appiumExecutable, kind, "list", "--installed", "--json", "--verbose"),
            environment = environment,
            description = "list installed Appium ${kind}s",
        ).combinedOutput
        val root = objectMapper.readTree(output.ifBlank { "{}" })
        return root.fields().asSequence().associate { (name, node) ->
            name to AppiumExtensionMetadata.from(node)
        }
    }

    private fun runAppiumCommand(
        command: List<String>,
        environment: Map<String, String>,
        description: String,
        timeoutMs: Long = COMMAND_TIMEOUT_MS,
    ): AppiumExtensionCommandResult {
        return runExtensionCommand(
            command = command,
            workingDirectory = null,
            environment = environment,
            description = description,
            timeoutMs = timeoutMs,
        )
    }

    private fun runExtensionCommand(
        command: List<String>,
        workingDirectory: Path?,
        environment: Map<String, String>,
        description: String,
        timeoutMs: Long,
    ): AppiumExtensionCommandResult {
        val result = commandRunner.run(
            command = command,
            workingDirectory = workingDirectory,
            environment = environment,
            timeoutMs = timeoutMs,
        )
        if (result.exitCode != 0 || result.timedOut) {
            throw AppiumExtensionInstallException(
                buildString {
                    append("Failed to $description")
                    append(" (exitCode=${result.exitCode}, timedOut=${result.timedOut})")
                    val output = result.combinedOutput.trim()
                    if (output.isNotBlank()) {
                        append(": ")
                        append(output.take(MAX_ERROR_OUTPUT_CHARS))
                    }
                },
            )
        }
        return result
    }

    companion object {
        private const val COMMAND_TIMEOUT_MS = 30_000L
        private const val INSTALL_TIMEOUT_MS = 180_000L
        private const val NPM_TIMEOUT_MS = 300_000L
        private const val MAX_ERROR_OUTPUT_CHARS = 4_000
    }
}

data class AppiumExtensionMetadata(
    val installType: String? = null,
    val installSpec: String? = null,
    val installPath: String? = null,
) {
    companion object {
        fun from(node: JsonNode): AppiumExtensionMetadata {
            return AppiumExtensionMetadata(
                installType = node.path("installType").asTextOrNull(),
                installSpec = node.path("installSpec").asTextOrNull(),
                installPath = node.path("installPath").asTextOrNull(),
            )
        }

        private fun JsonNode.asTextOrNull(): String? {
            return takeIf { !it.isMissingNode && !it.isNull }?.asText()?.takeIf { value -> value.isNotBlank() }
        }
    }
}

interface AppiumPluginSourceResolver {
    fun sourceFor(pluginName: String): Path?
}

class KnownLocalAppiumPluginSourceResolver : AppiumPluginSourceResolver {
    override fun sourceFor(pluginName: String): Path? {
        if (pluginName != "soluna-ext") {
            return null
        }
        return candidateDirectories()
            .map { it.toAbsolutePath().normalize() }
            .firstOrNull { Files.isRegularFile(it.resolve("package.json")) }
    }

    private fun candidateDirectories(): List<Path> {
        val configured = listOfNotNull(
            System.getProperty("soluna.appium.ext.dir")?.takeIf { it.isNotBlank() }?.let { Path.of(it) },
            System.getenv("SOLUNA_APPIUM_EXT_DIR")?.takeIf { it.isNotBlank() }?.let { Path.of(it) },
        )
        val workingTreeCandidates = listOf(
            Path.of("lib", "soluna-appium-ext"),
            Path.of("plugins", "soluna-appium-ext"),
        )
        val installedAppCandidates = installedAppHome()
            ?.let { listOf(it.resolve("plugins").resolve("soluna-appium-ext")) }
            .orEmpty()
        return configured + workingTreeCandidates + installedAppCandidates
    }

    private fun installedAppHome(): Path? {
        val codeSource = LocalProcessAppiumServerManager::class.java.protectionDomain
            ?.codeSource
            ?.location
            ?.toURI()
            ?: return null
        val location = Path.of(codeSource)
        return location.parent?.parent
    }
}

interface AppiumExtensionCommandRunner {
    fun run(
        command: List<String>,
        workingDirectory: Path? = null,
        environment: Map<String, String> = emptyMap(),
        timeoutMs: Long,
    ): AppiumExtensionCommandResult
}

data class AppiumExtensionCommandResult(
    val exitCode: Int,
    val stdout: String = "",
    val stderr: String = "",
    val timedOut: Boolean = false,
) {
    val combinedOutput: String
        get() = listOf(stdout, stderr)
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")
}

class ProcessBuilderAppiumExtensionCommandRunner : AppiumExtensionCommandRunner {
    override fun run(
        command: List<String>,
        workingDirectory: Path?,
        environment: Map<String, String>,
        timeoutMs: Long,
    ): AppiumExtensionCommandResult {
        val processBuilder = ProcessBuilder(command)
        workingDirectory?.let { processBuilder.directory(it.toFile()) }
        processBuilder.environment().putAll(environment)
        val process = processBuilder.start()
        val executor = Executors.newFixedThreadPool(2)
        return try {
            val stdout = executor.submit<String> { process.inputStream.bufferedReader().readText() }
            val stderr = executor.submit<String> { process.errorStream.bufferedReader().readText() }
            val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!completed) {
                process.destroy()
                if (!process.waitFor(2_000, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                    process.waitFor(2_000, TimeUnit.MILLISECONDS)
                }
            }
            AppiumExtensionCommandResult(
                exitCode = if (completed) process.exitValue() else -1,
                stdout = stdout.get(2, TimeUnit.SECONDS),
                stderr = stderr.get(2, TimeUnit.SECONDS),
                timedOut = !completed,
            )
        } finally {
            executor.shutdownNow()
        }
    }
}

class AppiumExtensionInstallException(
    message: String,
) : RuntimeException(message)
