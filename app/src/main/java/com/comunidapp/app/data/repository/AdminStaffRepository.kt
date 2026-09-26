package com.comunidapp.app.data.repository

import com.comunidapp.app.BuildConfig
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.authorization.AdminAccessPolicy
import com.comunidapp.app.domain.authorization.PlatformRoleCode
import com.comunidapp.app.domain.canonical.CanonicalBackend
import io.github.jan.supabase.auth.auth
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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter

data class AdminStaffSummary(
    val userId: String,
    val displayName: String,
    val username: String,
    val role: PlatformRoleCode,
    val active: Boolean,
    val mustChangePassword: Boolean,
    val isRoot: Boolean,
    val createdAtIso: String? = null,
    val lastSignInAtIso: String? = null
)

data class AdminStaffAuditEntry(
    val action: String,
    val occurredAtIso: String,
    val actorUsername: String
)

data class AdminStaffCredentials(
    val username: String,
    val temporaryPassword: String
)

data class AdminStaffCreateInput(
    val displayName: String,
    val username: String,
    val role: PlatformRoleCode,
    val active: Boolean
)

class AdminStaffException(val code: String, message: String) : Exception(message)

interface AdminStaffRepository {
    suspend fun list(query: String = ""): Result<List<AdminStaffSummary>>
    suspend fun get(userId: String): Result<AdminStaffSummary>
    suspend fun listAudit(userId: String): Result<List<AdminStaffAuditEntry>>
    suspend fun create(input: AdminStaffCreateInput): Result<AdminStaffCredentials>
    suspend fun setDisabled(userId: String, disabled: Boolean): Result<Unit>
    suspend fun setRole(userId: String, role: PlatformRoleCode): Result<Unit>
    suspend fun forcePasswordChange(userId: String): Result<Unit>
    suspend fun resetPassword(userId: String): Result<AdminStaffCredentials>
}

class MockAdminStaffRepository : AdminStaffRepository {
    private val items = mutableListOf<AdminStaffSummary>()
    private val audit = mutableMapOf<String, MutableList<AdminStaffAuditEntry>>()

    override suspend fun list(query: String): Result<List<AdminStaffSummary>> {
        val q = query.trim().lowercase()
        return Result.success(
            items.filter {
                q.isBlank() ||
                    it.username.contains(q) ||
                    it.displayName.lowercase().contains(q)
            }
        )
    }

    override suspend fun get(userId: String): Result<AdminStaffSummary> {
        val row = items.firstOrNull { it.userId == userId }
            ?: return Result.failure(AdminStaffException("STAFF_NOT_FOUND", "No se encontró el usuario."))
        return Result.success(row)
    }

    override suspend fun listAudit(userId: String): Result<List<AdminStaffAuditEntry>> =
        Result.success(audit[userId].orEmpty())

    override suspend fun create(input: AdminStaffCreateInput): Result<AdminStaffCredentials> {
        if (input.role !in AdminAccessPolicy.assignableStaffRoles()) {
            return Result.failure(AdminStaffException("ROLE_FORBIDDEN", "Ese rol no se puede asignar."))
        }
        val username = input.username.trim().lowercase()
        if (!username.matches(Regex("^[a-z0-9._-]{3,64}$"))) {
            return Result.failure(AdminStaffException("USERNAME_INVALID", "El usuario no es válido."))
        }
        if (items.any { it.username == username }) {
            return Result.failure(AdminStaffException("USERNAME_TAKEN", "Ese usuario ya existe."))
        }
        val id = UUID.randomUUID().toString()
        items.add(
            AdminStaffSummary(
                userId = id,
                displayName = input.displayName.trim(),
                username = username,
                role = input.role,
                active = input.active,
                mustChangePassword = true,
                isRoot = false
            )
        )
        append(id, "ADMIN_STAFF_CREATED")
        return Result.success(AdminStaffCredentials(username, generateTemporaryPassword()))
    }

    override suspend fun setDisabled(userId: String, disabled: Boolean): Result<Unit> {
        val index = items.indexOfFirst { it.userId == userId }
        if (index < 0) return Result.failure(AdminStaffException("STAFF_NOT_FOUND", "No se encontró el usuario."))
        if (items[index].isRoot) {
            return Result.failure(AdminStaffException("ROOT_PROTECTED", "La cuenta raíz no se puede modificar."))
        }
        items[index] = items[index].copy(active = !disabled)
        append(userId, if (disabled) "ADMIN_STAFF_DISABLED" else "ADMIN_STAFF_ENABLED")
        return Result.success(Unit)
    }

