package com.comunidapp.app.data.repository

import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.domain.user.CompleteOnboardingCommand
import com.comunidapp.app.domain.user.ProfileVisibility
import com.comunidapp.app.domain.user.UpdateMyProfileCommand
import com.comunidapp.app.domain.user.UserPrivacySettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MockUserRepositoryProfileTest {

    private lateinit var repo: MockUserRepository

    @Before
    fun setUp() {
        repo = MockUserRepository()
        repo.resetProfileExtrasForTests()
    }

    @Test
    fun displayName_mapsFromName() = runTest {
        val profile = repo.getOwnProfile(MockData.currentUser.id).getOrThrow()
        assertEquals("María González", profile.displayName)
        assertNotNull(profile.username)
    }

    @Test
    fun username_reserved_unavailable() = runTest {
        assertFalse(repo.isUsernameAvailable("admin").getOrThrow())
    }

    @Test
    fun updateMyProfile_doesNotChangeUsernameOrAccountType() = runTest {
        val before = repo.getUser(MockData.currentUser.id)!!
        repo.updateMyProfile(
            before.id,
            UpdateMyProfileCommand(displayName = "Nuevo Nombre", bio = "hola")
        ).getOrThrow()
        val after = repo.getUser(before.id)!!
        assertEquals(before.username, after.username)
        assertEquals(before.accountType, after.accountType)
        assertEquals(before.accountStatus, after.accountStatus)
        assertEquals("Nuevo Nombre", after.displayName)
    }

    @Test
    fun publicProfile_hidesPrivate() = runTest {
        val target = MockData.users.first { it.id == "user_3" }
        repo.updatePrivacySettings(
            target.id,
            UserPrivacySettings(profileVisibility = ProfileVisibility.PRIVATE)
        )
        // ensure completed for visibility path
        repo.completeOnboarding(
            target.id,
            CompleteOnboardingCommand(
                displayName = target.name,
                username = "carlos.ruiz",
                privacy = UserPrivacySettings(ProfileVisibility.PRIVATE)
            )
        )
        val public = repo.getPublicProfile(MockData.currentUser.id, target.id).getOrThrow()
        assertNull(public)
    }

    @Test
    fun search_doesNotMatchEmail() = runTest {
        val results = repo.searchPublicProfiles(
            MockData.currentUser.id,
            "maria@email.com"
        ).getOrThrow()
        assertTrue(results.none { it.displayName.contains("@") })
    }
}
