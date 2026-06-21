package com.soluna.ui.autotest.appium.action

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.soluna.ui.autotest.appium.ext.CreateLogSessionRequest
import com.soluna.ui.autotest.appium.ext.DeleteLogSessionRequest
import com.soluna.ui.autotest.appium.ext.ReadLogSessionRequest
import com.soluna.ui.autotest.appium.ext.SolunaAppiumExtClient
import com.soluna.ui.autotest.appium.ext.UnifiedLogEntry
import com.soluna.ui.autotest.core.execution.ActionExecutionResult
import com.soluna.ui.autotest.core.execution.ActionExecutor
import com.soluna.ui.autotest.core.execution.ExecutionContext
import com.soluna.ui.autotest.core.model.ActionDefinition
import com.soluna.ui.autotest.extension.applog.AppLogAssertionInput
import com.soluna.ui.autotest.extension.applog.AppLogAssertionRegistry
import com.soluna.ui.autotest.extension.applog.AppLogAssertionRunContext
import com.soluna.ui.autotest.extension.applog.ServiceLoaderAppLogAssertionRegistry
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class CaptureAppLogStartActionExecutor(
    private val extClient: SolunaAppiumExtClient?,
    private val udid: String?,
    private val platform: String?,
    private val objectMapper: ObjectMapper = appLogObjectMapper(),
) : ActionExecutor {
    override val keyword: String = "captureAppLogStart"

    override fun execute(
        action: ActionDefinition,
        context: ExecutionContext,
    ): ActionExecutionResult {
        val client = extClient
            ?: return ActionExecutionResult.failed("captureAppLogStart requires soluna-ext client")
        val resolvedUdid = action.args["udid"]?.textOrNull()?.resolveRuntimeText(context)
            ?: udid
            ?: return ActionExecutionResult.failed("captureAppLogStart requires device udid")
        val saveAs = action.args["saveAs"]?.textOrNull()
            ?: return ActionExecutionResult.failed("captureAppLogStart requires args.saveAs")
        val scope = action.args["scope"]?.textOrNull() ?: "case"
        val created = client.createLogSession(
            CreateLogSessionRequest(
                udid = resolvedUdid,
                maxBufferEntries = action.args["maxBufferEntries"]?.intOrNull(),
                maxSessionBytes = action.args["maxSessionBytes"]?.longOrNull(),
                ttlMs = action.args["ttlMs"]?.longOrNull(),
                filter = action.args["filter"],
            ),
        )
        val descriptor = JsonNodeFactory.instance.objectNode().also { node ->
            node.put("sessionId", created.session.sessionId)
            node.put("udid", created.session.udid)
            node.put("platform", created.session.platform.toWireValue())
            node.put("cursor", created.session.nextSeq)
            node.put("startedAt", created.session.startedAt)
            node.put("status", created.session.status.toWireValue())
            action.args["filter"]?.let { node.set<JsonNode>("filter", it.deepCopy()) }
        }
        context.variables.set(
            scope = scope,
            name = saveAs,
            value = descriptor,
            caseId = context.caseVariableScopeId(),
        )
        context.variables.set(
            scope = "case",
            name = "lastAppLogCapture",
            value = descriptor,
            caseId = context.caseVariableScopeId(),
        )
        return ActionExecutionResult.passed("app log capture started")
    }
}

