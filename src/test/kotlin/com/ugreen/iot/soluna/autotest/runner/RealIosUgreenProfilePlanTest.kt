package com.ugreen.iot.soluna.autotest.runner

import com.fasterxml.jackson.databind.ObjectMapper
import com.ugreen.iot.soluna.autotest.core.execution.ExecutionStatus
import com.ugreen.iot.soluna.autotest.report.LocalReportWriter
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RealIosUgreenProfilePlanTest {
    @Test
    fun `runs UGREEN profile nickname YAML plan on real iOS device`() {
        if (System.getenv("SOLUNA_IOS_UGREEN_PROFILE_SMOKE") != "true") {
            return
        }

        val result = PlanRunner().run(
            PlanRunRequest(
                runId = System.getenv("SOLUNA_RUN_ID") ?: "ugreen-profile-ios-local",
                planPath = Path.of(
                    System.getenv("SOLUNA_IOS_UGREEN_PROFILE_PLAN_PATH")
                        ?: "examples/plans/ugreen-profile-nickname-ios.yaml",
                ),
                reportWriter = LocalReportWriter(),
                parameterOverrides = newNicknameOverride(),
            ),
        )

        assertEquals(ExecutionStatus.PASSED, result.executionResult.status)
        assertNotNull(result.report)
        assertTrue(result.traceArtifacts.isEmpty(), "Expected no trace artifacts for passed run")
        result.artifactUploads?.let { uploads ->
            assertTrue(uploads.completed, "Expected artifact upload drain to complete")
            assertEquals(0, uploads.failedCount, "Expected no failed artifact uploads")
            assertEquals(0, uploads.abandonedCount, "Expected no abandoned artifact uploads")
        }
        if (result.notifications.isNotEmpty()) {
            assertTrue(
                result.notifications.all { it.delivered },
                "Expected all notifications to be delivered, got ${result.notifications}",
            )
        }
    }

    private fun newNicknameOverride(): Map<String, com.fasterxml.jackson.databind.JsonNode> {
        val nickname = System.getenv("SOLUNA_IOS_UGREEN_PROFILE_NEW_NICKNAME") ?: return emptyMap()
        return mapOf("profile.newNickname" to ObjectMapper().valueToTree(nickname))
    }
}
