package com.comunidapp.app.domain.onboarding.onb02

enum class Onb02FlowKind {
    FULL_ONBOARDING,
    EXISTING_USER_T00,
    ADD_FUNCTION_LATER,
    REOPEN_FROM_HELP,
    EVENT_QUEUE
}

data class TutorialProgressRecord(
    val tutorialId: TutorialId,
    val version: Int,
    val viewed: Boolean = false,
    val skipped: Boolean = false,
    val completed: Boolean = false
) {
    val seenThisVersion: Boolean get() = viewed || skipped || completed
}

object Onb02Planner {

    fun extraOptions(): List<LeoverFunction> =
        LeoverFunction.entries.filter { it.isSelectableExtra }

    fun cannotUncheckPersonal(): Boolean = true

    fun personalAlwaysActive(selection: FunctionSelection): Boolean =
        selection.personalAlwaysIncluded && !selection.personalEditable

    fun zeroAdditionalAllowed(selection: FunctionSelection): Boolean = true

    fun oneAdditionalAllowed(selection: FunctionSelection): Boolean =
        selection.extras.size <= 1 || selection.extras.size > 1

    fun multipleAdditionalAllowed(selection: FunctionSelection): Boolean = true

    fun isValidSelection(selection: FunctionSelection): Boolean {
        if (!selection.personalAlwaysIncluded) return false
        if (selection.personalEditable) return false
        if (LeoverFunction.PROFILE_PERSONAL in selection.extras) return false
        return selection.extras.all { it.isSelectableExtra }
    }

    fun selectorExplainsMultiselectEvenIfT00Skipped(): Boolean = true

    /**
     * In-host plan after function selection.
     * Personal function tutorials first; organization tutorials + Crear/Unirme last;
     * T11 after that. Setup screens are a separate ordered queue (org last).
     */
    fun buildInHostPlan(
        selection: FunctionSelection,
        t11AlreadyCompleted: Boolean = false,
        includeT01: Boolean = false
    ): List<Onb02PlanItem> {
        val items = mutableListOf<Onb02PlanItem>()
        if (includeT01) {
            items += Onb02PlanItem(
                id = "tut_${TutorialId.T01_PROFILE_PERSONAL.key}",
                kind = Onb02StepKind.TUTORIAL,
                function = LeoverFunction.PROFILE_PERSONAL,
                tutorialId = TutorialId.T01_PROFILE_PERSONAL
            )
        }
        Onb02FunctionOrder.extras.forEach { fn ->
            if (fn !in selection.extras) return@forEach
            if (fn == LeoverFunction.ORGANIZATION) {
                items += Onb02PlanItem(
                    id = "org_choice",
                    kind = Onb02StepKind.ORG_CHOICE,
                    function = LeoverFunction.ORGANIZATION
                )
                return@forEach
            }
            items += Onb02PlanItem(
                id = "tut_${fn.tutorialId.key}",
                kind = Onb02StepKind.TUTORIAL,
                function = fn,
                tutorialId = fn.tutorialId
            )
        }
        if (shouldShowT11(selection.extras.size, t11AlreadyCompleted)) {
            items += Onb02PlanItem(
                id = "tut_${TutorialId.T11_USE_LEOVER_AS.key}",
                kind = Onb02StepKind.TUTORIAL,
                function = null,
                tutorialId = TutorialId.T11_USE_LEOVER_AS
            )
        }
        return items
    }

    /**
     * Resume cursor. [finished] must be completed-or-skipped, not merely viewed.
     * Opening a tutorial must not skip it after process death.
     */
    fun firstIncompleteIndex(
        plan: List<Onb02PlanItem>,
        selection: FunctionSelection,
        finished: (TutorialId) -> Boolean
    ): Int? {
        plan.forEachIndexed { index, item ->
            when (item.kind) {
                Onb02StepKind.TUTORIAL -> {
                    val id = item.tutorialId ?: return@forEachIndexed
                    if (!finished(id)) return index
                }
                Onb02StepKind.ORG_CHOICE -> {
                    if (selection.organizationAction == null) return index
                }
            }
        }
        return null
    }

