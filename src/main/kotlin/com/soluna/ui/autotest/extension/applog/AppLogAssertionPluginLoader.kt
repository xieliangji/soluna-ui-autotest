package com.soluna.ui.autotest.extension.applog

import org.slf4j.LoggerFactory
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

object AppLogAssertionPluginLoader {
    const val DEFAULT_PLUGIN_DIRECTORY: String = "plugins/app-log"
    const val PLUGIN_DIRS_PROPERTY: String = "soluna.appLogPluginDirs"
    const val PLUGIN_DIRS_ENV: String = "SOLUNA_APP_LOG_PLUGIN_DIRS"

    private val logger = LoggerFactory.getLogger(AppLogAssertionPluginLoader::class.java)

    fun defaultRegistry(
        planPath: Path,
        workingDirectory: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
        applicationRoot: Path? = inferApplicationRoot(),
        parentClassLoader: ClassLoader = AppLogAssertionPlugin::class.java.classLoader,
    ): AppLogAssertionRegistry {
        val directories = configuredPluginDirectories(workingDirectory) +
            defaultPluginDirectories(planPath, workingDirectory, applicationRoot)
        return registryFor(directories, parentClassLoader)
    }

    fun registryFor(
        pluginDirectories: List<Path>,
        parentClassLoader: ClassLoader = AppLogAssertionPlugin::class.java.classLoader,
    ): AppLogAssertionRegistry {
        val jars = discoverPluginJars(pluginDirectories)
        val registries = mutableListOf<AppLogAssertionRegistry>()
        if (jars.isNotEmpty()) {
            logger.info("appLog.assertion.plugins loading jars={}", jars)
            val classLoader = URLClassLoader(
                jars.map { jar -> jar.toUri().toURL() }.toTypedArray(),
                parentClassLoader,
            )
            registries += ServiceLoaderAppLogAssertionRegistry.fromClassLoader(classLoader)
        }
        registries += ServiceLoaderAppLogAssertionRegistry.fromClassLoader(parentClassLoader)
        return CompositeAppLogAssertionRegistry(registries)
    }

    internal fun defaultPluginDirectories(
        planPath: Path,
        workingDirectory: Path,
        applicationRoot: Path?,
    ): List<Path> {
        return listOfNotNull(
            inferAssetRoot(planPath)?.resolve(DEFAULT_PLUGIN_DIRECTORY),
            workingDirectory.resolve(DEFAULT_PLUGIN_DIRECTORY),
            applicationRoot?.resolve(DEFAULT_PLUGIN_DIRECTORY),
        ).map { it.toAbsolutePath().normalize() }
            .distinct()
    }

    internal fun configuredPluginDirectories(
        workingDirectory: Path,
        propertyValue: String? = System.getProperty(PLUGIN_DIRS_PROPERTY),
        envValue: String? = System.getenv(PLUGIN_DIRS_ENV),
    ): List<Path> {
        return listOfNotNull(propertyValue, envValue)
            .flatMap { value -> value.split(File.pathSeparatorChar) }
            .map { value -> value.trim() }
            .filter { value -> value.isNotEmpty() }
            .map { value ->
                val path = Path.of(value)
                if (path.isAbsolute) path.normalize() else workingDirectory.resolve(path).normalize()
            }
            .distinct()
    }

    internal fun discoverPluginJars(pluginDirectories: List<Path>): List<Path> {
        return pluginDirectories
            .asSequence()
            .map { directory -> directory.toAbsolutePath().normalize() }
            .distinct()
            .filter { directory -> Files.isDirectory(directory) }
            .flatMap { directory ->
                Files.list(directory).use { stream ->
                    stream
                        .filter { path -> Files.isRegularFile(path) }
                        .filter { path -> path.fileName.toString().endsWith(".jar", ignoreCase = true) }
                        .sorted()
                        .toList()
                        .asSequence()
                }
            }
            .map { jar -> jar.toAbsolutePath().normalize() }
            .distinct()
            .toList()
    }

    internal fun inferAssetRoot(planPath: Path): Path? {
        val initial = (if (Files.isDirectory(planPath)) planPath else planPath.parent)
            ?.toAbsolutePath()
            ?.normalize()
        var current = initial
        while (current != null) {
            if (Files.isDirectory(current.resolve("plans"))) {
                return current
            }
            current = current.parent
        }
        return initial
    }

    internal fun inferApplicationRoot(): Path? {
        val location = AppLogAssertionPluginLoader::class.java.protectionDomain
            ?.codeSource
            ?.location
            ?: return null
        val path = runCatching { Path.of(location.toURI()).toAbsolutePath().normalize() }.getOrNull()
            ?: return null
        val directory = (if (Files.isRegularFile(path)) path.parent else path) ?: return null
        return when (directory.name) {
            "lib" -> directory.parent
            else -> directory
        }?.toAbsolutePath()?.normalize()
    }
}
