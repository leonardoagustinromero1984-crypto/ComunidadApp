package com.comunidapp.app.data.local

import android.content.Context
import com.comunidapp.app.LeoverApplication
import com.comunidapp.app.domain.onboarding.onb02.FunctionSelection
import com.comunidapp.app.domain.onboarding.onb02.IndependentProfessionalSpecialty
import com.comunidapp.app.domain.onboarding.onb02.LeoverFunction
import com.comunidapp.app.domain.onboarding.onb02.Onb02EntryPolicy
import com.comunidapp.app.domain.onboarding.onb02.Onb02FlowKind
import com.comunidapp.app.domain.onboarding.onb02.OrganizationKindOption
import com.comunidapp.app.domain.onboarding.onb02.OrganizationSetupAction
import com.comunidapp.app.domain.onboarding.onb02.ProfileActorKind
import com.comunidapp.app.domain.onboarding.onb02.TutorialId
import com.comunidapp.app.domain.onboarding.onb02.TutorialProgressRecord

enum class Onb02Completion {
    NOT_STARTED,
    FULL_PENDING,
    EXISTING_T00_PENDING,
    COMPLETED,
    EXISTING_ACKNOWLEDGED
}

interface Onb02Store {
    fun completion(userId: String): Onb02Completion
    fun markFullPending(userId: String)
    fun markExistingT00Pending(userId: String)
    fun markCompleted(userId: String)
    fun markExistingAcknowledged(userId: String)

    fun selection(userId: String): FunctionSelection
    fun saveSelection(userId: String, selection: FunctionSelection)
    fun addExtras(userId: String, extras: Set<LeoverFunction>): FunctionSelection

    fun progress(userId: String, id: TutorialId): TutorialProgressRecord
    fun markViewed(userId: String, id: TutorialId)
    fun markSkipped(userId: String, id: TutorialId)
    fun markCompleted(userId: String, id: TutorialId)

    fun selectionConfirmed(userId: String): Boolean
    fun markSelectionConfirmed(userId: String)

    fun tutorialPage(userId: String, id: TutorialId): Int
    fun markTutorialPage(userId: String, id: TutorialId, page: Int)
    fun applyRemoteProgress(
        userId: String,
        id: TutorialId,
        viewed: Boolean,
        skipped: Boolean,
        completed: Boolean
    )
}

class InMemoryOnb02Store : Onb02Store {
    private val completion = mutableMapOf<String, Onb02Completion>()
    private val selections = mutableMapOf<String, FunctionSelection>()
    private val progress = mutableMapOf<String, TutorialProgressRecord>()

    override fun completion(userId: String): Onb02Completion =
        completion[userId] ?: Onb02Completion.NOT_STARTED

    override fun markFullPending(userId: String) {
        completion[userId] = Onb02Completion.FULL_PENDING
    }

    override fun markExistingT00Pending(userId: String) {
        if (completion[userId] == null || completion[userId] == Onb02Completion.NOT_STARTED) {
            completion[userId] = Onb02Completion.EXISTING_T00_PENDING
        }
    }

    override fun markCompleted(userId: String) {
        completion[userId] = Onb02Completion.COMPLETED
    }

    override fun markExistingAcknowledged(userId: String) {
        completion[userId] = Onb02Completion.EXISTING_ACKNOWLEDGED
    }

    override fun selection(userId: String): FunctionSelection =
        selections[userId] ?: FunctionSelection()

    override fun saveSelection(userId: String, selection: FunctionSelection) {
        selections[userId] = selection
    }

    override fun addExtras(userId: String, extras: Set<LeoverFunction>): FunctionSelection {
        val current = selection(userId)
        val merged = current.copy(extras = current.extras + extras.filter { it.isSelectableExtra })
        selections[userId] = merged
        return merged
    }

    override fun progress(userId: String, id: TutorialId): TutorialProgressRecord =
        progress[key(userId, id)] ?: TutorialProgressRecord(id, id.version)

    override fun markViewed(userId: String, id: TutorialId) {
        val prev = progress(userId, id)
        progress[key(userId, id)] = prev.copy(viewed = true)
    }

    override fun markSkipped(userId: String, id: TutorialId) {
        val prev = progress(userId, id)
        progress[key(userId, id)] = prev.copy(viewed = true, skipped = true)
    }

    override fun markCompleted(userId: String, id: TutorialId) {
        val prev = progress(userId, id)
        progress[key(userId, id)] = prev.copy(viewed = true, completed = true)
    }

