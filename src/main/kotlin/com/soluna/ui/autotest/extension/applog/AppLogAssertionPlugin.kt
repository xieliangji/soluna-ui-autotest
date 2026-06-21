package com.soluna.ui.autotest.extension.applog

import com.fasterxml.jackson.databind.JsonNode
import java.nio.file.Path
import java.util.ServiceLoader

interface AppLogAssertionPlugin {
    val id: String

    fun assertion(name: String): AppLogAssertion?
}

interface AppLogAssertion {
    val name: String

    fun evaluate(input: AppLogAssertionInput): AppLogAssertionResult
}

data class AppLogAssertionInput(
    val pluginId: String,
    val assertionName: String,
    val logFile: Path,
    val source: JsonNode?,
    val args: JsonNode?,
    val context: AppLogAssertionRunContext,
)

data class AppLogAssertionRunContext(
    val runId: String,
    val planId: String,
    val stageId: String?,
    val caseId: String?,
    val platform: String?,
    val udid: String?,
)

data class AppLogAssertionResult(
    val passed: Boolean,
    val message: String? = null,
    val error: String? = null,
) {
    companion object {
        fun passed(message: String? = null): AppLogAssertionResult {
            return AppLogAssertionResult(passed = true, message = message)
        }

        fun failed(error: String): AppLogAssertionResult {
            return AppLogAssertionResult(passed = false, error = error)
        }
    }
}

interface AppLogAssertionRegistry {
    fun find(
        pluginId: String,
        assertionName: String,
    ): AppLogAssertion?
}

class CompositeAppLogAssertionRegistry(
    private val registries: List<AppLogAssertionRegistry>,
) : AppLogAssertionRegistry {
    override fun find(
        pluginId: String,
        assertionName: String,
    ): AppLogAssertion? {
        return registries.firstNotNullOfOrNull { registry ->
            registry.find(pluginId, assertionName)
        }
    }
}

class ServiceLoaderAppLogAssertionRegistry private constructor(
    private val loaders: List<ServiceLoader<AppLogAssertionPlugin>>,
) : AppLogAssertionRegistry {
    constructor(
        loader: ServiceLoader<AppLogAssertionPlugin> = ServiceLoader.load(AppLogAssertionPlugin::class.java),
    ) : this(listOf(loader))

    override fun find(
        pluginId: String,
        assertionName: String,
    ): AppLogAssertion? {
        return loaders
            .asSequence()
            .flatMap { loader -> loader.asSequence() }
            .firstOrNull { plugin -> plugin.id == pluginId }
            ?.assertion(assertionName)
    }

    companion object {
        fun fromClassLoader(classLoader: ClassLoader): ServiceLoaderAppLogAssertionRegistry {
            return ServiceLoaderAppLogAssertionRegistry(
                ServiceLoader.load(AppLogAssertionPlugin::class.java, classLoader),
            )
        }
    }
}
