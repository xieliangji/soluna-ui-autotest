package com.soluna.ui.autotest.runner

import com.fasterxml.jackson.databind.ObjectMapper
import com.soluna.ui.autotest.core.execution.ExecutionStatus
import com.soluna.ui.autotest.report.LocalReportWriter
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RealAndroidUgreenProfilePlanTest {
    @Test
    fun `runs UGREEN profile nickname YAML plan on real Android device`() {
        if (System.getenv("SOLUNA_UGREEN_PROFILE_SMOKE") != "true") {
            return
        }

        val result = PlanRunner().run(
            PlanRunRequest(
                runId = System.getenv("SOLUNA_RUN_ID") ?: "ugreen-profile-local",
                planPath = Path.of(
                    System.getenv("SOLUNA_UGREEN_PROFILE_PLAN_PATH")
                        ?: "examples/plans/ugreen-profile-nickname.yaml",
                ),
                reportWriter = LocalReportWriter(),
                parameterOverrides = newNicknameOverride(),
            ),
        )

        val expectedStatus = expectedStatus()
        assertEquals(expectedStatus, result.executionResult.status)
        assertNotNull(result.report)
        if (expectedStatus == ExecutionStatus.PASSED) {
            assertTrue(result.traceArtifacts.isEmpty(), "Expected no trace artifacts for passed run")
        } else {
            assertTrue(result.traceArtifacts.isNotEmpty(), "Expected failed run to publish trace artifacts")
            assertTrue(result.traceArtifacts.all { it.url != null }, "Expected uploaded trace artifacts to expose URLs")
        }
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
        val nickname = System.getenv("SOLUNA_UGREEN_PROFILE_NEW_NICKNAME") ?: return emptyMap()
        return mapOf("profile.newNickname" to ObjectMapper().valueToTree(nickname))
    }

    private fun expectedStatus(): ExecutionStatus {
        return when (System.getenv("SOLUNA_UGREEN_PROFILE_EXPECTED_STATUS")?.lowercase()) {
            null, "", "passed", "pass" -> ExecutionStatus.PASSED
            "failed", "fail" -> ExecutionStatus.FAILED
            else -> error("Unsupported SOLUNA_UGREEN_PROFILE_EXPECTED_STATUS")
        }
    }
}
