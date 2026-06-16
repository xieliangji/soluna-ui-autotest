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
        val fragments = loadFragments(plan, planBaseDir, platform)
        return plan.copy(
            setupActions = resolveFragments(plan.setupFragments, fragments),
            teardownActions = resolveFragments(plan.teardownFragments, fragments),
            stages = plan.stages.map { stage ->
                resolveStage(
                    stage = stage,
                    planBaseDir = planBaseDir,
                    platform = platform,
                    fragments = fragments,
                )
            },
        )
    }

    private fun resolveStage(
        stage: StageDefinition,
        planBaseDir: Path,
        platform: String?,
        fragments: Map<String, FragmentDefinition>,
    ): StageDefinition {
        val inlineCases = stage.cases.map { case ->
            resolveCase(
                case = case,
                caseBaseDir = planBaseDir,
                platform = platform,
                fragments = fragments,
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
            val case = ref.id?.let { parsedCase.copy(id = it) } ?: parsedCase
            listOf(
                resolveCase(
                    case = case.copy(
                        dataRefs = case.dataRefs.map { it.withResolvedPath(casePath.parent ?: planBaseDir) },
                    ),
                    caseBaseDir = casePath.parent ?: planBaseDir,
                    platform = platform,
                    fragments = fragments,
                ),
            )
        }

        return stage.copy(
            setupActions = resolveFragments(stage.setupFragments, fragments),
            teardownActions = resolveFragments(stage.teardownFragments, fragments),
            cases = inlineCases + referencedCases,
        )
    }

    private fun resolveCase(
        case: CaseDefinition,
        caseBaseDir: Path,
        platform: String?,
        fragments: Map<String, FragmentDefinition>,
    ): CaseDefinition {
        val elements = loadElements(case.elementRefs, caseBaseDir, platform)
        val setupActions = resolveFragments(case.setupFragments, fragments).map { action ->
            resolveActionElement(action, elements)
        }
        val teardownActions = resolveFragments(case.teardownFragments, fragments).map { action ->
            resolveActionElement(action, elements)
        }
        val actions = case.actions.map { action ->
            resolveActionElement(action, elements)
        }
        return case.copy(
            setupActions = setupActions,
            teardownActions = teardownActions + case.teardownActions.map { action ->
                resolveActionElement(action, elements)
            },
            actions = actions,
        )
    }

    private fun resolveActionElement(
        action: ActionDefinition,
        elements: Map<String, LocatorDefinition>,
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
        return action.copy(
            locator = locator,
            conditionAction = action.conditionAction?.let { resolveActionElement(it, elements) },
            thenActions = action.thenActions.map { resolveActionElement(it, elements) },
            elseActions = action.elseActions.map { resolveActionElement(it, elements) },
        )
    }

    private fun loadFragments(
        plan: PlanDefinition,
        planBaseDir: Path,
        platform: String?,
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
                val resolvedFragment = fragment.copy(
                    actions = fragment.actions.map { action -> resolveActionElement(action, elements) },
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