    override suspend fun setRole(userId: String, role: PlatformRoleCode): Result<Unit> {
        if (role !in AdminAccessPolicy.assignableStaffRoles()) {
            return Result.failure(AdminStaffException("ROLE_FORBIDDEN", "Ese rol no se puede asignar."))
        }
        val index = items.indexOfFirst { it.userId == userId }
        if (index < 0) return Result.failure(AdminStaffException("STAFF_NOT_FOUND", "No se encontró el usuario."))
        if (items[index].isRoot || items[index].role == PlatformRoleCode.SUPERADMIN) {
            return Result.failure(AdminStaffException("ROOT_PROTECTED", "La cuenta raíz no se puede modificar."))
        }
        items[index] = items[index].copy(role = role)
        append(userId, "ADMIN_STAFF_ROLE_CHANGED")
        return Result.success(Unit)
    }

    override suspend fun forcePasswordChange(userId: String): Result<Unit> {
        val index = items.indexOfFirst { it.userId == userId }
        if (index < 0) return Result.failure(AdminStaffException("STAFF_NOT_FOUND", "No se encontró el usuario."))
        if (items[index].isRoot) {
            return Result.failure(AdminStaffException("ROOT_PROTECTED", "La cuenta raíz no se puede modificar."))
        }
        items[index] = items[index].copy(mustChangePassword = true)
        append(userId, "ADMIN_STAFF_FORCE_PASSWORD_CHANGE")
        return Result.success(Unit)
    }

    override suspend fun resetPassword(userId: String): Result<AdminStaffCredentials> {
        val row = items.firstOrNull { it.userId == userId }
            ?: return Result.failure(AdminStaffException("STAFF_NOT_FOUND", "No se encontró el usuario."))
        if (row.isRoot) {
            return Result.failure(AdminStaffException("ROOT_PROTECTED", "La cuenta raíz no se puede modificar."))
        }
        val index = items.indexOf(row)
        items[index] = row.copy(mustChangePassword = true)
        append(userId, "ADMIN_STAFF_PASSWORD_RESET")
        return Result.success(AdminStaffCredentials(row.username, generateTemporaryPassword()))
    }

    private fun append(userId: String, action: String) {
        audit.getOrPut(userId) { mutableListOf() }.add(
            0,
            AdminStaffAuditEntry(action, "", "local")
        )
    }
}

class SupabaseAdminStaffRepository : AdminStaffRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun list(query: String): Result<List<AdminStaffSummary>> = runCatching {
        val element = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_LIST_ADMIN_STAFF,
            parameters = buildJsonObject { put("p_query", query) }
        ).decodeAs<JsonElement>()
        decodeList(element).map { it.toSummary() }
    }

    override suspend fun get(userId: String): Result<AdminStaffSummary> = runCatching {
        val element = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_GET_ADMIN_STAFF,
            parameters = buildJsonObject { put("p_user_id", userId) }
        ).decodeAs<JsonElement>()
        json.decodeFromJsonElement(StaffRpcRow.serializer(), element).toSummary()
    }

    override suspend fun listAudit(userId: String): Result<List<AdminStaffAuditEntry>> = runCatching {
        val element = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_LIST_ADMIN_STAFF_AUDIT,
            parameters = buildJsonObject { put("p_user_id", userId) }
        ).decodeAs<JsonElement>()
        val array = element as? JsonArray ?: return@runCatching emptyList()
        array.mapNotNull {
            runCatching { json.decodeFromJsonElement(StaffAuditRpcRow.serializer(), it) }.getOrNull()
        }.map { AdminStaffAuditEntry(it.action, it.occurredAt.orEmpty(), it.actorUsername.orEmpty()) }
    }

    override suspend fun create(input: AdminStaffCreateInput): Result<AdminStaffCredentials> {
        if (input.role !in AdminAccessPolicy.assignableStaffRoles()) {
            return Result.failure(AdminStaffException("ROLE_FORBIDDEN", "Ese rol no se puede asignar."))
        }
        return invokeStaffFunction(
            JSONObject()
                .put("action", "create")
                .put("display_name", input.displayName.trim())
                .put("username", input.username.trim().lowercase())
                .put("role", input.role.name)
                .put("active", input.active)
        ).map { payload ->
            AdminStaffCredentials(
                username = payload.optString("username"),
                temporaryPassword = payload.optString("temporary_password")
            )
        }
    }

    override suspend fun setDisabled(userId: String, disabled: Boolean): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_STAFF_SET_DISABLED,
            parameters = buildJsonObject {
                put("p_user_id", userId)
                put("p_disabled", disabled)
            }
        )
        Unit
    }.recoverCatching { throw mapStaffFailure(it) }

    override suspend fun setRole(userId: String, role: PlatformRoleCode): Result<Unit> {
        if (role !in AdminAccessPolicy.assignableStaffRoles()) {
            return Result.failure(AdminStaffException("ROLE_FORBIDDEN", "Ese rol no se puede asignar."))
        }
        return runCatching {
            supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_STAFF_SET_ROLE,
                parameters = buildJsonObject {
                    put("p_user_id", userId)
                    put("p_role_code", role.name)
                }
            )
            Unit
        }.recoverCatching { throw mapStaffFailure(it) }
    }

    override suspend fun forcePasswordChange(userId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_STAFF_FORCE_PASSWORD_CHANGE,
            parameters = buildJsonObject { put("p_user_id", userId) }
        )
        Unit
    }.recoverCatching { throw mapStaffFailure(it) }

    override suspend fun resetPassword(userId: String): Result<AdminStaffCredentials> {
        return invokeStaffFunction(
            JSONObject()
                .put("action", "reset_password")
                .put("user_id", userId)
        ).map { payload ->
            AdminStaffCredentials(
                username = "",
                temporaryPassword = payload.optString("temporary_password")
            )
        }
    }

    private fun decodeList(element: JsonElement): List<StaffRpcRow> {
        val array = when (element) {
            is JsonArray -> element
            is JsonObject -> JsonArray(listOf(element))
            is JsonNull -> JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        return array.mapNotNull {
            runCatching { json.decodeFromJsonElement(StaffRpcRow.serializer(), it) }.getOrNull()
        }
    }

    private suspend fun invokeStaffFunction(body: JSONObject): Result<JSONObject> {
        val session = supabase.auth.currentSessionOrNull()
            ?: return Result.failure(AdminStaffException("NOT_AUTHENTICATED", "Sesión vencida."))
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/admin-staff")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 30_000
                    readTimeout = 30_000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer ${session.accessToken}")
                    setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    setRequestProperty("Content-Type", "application/json")
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
                val code = conn.responseCode
                val text = runCatching {
                    (if (code in 200..299) conn.inputStream else conn.errorStream)
                        ?.bufferedReader()
                        ?.readText()
                        .orEmpty()
                }.getOrDefault("")
                conn.disconnect()
                val payload = runCatching { JSONObject(text.ifBlank { "{}" }) }.getOrDefault(JSONObject())
                if (code in 200..299 && payload.optBoolean("ok", false)) {
                    Result.success(payload)
                } else {
                    Result.failure(staffHttpError(payload.optString("error"), code))
                }
            } catch (e: Exception) {
                Result.failure(AdminStaffException("NETWORK", "No se pudo completar la operación."))
            }
        }
    }
}

