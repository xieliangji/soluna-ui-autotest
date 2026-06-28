package io.soluna.ui.autotest.config

import io.soluna.ui.autotest.appium.ext.SolunaAppiumExtClient
import io.soluna.ui.autotest.appium.ext.SolunaAppiumExtHttpClient
import java.net.URI

class DeviceConfigResolver(
    private val extClientFactory: (URI) -> SolunaAppiumExtClient = { SolunaAppiumExtHttpClient(it) },
) {
    fun resolve(
        config: DeviceConfigDefinition,
        appiumServerUrl: String,
    ): DeviceConfigDefinition {
        val platform = config.device.platform
        val isIos = platform != null && platform.equals("ios", ignoreCase = true)
        val requiresLookup = platform == null ||
            config.device.name.isNullOrBlank() ||
            (isIos && config.device.osVersion == null)

        val client = extClientFactory(URI.create(appiumServerUrl))
        val lookup = runCatching { client.getDevice(config.device.udid) }
            .getOrElse { err ->
                if (requiresLookup) {
                    throw err
                }
                return config
            }
        val device = lookup.device
            ?: run {
                if (requiresLookup) {
                    error("Device '${config.device.udid}' was not found through soluna-ext: ${lookup.message.orEmpty()}")
                }
                return config
            }

        return config.copy(
            device = config.device.copy(
                platform = device.platform.toWireValue(),
                name = device.name.takeIf { it.isNotBlank() }
                    ?: config.device.name?.takeIf { it.isNotBlank() }
                    ?: config.device.udid,
                osVersion = config.device.osVersion ?: device.osVersion,
            ),
        )
    }
}
