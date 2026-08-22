package com.comunidapp.app.domain.user

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSessionGateTest {

    @Test
    fun notStarted_requiresSetup() {
        val gate = ProfileSessionGate.evaluate(
            ProfileSetupStatus.NOT_STARTED,
            AccountStatus.ACTIVE,
            username = null
        )
        assertEquals(ProfileGate.ProfileSetupRequired, gate)
    }

    @Test
    fun completedActive_ready() {
        val gate = ProfileSessionGate.evaluate(
            ProfileSetupStatus.COMPLETED,
            AccountStatus.ACTIVE,
            username = Username.ofNormalized("maria.demo")
        )
        assertEquals(ProfileGate.ProfileReady, gate)
    }

    @Test
    fun completedRestricted_restricted() {
        val gate = ProfileSessionGate.evaluate(
            ProfileSetupStatus.COMPLETED,
            AccountStatus.RESTRICTED,
            username = Username.ofNormalized("maria.demo")
        )
        assertEquals(ProfileGate.AccountRestricted, gate)
    }

    @Test
    fun suspended_blocks() {
        val gate = ProfileSessionGate.evaluate(
            ProfileSetupStatus.COMPLETED,
            AccountStatus.SUSPENDED,
            username = Username.ofNormalized("maria.demo")
        )
        assertEquals(ProfileGate.AccountSuspended, gate)
    }

    @Test
    fun banned_blocks() {
        val gate = ProfileSessionGate.evaluate(
            ProfileSetupStatus.COMPLETED,
            AccountStatus.BANNED,
            username = Username.ofNormalized("x")
        )
        assertEquals(ProfileGate.AccountBanned, gate)
    }

    @Test
    fun publicProfile_hasNoEmail() {
        val user = com.comunidapp.app.data.mock.MockData.currentUser
        val public = UserProfileMapper.toPublicUserProfile(user)
        assertTrue(public.displayName.isNotBlank())
        // PublicUserProfile type has no email field — compile-time guarantee.
        assertEquals("maria.demo", public.username)
    }

    @Test
    fun avatarPath_ownershipShape() {
        val uid = "abc-123"
        val path = com.comunidapp.app.data.remote.storage.StoragePaths.userAvatar(uid)
        assertTrue(path.startsWith("users/$uid/avatar/"))
        assertTrue(!path.contains(".."))
    }

    @Test
    fun canonicalPersonWithoutLocality_requiresOnboarding() {
        val user = com.comunidapp.app.data.model.User(
            id = "u1",
            name = "Leo",
            email = "leo@email.com",
            username = "leonardo",
            displayName = "Leo",
            birthDate = "1990-01-15",
            homeLocalityId = null,
            onboardingStatus = "COMPLETED"
        )
        assertEquals(ProfileGate.ProfileSetupRequired, ProfileSessionGate.evaluate(user))
        assertEquals(ProfileSetupStatus.IN_PROGRESS, OnboardingCompleteness.statusFor(user))
    }

    @Test
    fun canonicalPersonWithLocality_skipsOnboardingOnSecondLogin() {
        val user = com.comunidapp.app.data.model.User(
            id = "u1",
            name = "Leo",
            email = "leo@email.com",
            username = "leonardo",
            displayName = "Leo",
            birthDate = "1990-01-15",
            homeLocalityId = "loc-ar-loc-san-vicente",
            onboardingStatus = "IN_PROGRESS"
        )
        assertEquals(ProfileGate.ProfileReady, ProfileSessionGate.evaluate(user))
        assertTrue(OnboardingCompleteness.isComplete(user))
    }

    @Test
    fun mockCompletedProfileWithoutCanonicalBirthDate_staysReady() {
        val gate = ProfileSessionGate.evaluate(com.comunidapp.app.data.mock.MockData.currentUser)
        assertEquals(ProfileGate.ProfileReady, gate)
    }
}
