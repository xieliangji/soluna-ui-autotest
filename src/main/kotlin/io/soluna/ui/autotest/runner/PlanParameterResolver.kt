package io.soluna.ui.autotest.runner

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.soluna.ui.autotest.config.ParameterDataDefinition
import io.soluna.ui.autotest.config.YamlParameterDataParser
import io.soluna.ui.autotest.core.model.ActionDefinition
import io.soluna.ui.autotest.core.model.AssertionDefinition
import io.soluna.ui.autotest.core.model.CaseDefinition
import io.soluna.ui.autotest.core.model.LocatorDefinition
import io.soluna.ui.autotest.core.model.ParameterFileRef
import io.soluna.ui.autotest.core.model.PlanDefinition
import io.soluna.ui.autotest.core.model.StageDefinition
import io.soluna.ui.autotest.core.model.WaitDefinition
import io.soluna.ui.autotest.dsl.DslParser
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
        val planAssetBaseDirs = plan.parameters.resolvedDirectories(baseDir) + baseDir
        val values = objectMapper.createObjectNode()

        mergeParameterFiles(values, plan.parameters, baseDir)
        seedPlanApp(values, plan)
        overrides.forEach { (path, value) ->
            putPath(values, path, value)
        }

        return plan.copy(
            metadata = plan.metadata.mapValues { (_, value) -> resolveNode(value, values) },
            setupActions = plan.setupActions.map { resolveAction(it, values, planAssetBaseDirs) },
            caseSetupActions = plan.caseSetupActions.map { resolveAction(it, values, planAssetBaseDirs) },
            caseTeardownActions = plan.caseTeardownActions.map { resolveAction(it, values, planAssetBaseDirs) },
            teardownActions = plan.teardownActions.map { resolveAction(it, values, planAssetBaseDirs) },
            stages = plan.stages.map { resolveStage(it, values, baseDir, planAssetBaseDirs, overrides) },
        )
    }

    private fun resolveStage(
        stage: StageDefinition,
        values: JsonNode,
        baseDir: Path,
        assetBaseDirs: List<Path>,
        overrides: Map<String, JsonNode>,
    ): StageDefinition {
        val stageValues = values.deepCopy<ObjectNode>()
        mergeInlineParameters(stageValues, stage.parameters)
        return stage.copy(
            parameters = stage.parameters.mapValues { (_, value) -> resolveNode(value, values) },
            setupActions = stage.setupActions.map { resolveAction(it, stageValues, assetBaseDirs) },
            caseSetupActions = stage.caseSetupActions.map { resolveAction(it, stageValues, assetBaseDirs) },
            caseTeardownActions = stage.caseTeardownActions.map { resolveAction(it, stageValues, assetBaseDirs) },
            teardownActions = stage.teardownActions.map { resolveAction(it, stageValues, assetBaseDirs) },
            cases = stage.cases.map { resolveCase(it, stageValues, baseDir, assetBaseDirs, overrides) },
        )
    }

    private fun resolveCase(
        case: CaseDefinition,
        values: JsonNode,
        baseDir: Path,
        inheritedAssetBaseDirs: List<Path>,
        overrides: Map<String, JsonNode>,
    ): CaseDefinition {
        val caseValues = values.deepCopy<ObjectNode>()
        mergeParameterFiles(caseValues, case.dataRefs, baseDir)
        mergeInlineParameters(caseValues, case.parameters)
        overrides.forEach { (path, value) ->
            putPath(caseValues, path, value)
        }
        val caseAssetBaseDirs = (case.dataRefs.resolvedDirectories(baseDir) + inheritedAssetBaseDirs + baseDir).distinct()
        return case.copy(
            parameters = case.parameters.mapValues { (_, value) -> resolveNode(value, caseValues) },
            setupActions = case.setupActions.map { resolveAction(it, caseValues, caseAssetBaseDirs) },
            caseSetupActions = case.caseSetupActions.map { resolveAction(it, caseValues, caseAssetBaseDirs) },
            caseTeardownActions = case.caseTeardownActions.map { resolveAction(it, caseValues, caseAssetBaseDirs) },
            teardownActions = case.teardownActions.map { resolveAction(it, caseValues, caseAssetBaseDirs) },
            actions = case.actions.map { resolveAction(it, caseValues, caseAssetBaseDirs) },
        )
    }

    private fun resolveAction(
        action: ActionDefinition,
        values: JsonNode,
        assetBaseDirs: List<Path>,
    ): ActionDefinition {
        val resolvedArgs = action.args.mapValues { (_, value) -> resolveNode(value, values) }
        return action.copy(
            target = action.target?.let { resolveString(it, values) },
            locator = action.locator?.let { resolveLocator(it, values) },
            value = action.value?.let { resolveNode(it, values) },
            wait = action.wait?.let { resolveWait(it, values) },
            assertion = action.assertion?.let { resolveAssertion(it, values) },
            resourceId = action.resourceId?.let { resolveString(it, values) },
            args = resolveVisualTemplateArg(action, resolvedArgs, assetBaseDirs),
            conditionAction = action.conditionAction?.let { resolveAction(it, values, assetBaseDirs) },
            thenActions = action.thenActions.map { resolveAction(it, values, assetBaseDirs) },
            elseActions = action.elseActions.map { resolveAction(it, values, assetBaseDirs) },
        )
    }

    private fun resolveVisualTemplateArg(
        action: ActionDefinition,
        args: Map<String, JsonNode>,
        assetBaseDirs: List<Path>,
    ): Map<String, JsonNode> {
        val template = args["template"] ?: return args
        if (!template.isTextual) {
            return args
        }
        val raw = template.asText()
        if (raw.hasRuntimeReference()) {
            return args
        }
        val resolved = resolveAssetPath(assetBaseDirs, raw)
        require(Files.exists(resolved)) {
            "Visual template file '$raw' for action '${action.id ?: action.keyword}' does not exist under: " +
                assetBaseDirs.joinToString()
        }
        return args + ("template" to TextNode(resolved.toString()))
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

    private fun mergeInlineParameters(
        values: ObjectNode,
        parameters: Map<String, JsonNode>,
    ) {
        parameters.forEach { (name, value) ->
            val resolved = resolveNode(value, values)
            if (name.contains('.')) {
                putPath(values, name, resolved)
            } else {
                mergeNamedParameter(values, name, resolved)
            }
        }
    }

    private fun mergeNamedParameter(
        target: ObjectNode,
        name: String,
        value: JsonNode,
    ) {
        val existing = target.get(name)
        if (existing is ObjectNode && value.isObject) {
            mergeObject(existing, value)
        } else {
            target.set<JsonNode>(name, value.deepCopy())
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

    private fun resolveAssetPath(
        baseDirs: List<Path>,
        file: String,
    ): Path {
        val path = Path.of(file)
        if (path.isAbsolute) {
            return path.normalize()
        }
        val normalizedBaseDirs = baseDirs.ifEmpty { listOf(Path.of(".").toAbsolutePath().normalize()) }
        return normalizedBaseDirs
            .map { it.resolve(path).normalize() }
            .firstOrNull { Files.exists(it) }
            ?: normalizedBaseDirs.first().resolve(path).normalize()
    }

    private fun List<ParameterFileRef>.resolvedDirectories(baseDir: Path): List<Path> {
        return map { ref ->
            val path = resolvePath(baseDir, ref.file)
            path.parent ?: baseDir
        }.distinct()
    }

    private fun String.hasRuntimeReference(): Boolean {
        return contains("\${") || contains("@{")
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
