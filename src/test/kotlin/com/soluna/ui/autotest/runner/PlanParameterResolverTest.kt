package com.soluna.ui.autotest.runner

import com.fasterxml.jackson.databind.ObjectMapper
import com.soluna.ui.autotest.core.model.ActionDefinition
import com.soluna.ui.autotest.core.model.LocatorDefinition
import com.soluna.ui.autotest.core.model.ParameterFileRef
import com.soluna.ui.autotest.core.model.PlanDefinition
import com.soluna.ui.autotest.core.model.StageDefinition
import com.soluna.ui.autotest.dsl.YamlPlanParser
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class PlanParameterResolverTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `resolves parameter references from plan data files and overrides`() {
        val root = Files.createTempDirectory("soluna-params-test")
        val dataPath = root.resolve("data/profile.yaml")
        Files.createDirectories(dataPath.parent)
        Files.writeString(
            dataPath,
            """
            schemaVersion: "1.0"
            id: profile
            values:
              profile:
                originalNickname: OldName
              locators:
                nicknameText: "com.example:id/nickname"
            """.trimIndent(),
        )
        val elementPath = root.resolve("elements/common.yaml")
        Files.createDirectories(elementPath.parent)
        Files.writeString(
            elementPath,
            """
            schemaVersion: "1.0"
            id: common-elements
            elements:
              nickname:
                strategy: id
                value: "${'$'}{locators.nicknameText}"
            """.trimIndent(),
        )
        val planPath = root.resolve("plans/profile.yaml")
        Files.createDirectories(planPath.parent)
        Files.writeString(
            planPath,
            """
            schemaVersion: "1.0"
            id: profile-plan
            name: Profile Plan
            productModel: Test Product
            deviceConfig: ../devices/android.yaml
            parameters:
              - id: profile
                file: ../data/profile.yaml
            stages:
              - id: main
                name: Main
                cases:
                  - id: edit
                    name: Edit
                    elementRefs:
                      - id: common
                        file: ../elements/common.yaml
                    actions:
                      - input: input
                        element: common.nickname
                        value: "${'$'}{profile.newNickname}"
                      - assertElementAttrEquals: assert
                        element: common.nickname
                        attr: text
                        expected: "${'$'}{profile.originalNickname}"
            """.trimIndent(),
        )

        val plan = YamlPlanParser().parse(Files.readString(planPath))
        val assembled = PlanReferenceResolver().resolve(plan, planPath)
        val resolved = PlanParameterResolver().resolve(
            plan = assembled,
            planPath = planPath,
            overrides = mapOf("profile.newNickname" to objectMapper.valueToTree("SolunaTester")),
        )

        val actions = resolved.stages.single().cases.single().actions
        assertEquals("com.example:id/nickname", actions.first().locator?.value)
        assertEquals("SolunaTester", actions.first().value?.asText())
        assertEquals("OldName", actions.last().value?.asText())
    }

    @Test
    fun `overrides take precedence over case scoped data refs`() {
        val root = Files.createTempDirectory("soluna-case-params-test")
        val dataPath = root.resolve("data/profile.yaml")
        Files.createDirectories(dataPath.parent)
        Files.writeString(
            dataPath,
            """
            schemaVersion: "1.0"
            id: profile
            values:
              profile:
                newNickname: FromCaseData
            """.trimIndent(),
        )
        val elementPath = root.resolve("elements/common.yaml")
        Files.createDirectories(elementPath.parent)
        Files.writeString(
            elementPath,
            """
            schemaVersion: "1.0"
            id: common-elements
            elements:
              nickname:
                strategy: id
                value: nickname
            """.trimIndent(),
        )
        val planPath = root.resolve("plans/profile.yaml")
        Files.createDirectories(planPath.parent)
        Files.writeString(
            planPath,
            """
            schemaVersion: "1.0"
            id: profile-plan
            name: Profile Plan
            productModel: Test Product
            deviceConfig: ../devices/android.yaml
            stages:
              - id: main
                name: Main
                cases:
                  - id: edit
                    name: Edit
                    dataRefs:
                      - id: profile
                        file: ../data/profile.yaml
                    elementRefs:
                      - id: common
                        file: ../elements/common.yaml
                    actions:
                      - input: input
                        element: common.nickname
                        value: "${'$'}{profile.newNickname}"
            """.trimIndent(),
        )

        val plan = YamlPlanParser().parse(Files.readString(planPath))
        val assembled = PlanReferenceResolver().resolve(plan, planPath)
        val resolved = PlanParameterResolver().resolve(
            plan = assembled,
            planPath = planPath,
            overrides = mapOf("profile.newNickname" to objectMapper.valueToTree("FromOverride")),
        )

        val action = resolved.stages.single().cases.single().actions.single()
        assertEquals("FromOverride", action.value?.asText())
    }

    @Test
    fun `resolves visual template parameter references relative to data file directory`() {
        val root = Files.createTempDirectory("soluna-template-param-test")
        val templatePath = root.resolve("data/common/templates/back-icon.png")
        Files.createDirectories(templatePath.parent)
        Files.write(templatePath, byteArrayOf(1, 2, 3))
        Files.writeString(
            root.resolve("data/common/mine.yaml"),
            """
            schemaVersion: "1.0"
            id: mine
            values:
              mine:
                visualTemplates:
                  backIcon: templates/back-icon.png
            """.trimIndent(),
        )
        Files.createDirectories(root.resolve("plans"))
        val planPath = root.resolve("plans/common.yaml")
        Files.writeString(
            planPath,
            """
            schemaVersion: "1.0"
            id: common-plan
            name: Common Plan
            productModel: Test Product
            deviceConfig: ../devices/ios.yaml
            stages:
              - id: main
                name: Main
                cases:
                  - id: feedback
                    name: Feedback
                    dataRefs:
                      - id: mine
                        file: ../data/common/mine.yaml
                    actions:
                      - tapVisualTemplate:
                          id: tap-back
                          template: "${'$'}{mine.visualTemplates.backIcon}"
            """.trimIndent(),
        )

        val plan = YamlPlanParser().parse(Files.readString(planPath))
        val assembled = PlanReferenceResolver().resolve(plan, planPath)
        val resolved = PlanParameterResolver().resolve(plan = assembled, planPath = planPath)

        val action = resolved.stages.single().cases.single().actions.single()
        assertEquals(templatePath.normalize().toString(), action.args["template"]?.asText())
    }

    @Test
    fun `stage and case inline parameters resolve later action references`() {
        val root = Files.createTempDirectory("soluna-inline-params-test")
        val dataPath = root.resolve("data/app-state.yaml")
        Files.createDirectories(dataPath.parent)
        Files.writeString(
            dataPath,
            """
            schemaVersion: "1.0"
            id: app-state
            values:
              appState:
                mine:
                  entryIndex: 5
            """.trimIndent(),
        )
        val elementPath = root.resolve("elements/common.yaml")
        Files.createDirectories(elementPath.parent)
        Files.writeString(
            elementPath,
            """
            schemaVersion: "1.0"
            id: common-elements
            elements:
              mineEntry:
                strategy: xpath
                value: "(//*[@resource-id='com.example:id/recy']/android.view.ViewGroup)[${'$'}{appState.mine.entryIndex}]"
            """.trimIndent(),
        )
        val planPath = root.resolve("plans/common.yaml")
        Files.createDirectories(planPath.parent)
        Files.writeString(
            planPath,
            """
            schemaVersion: "1.0"
            id: common-plan
            name: Common Plan
            productModel: Test Product
            deviceConfig: ../devices/android.yaml
            parameters:
              - id: appState
                file: ../data/app-state.yaml
            stages:
              - id: guest
                name: Guest
                parameters:
                  appState:
                    mine:
                      entryIndex: 4
                cases:
                  - id: open
                    name: Open
                    parameters:
                      expectedIndex: "${'$'}{appState.mine.entryIndex}"
                    elementRefs:
                      - id: common
                        file: ../elements/common.yaml
                    actions:
                      - tap:
                          id: tap-entry
                          element: common.mineEntry
                      - input:
                          id: save-index
                          element: common.mineEntry
                          value: "${'$'}{expectedIndex}"
            """.trimIndent(),
        )

        val plan = YamlPlanParser().parse(Files.readString(planPath))
        val assembled = PlanReferenceResolver().resolve(plan, planPath)
        val resolved = PlanParameterResolver().resolve(plan = assembled, planPath = planPath)

        val actions = resolved.stages.single().cases.single().actions
        assertEquals("(//*[@resource-id='com.example:id/recy']/android.view.ViewGroup)[4]", actions.first().locator?.value)
        assertEquals("4", actions.last().value?.asText())
    }

    @Test
    fun `resolves parameter references inside fragment control flow actions`() {
        val root = Files.createTempDirectory("soluna-if-params-test")
        val dataPath = root.resolve("data/state.yaml")
        Files.createDirectories(dataPath.parent)
        Files.writeString(
            dataPath,
            """
            schemaVersion: "1.0"
            id: state
            values:
              state:
                sourcePattern: Ready
                inputValue: SolunaTester
              locators:
                input: com.example:id/input
            """.trimIndent(),
        )
        val planPath = root.resolve("plans/app-state.yaml")
        Files.createDirectories(planPath.parent)
        Files.writeString(planPath, "schemaVersion: \"1.0\"")
        val plan = PlanDefinition(
            schemaVersion = "1.0",
            id = "if-plan",
            name = "If Plan",
            parameters = listOf(ParameterFileRef(id = "state", file = "../data/state.yaml")),
            stages = listOf(
                StageDefinition(
                    id = "main",
                    name = "Main",
                    setupActions = listOf(
                        ActionDefinition(
                            id = "ensure-state",
                            keyword = "if",
                            conditionAction = ActionDefinition(
                                id = "detect-ready",
                                keyword = "assertSourceRegexMatch",
                                value = objectMapper.valueToTree("${'$'}{state.sourcePattern}"),
                            ),
                            thenActions = listOf(
                                ActionDefinition(
                                    id = "input-value",
                                    keyword = "input",
                                    locator = LocatorDefinition(strategy = "id", value = "${'$'}{locators.input}"),
                                    value = objectMapper.valueToTree("${'$'}{state.inputValue}"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val resolved = PlanParameterResolver().resolve(plan, planPath)
        val action = resolved.stages.single().setupActions.single()

        assertEquals("Ready", action.conditionAction?.value?.asText())
        assertEquals("com.example:id/input", action.thenActions.single().locator?.value)
        assertEquals("SolunaTester", action.thenActions.single().value?.asText())
    }
}