@Serializable
private data class StaffRpcRow(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String? = null,
    val username: String? = null,
    @SerialName("role_code") val roleCode: String? = null,
    val active: Boolean = true,
    @SerialName("must_change_password") val mustChangePassword: Boolean = false,
    @SerialName("is_root") val isRoot: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_sign_in_at") val lastSignInAt: String? = null
) {
    fun toSummary(): AdminStaffSummary {
        val role = runCatching {
            PlatformRoleCode.valueOf(roleCode.orEmpty().uppercase())
        }.getOrDefault(PlatformRoleCode.USER)
        return AdminStaffSummary(
            userId = userId,
            displayName = displayName.orEmpty(),
            username = username.orEmpty(),
            role = role,
            active = active,
            mustChangePassword = mustChangePassword,
            isRoot = isRoot,
            createdAtIso = createdAt,
            lastSignInAtIso = lastSignInAt
        )
    }
}

@Serializable
private data class StaffAuditRpcRow(
    val action: String,
    @SerialName("occurred_at") val occurredAt: String? = null,
    @SerialName("actor_username") val actorUsername: String? = null
)

internal fun generateTemporaryPassword(): String {
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%"
    val rng = SecureRandom()
    return CharArray(20) { alphabet[rng.nextInt(alphabet.length)] }.concatToString()
}

internal fun mapStaffFailure(error: Throwable): AdminStaffException {
    if (error is AdminStaffException) return error
    val raw = error.message.orEmpty().uppercase()
    return staffHttpError(
        when {
            raw.contains("ROOT_PROTECTED") -> "root_protected"
            raw.contains("USERNAME_TAKEN") -> "username_taken"
            raw.contains("USERNAME_INVALID") -> "username_invalid"
            raw.contains("ROLE_FORBIDDEN") -> "role_forbidden"
            raw.contains("RATE_LIMITED") -> "rate_limited"
            raw.contains("FORBIDDEN") -> "forbidden"
            raw.contains("STAFF_NOT_FOUND") -> "staff_not_found"
            else -> "rpc_failed"
        },
        400
    )
}

internal fun staffHttpError(code: String, http: Int): AdminStaffException {
    val normalized = code.trim().lowercase().ifBlank { "rpc_failed" }
    val message = when (normalized) {
        "root_protected" -> "La cuenta raíz no se puede modificar."
        "username_taken" -> "Ese usuario ya existe."
        "username_invalid" -> "El usuario no es válido."
        "role_forbidden" -> "Ese rol no se puede asignar."
        "rate_limited" -> "Demasiados intentos. Probá más tarde."
        "forbidden" -> "No tenés permiso para esta acción."
        "staff_not_found" -> "No se encontró el usuario."
        "name_required" -> "El nombre es obligatorio."
        else -> "No se pudo completar la operación."
    }
    return AdminStaffException(normalized.uppercase(), message)
}
