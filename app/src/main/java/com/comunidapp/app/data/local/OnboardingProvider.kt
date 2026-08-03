package com.comunidapp.app.data.local

import com.comunidapp.app.LeoverApplication

object OnboardingProvider {
    val preferences: OnboardingPreferencesRepository by lazy {
        DataStoreOnboardingPreferencesRepository.fromContext(LeoverApplication.instance)
    }

    /** Solo tests — reemplaza el repositorio en memoria. */
    internal var testOverride: OnboardingPreferencesRepository? = null

    fun repository(): OnboardingPreferencesRepository =
        testOverride ?: preferences

    fun resetForTests() {
        testOverride = null
    }
}
