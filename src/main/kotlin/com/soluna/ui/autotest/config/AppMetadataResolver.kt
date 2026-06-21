package com.soluna.ui.autotest.config

import com.soluna.ui.autotest.appium.ext.SolunaAppiumExtClient
import com.soluna.ui.autotest.appium.ext.SolunaAppiumExtHttpClient
import com.soluna.ui.autotest.core.model.PlanDefinition
import java.net.URI

class AppMetadataResolver(
    private val extClientFactory: (URI) -> SolunaAppiumExtClient = { SolunaAppiumExtHttpClient(it) },
) {
    fun resolve(
        plan: PlanDefinition,
        deviceConfig: DeviceConfigDefinition,
        appiumServerUrl: String,
    ): PlanDefinition {
        val app = plan.app ?: return plan
        val appId = app.id?.takeIf { it.isNotBlank() } ?: return plan
        val lookup = runCatching {
            extClientFactory(URI.create(appiumServerUrl)).getApp(deviceConfig.device.udid, appId)
        }.getOrNull() ?: return plan
        val installedApp = lookup.app ?: return plan
        val resolvedName = installedApp.name?.takeIf { it.isNotBlank() }
        val resolvedPlatform = installedApp.platform.toWireValue().takeIf { it.isNotBlank() }
        if (resolvedName == null && resolvedPlatform == app.platform) {
            return plan
        }
        return plan.copy(
            app = app.copy(
                name = resolvedName ?: app.name,
                platform = resolvedPlatform ?: app.platform,
            ),
        )
    }
}
