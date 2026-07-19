package com.comunidapp.app.data.repository

import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.mock.MockUserStore
import com.comunidapp.app.data.model.User
import com.comunidapp.app.domain.user.AccountStatus
import com.comunidapp.app.domain.user.CompleteOnboardingCommand
import com.comunidapp.app.domain.user.ProfileSetupStatus
import com.comunidapp.app.domain.user.ProfileVisibility
import com.comunidapp.app.domain.user.PublicUserProfile
import com.comunidapp.app.domain.user.UpdateMyProfileCommand
import com.comunidapp.app.domain.user.UserPrivacySettings
import com.comunidapp.app.domain.user.UserProfile
import com.comunidapp.app.domain.user.UserProfileMapper
import com.comunidapp.app.domain.user.UsernameValidators
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

interface UserRepository {
    suspend fun getUser(userId: String): User?
    suspend fun createUser(user: User): Result<Unit>
    /** Legacy; prefer [updateMyProfile]. Mock enforces allowlist. */
    suspend fun updateUser(user: User): Result<Unit>
    suspend fun searchUsers(query: String, excludeUserId: String): List<User>
    fun observeUser(userId: String): Flow<User?>
    fun observeUsers(): Flow<List<User>>

    suspend fun getOwnProfile(userId: String): Result<UserProfile> =
        Result.failure(UnsupportedOperationException("getOwnProfile"))

    fun observeOwnProfile(userId: String): Flow<UserProfile?> =
        observeUser(userId).map { user -> user?.let { UserProfileMapper.toUserProfile(it) } }

    suspend fun isUsernameAvailable(username: String, excludingUserId: String? = null): Result<Boolean> =
        Result.success(false)

    suspend fun completeOnboarding(
        userId: String,
        command: CompleteOnboardingCommand
    ): Result<UserProfile> = Result.failure(UnsupportedOperationException("completeOnboarding"))

    suspend fun updateMyProfile(
        userId: String,
        command: UpdateMyProfileCommand
    ): Result<UserProfile> = Result.failure(UnsupportedOperationException("updateMyProfile"))

    suspend fun getPublicProfile(
        viewerId: String,
        targetUserId: String
    ): Result<PublicUserProfile?> = Result.success(null)

    suspend fun searchPublicProfiles(
        viewerId: String,
        query: String,
        limit: Int = 20
    ): Result<List<PublicUserProfile>> = Result.success(emptyList())

    suspend fun getPrivacySettings(userId: String): Result<UserPrivacySettings> =
        Result.success(UserPrivacySettings())

    suspend fun updatePrivacySettings(userId: String, settings: UserPrivacySettings): Result<Unit> =
        Result.success(Unit)
}

class MockUserRepository : UserRepository {

    private val privacyByUser = MutableStateFlow<Map<String, UserPrivacySettings>>(emptyMap())
    private val reserved = UsernameValidators.reservedWords

    init {
        seedDemoProfiles()
    }

    private fun seedDemoProfiles() {
        MockData.users.forEach { u ->
            if (MockUserStore.get(u.id) == null) MockUserStore.upsert(u)
        }
        val demo = MockData.currentUser
        MockUserStore.upsert(
            demo.copy(
                username = "maria.demo",
                displayName = demo.name,
                onboardingStatus = ProfileSetupStatus.COMPLETED.name,
                accountStatus = AccountStatus.ACTIVE.name
            )
        )
        privacyByUser.update {
            it + (demo.id to UserPrivacySettings(ProfileVisibility.PUBLIC))
        }
    }

    fun resetProfileExtrasForTests() {
        privacyByUser.value = emptyMap()
        seedDemoProfiles()
    }

    override suspend fun getUser(userId: String): User? =
        MockUserStore.get(userId) ?: MockData.users.find { it.id == userId }

    override suspend fun createUser(user: User): Result<Unit> {
        MockUserStore.upsert(
            user.copy(
                displayName = user.displayName ?: user.name,
                onboardingStatus = user.onboardingStatus.ifBlank { "NOT_STARTED" }
            )
        )
        privacyByUser.update {
            it + (user.id to UserPrivacySettings(ProfileVisibility.PRIVATE))
        }
        return Result.success(Unit)
    }

