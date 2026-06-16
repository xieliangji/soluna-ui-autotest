package com.ugreen.iot.soluna.autotest.runner

import com.ugreen.iot.soluna.autotest.core.model.ActionDefinition
import com.ugreen.iot.soluna.autotest.core.model.CaseDefinition
import com.ugreen.iot.soluna.autotest.core.model.PlanDefaults
import com.ugreen.iot.soluna.autotest.core.model.PlanDefinition
import com.ugreen.iot.soluna.autotest.core.model.StageDefinition
import com.ugreen.iot.soluna.autotest.core.model.WaitDefinition
import kotlin.test.Test
import kotlin.test.assertEquals

class PlanDefaultsResolverTest {
    @Test
    fun `applies default action wait without overriding explicit action wait`() {
        val plan = PlanDefinition(
            schemaVersion = "1.0",
            id = "plan-001",
            name = "Plan 001",
            defaults = PlanDefaults(
                actionWait = WaitDefinition(timeoutMs = 10_000, intervalMs = 500),
            ),
            setupActions = listOf(ActionDefinition(id = "plan-setup", keyword = "tap")),
            teardownActions = listOf(ActionDefinition(id = "plan-teardown", keyword = "tap")),
            stages = listOf(
                StageDefinition(
                    id = "stage-001",
                    name = "Stage 001",
                    setupActions = listOf(ActionDefinition(id = "stage-setup", keyword = "tap")),
                    teardownActions = listOf(ActionDefinition(id = "stage-teardown", keyword = "tap")),
                    cases = listOf(
                        CaseDefinition(
                            id = "case-001",
                            name = "Case 001",
                            setupActions = listOf(ActionDefinition(id = "case-setup", keyword = "tap")),
                            teardownActions = listOf(ActionDefinition(id = "case-teardown", keyword = "tap")),
                            actions = listOf(
                                ActionDefinition(id = "default-wait", keyword = "tap"),
                                ActionDefinition(
                                    id = "explicit-wait",
                                    keyword = "tap",
                                    wait = WaitDefinition(timeoutMs = 1_000, intervalMs = 100),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val resolved = PlanDefaultsResolver().resolve(plan)
        val case = resolved.stages.single().cases.single()

        assertEquals(10_000, resolved.setupActions.single().wait?.timeoutMs)
        assertEquals(10_000, resolved.teardownActions.single().wait?.timeoutMs)
        assertEquals(10_000, resolved.stages.single().setupActions.single().wait?.timeoutMs)
        assertEquals(10_000, resolved.stages.single().teardownActions.single().wait?.timeoutMs)
        assertEquals(10_000, case.setupActions.single().wait?.timeoutMs)
        assertEquals(10_000, case.teardownActions.single().wait?.timeoutMs)
        assertEquals(10_000, case.actions.first().wait?.timeoutMs)
        assertEquals(500, case.actions.first().wait?.intervalMs)
        assertEquals(1_000, case.actions.last().wait?.timeoutMs)
        assertEquals(100, case.actions.last().wait?.intervalMs)
    }

    @Test
    fun `applies default action wait inside fragment control flow actions`() {
        val plan = PlanDefinition(
            schemaVersion = "1.0",
            id = "plan-if",
            name = "Plan If",
            defaults = PlanDefaults(
                actionWait = WaitDefinition(timeoutMs = 10_000, intervalMs = 500),
            ),
            stages = listOf(
                StageDefinition(
                    id = "stage-001",
                    name = "Stage 001",
                    setupActions = listOf(
                        ActionDefinition(
                            id = "ensure-state",
                            keyword = "if",
                            conditionAction = ActionDefinition(id = "detect-state", keyword = "assertSourceRegexMatch"),
                            thenActions = listOf(ActionDefinition(id = "tap-then", keyword = "tap")),
                            elseActions = listOf(
                                ActionDefinition(
                                    id = "tap-else",
                                    keyword = "tap",
                                    wait = WaitDefinition(timeoutMs = 1_000, intervalMs = 100),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val resolved = PlanDefaultsResolver().resolve(plan)
        val action = resolved.stages.single().setupActions.single()

        assertEquals(10_000, action.wait?.timeoutMs)
        assertEquals(10_000, action.conditionAction?.wait?.timeoutMs)
        assertEquals(10_000, action.thenActions.single().wait?.timeoutMs)
        assertEquals(1_000, action.elseActions.single().wait?.timeoutMs)
    }
}
