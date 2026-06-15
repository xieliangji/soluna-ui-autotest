package com.ugreen.iot.soluna.autotest.runner

import com.ugreen.iot.soluna.autotest.core.model.ActionDefinition
import com.ugreen.iot.soluna.autotest.core.model.PlanDefinition
import com.ugreen.iot.soluna.autotest.core.model.WaitDefinition

class PlanDefaultsResolver {
    fun resolve(plan: PlanDefinition): PlanDefinition {
        val defaultWait = plan.defaults.actionWait ?: return plan
        return plan.copy(
            setupActions = plan.setupActions.map { it.withDefaultWait(defaultWait) },
            teardownActions = plan.teardownActions.map { it.withDefaultWait(defaultWait) },
            stages = plan.stages.map { stage ->
                stage.copy(
                    setupActions = stage.setupActions.map { it.withDefaultWait(defaultWait) },
                    teardownActions = stage.teardownActions.map { it.withDefaultWait(defaultWait) },
                    cases = stage.cases.map { case ->
                        case.copy(
                            setupActions = case.setupActions.map { it.withDefaultWait(defaultWait) },
                            teardownActions = case.teardownActions.map { it.withDefaultWait(defaultWait) },
                            actions = case.actions.map { it.withDefaultWait(defaultWait) },
                        )
                    },
                )
            },
        )
    }

    private fun ActionDefinition.withDefaultWait(
        wait: WaitDefinition,
    ): ActionDefinition {
        return if (this.wait == null) {
            copy(wait = wait)
        } else {
            this
        }
    }
}