    override suspend fun updateUser(user: User): Result<Unit> {
        val existing = getUser(user.id) ?: return Result.failure(IllegalStateException("not found"))
        // Allowlist: no accountType/modules/reputation/accountStatus changes by client path.
        val safe = existing.copy(
            name = user.name,
            displayName = user.displayName ?: user.name,
            bio = user.bio,
            locationText = user.locationText,
            phone = user.phone,
            phonePublic = user.phonePublic,
            profilePrivate = user.profilePrivate,
            profileImageUrl = user.profileImageUrl,
            avatarPath = user.avatarPath,
            city = user.city,
            province = user.province,
            countryCode = user.countryCode,
            locale = user.locale,
            timezone = user.timezone
            // username / accountType / activeModules / accountStatus preserved
        )
        MockUserStore.upsert(safe)
        return Result.success(Unit)
    }

    override suspend fun searchUsers(query: String, excludeUserId: String): List<User> =
        MockUserStore.search(query, excludeUserId)
            .filter { it.onboardingStatus == ProfileSetupStatus.COMPLETED.name }

    override fun observeUser(userId: String): Flow<User?> = MockUserStore.observe(userId)

    override fun observeUsers(): Flow<List<User>> = MockUserStore.observeAll()

    override suspend fun getOwnProfile(userId: String): Result<UserProfile> {
        val user = getUser(userId) ?: return Result.failure(IllegalStateException("not found"))
        val privacy = privacyByUser.value[userId] ?: UserPrivacySettings()
        return Result.success(UserProfileMapper.toUserProfile(user, privacy = privacy))
    }

    override fun observeOwnProfile(userId: String): Flow<UserProfile?> =
        observeUser(userId).map { user ->
            user?.let {
                UserProfileMapper.toUserProfile(
                    it,
                    privacy = privacyByUser.value[userId] ?: UserPrivacySettings()
                )
            }
        }

    override suspend fun isUsernameAvailable(
        username: String,
        excludingUserId: String?
    ): Result<Boolean> {
        UsernameValidators.validate(username).getOrElse {
            return Result.success(false)
        }
        val normalized = UsernameValidators.normalize(username)
        if (normalized in reserved) return Result.success(false)
        val occupied = MockUserStore.allUsers().any { u ->
            u.username.equals(normalized, ignoreCase = true) && u.id != excludingUserId
        }
        return Result.success(!occupied)
    }

    override suspend fun completeOnboarding(
        userId: String,
        command: CompleteOnboardingCommand
    ): Result<UserProfile> {
        val existing = getUser(userId) ?: return Result.failure(IllegalStateException("not found"))
        UsernameValidators.validate(command.username).getOrElse {
            return Result.failure(it)
        }
        val available = isUsernameAvailable(command.username, userId).getOrDefault(false)
        if (!available) return Result.failure(IllegalStateException("USERNAME_UNAVAILABLE"))
        val display = command.displayName.trim()
        if (display.length !in 2..80) {
            return Result.failure(IllegalArgumentException("DISPLAY_NAME_INVALID"))
        }
        if (command.avatarPath != null &&
            !command.avatarPath.startsWith("users/$userId/avatar/")
        ) {
            return Result.failure(IllegalArgumentException("AVATAR_PATH_INVALID"))
        }
        val updated = existing.copy(
            name = display,
            displayName = display,
            username = UsernameValidators.normalize(command.username),
            city = command.city?.trim()?.ifBlank { null },
            province = command.province?.trim()?.ifBlank { null },
            countryCode = command.countryCode?.trim()?.uppercase()?.ifBlank { null },
            bio = command.bio?.trim()?.ifBlank { null },
            avatarPath = command.avatarPath ?: existing.avatarPath,
            locale = command.locale,
            timezone = command.timezone,
            locationText = listOfNotNull(command.city, command.province)
                .joinToString(", ").ifBlank { existing.locationText },
            profilePrivate = command.privacy.profilePrivate,
            phonePublic = command.privacy.showPhone,
            onboardingStatus = ProfileSetupStatus.COMPLETED.name,
            accountStatus = AccountStatus.ACTIVE.name
        )
        MockUserStore.upsert(updated)
        privacyByUser.update { it + (userId to command.privacy) }
        return getOwnProfile(userId)
    }

