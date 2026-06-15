package com.ugreen.iot.soluna.autotest.core.hook

interface ExecutionLogger {
    fun info(message: String)
}

class DefaultLoggingHook(
    private val logger: ExecutionLogger,
) : HookConsumer {
    override val id: String = "default-logging-hook"

    private val loggedEvents = setOf(
        HookEventType.PLAN_BEFORE,
        HookEventType.PLAN_AFTER,
        HookEventType.STAGE_BEFORE,
        HookEventType.STAGE_AFTER,
        HookEventType.CASE_BEFORE,
        HookEventType.CASE_AFTER,
        HookEventType.ACTION_BEFORE,
    )

    override fun supports(event: HookEvent): Boolean {
        return event.type in loggedEvents
    }

    override fun handle(event: HookEvent) {
        logger.info(buildMessage(event))
    }

    private fun buildMessage(event: HookEvent): String {
        return buildList {
            add("event=${event.name}")
            add("runId=${event.runId}")
            add("planId=${event.planId}")
            event.stageId?.let { add("stageId=$it") }
            event.caseId?.let { add("caseId=$it") }
            event.actionId?.let { add("actionId=$it") }
            event.actionKeyword?.let { add("actionKeyword=$it") }
            add("status=${event.status.name.lowercase()}")
            event.error?.let { add("error=$it") }
        }.joinToString(separator = " ")
    }
}
