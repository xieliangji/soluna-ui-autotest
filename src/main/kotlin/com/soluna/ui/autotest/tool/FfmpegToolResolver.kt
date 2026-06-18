package com.soluna.ui.autotest.tool

import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.name

interface FfmpegToolResolver {
    fun resolve(): FfmpegToolResolution

    fun resolvePathEntryForManagedProcess(): Path?
}

interface ToolExecutableProbe {
    fun canRun(command: String): Boolean
}

data class FfmpegPlatform(
    val os: String,
    val arch: String,
) {
    val directoryName: String = "$os-$arch"
    val executableName: String = if (os == OS_WINDOWS) "ffmpeg.exe" else "ffmpeg"

    companion object {
        const val OS_WINDOWS = "windows"
        const val OS_MACOS = "macos"
        const val OS_LINUX = "linux"

        fun current(
            osName: String = System.getProperty("os.name"),
            osArch: String = System.getProperty("os.arch"),
        ): FfmpegPlatform {
            return FfmpegPlatform(
                os = normalizeOs(osName),
                arch = normalizeArch(osArch),
            )
        }

        private fun normalizeOs(osName: String): String {
            val value = osName.lowercase()
            return when {
                value.contains("win") -> OS_WINDOWS
                value.contains("mac") || value.contains("darwin") -> OS_MACOS
                value.contains("linux") -> OS_LINUX
                else -> error("Unsupported OS for ffmpeg tool resolution: $osName")
            }
        }

        private fun normalizeArch(osArch: String): String {
            val value = osArch.lowercase()
            return when (value) {
                "x86_64", "amd64" -> "x64"
                "aarch64", "arm64" -> "arm64"
                else -> error("Unsupported CPU architecture for ffmpeg tool resolution: $osArch")
            }
        }
    }
}

data class FfmpegToolResolution(
    val command: String,
    val directory: Path?,
    val source: FfmpegToolSource,
)

enum class FfmpegToolSource {
    EXPLICIT,
    BUNDLED,
    PATH,
}