    override suspend fun updateMyProfile(
        userId: String,
        command: UpdateMyProfileCommand
    ): Result<UserProfile> {
        val existing = getUser(userId) ?: return Result.failure(IllegalStateException("not found"))
        if (command.avatarPath != null &&
            !command.avatarPath.startsWith("users/$userId/avatar/")
        ) {
            return Result.failure(IllegalArgumentException("AVATAR_PATH_INVALID"))
        }
        val display = command.displayName?.trim()
        if (display != null && display.length !in 2..80) {
            return Result.failure(IllegalArgumentException("DISPLAY_NAME_INVALID"))
        }
        val updated = existing.copy(
            name = display ?: existing.name,
            displayName = display ?: existing.displayName ?: existing.name,
            bio = command.bio?.trim()?.ifBlank { null } ?: existing.bio.takeIf { command.bio == null },
            city = command.city?.trim()?.ifBlank { null } ?: existing.city.takeIf { command.city == null },
            province = command.province?.trim()?.ifBlank { null }
                ?: existing.province.takeIf { command.province == null },
            countryCode = command.countryCode?.trim()?.uppercase()?.ifBlank { null }
                ?: existing.countryCode.takeIf { command.countryCode == null },
            locale = command.locale ?: existing.locale,
            timezone = command.timezone ?: existing.timezone,
            avatarPath = command.avatarPath ?: existing.avatarPath,
            locationText = listOfNotNull(
                command.city?.trim()?.ifBlank { null } ?: existing.city,
                command.province?.trim()?.ifBlank { null } ?: existing.province
            ).joinToString(", ").ifBlank { existing.locationText }
        )
        MockUserStore.upsert(updated)
        return getOwnProfile(userId)
    }

    override suspend fun getPublicProfile(
        viewerId: String,
        targetUserId: String
    ): Result<PublicUserProfile?> {
        val target = getUser(targetUserId) ?: return Result.success(null)
        if (viewerId != targetUserId) {
            if (target.accountStatus in setOf("SUSPENDED", "BANNED")) return Result.success(null)
            if (target.onboardingStatus != ProfileSetupStatus.COMPLETED.name) {
                return Result.success(null)
            }
        }
        val privacy = privacyByUser.value[targetUserId] ?: UserPrivacySettings(
            if (target.profilePrivate) ProfileVisibility.PRIVATE else ProfileVisibility.PUBLIC
        )
        val isSelf = viewerId == targetUserId
        val canView = when {
            isSelf -> true
            privacy.profileVisibility == ProfileVisibility.PUBLIC -> true
            privacy.profileVisibility == ProfileVisibility.PRIVATE -> false
            else -> false // FRIENDS sin grafo mock completo → denegar
        }
        if (!canView) return Result.success(null)
        val profile = UserProfileMapper.toUserProfile(target, privacy = privacy)
        return Result.success(UserProfileMapper.toPublicUserProfile(profile))
    }

    override suspend fun searchPublicProfiles(
        viewerId: String,
        query: String,
        limit: Int
    ): Result<List<PublicUserProfile>> {
        val q = query.trim().lowercase()
        if (q.length < 2) return Result.success(emptyList())
        val lim = limit.coerceIn(1, 50)
        val results = mutableListOf<PublicUserProfile>()
        val candidates = MockData.users.map { MockUserStore.get(it.id) ?: it } +
            listOfNotNull(MockUserStore.get(MockData.currentUser.id))
        candidates.distinctBy { it.id }
            .filter { it.id != viewerId }
            .filter { it.onboardingStatus == ProfileSetupStatus.COMPLETED.name }
            .filter { it.accountStatus == AccountStatus.ACTIVE.name }
            .forEach { user ->
                val privacy = privacyByUser.value[user.id] ?: UserPrivacySettings(
                    if (user.profilePrivate) ProfileVisibility.PRIVATE else ProfileVisibility.PUBLIC
                )
                if (privacy.profileVisibility == ProfileVisibility.PRIVATE) return@forEach
                val hay = listOfNotNull(user.username, user.displayName, user.name)
                    .joinToString(" ").lowercase()
                if (hay.contains(q)) {
                    getPublicProfile(viewerId, user.id).getOrNull()?.let { results.add(it) }
                }
            }
        return Result.success(results.take(lim))
    }

    override suspend fun getPrivacySettings(userId: String): Result<UserPrivacySettings> =
        Result.success(privacyByUser.value[userId] ?: UserPrivacySettings())

    override suspend fun updatePrivacySettings(
        userId: String,
        settings: UserPrivacySettings
    ): Result<Unit> {
        privacyByUser.update { it + (userId to settings) }
        val user = getUser(userId) ?: return Result.success(Unit)
        MockUserStore.upsert(
            user.copy(
                profilePrivate = settings.profilePrivate,
                phonePublic = settings.showPhone
            )
        )
        return Result.success(Unit)
    }
}
