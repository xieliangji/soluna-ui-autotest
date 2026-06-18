package com.soluna.ui.autotest.core.execution

import com.soluna.ui.autotest.core.hook.DefaultLoggingHook
import com.soluna.ui.autotest.core.hook.ExecutionLogger
import com.soluna.ui.autotest.core.hook.HookConsumer
import com.soluna.ui.autotest.core.hook.HookEvent
import com.soluna.ui.autotest.core.hook.SimpleHookBus
import com.soluna.ui.autotest.core.model.ActionDefinition
import com.soluna.ui.autotest.core.model.CaseDefinition
import com.soluna.ui.autotest.core.model.PlanDefinition
import com.soluna.ui.autotest.core.model.StageDefinition
import com.soluna.ui.autotest.dsl.YamlPlanParser
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinearExecutionEngineTest {
    private val fixedClock = Clock.fixed(
        Instant.parse("2026-06-12T10:00:00Z"),
        ZoneOffset.UTC,
    )

    @Test
    fun `executes plan linearly and emits hook events`() {
        val recorder = RecordingHookConsumer()
        val executedKeywords = mutableListOf<String>()
        val engine = LinearExecutionEngine(
            actionExecutorRegistry = DefaultActionExecutorRegistry(
                listOf(
                    RecordingActionExecutor("tap", executedKeywords),
                    RecordingActionExecutor("input", executedKeywords),
                    RecordingActionExecutor("screenshot", executedKeywords),
                ),
            ),
            hookBus = SimpleHookBus(listOf(recorder)),
            clock = fixedClock,
        )

        val plan = YamlPlanParser().parse(Files.readString(Path.of("examples/plans/daily-smoke.yaml")))
        val result = engine.execute(plan, ExecutionRequest(runId = "run-001"))

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(
            listOf("tap", "input", "screenshot"),
            executedKeywords,
        )
        assertEquals(
            listOf(
                "plan.before",
                "stage.before",
                "case.before",
                "action.before",
                "action.after",
                "action.before",
                "action.after",
                "action.before",
                "action.after",
                "case.after",
                "stage.after",
                "plan.after",
            ),
            recorder.events.map { it.name },
        )
        assertTrue(recorder.events.all { it.runId == "run-001" })
        assertEquals("daily-smoke", recorder.events.first().planId)
    }

    @Test
    fun `default logging hook logs configured lifecycle events`() {
        val logger = InMemoryExecutionLogger()
        val hookBus = SimpleHookBus(
            listOf(
                DefaultLoggingHook(logger),
            ),
        )
        val engine = LinearExecutionEngine(
            actionExecutorRegistry = DefaultActionExecutorRegistry(
                listOf(RecordingActionExecutor("tap", mutableListOf())),
            ),
            hookBus = hookBus,
            clock = fixedClock,
        )

        engine.execute(singleActionPlan(keyword = "tap"), ExecutionRequest(runId = "run-log"))

        val messages = logger.messages
        assertTrue(messages.any { it.contains("event=plan.before") })
        assertTrue(messages.any { it.contains("event=plan.after") })
        assertTrue(messages.any { it.contains("event=stage.before") })
        assertTrue(messages.any { it.contains("event=stage.after") })
        assertTrue(messages.any { it.contains("event=case.before") })
        assertTrue(messages.any { it.contains("event=case.after") })
        assertTrue(messages.any { it.contains("event=action.before") })
        assertFalse(messages.any { it.contains("event=action.after") })
    }

    @Test
    fun `fail fast strategy stops remaining actions and marks parent results failed`() {
        val recorder = RecordingHookConsumer()
        val executedKeywords = mutableListOf<String>()
        val engine = LinearExecutionEngine(
            actionExecutorRegistry = DefaultActionExecutorRegistry(
                listOf(
                    FailingActionExecutor("tap", executedKeywords),
                    RecordingActionExecutor("input", executedKeywords),
                ),
            ),
            hookBus = SimpleHookBus(listOf(recorder)),
            clock = fixedClock,
        )

        val result = engine.execute(twoActionPlan(), ExecutionRequest(runId = "run-fail"))

        assertEquals(ExecutionStatus.FAILED, result.status)
        assertEquals(ExecutionStatus.FAILED, result.stages.single().status)
        assertEquals(ExecutionStatus.FAILED, result.stages.single().cases.single().status)
        assertEquals(1, result.stages.single().cases.single().actions.size)
        assertEquals(listOf("tap"), executedKeywords)
        assertEquals(
            listOf(
                "plan.before",
                "stage.before",
                "case.before",
                "action.before",
                "action.after",
                "case.after",
                "stage.after",
                "plan.after",
            ),
            recorder.events.map { it.name },
        )
        assertEquals(ExecutionStatus.FAILED, recorder.events.last().status)
    }

    @Test
    fun `executes case teardown after failed case action`() {
        val recorder = RecordingHookConsumer()
        val executedKeywords = mutableListOf<String>()
        val engine = LinearExecutionEngine(
            actionExecutorRegistry = DefaultActionExecutorRegistry(
                listOf(
                    FailingActionExecutor("tap", executedKeywords),
                    RecordingActionExecutor("input", executedKeywords),
                ),
            ),
            hookBus = SimpleHookBus(listOf(recorder)),
            clock = fixedClock,
        )

        val result = engine.execute(
            PlanDefinition(
                schemaVersion = "1.0",
                id = "plan-cleanup",
                name = "Plan Cleanup",
                stages = listOf(
                    StageDefinition(
                        id = "stage-001",
                        name = "Stage 001",
                        cases = listOf(
                            CaseDefinition(
                                id = "case-001",
                                name = "Case 001",
                                actions = listOf(ActionDefinition(id = "main", keyword = "tap")),
                                teardownActions = listOf(ActionDefinition(id = "restore", keyword = "input")),
                            ),
                        ),
                    ),
                ),
            ),
            ExecutionRequest(runId = "run-cleanup"),
        )

        val case = result.stages.single().cases.single()
        assertEquals(ExecutionStatus.FAILED, result.status)
        assertEquals(listOf("tap", "input"), executedKeywords)
        assertEquals(1, case.actions.size)
        assertEquals(1, case.teardownActions.size)
        assertEquals(ExecutionStatus.PASSED, case.teardownActions.single().status)
        assertEquals(
            listOf(
                "plan.before",
                "stage.before",
                "case.before",
                "action.before",
                "action.after",
                "action.before",
                "action.after",
                "case.after",
                "stage.after",
                "plan.after",
            ),
            recorder.events.map { it.name },
        )
    }

    @Test
    fun `teardown failure marks successful case failed`() {
        val executedKeywords = mutableListOf<String>()
        val engine = LinearExecutionEngine(
            actionExecutorRegistry = DefaultActionExecutorRegistry(
                listOf(
                    RecordingActionExecutor("tap", executedKeywords),
                    FailingActionExecutor("input", executedKeywords),
                ),
            ),
            hookBus = SimpleHookBus(),
            clock = fixedClock,
        )

        val result = engine.execute(
            PlanDefinition(
                schemaVersion = "1.0",
                id = "plan-cleanup-fail",
                name = "Plan Cleanup Fail",
                stages = listOf(
                    StageDefinition(
                        id = "stage-001",
                        name = "Stage 001",
                        cases = listOf(
                            CaseDefinition(
                                id = "case-001",
                                name = "Case 001",
                                actions = listOf(ActionDefinition(id = "main", keyword = "tap")),
                                teardownActions = listOf(ActionDefinition(id = "restore", keyword = "input")),
                            ),
                        ),
                    ),
                ),
            ),
            ExecutionRequest(runId = "run-cleanup-fail"),
        )

        val case = result.stages.single().cases.single()
        assertEquals(ExecutionStatus.FAILED, result.status)
        assertEquals(ExecutionStatus.FAILED, result.stages.single().status)
        assertEquals(ExecutionStatus.FAILED, case.status)
        assertEquals(ExecutionStatus.PASSED, case.actions.single().status)
        assertEquals(ExecutionStatus.FAILED, case.teardownActions.single().status)
        assertEquals(listOf("tap", "input"), executedKeywords)
    }

    @Test
    fun `missing action executor fails the action`() {
        val engine = LinearExecutionEngine(
            actionExecutorRegistry = DefaultActionExecutorRegistry(emptyList()),
            hookBus = SimpleHookBus(),
            clock = fixedClock,
        )

        val result = engine.execute(singleActionPlan(keyword = "tap"), ExecutionRequest(runId = "run-missing"))
        val actionResult = result.stages.single().cases.single().actions.single()

        assertEquals(ExecutionStatus.FAILED, result.status)
        assertEquals("No action executor registered for keyword 'tap'", actionResult.error)
    }

    @Test
    fun `executes setup actions at plan stage and case scopes`() {
        val executedKeywords = mutableListOf<String>()
        val engine = LinearExecutionEngine(
            actionExecutorRegistry = DefaultActionExecutorRegistry(
                listOf(
                    RecordingActionExecutor("restartApp", executedKeywords),
                    RecordingActionExecutor("tap", executedKeywords),
                ),
            ),
            hookBus = SimpleHookBus(),
            clock = fixedClock,
        )
        val plan = PlanDefinition(
            schemaVersion = "1.0",
            id = "plan-setup",
            name = "Plan Setup",
            setupActions = listOf(ActionDefinition(id = "plan-restart", keyword = "restartApp")),
            stages = listOf(
                StageDefinition(
                    id = "stage-001",
                    name = "Stage 001",
                    setupActions = listOf(ActionDefinition(id = "stage-restart", keyword = "restartApp")),
                    cases = listOf(
                        CaseDefinition(
                            id = "case-001",
                            name = "Case 001",
                            setupActions = listOf(ActionDefinition(id = "case-restart", keyword = "restartApp")),
                            actions = listOf(ActionDefinition(id = "tap", keyword = "tap")),
                        ),
                    ),
                ),
            ),
        )

        val result = engine.execute(plan, ExecutionRequest(runId = "run-setup"))

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf("restartApp", "restartApp", "restartApp", "tap"), executedKeywords)
        assertEquals(1, result.setupActions.size)
        assertEquals(1, result.stages.single().setupActions.size)
        assertEquals(1, result.stages.single().cases.single().setupActions.size)
        assertEquals(1, result.stages.single().cases.single().actions.size)
    }

    @Test
    fun `max attempts retry strategy retries failed action until pass`() {
        val attempts = mutableListOf<String>()
        val engine = LinearExecutionEngine(
            actionExecutorRegistry = DefaultActionExecutorRegistry(
                listOf(FlakyActionExecutor("tap", attempts, passOnAttempt = 3)),
            ),
            retryStrategy = MaxAttemptsRetryStrategy(maxAttempts = 3),
            hookBus = SimpleHookBus(),
            clock = fixedClock,
        )

        val result = engine.execute(singleActionPlan(keyword = "tap"), ExecutionRequest(runId = "run-retry"))

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf("tap#1", "tap#2", "tap#3"), attempts)
        assertEquals(ExecutionStatus.PASSED, result.stages.single().cases.single().actions.single().status)
    }

    @Test
    fun `no retry strategy keeps single failed action attempt`() {
        val attempts = mutableListOf<String>()
        val engine = LinearExecutionEngine(
            actionExecutorRegistry = DefaultActionExecutorRegistry(
                listOf(FlakyActionExecutor("tap", attempts, passOnAttempt = 2)),
            ),
            retryStrategy = NoRetryStrategy,
            hookBus = SimpleHookBus(),
            clock = fixedClock,
        )

        val result = engine.execute(singleActionPlan(keyword = "tap"), ExecutionRequest(runId = "run-no-retry"))

        assertEquals(ExecutionStatus.FAILED, result.status)
        assertEquals(listOf("tap#1"), attempts)
    }

    @Test
    fun `fragment if action executes then branch when condition passes`() {
        val executedKeywords = mutableListOf<String>()
        val engine = LinearExecutionEngine(
            actionExecutorRegistry = DefaultActionExecutorRegistry(
                listOf(
                    RecordingActionExecutor("assertSourceRegexMatch", executedKeywords),
                    RecordingActionExecutor("tap", executedKeywords),
                    RecordingActionExecutor("input", executedKeywords),
                ),
            ),
            hookBus = SimpleHookBus(),
            clock = fixedClock,
        )
        val plan = singleIfSetupPlan(
            condition = ActionDefinition(id = "detect-login-page", keyword = "assertSourceRegexMatch"),
            thenActions = listOf(ActionDefinition(id = "tap-login", keyword = "tap")),
            elseActions = listOf(ActionDefinition(id = "input-else", keyword = "input")),
        )

        val result = engine.execute(plan, ExecutionRequest(runId = "run-if-then"))

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf("assertSourceRegexMatch", "tap"), executedKeywords)
        assertEquals(ExecutionStatus.PASSED, result.stages.single().setupActions.single().status)
    }

    @Test
    fun `fragment if action executes else branch when condition fails`() {
        val executedKeywords = mutableListOf<String>()
        val engine = LinearExecutionEngine(
            actionExecutorRegistry = DefaultActionExecutorRegistry(
                listOf(
                    FailingActionExecutor("assertSourceRegexMatch", executedKeywords),
                    RecordingActionExecutor("tap", executedKeywords),
                    RecordingActionExecutor("input", executedKeywords),
                ),
            ),
            hookBus = SimpleHookBus(),
            clock = fixedClock,
        )
        val plan = singleIfSetupPlan(
            condition = ActionDefinition(id = "detect-login-page", keyword = "assertSourceRegexMatch"),
            thenActions = listOf(ActionDefinition(id = "tap-login", keyword = "tap")),
            elseActions = listOf(ActionDefinition(id = "input-else", keyword = "input")),
        )

        val result = engine.execute(plan, ExecutionRequest(runId = "run-if-else"))

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf("assertSourceRegexMatch", "input"), executedKeywords)
        assertEquals(ExecutionStatus.PASSED, result.stages.single().setupActions.single().status)
    }

    private fun singleActionPlan(keyword: String): PlanDefinition {
        return PlanDefinition(
            schemaVersion = "1.0",
            id = "plan-001",
            name = "Plan 001",
            stages = listOf(
                StageDefinition(
                    id = "stage-001",
                    name = "Stage 001",
                    cases = listOf(
                        CaseDefinition(
                            id = "case-001",
                            name = "Case 001",
                            actions = listOf(
                                ActionDefinition(
                                    id = "action-001",
                                    keyword = keyword,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun singleIfSetupPlan(
        condition: ActionDefinition,
        thenActions: List<ActionDefinition>,
        elseActions: List<ActionDefinition>,
    ): PlanDefinition {
        return PlanDefinition(
            schemaVersion = "1.0",
            id = "plan-if",
            name = "Plan If",
            stages = listOf(
                StageDefinition(
                    id = "stage-001",
                    name = "Stage 001",
                    setupActions = listOf(
                        ActionDefinition(
                            id = "ensure-state",
                            keyword = "if",
                            conditionAction = condition,
                            thenActions = thenActions,
                            elseActions = elseActions,
                        ),
                    ),
                    cases = listOf(
                        CaseDefinition(
                            id = "case-001",
                            name = "Case 001",
                            actions = emptyList(),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun twoActionPlan(): PlanDefinition {
        return PlanDefinition(
            schemaVersion = "1.0",
            id = "plan-002",
            name = "Plan 002",
            stages = listOf(
                StageDefinition(
                    id = "stage-001",
                    name = "Stage 001",
                    cases = listOf(
                        CaseDefinition(
                            id = "case-001",
                            name = "Case 001",
                            actions = listOf(
                                ActionDefinition(id = "action-001", keyword = "tap"),
                                ActionDefinition(id = "action-002", keyword = "input"),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    private class RecordingActionExecutor(
        override val keyword: String,
        private val executedKeywords: MutableList<String>,
    ) : ActionExecutor {
        override fun execute(
            action: ActionDefinition,
            context: ExecutionContext,
        ): ActionExecutionResult {
            executedKeywords += action.keyword
            return ActionExecutionResult.passed()
        }
    }

    private class FailingActionExecutor(
        override val keyword: String,
        private val executedKeywords: MutableList<String>,
    ) : ActionExecutor {
        override fun execute(
            action: ActionDefinition,
            context: ExecutionContext,
        ): ActionExecutionResult {
            executedKeywords += action.keyword
            return ActionExecutionResult.failed("failed by test")
        }
    }

    private class FlakyActionExecutor(
        override val keyword: String,
        private val attempts: MutableList<String>,
        private val passOnAttempt: Int,
    ) : ActionExecutor {
        private var currentAttempt = 0

        override fun execute(
            action: ActionDefinition,
            context: ExecutionContext,
        ): ActionExecutionResult {
            currentAttempt += 1
            attempts += "${action.keyword}#$currentAttempt"
            return if (currentAttempt >= passOnAttempt) {
                ActionExecutionResult.passed()
            } else {
                ActionExecutionResult.failed("attempt $currentAttempt failed")
            }
        }
    }

    private class RecordingHookConsumer : HookConsumer {
        override val id: String = "recording-hook"
        val events = mutableListOf<HookEvent>()

        override fun supports(event: HookEvent): Boolean {
            return true
        }

        override fun handle(event: HookEvent) {
            events += event
        }
    }

    private class InMemoryExecutionLogger : ExecutionLogger {
        val messages = mutableListOf<String>()

        override fun info(message: String) {
            messages += message
        }
    }
}
