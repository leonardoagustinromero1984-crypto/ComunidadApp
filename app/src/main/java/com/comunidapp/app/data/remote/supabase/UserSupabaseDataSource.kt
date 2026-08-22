package com.comunidapp.app.data.remote.supabase

import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.remote.supabase.canonical.toUser
import com.comunidapp.app.domain.user.CompleteOnboardingCommand
import com.comunidapp.app.domain.user.PublicUserProfile
import com.comunidapp.app.domain.user.UpdateMyProfileCommand
import com.comunidapp.app.domain.user.UserPrivacySettings
import com.comunidapp.app.domain.user.UserProfile
import com.comunidapp.app.domain.user.UserProfileMapper
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlin.coroutines.coroutineContext

@Serializable
data class SearchPersonRpcRow(
    @SerialName("user_id") val userId: String,
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_asset_id") val avatarAssetId: String? = null
)

@Serializable
data class PublicProfileRpcRow(
    val id: String,
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_path") val avatarPath: String? = null,
    val bio: String? = null,
    @SerialName("location_text") val locationText: String? = null,
    val city: String? = null,
    val province: String? = null,
    @SerialName("country_code") val countryCode: String? = null
)

@Serializable
data class PrivacySettingsRow(
    @SerialName("user_id") val userId: String,
    @SerialName("profile_visibility") val profileVisibility: String = "PRIVATE",
    @SerialName("show_location") val showLocation: Boolean = true,
    @SerialName("show_phone") val showPhone: Boolean = false,
    @SerialName("allow_friend_requests") val allowFriendRequests: Boolean = true
)

