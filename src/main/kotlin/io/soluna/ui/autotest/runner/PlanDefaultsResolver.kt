package io.soluna.ui.autotest.runner

import io.soluna.ui.autotest.core.model.ActionDefinition
import io.soluna.ui.autotest.core.model.PlanDefinition
import io.soluna.ui.autotest.core.model.WaitDefinition

class PlanDefaultsResolver {
    fun resolve(plan: PlanDefinition): PlanDefinition {
        val defaultWait = plan.defaults.actionWait ?: return plan
        return plan.copy(
            setupActions = plan.setupActions.map { it.withDefaultWait(defaultWait) },
            caseSetupActions = plan.caseSetupActions.map { it.withDefaultWait(defaultWait) },
            caseTeardownActions = plan.caseTeardownActions.map { it.withDefaultWait(defaultWait) },
            teardownActions = plan.teardownActions.map { it.withDefaultWait(defaultWait) },
            stages = plan.stages.map { stage ->
                stage.copy(
                    setupActions = stage.setupActions.map { it.withDefaultWait(defaultWait) },
                    caseSetupActions = stage.caseSetupActions.map { it.withDefaultWait(defaultWait) },
                    caseTeardownActions = stage.caseTeardownActions.map { it.withDefaultWait(defaultWait) },
                    teardownActions = stage.teardownActions.map { it.withDefaultWait(defaultWait) },
                    cases = stage.cases.map { case ->
                        case.copy(
                            setupActions = case.setupActions.map { it.withDefaultWait(defaultWait) },
                            caseSetupActions = case.caseSetupActions.map { it.withDefaultWait(defaultWait) },
                            caseTeardownActions = case.caseTeardownActions.map { it.withDefaultWait(defaultWait) },
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
        val withOwnWait = if (this.wait == null) {
            copy(wait = wait)
        } else {
            this
        }
        return withOwnWait.copy(
            conditionAction = withOwnWait.conditionAction?.withDefaultWait(wait),
            thenActions = withOwnWait.thenActions.map { it.withDefaultWait(wait) },
            elseActions = withOwnWait.elseActions.map { it.withDefaultWait(wait) },
        )
    }
}
