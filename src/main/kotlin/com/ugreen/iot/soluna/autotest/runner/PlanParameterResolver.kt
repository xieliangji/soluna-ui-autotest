package com.ugreen.iot.soluna.autotest.runner

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.ugreen.iot.soluna.autotest.config.ParameterDataDefinition
import com.ugreen.iot.soluna.autotest.config.YamlParameterDataParser
import com.ugreen.iot.soluna.autotest.core.model.ActionDefinition
import com.ugreen.iot.soluna.autotest.core.model.AssertionDefinition
import com.ugreen.iot.soluna.autotest.core.model.CaseDefinition
import com.ugreen.iot.soluna.autotest.core.model.LocatorDefinition
import com.ugreen.iot.soluna.autotest.core.model.ParameterFileRef
import com.ugreen.iot.soluna.autotest.core.model.PlanDefinition
import com.ugreen.iot.soluna.autotest.core.model.StageDefinition
import com.ugreen.iot.soluna.autotest.core.model.WaitDefinition
import com.ugreen.iot.soluna.autotest.dsl.DslParser
import java.nio.file.Files
import java.nio.file.Path

class PlanParameterResolver(
    private val parameterDataParser: DslParser<ParameterDataDefinition> = YamlParameterDataParser(),
    private val objectMapper: ObjectMapper = defaultObjectMapper(),
) {
    fun resolve(
        plan: PlanDefinition,
        planPath: Path,
        overrides: Map<String, JsonNode> = emptyMap(),
    ): PlanDefinition {
        val baseDir = planPath.parent ?: Path.of(".").toAbsolutePath().normalize()
        val values = objectMapper.createObjectNode()

        mergeParameterFiles(values, plan.parameters, baseDir)
        seedPlanApp(values, plan)
        overrides.forEach { (path, value) ->
            putPath(values, path, value)
        }

        return plan.copy(
            metadata = plan.metadata.mapValues { (_, value) -> resolveNode(value, values) },
            setupActions = plan.setupActions.map { resolveAction(it, values) },
            teardownActions = plan.teardownActions.map { resolveAction(it, values) },
            stages = plan.stages.map { resolveStage(it, values, baseDir, overrides) },
        )
    }

    private fun resolveStage(
        stage: StageDefinition,
        values: JsonNode,
        baseDir: Path,
        overrides: Map<String, JsonNode>,
    ): StageDefinition {
        return stage.copy(
            parameters = stage.parameters.mapValues { (_, value) -> resolveNode(value, values) },
            setupActions = stage.setupActions.map { resolveAction(it, values) },
            teardownActions = stage.teardownActions.map { resolveAction(it, values) },
            cases = stage.cases.map { resolveCase(it, values, baseDir, overrides) },
        )
    }

    private fun resolveCase(
        case: CaseDefinition,
        values: JsonNode,
        baseDir: Path,
        overrides: Map<String, JsonNode>,
    ): CaseDefinition {
        val caseValues = values.deepCopy<ObjectNode>()
        mergeParameterFiles(caseValues, case.dataRefs, baseDir)
        overrides.forEach { (path, value) ->
            putPath(caseValues, path, value)
        }
        return case.copy(
            parameters = case.parameters.mapValues { (_, value) -> resolveNode(value, caseValues) },
            setupActions = case.setupActions.map { resolveAction(it, caseValues) },
            teardownActions = case.teardownActions.map { resolveAction(it, caseValues) },
            actions = case.actions.map { resolveAction(it, caseValues) },
        )
    }

    private fun resolveAction(
        action: ActionDefinition,
        values: JsonNode,
    ): ActionDefinition {
        return action.copy(
            target = action.target?.let { resolveString(it, values) },
            locator = action.locator?.let { resolveLocator(it, values) },
            value = action.value?.let { resolveNode(it, values) },
            wait = action.wait?.let { resolveWait(it, values) },
            assertion = action.assertion?.let { resolveAssertion(it, values) },
            resourceId = action.resourceId?.let { resolveString(it, values) },
            args = action.args.mapValues { (_, value) -> resolveNode(value, values) },
        )
    }

    private fun resolveLocator(
        locator: LocatorDefinition,
        values: JsonNode,
    ): LocatorDefinition {
        return locator.copy(
            strategy = resolveString(locator.strategy, values),
            value = resolveString(locator.value, values),
            args = locator.args.mapValues { (_, value) -> resolveNode(value, values) },
        )
    }

    private fun resolveWait(
        wait: WaitDefinition,
        values: JsonNode,
    ): WaitDefinition {
        return wait.copy(
            condition = wait.condition.mapValues { (_, value) -> resolveNode(value, values) },
        )
    }

    private fun resolveAssertion(
        assertion: AssertionDefinition,
        values: JsonNode,
    ): AssertionDefinition {
        return assertion.copy(
            target = assertion.target?.let { resolveString(it, values) },
            expected = assertion.expected?.let { resolveNode(it, values) },
            args = assertion.args.mapValues { (_, value) -> resolveNode(value, values) },
        )
    }

    private fun resolveNode(
        node: JsonNode,
        values: JsonNode,
    ): JsonNode {
        if (node.isTextual) {
            val raw = node.asText()
            val exact = exactReference.matchEntire(raw)
            if (exact != null) {
                return lookup(values, exact.groupValues[1]).deepCopy()
            }
            return TextNode(resolveString(raw, values))
        }
        if (node.isObject) {
            val result = objectMapper.createObjectNode()
            node.fields().forEachRemaining { (name, child) ->
                result.set<JsonNode>(name, resolveNode(child, values))
            }
            return result
        }
        if (node.isArray) {
            val result = objectMapper.createArrayNode()
            node.forEach { child -> result.add(resolveNode(child, values)) }
            return result
        }
        return node.deepCopy()
    }

    private fun resolveString(
        value: String,
        values: JsonNode,
    ): String {
        return parameterReference.replace(value) { match ->
            lookup(values, match.groupValues[1]).asText()
        }
    }

    private fun lookup(
        values: JsonNode,
        path: String,
    ): JsonNode {
        val result = path.split('.')
            .fold(values) { current, segment -> current.path(segment) }
        if (result.isMissingNode) {
            error("Parameter '$path' is not defined")
        }
        return result
    }

    private fun mergeParameterFiles(
        values: ObjectNode,
        refs: List<ParameterFileRef>,
        baseDir: Path,
    ) {
        refs.forEach { ref ->
            val path = resolvePath(baseDir, ref.file)
            if (!Files.exists(path)) {
                if (ref.required) {
                    error("Required parameter file '${ref.file}' does not exist")
                }
                return@forEach
            }
            mergeObject(values, parameterDataParser.parse(Files.readString(path)).values)
        }
    }

    private fun seedPlanApp(
        values: ObjectNode,
        plan: PlanDefinition,
    ) {
        val app = plan.app ?: return
        app.id?.let { putPath(values, "app.id", objectMapper.valueToTree(it)) }
        app.name?.let { putPath(values, "app.name", objectMapper.valueToTree(it)) }
        app.platform?.let { putPath(values, "app.platform", objectMapper.valueToTree(it)) }
        app.reset?.let { putPath(values, "app.reset", objectMapper.valueToTree(it)) }
    }

    private fun mergeObject(
        target: ObjectNode,
        source: JsonNode,
    ) {
        require(source.isObject) { "Parameter values must be an object" }
        source.fields().forEachRemaining { (name, value) ->
            val existing = target.get(name)
            if (existing is ObjectNode && value.isObject) {
                mergeObject(existing, value)
            } else {
                target.set<JsonNode>(name, value.deepCopy())
            }
        }
    }

    private fun putPath(
        target: ObjectNode,
        path: String,
        value: JsonNode,
    ) {
        val segments = path.split('.').filter { it.isNotBlank() }
        require(segments.isNotEmpty()) { "Parameter override path must not be blank" }
        var current = target
        segments.dropLast(1).forEach { segment ->
            val next = current.get(segment)
            current = if (next is ObjectNode) {
                next
            } else {
                objectMapper.createObjectNode().also { current.set<ObjectNode>(segment, it) }
            }
        }
        current.set<JsonNode>(segments.last(), value)
    }

    private fun resolvePath(
        baseDir: Path,
        file: String,
    ): Path {
        val path = Path.of(file)
        return if (path.isAbsolute) {
            path.normalize()
        } else {
            baseDir.resolve(path).normalize()
        }
    }

    companion object {
        private val parameterReference = Regex("""\$\{([^}]+)}""")
        private val exactReference = Regex("""^\$\{([^}]+)}$""")

        fun defaultObjectMapper(): ObjectMapper {
            return ObjectMapper()
                .registerModule(KotlinModule.Builder().build())
        }
    }
}
