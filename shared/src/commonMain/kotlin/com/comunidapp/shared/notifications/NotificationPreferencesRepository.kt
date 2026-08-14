package com.comunidapp.shared.notifications

import com.comunidapp.shared.session.SessionRepository
import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.ui.ErrorSanitizer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import io.github.jan.supabase.postgrest.rpc

@Serializable
internal data class RemoteNotificationPreferenceRow(
    @SerialName("user_id") val userId: String? = null,
    val category: String = "",
    @SerialName("in_app_enabled") val inAppEnabled: Boolean = true,
    @SerialName("push_enabled") val pushEnabled: Boolean = true,
    @SerialName("email_enabled") val emailEnabled: Boolean = false,
    @SerialName("marketing_consent") val marketingConsent: Boolean = false,
    val timezone: String = "UTC",
    @SerialName("quiet_hours_start") val quietHoursStart: String? = null,
    @SerialName("quiet_hours_end") val quietHoursEnd: String? = null
)

internal interface NotificationPreferencesGateway {
    suspend fun getPreferences(): Result<List<RemoteNotificationPreferenceRow>>
    suspend fun updatePreference(
        category: String,
        inAppEnabled: Boolean,
        pushEnabled: Boolean,
        marketingConsent: Boolean,
        timezone: String,
        quietHoursStart: String?,
        quietHoursEnd: String?
    ): Result<RemoteNotificationPreferenceRow>
}

internal class SupabaseNotificationPreferencesGateway(
    private val client: SupabaseClient
) : NotificationPreferencesGateway {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getPreferences(): Result<List<RemoteNotificationPreferenceRow>> = try {
        val element = client.postgrest.rpc(
            function = "m06_get_preferences",
            parameters = buildJsonObject {}
        ).decodeAs<JsonElement>()
        val rows = when (element) {
            is JsonArray -> element.mapNotNull { el ->
                runCatching {
                    json.decodeFromJsonElement<RemoteNotificationPreferenceRow>(el)
                }.getOrNull()
            }
            is JsonObject -> listOf(
                json.decodeFromJsonElement<RemoteNotificationPreferenceRow>(element)
            )
            else -> emptyList()
        }
        Result.success(rows.filter { it.category.isNotBlank() })
    } catch (t: Throwable) {
        Result.failure(t)
    }

    override suspend fun updatePreference(
        category: String,
        inAppEnabled: Boolean,
        pushEnabled: Boolean,
        marketingConsent: Boolean,
        timezone: String,
        quietHoursStart: String?,
        quietHoursEnd: String?
    ): Result<RemoteNotificationPreferenceRow> = try {
        val element = client.postgrest.rpc(
            function = "m06_update_preference",
            parameters = buildJsonObject {
                put("p_category", category)
                put("p_in_app_enabled", inAppEnabled)
                put("p_push_enabled", pushEnabled)
                put("p_email_enabled", false)
                put("p_marketing_consent", marketingConsent)
                put("p_timezone", timezone.ifBlank { "UTC" })
                if (quietHoursStart != null) {
                    put("p_quiet_hours_start", quietHoursStart)
                } else {
                    put("p_quiet_hours_start", JsonNull)
                }
                if (quietHoursEnd != null) {
                    put("p_quiet_hours_end", quietHoursEnd)
                } else {
                    put("p_quiet_hours_end", JsonNull)
                }
                put("p_quiet_hours_days", JsonNull)
            }
        ).decodeAs<JsonObject>()
        Result.success(json.decodeFromJsonElement(element))
    } catch (t: Throwable) {
        Result.failure(t)
    }
}

