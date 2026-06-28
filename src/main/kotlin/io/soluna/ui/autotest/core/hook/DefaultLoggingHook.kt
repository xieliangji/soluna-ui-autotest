package io.soluna.ui.autotest.core.hook

import org.slf4j.LoggerFactory

interface ExecutionLogger {
    fun info(message: String)
}

object Slf4jExecutionLogger : ExecutionLogger {
    private val logger = LoggerFactory.getLogger("soluna.execution")

    override fun info(message: String) {
        logger.info(message)
    }
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
        if (event.type == HookEventType.PLAN_BEFORE) {
            logger.info(bannerTxt())
        }
        logger.info(buildMessage(event))
    }

    private fun bannerTxt(): String {
        return """

   _____       _                      ______             _
  / ____|     | |                    |  ____|           (_)
 | (___   ___ | |_   _ _ __   __ _   | |__   _ __   __ _ _ _ __   ___
  \___ \ / _ \| | | | | '_ \ / _` |  |  __| | '_ \ / _` | | '_ \ / _ \
  ____) | (_) | | |_| | | | | (_| |  | |____| | | | (_| | | | | |  __/
 |_____/ \___/|_|\__,_|_| |_|\__,_|  |______|_| |_|\__, |_|_| |_|\___|
                                                     __/ |
                                                    |___/


 Soluna Engine
 Mobile Automation Core
 iOS / Android / WebDriver Runtime

        """.trimIndent()
    }

    private fun buildMessage(event: HookEvent): String {
        return buildList {
            add("event=${event.name}")
            add("plan=${event.planId}")
            add("run=${event.runId}")
            event.stageId?.let { add("stage=$it") }
            event.caseId?.let { add("case=$it") }
            event.actionId?.let { add("action=$it") }
            event.actionKeyword?.let { add("keyword=$it") }
            add("status=${event.status.name.lowercase()}")
            event.error?.let { add("error=[$it]") }
        }.joinToString(separator = " ")
    }
}
