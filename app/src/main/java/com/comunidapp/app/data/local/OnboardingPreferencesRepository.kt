package com.comunidapp.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.comunidapp.app.domain.onboarding.ContextualHelpId
import com.comunidapp.app.domain.onboarding.ONBOARDING_VERSION
import com.comunidapp.app.domain.onboarding.OnboardingIntent
import com.comunidapp.app.domain.onboarding.OnboardingProgress
import com.comunidapp.app.domain.onboarding.OnboardingStatus
import com.comunidapp.app.domain.onboarding.OnboardingStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.emptyPreferences as datastoreEmptyPreferences

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "leover_first_run_onboarding"
)

interface OnboardingPreferencesRepository {
    val progressFlow: Flow<OnboardingProgress>
    suspend fun load(): OnboardingProgress
    suspend fun save(progress: OnboardingProgress): Boolean
    suspend fun markContextualHelpSeen(id: ContextualHelpId)
    suspend fun isContextualHelpSeen(id: ContextualHelpId): Boolean
    suspend fun resetContextualHelpSeen()
}

class DataStoreOnboardingPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) : OnboardingPreferencesRepository {

    override val progressFlow: Flow<OnboardingProgress> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs.toProgress() }

    override suspend fun load(): OnboardingProgress =
        runCatching { progressFlow.first() }.getOrDefault(OnboardingProgress())

    override suspend fun save(progress: OnboardingProgress): Boolean =
        runCatching {
            dataStore.edit { prefs ->
                prefs[KEY_VERSION] = progress.onboardingVersion
                prefs[KEY_STATUS] = progress.status.name
                prefs[KEY_STEP] = progress.currentStep.name
                progress.selectedIntent?.let { prefs[KEY_INTENT] = it.name }
                    ?: prefs.remove(KEY_INTENT)
                progress.startedAtEpochMs?.let { prefs[KEY_STARTED] = it }
                    ?: prefs.remove(KEY_STARTED)
                progress.completedAtEpochMs?.let { prefs[KEY_COMPLETED] = it }
                    ?: prefs.remove(KEY_COMPLETED)
                progress.skippedAtEpochMs?.let { prefs[KEY_SKIPPED] = it }
                    ?: prefs.remove(KEY_SKIPPED)
                progress.approximateZone?.let { prefs[KEY_ZONE] = it }
                    ?: prefs.remove(KEY_ZONE)
                progress.displayNameDraft?.let { prefs[KEY_DISPLAY_NAME] = it }
                    ?: prefs.remove(KEY_DISPLAY_NAME)
                prefs[KEY_HELP_SEEN] = progress.contextualHelpSeen.map { it.name }.toSet()
            }
        }.isSuccess

    override suspend fun markContextualHelpSeen(id: ContextualHelpId) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_HELP_SEEN].orEmpty().toMutableSet()
            current.add(id.name)
            prefs[KEY_HELP_SEEN] = current
        }
    }

    override suspend fun isContextualHelpSeen(id: ContextualHelpId): Boolean {
        val prefs = runCatching { dataStore.data.first() }.getOrNull() ?: return false
        return id.name in prefs[KEY_HELP_SEEN].orEmpty()
    }

    override suspend fun resetContextualHelpSeen() {
        dataStore.edit { it.remove(KEY_HELP_SEEN) }
    }

    private fun Preferences.toProgress(): OnboardingProgress {
        val version = this[KEY_VERSION] ?: ONBOARDING_VERSION
        val status = this[KEY_STATUS]?.let { runCatching { OnboardingStatus.valueOf(it) }.getOrNull() }
            ?: OnboardingStatus.NOT_STARTED
        val step = this[KEY_STEP]?.let { runCatching { OnboardingStep.valueOf(it) }.getOrNull() }
            ?: OnboardingStep.WELCOME
        val intent = this[KEY_INTENT]?.let { runCatching { OnboardingIntent.valueOf(it) }.getOrNull() }
        val helpSeen = this[KEY_HELP_SEEN].orEmpty().mapNotNull { name ->
            runCatching { ContextualHelpId.valueOf(name) }.getOrNull()
        }.toSet()
        return OnboardingProgress(
            onboardingVersion = version,
            status = status,
            currentStep = step,
            selectedIntent = intent,
            startedAtEpochMs = this[KEY_STARTED],
            completedAtEpochMs = this[KEY_COMPLETED],
            skippedAtEpochMs = this[KEY_SKIPPED],
            contextualHelpSeen = helpSeen,
            approximateZone = this[KEY_ZONE],
            displayNameDraft = this[KEY_DISPLAY_NAME]
        )
    }

    companion object {
        private val KEY_VERSION = intPreferencesKey("onboarding_version")
        private val KEY_STATUS = stringPreferencesKey("status")
        private val KEY_STEP = stringPreferencesKey("current_step")
        private val KEY_INTENT = stringPreferencesKey("selected_intent")
        private val KEY_STARTED = longPreferencesKey("started_at")
        private val KEY_COMPLETED = longPreferencesKey("completed_at")
        private val KEY_SKIPPED = longPreferencesKey("skipped_at")
        private val KEY_ZONE = stringPreferencesKey("approximate_zone")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name_draft")
        private val KEY_HELP_SEEN = stringSetPreferencesKey("contextual_help_seen")

        fun fromContext(context: Context): DataStoreOnboardingPreferencesRepository =
            DataStoreOnboardingPreferencesRepository(context.applicationContext.onboardingDataStore)
    }
}

/** Almacenamiento en memoria para tests unitarios. */
class InMemoryOnboardingPreferencesRepository : OnboardingPreferencesRepository {
    private val state = MutableStateFlow(OnboardingProgress())

    override val progressFlow: Flow<OnboardingProgress> = state.asStateFlow()

    override suspend fun load(): OnboardingProgress = state.value

    override suspend fun save(progress: OnboardingProgress): Boolean {
        if (saveShouldFail) return false
        state.value = progress
        return true
    }

    override suspend fun markContextualHelpSeen(id: ContextualHelpId) {
        state.value = state.value.copy(contextualHelpSeen = state.value.contextualHelpSeen + id)
    }

    override suspend fun isContextualHelpSeen(id: ContextualHelpId): Boolean =
        id in state.value.contextualHelpSeen

    override suspend fun resetContextualHelpSeen() {
        state.value = state.value.copy(contextualHelpSeen = emptySet())
    }

    fun setProgress(value: OnboardingProgress) {
        state.value = value
    }

    /** Simula fallo de persistencia para tests de resiliencia. */
    var saveShouldFail: Boolean = false
}

private fun emptyPreferences(): Preferences = datastoreEmptyPreferences()
