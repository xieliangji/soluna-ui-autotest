package com.ugreen.iot.soluna.autotest.core.hook

import com.ugreen.iot.soluna.autotest.core.execution.ExecutionStatus
import java.time.Instant

enum class HookEventType(
    val eventName: String,
) {
    PLAN_BEFORE("plan.before"),
    PLAN_AFTER("plan.after"),
    STAGE_BEFORE("stage.before"),
    STAGE_AFTER("stage.after"),
    CASE_BEFORE("case.before"),
    CASE_AFTER("case.after"),
    ACTION_BEFORE("action.before"),
    ACTION_AFTER("action.after"),
}

data class HookEvent(
    val type: HookEventType,
    val runId: String,
    val planId: String,
    val status: ExecutionStatus,
    val timestamp: Instant,
    val stageId: String? = null,
    val caseId: String? = null,
    val actionId: String? = null,
    val actionKeyword: String? = null,
    val error: String? = null,
) {
    val name: String
        get() = type.eventName
}
