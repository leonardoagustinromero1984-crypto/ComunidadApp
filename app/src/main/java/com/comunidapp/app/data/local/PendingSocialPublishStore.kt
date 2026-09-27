package com.comunidapp.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.comunidapp.app.domain.social.PendingSocialPublish
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.reelPublishDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "leover_pending_social_publish"
)

class PendingSocialPublishStore(context: Context) {
    private val dataStore = context.applicationContext.reelPublishDataStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun jobFlow(userId: String?): Flow<PendingSocialPublish?> = dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs -> ownedOf(prefs, userId) }

    suspend fun read(userId: String?): PendingSocialPublish? =
        runCatching { ownedOf(dataStore.data.first(), userId) }.getOrNull()

    suspend fun write(job: PendingSocialPublish) {
        dataStore.edit { prefs ->
            prefs[keyFor(job.actorUserId)] = json.encodeToString(PendingSocialPublish.serializer(), job)
            val legacy = decode(prefs[KEY_JOB])
            if (legacy == null || legacy.actorUserId == job.actorUserId) {
                prefs.remove(KEY_JOB)
            }
        }
    }

    suspend fun clear(userId: String?) {
        if (userId.isNullOrBlank()) return
        dataStore.edit { prefs ->
            prefs.remove(keyFor(userId))
            val legacy = decode(prefs[KEY_JOB])
            if (legacy != null && legacy.actorUserId == userId) {
                prefs.remove(KEY_JOB)
            }
        }
    }

    private fun ownedOf(prefs: Preferences, userId: String?): PendingSocialPublish? {
        if (userId.isNullOrBlank()) return null
        decode(prefs[keyFor(userId)])?.takeIf { it.belongsTo(userId) }?.let { return it }
        return decode(prefs[KEY_JOB])?.takeIf { it.belongsTo(userId) }
    }

    private fun decode(raw: String?): PendingSocialPublish? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return runCatching { json.decodeFromString(PendingSocialPublish.serializer(), text) }.getOrNull()
    }

    companion object {
        private val KEY_JOB = stringPreferencesKey("pending_reel_job_v1")

        fun keyFor(userId: String): Preferences.Key<String> =
            stringPreferencesKey("pending_reel_job_v2_${userId.trim()}")
    }
}
