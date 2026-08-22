package com.comunidapp.app.domain.onboarding.onb03

import com.comunidapp.app.domain.onboarding.onb02.TutorialId

/**
 * Event-driven tutorial run after a successful canonical persist.
 * Not launched from route entry or composition.
 */
data class PendingTutorialRun(
    val tutorials: List<TutorialId>,
    val landingRoute: String,
    val popSetupRoute: String? = null
)

object PendingTutorialQueue {
    @Volatile
    private var pending: PendingTutorialRun? = null

    fun set(run: PendingTutorialRun) {
        pending = run.takeIf { it.tutorials.isNotEmpty() }
    }

    fun peek(): PendingTutorialRun? = pending

    fun consume(): PendingTutorialRun? {
        val value = pending
        pending = null
        return value
    }
}
