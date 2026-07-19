package com.comunidapp.app.domain.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOwnershipRulesTest {

    @Test
    fun dual_ownership_rejected() {
        val r = FileOwnershipRules.rejectDual(
            userId = "u1",
            organizationId = "o1",
            platform = false
        )
        assertTrue(r.isFailure)
        assertEquals("OWNER_DUAL_OR_MISSING", r.exceptionOrNull()?.message)
    }

    @Test
    fun missing_ownership_rejected() {
        val r = FileOwnershipRules.rejectDual(null, null, false)
        assertTrue(r.isFailure)
    }

    @Test
    fun user_owner_ok() {
        val r = FileOwnershipRules.rejectDual("u1", null, false)
        assertTrue(r.isSuccess)
        assertTrue(r.getOrNull() is FileAssetOwner.User)
    }

    @Test
    fun org_incompatible_with_avatar() {
        val r = FileOwnershipRules.compatibleWithPurpose(
            FileAssetOwner.Organization("o1"),
            FileAssetPurpose.USER_AVATAR
        )
        assertTrue(r.isFailure)
    }

    @Test
    fun blank_user_rejected() {
        val r = FileOwnershipRules.validate(FileAssetOwner.User(" "))
        assertTrue(r.isFailure)
    }
}
