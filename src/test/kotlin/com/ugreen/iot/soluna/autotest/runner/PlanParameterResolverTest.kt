package com.ugreen.iot.soluna.autotest.runner

import com.fasterxml.jackson.databind.ObjectMapper
import com.ugreen.iot.soluna.autotest.core.model.ActionDefinition
import com.ugreen.iot.soluna.autotest.core.model.LocatorDefinition
import com.ugreen.iot.soluna.autotest.core.model.ParameterFileRef
import com.ugreen.iot.soluna.autotest.core.model.PlanDefinition
import com.ugreen.iot.soluna.autotest.core.model.StageDefinition
import com.ugreen.iot.soluna.autotest.dsl.YamlPlanParser
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
