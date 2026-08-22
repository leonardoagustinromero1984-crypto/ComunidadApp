package com.comunidapp.app.domain.onboarding.onb02

/**
 * Explicit onboarding plan item. Tutorials never choose "Create organization".
 * The orchestrator advances to the next pending item.
 */
enum class Onb02StepKind {
    TUTORIAL,
    ORG_CHOICE
}

data class Onb02PlanItem(
    val id: String,
    val kind: Onb02StepKind,
    val function: LeoverFunction?,
    val tutorialId: TutorialId? = null
)

object Onb02FunctionOrder {
    /** Product order — not enum ordinal. Organization is always last. */
    val extras: List<LeoverFunction> = listOf(
        LeoverFunction.RESCUER,
        LeoverFunction.FOSTER,
        LeoverFunction.VETERINARY_PROFESSIONAL,
        LeoverFunction.WALKER,
        LeoverFunction.CAREGIVER,
        LeoverFunction.TRAINER,
        LeoverFunction.GROOMING,
        LeoverFunction.DAYCARE,
        LeoverFunction.ORGANIZATION
    )
}
