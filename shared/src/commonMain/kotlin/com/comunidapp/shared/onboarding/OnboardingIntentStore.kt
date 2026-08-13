package com.comunidapp.shared.onboarding

import com.comunidapp.app.domain.onboarding.OnboardingIntent
import com.comunidapp.shared.platform.PlatformPreferences

/**
 * Persistencia mínima de intención de onboarding (no crea roles).
 */
class OnboardingIntentStore(
    private val preferences: PlatformPreferences
) {
    fun save(intent: OnboardingIntent) {
        preferences.putString(KEY, intent.name)
    }

    fun read(): OnboardingIntent? {
        val raw = preferences.getString(KEY) ?: return null
        return runCatching { OnboardingIntent.valueOf(raw) }.getOrNull()
    }

    fun clear() {
        preferences.remove(KEY)
    }

    companion object {
        const val KEY = "onboarding.selected_intent"
    }
}
