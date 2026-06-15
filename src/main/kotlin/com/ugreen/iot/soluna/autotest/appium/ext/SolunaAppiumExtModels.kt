package com.ugreen.iot.soluna.autotest.appium.ext

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class Platform(
    private val wireValue: String,
) {
    ANDROID("android"),
    IOS("ios");

    @JsonValue
    fun toWireValue(): String {
        return wireValue
    }

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromWireValue(value: String): Platform {
            return entries.firstOrNull { it.wireValue == value.lowercase() }
                ?: throw IllegalArgumentException("Unsupported platform '$value'")
        }
    }
}

data class UnifiedDeviceInfo(
    val platform: Platform,
    val udid: String,
    val name: String,
    val model: String,
    val osVersion: String,
)

data class DeviceLookupResult(
    val exists: Boolean,
    val device: UnifiedDeviceInfo? = null,
    val message: String? = null,
)

data class ListDevicesResult(
    val count: Int,
    val devices: List<UnifiedDeviceInfo>,
)

data class IosInstalledApplication(
    val bundleId: String,
    val name: String? = null,
    val version: String? = null,
    val executable: String? = null,
    val applicationType: String? = null,
)

data class WdaBundleLookupResult(
    val exists: Boolean,
    val udid: String,
    val bundleId: String? = null,
    val app: IosInstalledApplication? = null,
    val message: String? = null,
)

enum class SupportedCommandTool(
    private val wireValue: String,
) {
    ADB("adb"),
    GO_IOS("go-ios"),
    IOS("ios");

    @JsonValue
    fun toWireValue(): String {
        return wireValue
    }

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromWireValue(value: String): SupportedCommandTool {
            return entries.firstOrNull { it.wireValue == value.lowercase() }
                ?: throw IllegalArgumentException("Unsupported command tool '$value'")
        }
    }
}

data class CommandExecuteRequest(
    val tool: SupportedCommandTool,
    val args: List<String> = emptyList(),
    val timeoutMs: Long? = null,
    val maxOutputBytes: Long? = null,
)

data class CommandExecuteResult(
    val command: String,
    val args: List<String>,
    val exitCode: Int?,
    val timedOut: Boolean,
    val truncated: Boolean,
    val durationMs: Long,
    val stdout: String,
    val stderr: String,
) {
    val ok: Boolean
        get() = exitCode == 0
}

enum class LogSessionStatus(
    private val wireValue: String,
) {
    RUNNING("running"),
    STOPPED("stopped"),
    FAILED("failed");

    @JsonValue
    fun toWireValue(): String {
        return wireValue
    }

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromWireValue(value: String): LogSessionStatus {
            return entries.firstOrNull { it.wireValue == value.lowercase() }
                ?: throw IllegalArgumentException("Unsupported log session status '$value'")
        }
    }
}

enum class LogLineSource(
    private val wireValue: String,
) {
    STDOUT("stdout"),
    STDERR("stderr");

    @JsonValue
    fun toWireValue(): String {
        return wireValue
    }

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromWireValue(value: String): LogLineSource {
            return entries.firstOrNull { it.wireValue == value.lowercase() }
                ?: throw IllegalArgumentException("Unsupported log line source '$value'")
        }
    }
}

data class CreateLogSessionRequest(
    val udid: String,
    val maxBufferEntries: Int? = null,
    val maxSessionBytes: Long? = null,
    val ttlMs: Long? = null,
)

data class ReadLogSessionRequest(
    val sessionId: String,
    val cursor: Long? = null,
    val limit: Int? = null,
)

data class DeleteLogSessionRequest(
    val sessionId: String,
)

data class LogSessionSnapshot(
    val sessionId: String,
    val udid: String,
    val platform: Platform,
    val status: LogSessionStatus,
    val command: String,
    val args: List<String>,
    val startedAt: String,
    val endedAt: String? = null,
    val lastActivityAt: String,
    val ttlMs: Long,
    val nextSeq: Long,
    val minSeq: Long,
    val droppedCount: Long,
    val maxBufferEntries: Int,
    val maxSessionBytes: Long,
    val error: String? = null,
)

data class UnifiedLogEntry(
    val seq: Long,
    val ts: String,
    val platform: Platform,
    val udid: String,
    val source: LogLineSource,
    val level: String? = null,
    val tag: String? = null,
    val process: String? = null,
    val pid: Int? = null,
    val message: String,
    val raw: String,
)

data class CreateLogSessionResult(
    val session: LogSessionSnapshot,
)

data class ReadLogSessionResult(
    val session: LogSessionSnapshot,
    val cursor: Long,
    val nextCursor: Long,
    val cursorAdjusted: Boolean,
    val entries: List<UnifiedLogEntry>,
)

data class DeleteLogSessionResult(
    val sessionId: String,
    val removed: Boolean,
)