class CaptureAppLogEndActionExecutor(
    private val extClient: SolunaAppiumExtClient?,
    private val sink: PlanResourceSink = NoOpPlanResourceSink,
    private val objectMapper: ObjectMapper = appLogObjectMapper(),
) : ActionExecutor {
    override val keyword: String = "captureAppLogEnd"

    override fun execute(
        action: ActionDefinition,
        context: ExecutionContext,
    ): ActionExecutionResult {
        val client = extClient
            ?: return ActionExecutionResult.failed("captureAppLogEnd requires soluna-ext client")
        val source = action.sourceDescriptor(context)
        val sessionId = source.requiredText("sessionId", action)
        var cursor = source.path("cursor").longOrNull() ?: 0
        val readLimit = action.args["readLimit"]?.intOrNull() ?: 500
        val maxReadBatches = action.args["maxReadBatches"]?.intOrNull() ?: 20
        val maxEntries = action.args["maxEntries"]?.intOrNull() ?: 5_000
        require(readLimit > 0) { "captureAppLogEnd action '${action.id ?: action.keyword}' requires readLimit > 0" }
        require(maxReadBatches > 0) { "captureAppLogEnd action '${action.id ?: action.keyword}' requires maxReadBatches > 0" }
        require(maxEntries > 0) { "captureAppLogEnd action '${action.id ?: action.keyword}' requires maxEntries > 0" }

        val entries = mutableListOf<UnifiedLogEntry>()
        var batches = 0
        while (batches < maxReadBatches && entries.size < maxEntries) {
            val batch = client.readLogSession(
                ReadLogSessionRequest(
                    sessionId = sessionId,
                    cursor = cursor,
                    limit = readLimit.coerceAtMost(maxEntries - entries.size),
                ),
            )
            batches += 1
            entries += batch.entries
            val nextCursor = batch.nextCursor
            if (nextCursor <= cursor || batch.entries.isEmpty()) {
                cursor = nextCursor
                break
            }
            cursor = nextCursor
        }
        val deleteResult = runCatching {
            client.deleteLogSession(DeleteLogSessionRequest(sessionId))
        }
        if (deleteResult.isFailure) {
            return ActionExecutionResult.failed(
                "captureAppLogEnd failed to delete log session '$sessionId': ${deleteResult.exceptionOrNull()?.message}",
            )
        }

        val bytes = entries.joinToString(separator = "\n", postfix = "\n") { entry ->
            objectMapper.writeValueAsString(entry)
        }.toByteArray(StandardCharsets.UTF_8)
        val captured = sink.accept(
            ExplicitPlanResource(
                runId = context.runId,
                planId = context.plan.id,
                actionId = action.id,
                resourceId = action.resourceId ?: action.args["resourceId"]?.textOrNull(),
                name = action.name,
                type = "log",
                purpose = "app_log_capture",
                contentType = "application/x-ndjson",
                bytes = bytes,
            ),
        ) ?: return ActionExecutionResult.failed("captureAppLogEnd requires a plan resource sink")

        val descriptor = JsonNodeFactory.instance.objectNode().also { node ->
            node.put("path", captured.localPath.toString())
            node.put("resourceId", captured.resourceId)
            node.put("fileName", captured.fileName)
            node.put("contentType", captured.contentType)
            node.put("sizeBytes", captured.sizeBytes)
            node.put("entries", entries.size)
            node.put("sessionId", sessionId)
            node.put("cursor", cursor)
            source.get("platform")?.takeIf { it.isTextual }?.let { node.put("platform", it.asText()) }
            source.get("udid")?.takeIf { it.isTextual }?.let { node.put("udid", it.asText()) }
        }
        context.variables.set(
            scope = "case",
            name = "lastAppLogFile",
            value = descriptor,
            caseId = context.caseVariableScopeId(),
        )

        val saveAs = action.args["saveAs"]?.textOrNull()
        if (saveAs != null) {
            val scope = action.args["scope"]?.textOrNull() ?: "case"
            context.variables.set(
                scope = scope,
                name = saveAs,
                value = descriptor,
                caseId = context.caseVariableScopeId(),
            )
        }
        return ActionExecutionResult.passed("captured ${entries.size} app log entries")
    }
}

