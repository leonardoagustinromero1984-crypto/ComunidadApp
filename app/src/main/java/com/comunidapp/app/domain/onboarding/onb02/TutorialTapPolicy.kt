package com.comunidapp.app.domain.onboarding.onb02

object TutorialTapPolicy {
    const val LEFT_ZONE = 0.28f
    const val RIGHT_ZONE = 0.72f
    const val BACKGROUND_TAP_CLOSES = false
    const val LAST_PAGE_RIGHT_FINISHES = false

    fun zone(x: Float, width: Float): TutorialTapZone {
        val w = width.coerceAtLeast(1f)
        val ratio = x / w
        return when {
            ratio < LEFT_ZONE -> TutorialTapZone.PREVIOUS
            ratio > RIGHT_ZONE -> TutorialTapZone.NEXT
            else -> TutorialTapZone.IGNORE
        }
    }
}

enum class TutorialTapZone { PREVIOUS, NEXT, IGNORE }