@Serializable
data class PrivacySettingsUpdateRow(
    @SerialName("profile_visibility") val profileVisibility: String,
    @SerialName("show_location") val showLocation: Boolean,
    @SerialName("show_phone") val showPhone: Boolean,
    @SerialName("allow_friend_requests") val allowFriendRequests: Boolean,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
private data class PrivacyStatePatch(
    @SerialName("privacy_state") val privacyState: String
)

class UserSupabaseDataSource {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getUser(userId: String): User? {
        return try {
            val person = supabase.from(SupabaseTables.PERSONS)
                .select {
                    filter { eq("user_id", userId) }
                }
                .decodeSingleOrNull<com.comunidapp.app.data.remote.supabase.canonical.CanonicalPersonRow>()
            if (person != null) {
                val email = supabase.auth.currentUserOrNull()?.email.orEmpty()
                val verified = supabase.auth.currentUserOrNull()?.emailConfirmedAt != null
                return person.toUser(email = email, emailVerified = verified)
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun createUser(user: User): Result<Unit> {
        // handle_new_user trigger; client INSERT revoked after 016.
        return Result.success(Unit)
    }

    /**
     * Direct UPDATE revoked after M02-016. Prefer [updateMyProfile].
     */
    suspend fun updateUser(user: User): Result<Unit> =
        updateMyProfile(
            UpdateMyProfileCommand(
                displayName = user.displayName ?: user.name,
                bio = user.bio,
                city = user.city,
                province = user.province,
                countryCode = user.countryCode,
                locale = user.locale,
                timezone = user.timezone,
                avatarPath = user.avatarPath
            )
        ).map { }

    suspend fun updateEmailVerified(userId: String, verified: Boolean): Result<Unit> {
        // Revoked client UPDATE of sensitive columns; Auth confirmation es fuente de verdad.
        return Result.success(Unit)
    }

    fun observeUser(userId: String): Flow<User?> = flow {
        while (coroutineContext.isActive) {
            try {
                emit(getUserAllowThrow(userId))
            } catch (_: Exception) {
                // Transient read errors must not look like "PERSON missing".
            }
            delay(4_000)
        }
    }

    private suspend fun getUserAllowThrow(userId: String): User? {
        val person = supabase.from(SupabaseTables.PERSONS)
            .select {
                filter { eq("user_id", userId) }
            }
            .decodeSingleOrNull<com.comunidapp.app.data.remote.supabase.canonical.CanonicalPersonRow>()
        if (person != null) {
            val email = supabase.auth.currentUserOrNull()?.email.orEmpty()
            val verified = supabase.auth.currentUserOrNull()?.emailConfirmedAt != null
            return person.toUser(email = email, emailVerified = verified)
        }
        return null
    }

    suspend fun fetchUsers(limit: Int = 100): List<User> = emptyList()

    suspend fun searchUsers(query: String, excludeUserId: String): List<User> = emptyList()

    fun observeUsers(): Flow<List<User>> = pollingFlow { fetchUsers() }

    suspend fun getOwnProfile(userId: String): Result<UserProfile> {
        val user = getUser(userId)
            ?: return Result.failure(IllegalStateException("USER_NOT_FOUND"))
        val privacy = getPrivacySettings(userId).getOrElse { UserPrivacySettings() }
        return Result.success(UserProfileMapper.toUserProfile(user, privacy = privacy))
    }

    suspend fun isUsernameAvailable(username: String): Result<Boolean> {
        return runCatching {
            supabase.postgrest.rpc(
                function = com.comunidapp.app.domain.canonical.CanonicalBackend.RPC_IS_USERNAME_AVAILABLE,
                parameters = buildJsonObject { put("p_username", username) }
            ).decodeAs<Boolean>()
        }
    }

    suspend fun completeOnboarding(command: CompleteOnboardingCommand): Result<UserProfile> {
        val localityId = command.homeLocalityId?.trim().orEmpty()
        if (localityId.isEmpty()) {
            return Result.failure(IllegalArgumentException("HOME_LOCALITY_REQUIRED"))
        }
        return try {
            val uid = supabase.auth.currentUserOrNull()?.id
                ?: return Result.failure(IllegalStateException("NOT_AUTHENTICATED"))
            val existing = getUser(uid)
            if (existing == null) {
                val birth = command.birthDate?.trim().orEmpty()
                if (birth.isEmpty()) {
                    return Result.failure(IllegalArgumentException("BIRTH_DATE_INVALID"))
                }
                supabase.postgrest.rpc(
                    function = com.comunidapp.app.domain.canonical.CanonicalBackend.RPC_PROVISION_MY_PERSON,
                    parameters = buildJsonObject {
                        put("p_username", command.username)
                        put("p_display_name", command.displayName)
                        put("p_birth_date", birth)
                        put("p_home_locality_id", localityId)
                    }
                )
            } else {
                supabase.postgrest.rpc(
                    function = com.comunidapp.app.domain.canonical.CanonicalBackend.RPC_UPDATE_MY_PERSON,
                    parameters = buildJsonObject {
                        put("p_display_name", command.displayName)
                        put("p_home_locality_id", localityId)
                    }
                )
            }
            getOwnProfile(uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateMyProfile(command: UpdateMyProfileCommand): Result<UserProfile> {
        return try {
            supabase.postgrest.rpc(
                function = com.comunidapp.app.domain.canonical.CanonicalBackend.RPC_UPDATE_MY_PERSON,
                parameters = buildJsonObject {
                    command.displayName?.let { put("p_display_name", it) }
                    command.homeLocalityId?.trim()?.takeIf { it.isNotEmpty() }?.let {
                        put("p_home_locality_id", it)
                    }
                }
            )
            val uid = supabase.auth.currentUserOrNull()?.id
                ?: return Result.failure(IllegalStateException("NOT_AUTHENTICATED"))
            getOwnProfile(uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setPersonAvatar(assetId: String): Result<Unit> {
        val id = assetId.trim()
        if (id.isBlank()) {
            return Result.failure(IllegalStateException("SET_PERSON_AVATAR: MEDIA_ASSET_NOT_FOUND"))
        }
        return try {
            callSetPersonAvatar(id)
            Result.success(Unit)
        } catch (e: Exception) {
            val signal = e.message.orEmpty().uppercase()
            val authish = "JWT" in signal || "401" in signal ||
                "NOT_AUTHENTICATED" in signal || "PGRST301" in signal
            if (authish && supabase.auth.currentUserOrNull() != null) {
                runCatching { supabase.auth.refreshCurrentSession() }
                return try {
                    callSetPersonAvatar(id)
                    Result.success(Unit)
                } catch (retry: Exception) {
                    Result.failure(
                        IllegalStateException(
                            "SET_PERSON_AVATAR: ${retry.message?.take(80).orEmpty()}",
                            retry
                        )
                    )
                }
            }
            Result.failure(
                IllegalStateException(
                    "SET_PERSON_AVATAR: ${e.message?.take(80).orEmpty()}",
                    e
                )
            )
        }
    }

    private suspend fun callSetPersonAvatar(assetId: String) {
        supabase.postgrest.rpc(
            function = com.comunidapp.app.domain.canonical.CanonicalBackend.RPC_SET_PERSON_AVATAR,
            parameters = buildJsonObject { put("p_media_asset_id", assetId) }
        )
    }

    suspend fun getPublicProfile(targetUserId: String): Result<PublicUserProfile?> {
        return try {
            val element = supabase.postgrest.rpc(
                function = com.comunidapp.app.domain.canonical.CanonicalBackend.RPC_GET_PUBLIC_PERSON,
                parameters = buildJsonObject { put("p_user_id", targetUserId) }
            ).decodeAs<JsonElement>()
            if (element is JsonNull) return Result.success(null)
            val row = json.decodeFromJsonElement(PublicProfileRpcRow.serializer(), element)
            Result.success(
                PublicUserProfile(
                    id = row.id,
                    displayName = row.displayName.orEmpty(),
                    username = row.username,
                    avatarPath = row.avatarPath,
                    bio = row.bio,
                    locationText = row.locationText,
                    city = row.city,
                    province = row.province,
                    countryCode = row.countryCode
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchPublicProfiles(query: String, limit: Int): Result<List<PublicUserProfile>> {
        val normalized = com.comunidapp.app.domain.user.PersonSearchQuery.normalize(query)
        if (normalized.length < 2 || limit <= 0) return Result.success(emptyList())
        return try {
            val authPresent = supabase.auth.currentUserOrNull() != null
            com.comunidapp.app.core.logging.AppLog.info(
                "FriendSearch",
                "SEARCH rpc=canon_search_persons auth=${if (authPresent) "YES" else "NO"} qLen=${normalized.length}"
            )
            val element = supabase.postgrest.rpc(
                function = com.comunidapp.app.domain.canonical.CanonicalBackend.RPC_SEARCH_PERSONS,
                parameters = buildJsonObject { put("p_query", normalized) }
            ).decodeAs<JsonElement>()
            val rows = com.comunidapp.app.data.remote.supabase.m08.M08RpcDecoding
                .decodeRows<SearchPersonRpcRow>(element)
                .take(limit)
            com.comunidapp.app.core.logging.AppLog.info(
                "FriendSearch",
                "SEARCH status=OK rows=${rows.size}"
            )
            Result.success(
                rows.map { row ->
                    PublicUserProfile(
                        id = row.userId,
                        displayName = row.displayName.orEmpty(),
                        username = row.username,
                        avatarPath = row.avatarAssetId
                    )
                }
            )
        } catch (e: Exception) {
            com.comunidapp.app.core.logging.AppLog.info(
                "FriendSearch",
                "SEARCH status=FAIL type=${e::class.java.simpleName}"
            )
            Result.failure(e)
        }
    }

    suspend fun getPrivacySettings(userId: String): Result<UserPrivacySettings> {
        val person = getUser(userId)
        val visibility = com.comunidapp.app.domain.user.SocialProfileVisibility.fromRaw(
            if (person?.profilePrivate == false) "PUBLIC_LIMITED" else "PRIVATE"
        )
        return Result.success(UserPrivacySettings(profileVisibility = visibility))
    }

    suspend fun updatePrivacySettings(userId: String, settings: UserPrivacySettings): Result<Unit> {
        val uid = supabase.auth.currentUserOrNull()?.id
            ?: return Result.failure(IllegalStateException("NOT_AUTHENTICATED"))
        if (uid != userId) {
            return Result.failure(IllegalStateException("FORBIDDEN"))
        }
        return try {
            val state = com.comunidapp.app.domain.user.SocialProfileVisibility
                .toCanonicalPrivacyState(settings.profileVisibility)
            supabase.from(SupabaseTables.PERSONS).update(
                PrivacyStatePatch(privacyState = state)
            ) {
                filter { eq("user_id", uid) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun <T> pollingFlow(fetch: suspend () -> T): Flow<T> = flow {
        while (coroutineContext.isActive) {
            emit(fetch())
            delay(4_000)
        }
    }
}