internal class FakeNotificationPreferencesGateway(
    var rows: MutableList<RemoteNotificationPreferenceRow> = mutableListOf(
        RemoteNotificationPreferenceRow(category = "PET", pushEnabled = true),
        RemoteNotificationPreferenceRow(category = "LOST_FOUND", pushEnabled = true),
        RemoteNotificationPreferenceRow(category = "ADOPTION", pushEnabled = true),
        RemoteNotificationPreferenceRow(category = "SECURITY", pushEnabled = false, inAppEnabled = true)
    ),
    var getError: Throwable? = null,
    var updateError: Throwable? = null,
    var updateCalls: Int = 0,
    var lastUpdateCategory: String? = null,
    var lastEmailEnabled: Boolean? = null
) : NotificationPreferencesGateway {
    override suspend fun getPreferences(): Result<List<RemoteNotificationPreferenceRow>> {
        getError?.let { return Result.failure(it) }
        return Result.success(rows.toList())
    }

    override suspend fun updatePreference(
        category: String,
        inAppEnabled: Boolean,
        pushEnabled: Boolean,
        marketingConsent: Boolean,
        timezone: String,
        quietHoursStart: String?,
        quietHoursEnd: String?
    ): Result<RemoteNotificationPreferenceRow> {
        updateCalls++
        lastUpdateCategory = category
        lastEmailEnabled = false
        updateError?.let { return Result.failure(it) }
        if (!inAppEnabled && category.uppercase() in MANDATORY_IN_APP) {
            return Result.failure(IllegalStateException("M06_IN_APP_MANDATORY"))
        }
        val updated = RemoteNotificationPreferenceRow(
            category = category.uppercase(),
            inAppEnabled = inAppEnabled,
            pushEnabled = pushEnabled,
            emailEnabled = false,
            marketingConsent = marketingConsent,
            timezone = timezone,
            quietHoursStart = quietHoursStart,
            quietHoursEnd = quietHoursEnd
        )
        val idx = rows.indexOfFirst { it.category.equals(category, ignoreCase = true) }
        if (idx >= 0) rows[idx] = updated else rows += updated
        return Result.success(updated)
    }

    companion object {
        private val MANDATORY_IN_APP = setOf(
            "ACCOUNT", "SECURITY", "INVITATION", "MODERATION", "APPEAL", "VERIFICATION", "PAYMENT"
        )
    }
}

internal class RemoteNotificationPreferencesRepository(
    private val gateway: NotificationPreferencesGateway,
    private val sessionRepository: SessionRepository
) : NotificationPreferencesRepository {
    override val dataMode: NotificationPreferencesDataMode =
        NotificationPreferencesDataMode.REAL_REMOTE

    override suspend fun getPreferences(): Result<List<SharedNotificationPreference>> {
        if (sessionRepository.currentSession() !is SessionState.Authenticated) {
            return Result.failure(IllegalStateException("UNAUTHENTICATED"))
        }
        return gateway.getPreferences().map { rows ->
            rows.map { it.toShared() }
        }
    }

    override suspend fun updatePreference(
        preference: SharedNotificationPreference
    ): NotificationPreferenceWriteResult {
        if (sessionRepository.currentSession() !is SessionState.Authenticated) {
            return NotificationPreferenceWriteResult.Unauthenticated("Tu sesión no está disponible.")
        }
        val result = gateway.updatePreference(
            category = preference.category.trim().uppercase(),
            inAppEnabled = preference.inAppEnabled,
            pushEnabled = preference.pushEnabled,
            marketingConsent = preference.marketingConsent,
            timezone = preference.timezone.ifBlank { "UTC" },
            quietHoursStart = preference.quietHoursStart,
            quietHoursEnd = preference.quietHoursEnd
        )
        return result.fold(
            onSuccess = {
                NotificationPreferenceWriteResult.Success(it.toShared())
            },
            onFailure = { mapWrite(it) }
        )
    }

    private fun mapWrite(t: Throwable): NotificationPreferenceWriteResult {
        val code = t.message.orEmpty()
        val msg = mapPrefsThrowable(t)
        return when {
            "UNAUTHENTICATED" in code || "401" in code.lowercase() || "jwt" in code.lowercase() ->
                NotificationPreferenceWriteResult.Unauthenticated(msg)
            "M06_IN_APP_MANDATORY" in code || "IN_APP_MANDATORY" in code ->
                NotificationPreferenceWriteResult.ValidationError(
                    "Las notificaciones in-app son obligatorias para esta categoría."
                )
            "M06_CATEGORY_INVALID" in code ->
                NotificationPreferenceWriteResult.ValidationError("Categoría no válida.")
            "403" in code.lowercase() || "forbidden" in code.lowercase() ->
                NotificationPreferenceWriteResult.Forbidden(msg)
            else -> NotificationPreferenceWriteResult.BackendError(msg)
        }
    }
}

