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
            IosBleWriteTriggeredAssertion.name -> IosBleWriteTriggeredAssertion
            else -> null
        }
    }
}

object IosBleWriteTriggeredAssertion : AppLogAssertion {
    override val name: String = "ios-ble-write-triggered"

    override fun evaluate(input: AppLogAssertionInput): AppLogAssertionResult {
        if (!input.context.platform.equals("ios", ignoreCase = true)) {
            return AppLogAssertionResult.failed(
                "$name is only valid for iOS App log captures; platform=${input.context.platform}",
            )
        }
        val spec = MatchSpec.from(input.args)
            ?: MatchSpec(
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
        return evaluateWindow(input, spec, "Matched iOS BLE write trigger")
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

    private fun normalize(value: String): String {
        return if (caseSensitive) value else value.lowercase(Locale.ROOT)
    }

    companion object {
        fun from(args: JsonNode?): MatchSpec? {
            if (args == null || args.isMissingNode || args.isNull) {
                return null
            }
            val caseSensitive = args.path("caseSensitive").takeIf { it.isBoolean }?.asBoolean() ?: false
            val regexOptions = if (caseSensitive) setOf(RegexOption.DOT_MATCHES_ALL) else {
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            }
            val containsAll = buildList {
                addAll(args.path("contains").strings())
                addAll(args.path("containsAll").strings())
                args.path("command").textOrNull()?.let { add(it) }
                args.path("status").textOrNull()?.let { add(it) }
            }
            val containsAny = args.path("containsAny").strings()
            val spec = MatchSpec(
                containsAll = containsAll,
                containsAny = containsAny,
                regex = args.path("regex").textOrNull()?.let { Regex(it, regexOptions) },
                messageRegex = args.path("messageRegex").textOrNull()?.let { Regex(it, regexOptions) },
                rawRegex = args.path("rawRegex").textOrNull()?.let { Regex(it, regexOptions) },
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