class DefaultFfmpegToolResolver(
    private val platform: FfmpegPlatform = FfmpegPlatform.current(),
    private val environment: Map<String, String> = System.getenv(),
    private val propertyProvider: (String) -> String? = { System.getProperty(it) },
    private val workingDirectory: Path = Path.of(System.getProperty("user.dir")),
    private val classPath: String = System.getProperty("java.class.path").orEmpty(),
    private val probe: ToolExecutableProbe = ProcessToolExecutableProbe,
) : FfmpegToolResolver {
    override fun resolve(): FfmpegToolResolution {
        val candidates = executableCandidates(includePathFallback = true)
        return candidates.firstOrNull { candidate ->
            prepareExecutable(candidate)
            probe.canRun(candidate.command)
        }?.toResolution()
            ?: error(missingFfmpegMessage(candidates))
    }

    override fun resolvePathEntryForManagedProcess(): Path? {
        val candidates = executableCandidates(includePathFallback = false)
        val resolution = candidates.firstOrNull { candidate ->
            prepareExecutable(candidate)
            probe.canRun(candidate.command)
        }?.toResolution()
        return resolution?.directory
    }

    internal fun executableCandidates(includePathFallback: Boolean): List<FfmpegToolCandidate> {
        val explicit = listOfNotNull(
            propertyProvider(PROPERTY_FFMPEG_PATH)?.toExplicitCandidate("system property $PROPERTY_FFMPEG_PATH"),
            environment[ENV_FFMPEG]?.toExplicitCandidate("environment $ENV_FFMPEG"),
        )

        val bundled = toolRoots()
            .distinctBy { it.normalize().absolute() }
            .map { root ->
                val executable = root.resolve("ffmpeg")
                    .resolve(platform.directoryName)
                    .resolve(platform.executableName)
                    .normalize()
                    .absolute()
                FfmpegToolCandidate(
                    command = executable.toString(),
                    directory = executable.parent,
                    source = FfmpegToolSource.BUNDLED,
                    description = "bundled tool ${executable}",
                )
            }

        val pathFallback = if (includePathFallback) {
            listOf(
                FfmpegToolCandidate(
                    command = platform.executableName,
                    directory = null,
                    source = FfmpegToolSource.PATH,
                    description = "PATH command ${platform.executableName}",
                ),
            )
        } else {
            emptyList()
        }

        return explicit + bundled + pathFallback
    }

    private fun toolRoots(): List<Path> {
        val configured = listOfNotNull(
            propertyProvider(PROPERTY_TOOLS_DIR)?.takeIf { it.isNotBlank() }?.let { Path.of(it) },
            environment[ENV_TOOLS_DIR]?.takeIf { it.isNotBlank() }?.let { Path.of(it) },
        )
        val inferredApplicationToolRoots = inferredApplicationRoots().map { it.resolve("tools") }
        return configured + workingDirectory.resolve("tools") + inferredApplicationToolRoots
    }

    private fun inferredApplicationRoots(): List<Path> {
        return classPath.split(File.pathSeparator)
            .asSequence()
            .filter { it.isNotBlank() }
            .map { Path.of(it).normalize().absolute() }
            .mapNotNull { path ->
                val parent = path.parent
                if (parent?.name == "lib") {
                    parent.parent
                } else {
                    null
                }
            }
            .toList()
    }

    private fun String.toExplicitCandidate(description: String): FfmpegToolCandidate? {
        return takeIf { it.isNotBlank() }
            ?.let { Path.of(it).normalize().absolute() }
            ?.let { executable ->
                FfmpegToolCandidate(
                    command = executable.toString(),
                    directory = executable.parent,
                    source = FfmpegToolSource.EXPLICIT,
                    description = description,
                )
            }
    }

    private fun prepareExecutable(candidate: FfmpegToolCandidate) {
        if (platform.os != FfmpegPlatform.OS_WINDOWS && candidate.source != FfmpegToolSource.PATH) {
            runCatching {
                Path.of(candidate.command).toFile().setExecutable(true, false)
            }
        }
    }

    private fun missingFfmpegMessage(candidates: List<FfmpegToolCandidate>): String {
        val attempted = candidates.joinToString(separator = "\n") { "- ${it.description}: ${it.command}" }
        return "No runnable ffmpeg executable found for ${platform.directoryName}. " +
            "Put a bundled binary at tools/ffmpeg/${platform.directoryName}/${platform.executableName}, " +
            "or set -D$PROPERTY_FFMPEG_PATH=/path/to/${platform.executableName}, " +
            "or set $ENV_FFMPEG=/path/to/${platform.executableName}. Tried:\n$attempted"
    }

    companion object {
        const val PROPERTY_FFMPEG_PATH = "soluna.ffmpeg.path"
        const val ENV_FFMPEG = "SOLUNA_FFMPEG"
        const val PROPERTY_TOOLS_DIR = "soluna.tools.dir"
        const val ENV_TOOLS_DIR = "SOLUNA_TOOLS_DIR"
    }
}

data class FfmpegToolCandidate(
    val command: String,
    val directory: Path?,
    val source: FfmpegToolSource,
    val description: String,
) {
    fun toResolution(): FfmpegToolResolution {
        return FfmpegToolResolution(
            command = command,
            directory = directory,
            source = source,
        )
    }
}

object ProcessToolExecutableProbe : ToolExecutableProbe {
    override fun canRun(command: String): Boolean {
        return runCatching {
            val process = ProcessBuilder(command, "-version")
                .redirectErrorStream(true)
                .start()
            process.inputStream.use { it.readBytes() }
            process.waitFor() == 0
        }.getOrDefault(false)
    }
}

object NoOpFfmpegToolResolver : FfmpegToolResolver {
    override fun resolve(): FfmpegToolResolution {
        error("ffmpeg tool resolution is disabled")
    }

    override fun resolvePathEntryForManagedProcess(): Path? {
        return null
    }
}

fun prependPathEntry(
    environment: Map<String, String>,
    pathEntry: Path,
    baseEnvironment: Map<String, String> = System.getenv(),
): Map<String, String> {
    val pathKey = pathVariableName(environment, baseEnvironment)
    val existing = environment[pathKey]
        ?: baseEnvironment[pathKey]
        ?: baseEnvironment.entries.firstOrNull { it.key.equals(pathKey, ignoreCase = true) }?.value
    val pathValue = buildString {
        append(pathEntry.normalize().absolute())
        if (!existing.isNullOrBlank()) {
            append(File.pathSeparator)
            append(existing)
        }
    }
    return environment + (pathKey to pathValue)
}

private fun pathVariableName(
    environment: Map<String, String>,
    baseEnvironment: Map<String, String>,
): String {
    return (environment.keys + baseEnvironment.keys)
        .firstOrNull { it.equals("PATH", ignoreCase = true) }
        ?: "PATH"
}
