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
}
