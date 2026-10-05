package com.comunidapp.app.ui.onboarding

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.comunidapp.app.domain.onboarding.onb02.GuideSnapshot
import com.comunidapp.app.domain.onboarding.onb02.GuideSnapshotCodec
import com.comunidapp.app.domain.onboarding.onb02.InteractiveOnboardingGuide

/**
 * The guide step is stored in preferences so a recreated activity or a killed
 * process resumes the same step. A completed guide does not start again.
 */
object OnboardingGuideRuntime {
    private const val PREFS = "leover_onboarding_guide"
    private const val KEY = "snapshot"

    var snapshot by mutableStateOf(InteractiveOnboardingGuide.start())
        private set

    private var attached = false

    fun attach(context: Context) {
        if (attached) return
        attached = true
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        GuideSnapshotCodec.decode(raw)?.let { snapshot = it }
    }

    fun save(context: Context, next: GuideSnapshot) {
        snapshot = next
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, GuideSnapshotCodec.encode(next))
            .apply()
    }
}
