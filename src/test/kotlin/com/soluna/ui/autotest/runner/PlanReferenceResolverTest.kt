package com.soluna.ui.autotest.runner

import com.soluna.ui.autotest.dsl.YamlPlanParser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class PlanReferenceResolverTest {
    @Test
    fun `assembles referenced cases elements fragments and case scoped data`() {
        val root = Files.createTempDirectory("soluna-reference-resolver-test")
        write(
            root.resolve("plans/profile.yaml"),
            """
            schemaVersion: "1.0"
            id: profile-plan
            name: Profile Plan
            deviceConfig: ../devices/android.yaml
            fragmentRefs:
              - id: app
                file: ../fragments/app.yaml
            setupFragments:
              - app.restart
            app:
              id: com.example
            stages:
              - id: main
                name: Main
                setupFragments:
                  - app.restart
                caseRefs:
                  - file: ../cases/profile.yaml
            """.trimIndent(),
        )
        write(
            root.resolve("fragments/app.yaml"),
            """
            schemaVersion: "1.0"
            id: app-fragments
            fragments:
              restart:
                actions:
                  - restartApp: restart-app
                    appId: "${'$'}{app.id}"
            """.trimIndent(),
        )
        write(
            root.resolve("cases/profile.yaml"),
            """
            schemaVersion: "1.0"
            id: edit-profile
            name: Edit Profile
            dataRefs:
              - id: profile-data
                file: ../data/profile.yaml
            elementRefs:
              - id: common
                file: ../elements/common.yaml
            actions:
              - tap: tap-profile
                element: common.entry
              - input: input-nickname
                element: common.nicknameInput
                value: "${'$'}{profile.newNickname}"
            teardownFragments:
              - app.restart
            teardownActions:
              - input: restore-nickname
                element: common.nicknameInput
                value: "@{case.originalNickname}"
            """.trimIndent(),
        )
        write(
            root.resolve("elements/common.yaml"),
            """
            schemaVersion: "1.0"
            id: common-elements
            elements:
              entry:
                strategy: id
                value: com.example:id/profile
              nicknameInput:
                strategy: class
                value: android.widget.EditText
            """.trimIndent(),
        )
        write(
            root.resolve("data/profile.yaml"),
            """
            schemaVersion: "1.0"
            id: profile-data
            values:
              app:
                id: com.example
              profile:
                newNickname: SolunaTester
            """.trimIndent(),
        )

        val planPath = root.resolve("plans/profile.yaml")
        val parsed = YamlPlanParser().parse(Files.readString(planPath))
        val assembled = PlanReferenceResolver().resolve(parsed, planPath)
        val resolved = PlanParameterResolver().resolve(assembled, planPath)
        val planSetupActions = resolved.setupActions
        val stageSetupActions = resolved.stages.single().setupActions
        val case = resolved.stages.single().cases.single()
        val actions = case.actions
        val teardownActions = case.teardownActions

        assertEquals(listOf("restart-app"), planSetupActions.map { it.id })
        assertEquals("com.example", planSetupActions.single().args["appId"]?.asText())
        assertEquals(listOf("restart-app"), stageSetupActions.map { it.id })
        assertEquals("restartApp", stageSetupActions.single().keyword)
        assertEquals("com.example", stageSetupActions.single().args["appId"]?.asText())
        assertEquals(listOf("tap-profile", "input-nickname"), actions.map { it.id })
        assertEquals("com.example:id/profile", actions[0].locator?.value)
        assertEquals("android.widget.EditText", actions[1].locator?.value)
        assertEquals("SolunaTester", actions[1].value?.asText())
        assertEquals(listOf("restart-app", "restore-nickname"), teardownActions.map { it.id })
        assertEquals("com.example", teardownActions[0].args["appId"]?.asText())
        assertEquals("android.widget.EditText", teardownActions[1].locator?.value)
        assertEquals("@{case.originalNickname}", teardownActions[1].value?.asText())
    }

    @Test
    fun `selects platform specific element locator`() {
        val root = Files.createTempDirectory("soluna-platform-element-test")
        write(
            root.resolve("plans/profile.yaml"),
            """
            schemaVersion: "1.0"
            id: profile-plan
            name: Profile Plan
            deviceConfig: ../devices/android.yaml
            app:
              platform: android
            stages:
              - id: main
                name: Main
                caseRefs:
                  - file: ../cases/profile.yaml
            """.trimIndent(),
        )
        write(
            root.resolve("cases/profile.yaml"),
            """
            schemaVersion: "1.0"
            id: edit-profile
            name: Edit Profile
            elementRefs:
              - id: common
                file: ../elements/common.yaml
            actions:
              - tap: tap-profile
                element: common.entry
            """.trimIndent(),
        )
        write(
            root.resolve("elements/common.yaml"),
            """
            schemaVersion: "1.0"
            id: common-elements
            elements:
              entry:
                android:
                  strategy: id
                  value: com.example.android:id/profile
                ios:
                  strategy: accessibility id
                  value: profile_entry
            """.trimIndent(),
        )

        val planPath = root.resolve("plans/profile.yaml")
        val parsed = YamlPlanParser().parse(Files.readString(planPath))
        val assembled = PlanReferenceResolver().resolve(parsed, planPath)
        val action = assembled.stages.single().cases.single().actions.single()

        assertEquals("id", action.locator?.strategy)
        assertEquals("com.example.android:id/profile", action.locator?.value)
    }

    @Test
    fun `resolves element references inside fragment control flow`() {
        val root = Files.createTempDirectory("soluna-fragment-if-resolver-test")
        write(
            root.resolve("plans/app-state.yaml"),
            """
            schemaVersion: "1.0"
            id: app-state-plan
            name: App State Plan
            deviceConfig: ../devices/android.yaml
            fragmentRefs:
              - id: common
                file: ../fragments/app-state.yaml
            stages:
              - id: main
                name: Main
                setupFragments:
                  - common.ensureLoginPage
                cases:
                  - id: noop
                    name: Noop
                    actions:
                      - wait: noop
                        durationMs: 0
            """.trimIndent(),
        )
        write(
            root.resolve("fragments/app-state.yaml"),
            """
            schemaVersion: "1.0"
            id: app-state
            fragments:
              ensureLoginPage:
                elementRefs:
                  - id: common
                    file: ../elements/common.yaml
                actions:
                  - if:
                      assertElementAttrRegexMatch: detect-login-page
                      element: common.loginPageMarker
                      attr: text
                      pattern: "${'$'}{appState.loginPagePattern}"
                    then: []
                    else:
                      - tap: open-mine-tab
                        element: common.mineTab
            """.trimIndent(),
        )
        write(
            root.resolve("elements/common.yaml"),
            """
            schemaVersion: "1.0"
            id: app-state-elements
            elements:
              loginPageMarker:
                strategy: id
                value: com.example:id/login_marker
              mineTab:
                strategy: id
                value: com.example:id/mine_tab
            """.trimIndent(),
        )

        val planPath = root.resolve("plans/app-state.yaml")
        val parsed = YamlPlanParser().parse(Files.readString(planPath))
        val assembled = PlanReferenceResolver().resolve(parsed, planPath)
        val ifAction = assembled.stages.single().setupActions.single()

        assertEquals("if", ifAction.keyword)
        assertEquals("com.example:id/login_marker", ifAction.conditionAction?.locator?.value)
        assertEquals(null, ifAction.conditionAction?.element)
        assertEquals("com.example:id/mine_tab", ifAction.elseActions.single().locator?.value)
        assertEquals(null, ifAction.elseActions.single().element)
    }

    @Test
    fun `prepends scoped case setup actions to case setup`() {
        val root = Files.createTempDirectory("soluna-case-setup-scope-test")
        write(
            root.resolve("plans/common.yaml"),
            """
            schemaVersion: "1.0"
            id: common-plan
            name: Common Plan
            deviceConfig: ../devices/android.yaml
            fragmentRefs:
              - id: app
                file: ../fragments/app.yaml
            caseSetupActions:
              - wait:
                  id: plan-case-setup
                  durationMs: 0
            stages:
              - id: logged-in
                name: Logged In
                setupFragments:
                  - app.loggedIn
                caseSetupActions:
                  - wait:
                      id: stage-case-setup
                      durationMs: 0
                cases:
                  - id: first
                    name: First
                    caseSetupActions:
                      - wait:
                          id: case-case-setup
                          durationMs: 0
                    actions:
                      - wait:
                          id: first-noop
                          durationMs: 0
            """.trimIndent(),
        )
        write(
            root.resolve("fragments/app.yaml"),
            """
            schemaVersion: "1.0"
            id: app-fragments
            fragments:
              loggedIn:
                actions:
                  - restartApp: restart-to-stage
                    appId: com.example
            """.trimIndent(),
        )

        val planPath = root.resolve("plans/common.yaml")
        val parsed = YamlPlanParser().parse(Files.readString(planPath))
        val assembled = PlanReferenceResolver().resolve(parsed, planPath)
        val stage = assembled.stages.single()

        assertEquals(listOf("restart-to-stage"), stage.setupActions.map { it.id })
        assertEquals(listOf("plan-case-setup"), assembled.caseSetupActions.map { it.id })
        assertEquals(listOf("stage-case-setup"), stage.caseSetupActions.map { it.id })
        assertEquals(listOf("case-case-setup"), stage.cases.single().caseSetupActions.map { it.id })
        assertEquals(
            listOf("plan-case-setup", "stage-case-setup", "case-case-setup"),
            stage.cases.single().setupActions.map { it.id },
        )
    }

    private fun write(
        path: Path,
        content: String,
    ) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }
}