internal fun RemoteNotificationPreferenceRow.toShared(): SharedNotificationPreference =
    SharedNotificationPreference(
        category = category,
        inAppEnabled = inAppEnabled,
        pushEnabled = pushEnabled,
        emailEnabled = false,
        marketingConsent = marketingConsent,
        timezone = timezone.ifBlank { "UTC" },
        quietHoursStart = quietHoursStart,
        quietHoursEnd = quietHoursEnd
    )

internal fun mapPrefsThrowable(t: Throwable): String {
    val raw = t.message.orEmpty().lowercase()
    return when {
        "401" in raw || "jwt" in raw || "unauthenticated" in raw ->
            "Tu sesión no está disponible."
        "403" in raw || "forbidden" in raw ->
            "No tenés permiso para esta acción."
        "network" in raw || "timeout" in raw ->
            "Problema de conexión. Intentá nuevamente."
        else -> ErrorSanitizer.sanitize(t)
    }
}

class FakeNotificationPreferencesRepository(
    initial: List<SharedNotificationPreference> = listOf(
        SharedNotificationPreference(category = "PET", pushEnabled = true),
        SharedNotificationPreference(category = "LOST_FOUND", pushEnabled = true),
        SharedNotificationPreference(category = "ADOPTION", pushEnabled = true),
        SharedNotificationPreference(category = "SECURITY", pushEnabled = false, inAppEnabled = true)
    ),
    private val fail: Boolean = false
) : NotificationPreferencesRepository {
    override val dataMode: NotificationPreferencesDataMode =
        NotificationPreferencesDataMode.SHARED_FAKE

    private val rows = initial.toMutableList()
    var updateCalls: Int = 0
        private set
    var lastEmailForcedFalse: Boolean = false
        private set

    override suspend fun getPreferences(): Result<List<SharedNotificationPreference>> {
        if (fail) return Result.failure(IllegalStateException("PREFS_UNAVAILABLE"))
        return Result.success(rows.toList())
    }

    override suspend fun updatePreference(
        preference: SharedNotificationPreference
    ): NotificationPreferenceWriteResult {
        if (fail) {
            return NotificationPreferenceWriteResult.BackendError("Preferencias no disponibles.")
        }
        updateCalls++
        lastEmailForcedFalse = true
        val category = preference.category.trim().uppercase()
        if (!preference.inAppEnabled && category in MANDATORY_IN_APP) {
            return NotificationPreferenceWriteResult.ValidationError(
                "Las notificaciones in-app son obligatorias para esta categoría."
            )
        }
        val updated = preference.copy(
            category = category,
            emailEnabled = false
        )
        val idx = rows.indexOfFirst { it.category.equals(category, ignoreCase = true) }
        if (idx >= 0) rows[idx] = updated else rows += updated
        return NotificationPreferenceWriteResult.Success(updated)
    }

    companion object {
        private val MANDATORY_IN_APP = setOf(
            "ACCOUNT", "SECURITY", "INVITATION", "MODERATION", "APPEAL", "VERIFICATION", "PAYMENT"
        )
    }
}

class UnconfiguredNotificationPreferencesRepository : NotificationPreferencesRepository {
    override val dataMode: NotificationPreferencesDataMode =
        NotificationPreferencesDataMode.REAL_REMOTE

    override suspend fun getPreferences(): Result<List<SharedNotificationPreference>> =
        Result.failure(IllegalStateException("UNAVAILABLE"))

    override suspend fun updatePreference(
        preference: SharedNotificationPreference
    ): NotificationPreferenceWriteResult =
        NotificationPreferenceWriteResult.BackendError("Servicio no configurado.")
}
