package com.ugreen.iot.soluna.autotest.runner

import com.ugreen.iot.soluna.autotest.core.model.ActionDefinition
import com.ugreen.iot.soluna.autotest.core.model.CaseDefinition
import com.ugreen.iot.soluna.autotest.core.model.ElementCatalogDefinition
import com.ugreen.iot.soluna.autotest.core.model.ElementFileRef
import com.ugreen.iot.soluna.autotest.core.model.FragmentCatalogDefinition
import com.ugreen.iot.soluna.autotest.core.model.FragmentDefinition
import com.ugreen.iot.soluna.autotest.core.model.LocatorDefinition
import com.ugreen.iot.soluna.autotest.core.model.ParameterFileRef
import com.ugreen.iot.soluna.autotest.core.model.PlanDefinition
import com.ugreen.iot.soluna.autotest.core.model.StageDefinition
import com.ugreen.iot.soluna.autotest.dsl.DslParser
import com.ugreen.iot.soluna.autotest.dsl.YamlCaseParser
import com.ugreen.iot.soluna.autotest.dsl.YamlElementCatalogParser
import com.ugreen.iot.soluna.autotest.dsl.YamlFragmentCatalogParser
import com.fasterxml.jackson.databind.node.TextNode
import java.nio.file.Files
import java.nio.file.Path