    private val confirmed = mutableSetOf<String>()

    override fun selectionConfirmed(userId: String): Boolean = userId in confirmed

    override fun markSelectionConfirmed(userId: String) {
        confirmed += userId
    }

    private val pages = mutableMapOf<String, Int>()

    override fun tutorialPage(userId: String, id: TutorialId): Int =
        pages[key(userId, id)] ?: 0

    override fun markTutorialPage(userId: String, id: TutorialId, page: Int) {
        pages[key(userId, id)] = page.coerceAtLeast(0)
    }

    override fun applyRemoteProgress(
        userId: String,
        id: TutorialId,
        viewed: Boolean,
        skipped: Boolean,
        completed: Boolean
    ) {
        val prev = progress(userId, id)
        if (prev.completed || prev.skipped) return
        progress[key(userId, id)] = prev.copy(
            viewed = viewed || prev.viewed,
            skipped = skipped,
            completed = completed
        )
    }

    private fun key(userId: String, id: TutorialId) = "$userId|${id.key}|${id.version}"
}

class SharedPreferencesOnb02Store(
    private val contextProvider: () -> Context? = {
        runCatching { LeoverApplication.instance }.getOrNull()
    }
) : Onb02Store {

    private val prefs get() = contextProvider()?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun completion(userId: String): Onb02Completion {
        val raw = prefs?.getString(completionKey(userId), null) ?: return Onb02Completion.NOT_STARTED
        return runCatching { Onb02Completion.valueOf(raw) }.getOrDefault(Onb02Completion.NOT_STARTED)
    }

    override fun markFullPending(userId: String) = writeCompletion(userId, Onb02Completion.FULL_PENDING)
    override fun markExistingT00Pending(userId: String) {
        if (completion(userId) == Onb02Completion.NOT_STARTED) {
            writeCompletion(userId, Onb02Completion.EXISTING_T00_PENDING)
        }
    }
    override fun markCompleted(userId: String) = writeCompletion(userId, Onb02Completion.COMPLETED)
    override fun markExistingAcknowledged(userId: String) =
        writeCompletion(userId, Onb02Completion.EXISTING_ACKNOWLEDGED)

    override fun selection(userId: String): FunctionSelection {
        val extras = prefs?.getStringSet(extrasKey(userId), emptySet()).orEmpty()
            .mapNotNull { raw -> runCatching { LeoverFunction.valueOf(raw) }.getOrNull() }
            .filter { it.isSelectableExtra }
            .toSet()
        val action = prefs?.getString(orgActionKey(userId), null)
            ?.let { runCatching { OrganizationSetupAction.valueOf(it) }.getOrNull() }
        val kind = prefs?.getString(orgKindKey(userId), null)
            ?.let { runCatching { OrganizationKindOption.valueOf(it) }.getOrNull() }
        val actorKind = prefs?.getString(actorKindKey(userId), null)
            ?.let { runCatching { ProfileActorKind.valueOf(it) }.getOrNull() }
        val specialty = prefs?.getString(professionalSpecialtyKey(userId), null)
            ?.let { runCatching { IndependentProfessionalSpecialty.valueOf(it) }.getOrNull() }
        return FunctionSelection(
            extras = extras,
            organizationAction = action,
            organizationKind = kind,
            actorKind = actorKind,
            professionalSpecialty = specialty
        )
    }

    override fun saveSelection(userId: String, selection: FunctionSelection) {
        prefs?.edit()
            ?.putStringSet(extrasKey(userId), selection.extras.map { it.name }.toSet())
            ?.apply {
                selection.organizationAction?.let { putString(orgActionKey(userId), it.name) }
                    ?: remove(orgActionKey(userId))
                selection.organizationKind?.let { putString(orgKindKey(userId), it.name) }
                    ?: remove(orgKindKey(userId))
                selection.actorKind?.let { putString(actorKindKey(userId), it.name) }
                    ?: remove(actorKindKey(userId))
                selection.professionalSpecialty?.let { putString(professionalSpecialtyKey(userId), it.name) }
                    ?: remove(professionalSpecialtyKey(userId))
            }
            ?.apply()
    }

    override fun addExtras(userId: String, extras: Set<LeoverFunction>): FunctionSelection {
        val merged = selection(userId).copy(
            extras = selection(userId).extras + extras.filter { it.isSelectableExtra }
        )
        saveSelection(userId, merged)
        return merged
    }

    override fun progress(userId: String, id: TutorialId): TutorialProgressRecord {
        val prefix = progressPrefix(userId, id)
        val stored = prefs ?: return TutorialProgressRecord(id, id.version)
        return TutorialProgressRecord(
            tutorialId = id,
            version = id.version,
            viewed = stored.getBoolean("${prefix}_viewed", false),
            skipped = stored.getBoolean("${prefix}_skipped", false),
            completed = stored.getBoolean("${prefix}_completed", false)
        )
    }

    override fun markViewed(userId: String, id: TutorialId) {
        prefs?.edit()?.putBoolean("${progressPrefix(userId, id)}_viewed", true)?.apply()
    }

    override fun markSkipped(userId: String, id: TutorialId) {
        prefs?.edit()
            ?.putBoolean("${progressPrefix(userId, id)}_viewed", true)
            ?.putBoolean("${progressPrefix(userId, id)}_skipped", true)
            ?.apply()
    }

    override fun markCompleted(userId: String, id: TutorialId) {
        prefs?.edit()
            ?.putBoolean("${progressPrefix(userId, id)}_viewed", true)
            ?.putBoolean("${progressPrefix(userId, id)}_completed", true)
            ?.apply()
    }

    override fun selectionConfirmed(userId: String): Boolean =
        prefs?.getBoolean(selectionConfirmedKey(userId), false) == true

    override fun markSelectionConfirmed(userId: String) {
        prefs?.edit()?.putBoolean(selectionConfirmedKey(userId), true)?.apply()
    }

    override fun tutorialPage(userId: String, id: TutorialId): Int =
        prefs?.getInt("${progressPrefix(userId, id)}_page", 0) ?: 0

    override fun markTutorialPage(userId: String, id: TutorialId, page: Int) {
        prefs?.edit()?.putInt("${progressPrefix(userId, id)}_page", page.coerceAtLeast(0))?.apply()
    }

    override fun applyRemoteProgress(
        userId: String,
        id: TutorialId,
        viewed: Boolean,
        skipped: Boolean,
        completed: Boolean
    ) {
        val prev = progress(userId, id)
        if (prev.completed || prev.skipped) return
        if (completed) markCompleted(userId, id)
        else if (skipped) markSkipped(userId, id)
        else if (viewed) markViewed(userId, id)
    }

    private fun writeCompletion(userId: String, value: Onb02Completion) {
        prefs?.edit()?.putString(completionKey(userId), value.name)?.apply()
    }

    private fun completionKey(userId: String) = "completion_$userId"
    private fun extrasKey(userId: String) = "extras_$userId"
    private fun orgActionKey(userId: String) = "org_action_$userId"
    private fun orgKindKey(userId: String) = "org_kind_$userId"
    private fun actorKindKey(userId: String) = "actor_kind_$userId"
    private fun professionalSpecialtyKey(userId: String) = "professional_specialty_$userId"
    private fun selectionConfirmedKey(userId: String) = "selection_confirmed_$userId"
    private fun progressPrefix(userId: String, id: TutorialId) =
        "tut_${userId}_${id.key}_v${id.version}"

    companion object {
        private const val PREFS = "leover_onb02"
    }
}

object Onb02StoreProvider {
    @Volatile
    var override: Onb02Store? = null

    val instance: Onb02Store
        get() = override ?: SharedPreferencesOnb02Store()

    fun decideEntry(
        userId: String,
        justCompletedProfileSetup: Boolean,
        personOnboardingComplete: Boolean = false
    ): Onb02FlowKind? {
        val store = instance
        if (
            Onb02EntryPolicy.skipSelectorForExistingComplete(
                completion = store.completion(userId),
                personOnboardingComplete = personOnboardingComplete,
                justCompletedProfileSetup = justCompletedProfileSetup
            )
        ) {
            store.markCompleted(userId)
            store.markSelectionConfirmed(userId)
            return null
        }
        return when (store.completion(userId)) {
            Onb02Completion.FULL_PENDING -> Onb02FlowKind.FULL_ONBOARDING
            Onb02Completion.EXISTING_T00_PENDING -> {
                store.markExistingAcknowledged(userId)
                null
            }
            Onb02Completion.COMPLETED, Onb02Completion.EXISTING_ACKNOWLEDGED -> null
            Onb02Completion.NOT_STARTED -> {
                store.markFullPending(userId)
                Onb02FlowKind.FULL_ONBOARDING
            }
        }
    }
}
