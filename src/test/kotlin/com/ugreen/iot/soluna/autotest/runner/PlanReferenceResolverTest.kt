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
              - id: profile
                file: ../elements/profile.yaml
            actions:
              - tap: tap-profile
                element: profile.entry
              - input: input-nickname
                element: profile.nicknameInput
                value: "${'$'}{profile.newNickname}"
            teardownFragments:
              - app.restart
            teardownActions:
              - input: restore-nickname
                element: profile.nicknameInput
                value: "@{case.originalNickname}"
            """.trimIndent(),
        )
        write(
            root.resolve("elements/profile.yaml"),
            """
            schemaVersion: "1.0"
            id: profile-elements
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
              - id: profile
                file: ../elements/profile.yaml
            actions:
              - tap: tap-profile
                element: profile.entry
            """.trimIndent(),
        )
        write(
            root.resolve("elements/profile.yaml"),
            """
            schemaVersion: "1.0"
            id: profile-elements
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

    private fun write(
        path: Path,
        content: String,
    ) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }
}
