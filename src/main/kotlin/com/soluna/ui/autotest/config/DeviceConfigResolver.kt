package com.soluna.ui.autotest.config

import com.soluna.ui.autotest.appium.ext.SolunaAppiumExtClient
import com.soluna.ui.autotest.appium.ext.SolunaAppiumExtHttpClient
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
        val needsLookup = platform == null ||
            config.device.name == null ||
            (isIos && config.device.osVersion == null)
        if (!needsLookup) {
            return config
        }

        val client = extClientFactory(URI.create(appiumServerUrl))
        val lookup = client.getDevice(config.device.udid)
        val device = lookup.device
            ?: error("Device '${config.device.udid}' was not found through soluna-ext: ${lookup.message.orEmpty()}")

        return config.copy(
            device = config.device.copy(
                platform = config.device.platform ?: device.platform.toWireValue(),
                name = config.device.name ?: device.name,
                osVersion = config.device.osVersion ?: device.osVersion,
            ),
        )
    }
}
