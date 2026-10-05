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
    TEACH_ADD_FUNCTION,
    FEED
}

enum class GuideDestination {
    STAY,
    ADD_FUNCTION_SELECTOR,
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
        GuideStep.TEACH_ADD_FUNCTION,
        GuideStep.FEED
    )

    fun destination(step: GuideStep): GuideDestination = when (step) {
        GuideStep.TEACH_ADD_FUNCTION -> GuideDestination.ADD_FUNCTION_SELECTOR
        GuideStep.FEED -> GuideDestination.FEED
        else -> GuideDestination.STAY
    }

    fun shouldStart(onboardingAlreadyCompleted: Boolean): Boolean = !onboardingAlreadyCompleted

    fun start(): GuideSnapshot = GuideSnapshot(GuideStep.GENERAL_TUTORIAL, completed = false)

    fun restore(saved: GuideSnapshot?): GuideSnapshot = saved ?: start()

    fun allows(step: GuideStep, target: GuideTarget): Boolean = when (step) {
        GuideStep.GENERAL_TUTORIAL, GuideStep.FUNCTION_GUIDE, GuideStep.OPEN_SETTINGS ->
            target == GuideTarget.CONTINUE
        GuideStep.CHOOSE_FUNCTION -> target == GuideTarget.FUNCTION_CHOICE
        GuideStep.USE_LEOVER_AS -> target == GuideTarget.USE_AS_CHOICE
        GuideStep.HIGHLIGHT_ADD_FUNCTION -> target == GuideTarget.ADD_FUNCTION
        GuideStep.TEACH_ADD_FUNCTION -> target == GuideTarget.CONTINUE
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

enum class TeachAddFunctionResult {
    RETURN_TO_SETTINGS,
    FINISH_ON_FEED
}

/**
 * Back from the add-function step is not completion.
 * A null finish route means Atrás: return to Settings and restore the spotlight.
 * Only a real completion leaves the guide and opens the Feed.
 */
object TeachAddFunctionExit {
    fun resolve(step: GuideStep, finishedRoute: String?): TeachAddFunctionResult? {
        if (step != GuideStep.TEACH_ADD_FUNCTION) return null
        return if (finishedRoute.isNullOrBlank()) {
            TeachAddFunctionResult.RETURN_TO_SETTINGS
        } else {
            TeachAddFunctionResult.FINISH_ON_FEED
        }
    }

    fun snapshotAfterBack(): GuideSnapshot =
        GuideSnapshot(GuideStep.HIGHLIGHT_ADD_FUNCTION, completed = false)
}

enum class SecondaryScreenExit {
    POP_TO_ORIGIN,
    CONTINUE
}

object ContextualNavigation {
    fun exitFromAddFunction(): SecondaryScreenExit = SecondaryScreenExit.POP_TO_ORIGIN
}

object GuideSnapshotCodec {
    fun encode(snapshot: GuideSnapshot): String = "${snapshot.step.name}|${snapshot.completed}"

    fun decode(raw: String?): GuideSnapshot? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split("|")
        val step = runCatching { GuideStep.valueOf(parts[0]) }.getOrNull() ?: return null
        return GuideSnapshot(step, completed = parts.getOrNull(1) == "true")
    }
}

interface GuideStepStore {
    fun read(): String?
    fun write(encoded: String)
}

class OnboardingGuideSession(private val store: GuideStepStore) {
    fun current(): GuideSnapshot =
        GuideSnapshotCodec.decode(store.read()) ?: InteractiveOnboardingGuide.start()

    fun save(snapshot: GuideSnapshot) {
        store.write(GuideSnapshotCodec.encode(snapshot))
    }
}
