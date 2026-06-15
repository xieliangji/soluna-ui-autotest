package com.ugreen.iot.soluna.autotest.appium.ext

interface SolunaAppiumExtClient {
    fun getDevice(udid: String): DeviceLookupResult

    fun listDevices(): ListDevicesResult

    fun getWdaBundle(udid: String): WdaBundleLookupResult

    fun executeCommand(request: CommandExecuteRequest): CommandExecuteResult

    fun createLogSession(request: CreateLogSessionRequest): CreateLogSessionResult

    fun readLogSession(request: ReadLogSessionRequest): ReadLogSessionResult

    fun deleteLogSession(request: DeleteLogSessionRequest): DeleteLogSessionResult
}