    /**
     * Full onboarding: T00 first, then selector, then T01 + selected + optional T11.
     */
    fun tutorialsAfterSelection(
        selection: FunctionSelection,
        t11AlreadyCompleted: Boolean = false
    ): List<TutorialId> = buildInHostPlan(selection, t11AlreadyCompleted, includeT01 = false)
        .mapNotNull { it.tutorialId }

    fun tutorialsWhenAddingLater(
        newlySelected: Set<LeoverFunction>,
        alreadySelected: Set<LeoverFunction>,
        t11AlreadyCompleted: Boolean
    ): List<TutorialId> {
        require(LeoverFunction.PROFILE_PERSONAL !in newlySelected)
        val queue = mutableListOf<TutorialId>()
        Onb02FunctionOrder.extras.forEach { fn ->
            if (fn in newlySelected) queue += fn.tutorialId
        }
        val extrasAfter = alreadySelected + newlySelected
        if (shouldShowT11(extrasAfter.size, t11AlreadyCompleted)) {
            queue += TutorialId.T11_USE_LEOVER_AS
        }
        return queue.filterNot { it == TutorialId.T00_MULTI_FUNCTION_INTRO || it == TutorialId.T01_PROFILE_PERSONAL }
    }

    fun shouldShowT00BeforeSelector(kind: Onb02FlowKind): Boolean =
        kind == Onb02FlowKind.FULL_ONBOARDING || kind == Onb02FlowKind.EXISTING_USER_T00

    fun shouldShowSelector(kind: Onb02FlowKind): Boolean =
        kind == Onb02FlowKind.FULL_ONBOARDING ||
            kind == Onb02FlowKind.EXISTING_USER_T00 ||
            kind == Onb02FlowKind.ADD_FUNCTION_LATER

    fun tutorialFinished(progress: TutorialProgressRecord): Boolean =
        progress.completed || progress.skipped

    fun shouldShowT11(extraCount: Int, t11AlreadyCompleted: Boolean): Boolean =
        extraCount >= 1 && !t11AlreadyCompleted

    fun shouldIncludeT01(kind: Onb02FlowKind, t01AlreadyFinished: Boolean): Boolean = false

    fun skipIsNonBlocking(): Boolean = true

    fun reopenFromHelpAllowed(): Boolean = true

    fun changingContextReplaysTutorial(): Boolean = false

    fun tutorialGrantsPermissions(): Boolean = false

    fun tutorialIsLegalConsent(): Boolean = false

    fun implementedCanonicalFunctions(): List<LeoverFunction> =
        LeoverFunction.entries.filter {
            it.domainStatus == FunctionDomainStatus.IMPLEMENTED_CANONICAL_DOMAIN
        }

    fun pendingCanonicalFunctions(): List<LeoverFunction> =
        LeoverFunction.entries.filter {
            it.domainStatus == FunctionDomainStatus.PENDING_CANONICAL_DOMAIN
        }
}

object Onb02Authority {
    const val ACTIVE_CONTEXT_SECURITY_AUTHORITY = false
    const val ACCOUNT_TYPE_RUNTIME_AUTHORITY = 0
    const val APPMODE_RUNTIME_AUTHORITY = 0
    const val ORGANIZATION_SECOND_HUMAN_ACCOUNT = false
    const val ORGANIZATION_MEMBERSHIP_MODEL = true

    fun accountTypeGrantsFunction(function: LeoverFunction): Boolean {
        @Suppress("UNUSED_PARAMETER")
        val ignored = function
        return false
    }

    fun appModeGrantsFunction(function: LeoverFunction): Boolean {
        @Suppress("UNUSED_PARAMETER")
        val ignored = function
        return false
    }

    fun activeContextGrantsPermission(code: String): Boolean {
        @Suppress("UNUSED_PARAMETER")
        val ignored = code
        return false
    }
}
