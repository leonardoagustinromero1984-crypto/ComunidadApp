package com.comunidapp.app.data.remote.supabase

import com.comunidapp.app.data.model.User
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

class UserSupabaseDataSource {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getUser(userId: String): User? {
        return try {
            supabase.from(SupabaseTables.USERS)
                .select {
                    filter { eq("id", userId) }
                }
                .decodeSingleOrNull<UserRow>()
                ?.let(::parseUser)
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

    fun observeUser(userId: String): Flow<User?> = pollingFlow {
        getUser(userId)
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
        return try {
            val available = supabase.postgrest.rpc(
                function = "is_username_available",
                parameters = buildJsonObject { put("p_username", username) }
            ).decodeAs<Boolean>()
            Result.success(available)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun completeOnboarding(command: CompleteOnboardingCommand): Result<UserProfile> {
        return try {
            supabase.postgrest.rpc(
                function = "complete_profile_onboarding",
                parameters = buildJsonObject {
                    put("p_display_name", command.displayName)
                    put("p_username", command.username)
                    command.city?.let { put("p_city", it) }
                    command.province?.let { put("p_province", it) }
                    command.countryCode?.let { put("p_country_code", it) }
                    command.bio?.let { put("p_bio", it) }
                    command.avatarPath?.let { put("p_avatar_path", it) }
                    put("p_profile_visibility", command.privacy.profileVisibility.name)
                    put("p_show_location", command.privacy.showLocation)
                    put("p_show_phone", command.privacy.showPhone)
                    put("p_allow_friend_requests", command.privacy.allowFriendRequests)
                    command.locale?.let { put("p_locale", it) }
                    command.timezone?.let { put("p_timezone", it) }
                }
            )
            val uid = supabase.auth.currentUserOrNull()?.id
                ?: return Result.failure(IllegalStateException("NOT_AUTHENTICATED"))
            runCatching {
                supabase.postgrest.rpc(function = "ensure_my_default_user_role")
            }
            getOwnProfile(uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateMyProfile(command: UpdateMyProfileCommand): Result<UserProfile> {
        return try {
            supabase.postgrest.rpc(
                function = "update_my_profile",
                parameters = buildJsonObject {
                    command.displayName?.let { put("p_display_name", it) }
                    command.bio?.let { put("p_bio", it) }
                    command.city?.let { put("p_city", it) }
                    command.province?.let { put("p_province", it) }
                    command.countryCode?.let { put("p_country_code", it) }
                    command.locale?.let { put("p_locale", it) }
                    command.timezone?.let { put("p_timezone", it) }
                    command.avatarPath?.let { put("p_avatar_path", it) }
                }
            )
            val uid = supabase.auth.currentUserOrNull()?.id
                ?: return Result.failure(IllegalStateException("NOT_AUTHENTICATED"))
            getOwnProfile(uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPublicProfile(targetUserId: String): Result<PublicUserProfile?> {
        return try {
            val element = supabase.postgrest.rpc(
                function = "get_public_user_profile",
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
        return try {
            val element = supabase.postgrest.rpc(
                function = "search_public_user_profiles",
                parameters = buildJsonObject {
                    put("p_query", query)
                    put("p_limit", limit)
                    put("p_offset", 0)
                }
            ).decodeAs<JsonElement>()
            val array = element as? JsonArray ?: return Result.success(emptyList())
            val list = array.mapNotNull { item ->
                runCatching {
                    json.decodeFromJsonElement(PublicProfileRpcRow.serializer(), item)
                }.getOrNull()?.let { row ->
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
                }
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPrivacySettings(userId: String): Result<UserPrivacySettings> {
        return try {
            val row = supabase.from("user_privacy_settings")
                .select {
                    filter { eq("user_id", userId) }
                }
                .decodeSingleOrNull<PrivacySettingsRow>()
            Result.success(
                row?.let {
                    UserPrivacySettings(
                        profileVisibility = UserProfileMapper.parseVisibility(it.profileVisibility),
                        showLocation = it.showLocation,
                        showPhone = it.showPhone,
                        allowFriendRequests = it.allowFriendRequests
                    )
                } ?: UserPrivacySettings()
            )
        } catch (_: Exception) {
            Result.success(UserPrivacySettings())
        }
    }

    suspend fun updatePrivacySettings(userId: String, settings: UserPrivacySettings): Result<Unit> {
        return try {
            supabase.from("user_privacy_settings").update(
                PrivacySettingsUpdateRow(
                    profileVisibility = settings.profileVisibility.name,
                    showLocation = settings.showLocation,
                    showPhone = settings.showPhone,
                    allowFriendRequests = settings.allowFriendRequests,
                    updatedAt = nowIso()
                )
            ) {
                filter { eq("user_id", userId) }
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
