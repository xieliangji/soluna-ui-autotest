package com.ugreen.iot.soluna.autotest.appium.wda

import com.ugreen.iot.soluna.autotest.appium.ext.SolunaAppiumExtClient
import com.ugreen.iot.soluna.autotest.appium.ext.SolunaAppiumExtHttpClient
import java.net.URI

interface WdaBundleResolver {
    fun resolve(request: WdaBundleResolveRequest): WdaBundleResolution
}

data class WdaBundleResolveRequest(
    val appiumServerUrl: String,
    val udid: String,
    val bundleId: String?,
    val testRunnerBundleId: String?,
    val xctestConfig: String?,
)

data class WdaBundleResolution(
    val bundleId: String?,
    val testRunnerBundleId: String?,
    val xctestConfig: String?,
    val source: WdaBundleResolutionSource,
)

enum class WdaBundleResolutionSource {
    CONFIG,
    SOLUNA_EXT,
}

class SolunaExtWdaBundleResolver(
    private val extClientFactory: (URI) -> SolunaAppiumExtClient = { SolunaAppiumExtHttpClient(it) },
) : WdaBundleResolver {
    override fun resolve(request: WdaBundleResolveRequest): WdaBundleResolution {
        val configuredBundleId = request.bundleId?.takeIf { it.isNotBlank() }
        val configuredTestRunnerBundleId = request.testRunnerBundleId?.takeIf { it.isNotBlank() }
        val configuredXctestConfig = request.xctestConfig?.takeIf { it.isNotBlank() }
        if (configuredBundleId != null && configuredTestRunnerBundleId != null && configuredXctestConfig != null) {
            return WdaBundleResolution(
                bundleId = configuredBundleId,
                testRunnerBundleId = configuredTestRunnerBundleId,
                xctestConfig = configuredXctestConfig,
                source = WdaBundleResolutionSource.CONFIG,
            )
        }

        val client = extClientFactory(URI.create(request.appiumServerUrl))
        val lookup = client.getWdaBundle(request.udid)
        val detectedTestRunnerBundleId = lookup.bundleId?.takeIf { it.isNotBlank() }
            ?: error(
                "WDA runner bundle id was not resolved from soluna-ext for device '${request.udid}': " +
                    lookup.message.orEmpty(),
            )
        val testRunnerBundleId = configuredTestRunnerBundleId ?: detectedTestRunnerBundleId
        val xctestBundleId = configuredBundleId ?: inferXctestBundleId(testRunnerBundleId)
        val xctestConfig = configuredXctestConfig ?: inferXctestConfig(
            runnerBundleId = testRunnerBundleId,
            runnerName = lookup.app?.name,
            runnerExecutable = lookup.app?.executable,
        )

        return WdaBundleResolution(
            bundleId = xctestBundleId,
            testRunnerBundleId = testRunnerBundleId,
            xctestConfig = xctestConfig,
            source = WdaBundleResolutionSource.SOLUNA_EXT,
        )
    }

    private fun inferXctestBundleId(testRunnerBundleId: String): String {
        return testRunnerBundleId
    }

    private fun inferXctestConfig(
        runnerBundleId: String,
        runnerName: String?,
        runnerExecutable: String?,
    ): String {
        val runnerBaseName = listOfNotNull(runnerExecutable, runnerName, runnerBundleId.substringAfterLast("."))
            .map { value -> value.removeSuffix("-Runner").removeSuffix(".xctrunner").trim() }
            .firstOrNull { it.isNotBlank() && !it.equals("xctrunner", ignoreCase = true) }
            ?: "WebDriverAgentRunner"
        return "$runnerBaseName.xctest"
    }
}
