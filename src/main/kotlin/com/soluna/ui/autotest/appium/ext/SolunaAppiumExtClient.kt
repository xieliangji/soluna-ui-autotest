package com.soluna.ui.autotest.appium.ext

interface SolunaAppiumExtClient {
    fun getDevice(udid: String): DeviceLookupResult

    fun listDevices(): ListDevicesResult

    fun getApp(
        udid: String,
        appId: String,
    ): AppLookupResult

    fun getWdaBundle(udid: String): WdaBundleLookupResult

    fun executeCommand(request: CommandExecuteRequest): CommandExecuteResult

    fun createLogSession(request: CreateLogSessionRequest): CreateLogSessionResult

    fun readLogSession(request: ReadLogSessionRequest): ReadLogSessionResult

    fun deleteLogSession(request: DeleteLogSessionRequest): DeleteLogSessionResult
}
