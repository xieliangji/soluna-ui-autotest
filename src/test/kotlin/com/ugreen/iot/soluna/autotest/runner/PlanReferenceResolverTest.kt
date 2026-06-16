package com.ugreen.iot.soluna.autotest.runner

import com.ugreen.iot.soluna.autotest.dsl.YamlPlanParser
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
        assertEquals("com.example:id/mine_tab", ifAction.elseActions.single().locator?.value)
    }

    @Test
    fun `resolves AIot asset project profile plans`() {
        val plans = listOf(
            Path.of("AIot-Tests/apps/com.ugreen.iot/plans/profile/nickname-android.yaml")
                .toAbsolutePath()
                .normalize() to "android",
            Path.of("AIot-Tests/apps/com.ugreen.iot/plans/profile/nickname-ios.yaml")
                .toAbsolutePath()
                .normalize() to "ios",
        )

        plans.forEach { (planPath, platform) ->
            val parsed = YamlPlanParser().parse(Files.readString(planPath))
            val assembled = PlanReferenceResolver().resolve(parsed, planPath)
            val resolved = PlanParameterResolver().resolve(assembled, planPath)
            val stage = resolved.stages.single()
            val case = stage.cases.single()

            assertEquals("app.restart", stage.setupFragments.single())
            assertEquals(listOf("restart-app"), stage.setupActions.map { it.id })
            assertEquals("com.ugreen.iot", stage.setupActions.single().args["appId"]?.asText())
            assertEquals("SolunaTester", case.actions.first { it.id == "input-new-nickname" }.value?.asText())
            assertEquals(platform, resolved.app?.platform)
            assertEquals(
                expectedProfileEntryLocator(platform),
                case.actions.first { it.id == "open-profile-page" }.locator?.value,
            )
            assertEquals(
                expectedNicknameValueAttr(platform),
                case.actions.first { it.id == "assert-new-nickname" }.args["attr"]?.asText(),
            )
        }
    }

    private fun expectedProfileEntryLocator(platform: String): String {
        return when (platform) {
            "android" -> "com.ugreen.iot:id/flow_user_top"
            "ios" -> "(//XCUIElementTypeTable/XCUIElementTypeStaticText[number(@y) < 280])[1]"
            else -> error("Unsupported platform $platform")
        }
    }

    private fun expectedNicknameValueAttr(platform: String): String {
        return when (platform) {
            "android" -> "text"
            "ios" -> "name/label"
            else -> error("Unsupported platform $platform")
        }
    }

    private fun write(
        path: Path,
        content: String,
    ) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }
}
