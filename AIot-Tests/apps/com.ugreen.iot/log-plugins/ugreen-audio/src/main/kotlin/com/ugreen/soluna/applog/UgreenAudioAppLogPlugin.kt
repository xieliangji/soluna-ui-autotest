package com.ugreen.soluna.applog

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.soluna.ui.autotest.extension.applog.AppLogAssertion
import com.soluna.ui.autotest.extension.applog.AppLogAssertionInput
import com.soluna.ui.autotest.extension.applog.AppLogAssertionPlugin
import com.soluna.ui.autotest.extension.applog.AppLogAssertionResult
import java.nio.file.Files
import java.util.Locale

class UgreenAudioAppLogPlugin : AppLogAssertionPlugin {
    override val id: String = "ugreen-audio"

    override fun assertion(name: String): AppLogAssertion? {
        return when (name) {
            BleWriteTriggeredAssertion.legacyName,
            BleWriteTriggeredAssertion.name,
            BleWriteTriggeredAssertion.androidName,
            -> BleWriteTriggeredAssertion
            else -> null
        }
    }
}

object BleWriteTriggeredAssertion : AppLogAssertion {
    const val legacyName: String = "ios-ble-write-triggered"
    const val androidName: String = "android-ble-write-triggered"
    override val name: String = "ble-write-triggered"

    private val defaultIosSpec = MatchSpec(
        containsAll = listOf(
            "CBMsgIdCharacteristicWriteValue",
            "Writing value without response",
            "com.ugreen.iot-central",
        ),
        containsAny = emptyList(),
        regex = null,
        messageRegex = null,
        rawRegex = null,
        caseSensitive = false,
    )

    private val defaultAndroidSpec = MatchSpec(
        containsAll = listOf(
            "[蓝牙下发]",
            "发送 ble 数据 data:",
            "[蓝牙上报]",
            "Payload 已解密",
        ),
        containsAny = emptyList(),
        regex = null,
        messageRegex = null,
        rawRegex = null,
        caseSensitive = false,
    )

    override fun evaluate(input: AppLogAssertionInput): AppLogAssertionResult {
        return when (input.context.platform?.lowercase(Locale.ROOT)) {
            "ios" -> {
                val spec = MatchSpec.from(input.args) ?: defaultIosSpec
                evaluateWindow(input, spec, "Matched iOS BLE write trigger")
            }
            "android" -> {
                val spec = MatchSpec.fromPlatform(input.args, "android")
                    ?: MatchSpec.from(input.args)?.takeUnless { it.sameRequiredTerms(defaultIosSpec) }
                    ?: defaultAndroidSpec
                evaluateWindow(input, spec, "Matched Android BLE payload exchange")
            }
            else -> AppLogAssertionResult.failed(
                "$name only supports iOS and Android App log captures; platform=${input.context.platform}",
            )
        }
    }
}

private fun evaluateWindow(
    input: AppLogAssertionInput,
    spec: MatchSpec,
    successPrefix: String,
): AppLogAssertionResult {
    val corpus = LogCorpus.read(input.logFile)
    return if (spec.matches(corpus)) {
        AppLogAssertionResult.passed("$successPrefix: ${spec.description()}")
    } else {
        AppLogAssertionResult.failed(
            "No app log window matched ${spec.description()}; " +
                "entries=${corpus.entryCount}; sample=${corpus.sample()}",
        )
    }
}

