package com.comunidapp.app.domain.onboarding.onb02

/**
 * First-run path after the general tutorial and function choice.
 * An existing user who already finished onboarding does not see it again.
 * A process death restores the same step; only the step target accepts a tap.
 */
enum class GuideStep {
    GENERAL_TUTORIAL,
    CHOOSE_FUNCTION,
    FUNCTION_GUIDE,
    USE_LEOVER_AS,
    OPEN_SETTINGS,
    HIGHLIGHT_ADD_FUNCTION,
    FEED
}

enum class GuideTarget {
    CONTINUE,
    FUNCTION_CHOICE,
    USE_AS_CHOICE,
    ADD_FUNCTION,
    OTHER
}

data class GuideSnapshot(
    val step: GuideStep,
    val completed: Boolean
)

object InteractiveOnboardingGuide {
    val order: List<GuideStep> = listOf(
        GuideStep.GENERAL_TUTORIAL,
        GuideStep.CHOOSE_FUNCTION,
        GuideStep.FUNCTION_GUIDE,
        GuideStep.USE_LEOVER_AS,
        GuideStep.OPEN_SETTINGS,
        GuideStep.HIGHLIGHT_ADD_FUNCTION,
        GuideStep.FEED
    )

    fun shouldStart(onboardingAlreadyCompleted: Boolean): Boolean = !onboardingAlreadyCompleted

    fun start(): GuideSnapshot = GuideSnapshot(GuideStep.GENERAL_TUTORIAL, completed = false)

    fun restore(saved: GuideSnapshot?): GuideSnapshot = saved ?: start()

    fun allows(step: GuideStep, target: GuideTarget): Boolean = when (step) {
        GuideStep.GENERAL_TUTORIAL, GuideStep.FUNCTION_GUIDE, GuideStep.OPEN_SETTINGS ->
            target == GuideTarget.CONTINUE
        GuideStep.CHOOSE_FUNCTION -> target == GuideTarget.FUNCTION_CHOICE
        GuideStep.USE_LEOVER_AS -> target == GuideTarget.USE_AS_CHOICE
        GuideStep.HIGHLIGHT_ADD_FUNCTION -> target == GuideTarget.ADD_FUNCTION
        GuideStep.FEED -> false
    }

    fun advance(snapshot: GuideSnapshot, target: GuideTarget): GuideSnapshot {
        if (snapshot.completed || !allows(snapshot.step, target)) return snapshot
        val index = order.indexOf(snapshot.step)
        val next = order.getOrNull(index + 1) ?: GuideStep.FEED
        return if (next == GuideStep.FEED) {
            GuideSnapshot(GuideStep.FEED, completed = true)
        } else {
            GuideSnapshot(next, completed = false)
        }
    }
}

enum class SecondaryScreenExit {
    POP_TO_ORIGIN,
    CONTINUE
}

object ContextualNavigation {
    fun exitFromAddFunction(): SecondaryScreenExit = SecondaryScreenExit.POP_TO_ORIGIN
}