class CustomAssertAppLogActionExecutor(
    private val registry: AppLogAssertionRegistry = ServiceLoaderAppLogAssertionRegistry(),
) : ActionExecutor {
    override val keyword: String = "customAssertAppLog"

    override fun execute(
        action: ActionDefinition,
        context: ExecutionContext,
    ): ActionExecutionResult {
        val pluginId = action.args["plugin"]?.textOrNull()
            ?: return ActionExecutionResult.failed("customAssertAppLog requires args.plugin")
        val assertionName = action.args["assertion"]?.textOrNull()
            ?: return ActionExecutionResult.failed("customAssertAppLog requires args.assertion")
        val assertion = registry.find(pluginId, assertionName)
            ?: return ActionExecutionResult.failed("No app log assertion '$assertionName' registered by plugin '$pluginId'")
        val source = action.sourceDescriptor(context, defaultReference = "@{case.lastAppLogFile}")
        val logFile = source.path("path").textOrNull()
            ?.let { Path.of(it.resolveRuntimeText(context)) }
            ?: return ActionExecutionResult.failed("customAssertAppLog requires source.path")
        if (!Files.isRegularFile(logFile)) {
            return ActionExecutionResult.failed("customAssertAppLog source file does not exist: $logFile")
        }
        val result = assertion.evaluate(
            AppLogAssertionInput(
                pluginId = pluginId,
                assertionName = assertionName,
                logFile = logFile,
                source = source,
                args = action.args["args"],
                context = AppLogAssertionRunContext(
                    runId = context.runId,
                    planId = context.plan.id,
                    stageId = context.currentStageId,
                    caseId = context.currentCaseId,
                    platform = source.path("platform").textOrNull(),
                    udid = source.path("udid").textOrNull(),
                ),
            ),
        )
        return if (result.passed) {
            ActionExecutionResult.passed(result.message ?: "custom app log assertion passed")
        } else {
            ActionExecutionResult.failed(result.error ?: "custom app log assertion failed")
        }
    }
}

private fun ActionDefinition.sourceDescriptor(
    context: ExecutionContext,
    defaultReference: String = "@{case.lastAppLogCapture}",
): JsonNode {
    val source = args["source"]?.textOrNull() ?: defaultReference
    val exact = runtimeVariableReference.matchEntire(source)
    if (exact != null) {
        return context.lookupRuntimeVariable(exact.groupValues[1], exact.groupValues[2])
    }
    return JsonNodeFactory.instance.objectNode().put("path", source.resolveRuntimeText(context))
}

private fun JsonNode.requiredText(
    fieldName: String,
    action: ActionDefinition,
): String {
    return path(fieldName).textOrNull()
        ?: error("${action.keyword} action '${action.id ?: action.keyword}' requires source.$fieldName")
}

private fun JsonNode.textOrNull(): String? {
    return takeIf { isTextual }?.asText()
}

private fun JsonNode.intOrNull(): Int? {
    return when {
        isIntegralNumber -> asInt()
        isTextual -> asText().toIntOrNull()
        else -> null
    }
}

private fun JsonNode.longOrNull(): Long? {
    return when {
        isIntegralNumber -> asLong()
        isTextual -> asText().toLongOrNull()
        else -> null
    }
}

private fun String.resolveRuntimeText(context: ExecutionContext): String {
    val exact = runtimeVariableReference.matchEntire(this)
    if (exact != null) {
        return context.lookupRuntimeVariable(exact.groupValues[1], exact.groupValues[2]).asText()
    }
    return runtimeVariableReference.replace(this) { match ->
        context.lookupRuntimeVariable(match.groupValues[1], match.groupValues[2]).asText()
    }
}

private fun ExecutionContext.lookupRuntimeVariable(
    scope: String,
    name: String,
): JsonNode {
    return variables.get(
        scope = scope,
        name = name,
        caseId = caseVariableScopeId(),
    ) ?: error("Runtime variable '@{$scope.$name}' is not defined")
}

private fun appLogObjectMapper(): ObjectMapper {
    return ObjectMapper().registerModule(KotlinModule.Builder().build())
}

private val runtimeVariableReference = Regex("""@\{(plan|case)\.([^}]+)}""")