private data class MatchSpec(
    val containsAll: List<String>,
    val containsAny: List<String>,
    val regex: Regex?,
    val messageRegex: Regex?,
    val rawRegex: Regex?,
    val caseSensitive: Boolean,
) {
    fun matches(corpus: LogCorpus): Boolean {
        val combined = normalize(corpus.combined)
        val messages = normalize(corpus.messages)
        val raw = normalize(corpus.raw)
        if (containsAll.any { term -> !combined.contains(normalize(term)) }) {
            return false
        }
        if (containsAny.isNotEmpty() && containsAny.none { term -> combined.contains(normalize(term)) }) {
            return false
        }
        if (regex != null && !regex.containsMatchIn(corpus.combined)) {
            return false
        }
        if (messageRegex != null && !messageRegex.containsMatchIn(corpus.messages)) {
            return false
        }
        if (rawRegex != null && !rawRegex.containsMatchIn(corpus.raw)) {
            return false
        }
        return true
    }

    fun description(): String {
        return buildList {
            if (containsAll.isNotEmpty()) add("containsAll=$containsAll")
            if (containsAny.isNotEmpty()) add("containsAny=$containsAny")
            regex?.let { add("regex=${it.pattern}") }
            messageRegex?.let { add("messageRegex=${it.pattern}") }
            rawRegex?.let { add("rawRegex=${it.pattern}") }
            add("caseSensitive=$caseSensitive")
        }.joinToString(", ")
    }

    fun sameRequiredTerms(other: MatchSpec): Boolean {
        return normalizeList(containsAll) == normalizeList(other.containsAll) &&
            normalizeList(containsAny) == normalizeList(other.containsAny) &&
            regex?.pattern == other.regex?.pattern &&
            messageRegex?.pattern == other.messageRegex?.pattern &&
            rawRegex?.pattern == other.rawRegex?.pattern
    }

    private fun normalize(value: String): String {
        return if (caseSensitive) value else value.lowercase(Locale.ROOT)
    }

    private fun normalizeList(values: List<String>): List<String> {
        return values.map { normalize(it) }.sorted()
    }

    companion object {
        fun from(args: JsonNode?): MatchSpec? {
            return from(args, "")
        }

        fun fromPlatform(args: JsonNode?, platformPrefix: String): MatchSpec? {
            return from(args, platformPrefix)
        }

        private fun from(
            args: JsonNode?,
            platformPrefix: String,
        ): MatchSpec? {
            if (args == null || args.isMissingNode || args.isNull) {
                return null
            }
            val prefix = platformPrefix.takeIf { it.isNotBlank() } ?: ""
            val caseSensitive = args.path(key(prefix, "caseSensitive")).takeIf { it.isBoolean }?.asBoolean()
                ?: args.path("caseSensitive").takeIf { it.isBoolean }?.asBoolean()
                ?: false
            val regexOptions = if (caseSensitive) setOf(RegexOption.DOT_MATCHES_ALL) else {
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            }
            val containsAll = buildList {
                addAll(args.path(key(prefix, "contains")).strings())
                addAll(args.path(key(prefix, "containsAll")).strings())
                args.path(key(prefix, "command")).textOrNull()?.let { add(it) }
                args.path(key(prefix, "status")).textOrNull()?.let { add(it) }
            }
            val containsAny = args.path(key(prefix, "containsAny")).strings()
            val spec = MatchSpec(
                containsAll = containsAll,
                containsAny = containsAny,
                regex = args.path(key(prefix, "regex")).textOrNull()?.let { Regex(it, regexOptions) },
                messageRegex = args.path(key(prefix, "messageRegex")).textOrNull()?.let { Regex(it, regexOptions) },
                rawRegex = args.path(key(prefix, "rawRegex")).textOrNull()?.let { Regex(it, regexOptions) },
                caseSensitive = caseSensitive,
            )
            return spec.takeIf {
                it.containsAll.isNotEmpty() ||
                    it.containsAny.isNotEmpty() ||
                    it.regex != null ||
                    it.messageRegex != null ||
                    it.rawRegex != null
            }
        }

        private fun key(
            prefix: String,
            field: String,
        ): String {
            if (prefix.isBlank()) {
                return field
            }
            return prefix + field.replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
    }
}

private data class LogCorpus(
    val entryCount: Int,
    val combined: String,
    val messages: String,
    val raw: String,
    private val messageSamples: List<String>,
) {
    fun sample(): String {
        return messageSamples.joinToString(separator = " | ").ifBlank { "<empty>" }
    }

    companion object {
        private val mapper = ObjectMapper()

        fun read(logFile: java.nio.file.Path): LogCorpus {
            val entries = Files.readAllLines(logFile).filter { it.isNotBlank() }
            val parsed = entries.map { line -> LogEntryView.parse(line, mapper) }
            return LogCorpus(
                entryCount = parsed.size,
                combined = parsed.flatMap { it.searchParts }.joinToString(separator = "\n"),
                messages = parsed.joinToString(separator = "\n") { it.message },
                raw = parsed.joinToString(separator = "\n") { it.raw },
                messageSamples = parsed.map { it.message }.filter { it.isNotBlank() }.take(5),
            )
        }
    }
}

private data class LogEntryView(
    val message: String,
    val raw: String,
    val searchParts: List<String>,
) {
    companion object {
        fun parse(
            line: String,
            mapper: ObjectMapper,
        ): LogEntryView {
            val node = runCatching { mapper.readTree(line) }.getOrNull()
            val message = node?.path("message")?.textOrNull() ?: line
            val raw = node?.path("raw")?.textOrNull() ?: line
            val parts = buildList {
                add(message)
                add(raw)
                node?.path("tag")?.textOrNull()?.let { add(it) }
                node?.path("process")?.textOrNull()?.let { add(it) }
                node?.path("level")?.textOrNull()?.let { add(it) }
            }
            return LogEntryView(message = message, raw = raw, searchParts = parts)
        }
    }
}

private fun JsonNode.textOrNull(): String? {
    return takeIf { isTextual }?.asText()?.takeIf { it.isNotBlank() }
}

private fun JsonNode.strings(): List<String> {
    return when {
        isTextual -> listOf(asText()).filter { it.isNotBlank() }
        isArray -> mapNotNull { node -> node.textOrNull() }
        else -> emptyList()
    }
}