class PlanReferenceResolver(
    private val caseParser: DslParser<CaseDefinition> = YamlCaseParser(),
    private val elementCatalogParser: DslParser<ElementCatalogDefinition> = YamlElementCatalogParser(),
    private val fragmentCatalogParser: DslParser<FragmentCatalogDefinition> = YamlFragmentCatalogParser(),
) {
    fun resolve(
        plan: PlanDefinition,
        planPath: Path,
        platform: String? = plan.app?.platform,
    ): PlanDefinition {
        val planBaseDir = planPath.parent ?: Path.of(".").toAbsolutePath().normalize()
        val planAssetBaseDirs = plan.parameters.resolvedDirectories(planBaseDir) + planBaseDir
        val fragments = loadFragments(plan, planBaseDir, platform, planAssetBaseDirs)
        val planCaseSetupActions =
            resolveFragments(plan.caseSetupFragments, fragments) + plan.caseSetupActions.map { action ->
                resolveAction(action, emptyMap(), planAssetBaseDirs)
            }
        val planCaseTeardownActions =
            resolveFragments(plan.caseTeardownFragments, fragments) + plan.caseTeardownActions.map { action ->
                resolveAction(action, emptyMap(), planAssetBaseDirs)
            }
        return plan.copy(
            setupActions = resolveFragments(plan.setupFragments, fragments) + plan.setupActions.map { action ->
                resolveAction(action, emptyMap(), planAssetBaseDirs)
            },
            caseSetupActions = planCaseSetupActions,
            caseTeardownActions = planCaseTeardownActions,
            teardownActions = resolveFragments(plan.teardownFragments, fragments) + plan.teardownActions.map { action ->
                resolveAction(action, emptyMap(), planAssetBaseDirs)
            },
            stages = plan.stages.map { stage ->
                resolveStage(
                    stage = stage,
                    planBaseDir = planBaseDir,
                    platform = platform,
                    fragments = fragments,
                    planAssetBaseDirs = planAssetBaseDirs,
                    inheritedCaseSetupActions = planCaseSetupActions,
                    inheritedCaseTeardownActions = planCaseTeardownActions,
                )
            },
        )
    }

    private fun resolveStage(
        stage: StageDefinition,
        planBaseDir: Path,
        platform: String?,
        fragments: Map<String, FragmentDefinition>,
        planAssetBaseDirs: List<Path>,
        inheritedCaseSetupActions: List<ActionDefinition>,
        inheritedCaseTeardownActions: List<ActionDefinition>,
    ): StageDefinition {
        val stageCaseSetupActions =
            resolveFragments(stage.caseSetupFragments, fragments) + stage.caseSetupActions.map { action ->
                resolveAction(action, emptyMap(), planAssetBaseDirs)
            }
        val stageCaseTeardownActions =
            resolveFragments(stage.caseTeardownFragments, fragments) + stage.caseTeardownActions.map { action ->
                resolveAction(action, emptyMap(), planAssetBaseDirs)
            }
        val inlineCases = stage.cases.map { case ->
            resolveCase(
                case = case,
                caseBaseDir = planBaseDir,
                platform = platform,
                fragments = fragments,
                planAssetBaseDirs = planAssetBaseDirs,
                inheritedCaseSetupActions = inheritedCaseSetupActions + stageCaseSetupActions,
                inheritedCaseTeardownActions = stageCaseTeardownActions + inheritedCaseTeardownActions,
            )
        }
        val referencedCases = stage.caseRefs.flatMap { ref ->
            val casePath = resolvePath(planBaseDir, ref.file)
            if (!Files.exists(casePath)) {
                if (ref.required) {
                    error("Required case file '${ref.file}' does not exist")
                }
                return@flatMap emptyList()
            }
            val parsedCase = caseParser.parse(Files.readString(casePath))
            val resolvedDataRefs = parsedCase.dataRefs.map { it.withResolvedPath(casePath.parent ?: planBaseDir) }
            val parsedWithResolvedData = parsedCase.copy(dataRefs = resolvedDataRefs)
            val case = ref.id?.let { parsedWithResolvedData.copy(id = it) } ?: parsedWithResolvedData
            listOf(
                resolveCase(
                    case = case,
                    caseBaseDir = casePath.parent ?: planBaseDir,
                    platform = platform,
                    fragments = fragments,
                    planAssetBaseDirs = planAssetBaseDirs,
                    inheritedCaseSetupActions = inheritedCaseSetupActions + stageCaseSetupActions,
                    inheritedCaseTeardownActions = stageCaseTeardownActions + inheritedCaseTeardownActions,
                ),
            )
        }

        val cases = inlineCases + referencedCases
        return stage.copy(
            setupActions = resolveFragments(stage.setupFragments, fragments) + stage.setupActions.map { action ->
                resolveAction(action, emptyMap(), planAssetBaseDirs)
            },
            caseSetupActions = stageCaseSetupActions,
            caseTeardownActions = stageCaseTeardownActions,
            teardownActions = resolveFragments(stage.teardownFragments, fragments) + stage.teardownActions.map { action ->
                resolveAction(action, emptyMap(), planAssetBaseDirs)
            },
            cases = cases,
        )
    }

    private fun resolveCase(
        case: CaseDefinition,
        caseBaseDir: Path,
        platform: String?,
        fragments: Map<String, FragmentDefinition>,
        planAssetBaseDirs: List<Path>,
        inheritedCaseSetupActions: List<ActionDefinition> = emptyList(),
        inheritedCaseTeardownActions: List<ActionDefinition> = emptyList(),
    ): CaseDefinition {
        val elements = loadElements(case.elementRefs, caseBaseDir, platform)
        val resolvedDataRefs = case.dataRefs.map { ref ->
            if (Path.of(ref.file).isAbsolute) ref else ref.withResolvedPath(caseBaseDir)
        }
        val caseAssetBaseDirs = (resolvedDataRefs.resolvedDirectories(caseBaseDir) + planAssetBaseDirs + caseBaseDir).distinct()
        val setupActions = resolveFragments(case.setupFragments, fragments).map { action ->
            resolveAction(action, elements, caseAssetBaseDirs)
        }
        val caseSetupActions = resolveFragments(case.caseSetupFragments, fragments).map { action ->
            resolveAction(action, elements, caseAssetBaseDirs)
        } + case.caseSetupActions.map { action ->
            resolveAction(action, elements, caseAssetBaseDirs)
        }
        val teardownActions = resolveFragments(case.teardownFragments, fragments).map { action ->
            resolveAction(action, elements, caseAssetBaseDirs)
        }
        val caseTeardownActions = resolveFragments(case.caseTeardownFragments, fragments).map { action ->
            resolveAction(action, elements, caseAssetBaseDirs)
        } + case.caseTeardownActions.map { action ->
            resolveAction(action, elements, caseAssetBaseDirs)
        }
        val actions = case.actions.map { action ->
            resolveAction(action, elements, caseAssetBaseDirs)
        }
        return case.copy(
            dataRefs = resolvedDataRefs,
            caseSetupActions = caseSetupActions,
            caseTeardownActions = caseTeardownActions,
            setupActions = inheritedCaseSetupActions + caseSetupActions + setupActions + case.setupActions.map { action ->
                resolveAction(action, elements, caseAssetBaseDirs)
            },
            teardownActions = teardownActions + case.teardownActions.map { action ->
                resolveAction(action, elements, caseAssetBaseDirs)
            } + caseTeardownActions + inheritedCaseTeardownActions,
            actions = actions,
        )
    }

    private fun resolveAction(
        action: ActionDefinition,
        elements: Map<String, LocatorDefinition>,
        assetBaseDirs: List<Path>,
    ): ActionDefinition {
        val elementRef = action.element
        val locator = if (elementRef != null) {
            require(action.locator == null) {
                "Action '${action.id ?: action.keyword}' must not define both element and locator"
            }
            elements[elementRef]
                ?: error("Element '$elementRef' is not defined for action '${action.id ?: action.keyword}'")
        } else {
            action.locator
        }
        val resolvedArgs = resolveActionAssetArgs(action, assetBaseDirs)
        return action.copy(
            locator = locator,
            element = if (locator != null) null else action.element,
            args = resolvedArgs,
            conditionAction = action.conditionAction?.let { resolveAction(it, elements, assetBaseDirs) },
            thenActions = action.thenActions.map { resolveAction(it, elements, assetBaseDirs) },
            elseActions = action.elseActions.map { resolveAction(it, elements, assetBaseDirs) },
        )
    }

    private fun resolveActionAssetArgs(
        action: ActionDefinition,
        assetBaseDirs: List<Path>,
    ): Map<String, com.fasterxml.jackson.databind.JsonNode> {
        val template = action.args["template"] ?: return action.args
        if (!template.isTextual) {
            return action.args
        }
        val raw = template.asText()
        if (raw.hasRuntimeReference()) {
            return action.args
        }
        val resolved = resolveAssetPath(assetBaseDirs, raw)
        require(Files.exists(resolved)) {
            "Visual template file '$raw' for action '${action.id ?: action.keyword}' does not exist under: " +
                assetBaseDirs.joinToString()
        }
        return action.args + ("template" to TextNode(resolved.toString()))
    }

    private fun loadFragments(
        plan: PlanDefinition,
        planBaseDir: Path,
        platform: String?,
        planAssetBaseDirs: List<Path>,
    ): Map<String, FragmentDefinition> {
        val result = linkedMapOf<String, FragmentDefinition>()
        plan.fragmentRefs.forEach { ref ->
            val path = resolvePath(planBaseDir, ref.file)
            if (!Files.exists(path)) {
                if (ref.required) {
                    error("Required fragment file '${ref.file}' does not exist")
                }
                return@forEach
            }
            val catalog = fragmentCatalogParser.parse(Files.readString(path))
            catalog.fragments.forEach { (fragmentId, fragment) ->
                val fragmentBaseDir = path.parent ?: planBaseDir
                val elements = loadElements(fragment.elementRefs, fragmentBaseDir, platform)
                val fragmentAssetBaseDirs = (planAssetBaseDirs + fragmentBaseDir).distinct()
                val resolvedFragment = fragment.copy(
                    actions = fragment.actions.map { action -> resolveAction(action, elements, fragmentAssetBaseDirs) },
                )
                putUnique(result, "${ref.id}.$fragmentId", resolvedFragment, "fragment")
                putUnique(result, "${catalog.id}.$fragmentId", resolvedFragment, "fragment")
            }
        }
        return result
    }

    private fun resolveFragments(
        refs: List<String>,
        fragments: Map<String, FragmentDefinition>,
    ): List<ActionDefinition> {
        return refs.flatMap { ref ->
            fragments[ref]?.actions ?: error("Fragment '$ref' is not defined")
        }
    }

    private fun loadElements(
        refs: List<ElementFileRef>,
        caseBaseDir: Path,
        platform: String?,
    ): Map<String, LocatorDefinition> {
        val result = linkedMapOf<String, LocatorDefinition>()
        refs.forEach { ref ->
            val path = resolvePath(caseBaseDir, ref.file)
            if (!Files.exists(path)) {
                if (ref.required) {
                    error("Required element file '${ref.file}' does not exist")
                }
                return@forEach
            }
            val catalog = elementCatalogParser.parse(Files.readString(path))
            catalog.elements.forEach { (elementId, element) ->
                val locator = element.locatorFor(platform)
                putUnique(result, "${ref.id}.$elementId", locator, "element")
                putUnique(result, "${catalog.id}.$elementId", locator, "element")
            }
        }
        return result
    }

    private fun ParameterFileRef.withResolvedPath(baseDir: Path): ParameterFileRef {
        return copy(file = resolvePath(baseDir, file).toString())
    }

    private fun List<ParameterFileRef>.resolvedDirectories(baseDir: Path): List<Path> {
        return map { ref ->
            val path = resolvePath(baseDir, ref.file)
            path.parent ?: baseDir
        }.distinct()
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

    private fun String.hasRuntimeReference(): Boolean {
        return contains("\${") || contains("@{")
    }

    private fun <T> putUnique(
        values: MutableMap<String, T>,
        key: String,
        value: T,
        type: String,
    ) {
        require(!values.containsKey(key)) { "Duplicate $type reference '$key'" }
        values[key] = value
    }
}
