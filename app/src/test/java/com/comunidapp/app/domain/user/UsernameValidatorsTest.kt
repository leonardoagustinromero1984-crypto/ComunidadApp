package com.comunidapp.app.domain.user

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameValidatorsTest {

    @Test
    fun valid_username_normalizes_case() {
        val result = UsernameValidators.validate("Maria.Lopez_1")
        assertTrue(result.isSuccess)
        assertEquals("maria.lopez_1", result.getOrNull()!!.value)
    }

    @Test
    fun rejects_too_short() {
        val result = UsernameValidators.validate("ab")
        assertTrue(result.isFailure)
        assertEquals(
            UsernameErrorCode.TOO_SHORT.name,
            (result.exceptionOrNull() as UsernameValidationException).error.code
        )
    }

    @Test
    fun rejects_reserved() {
        val result = UsernameValidators.validate("Admin")
        assertTrue(result.isFailure)
        assertEquals(
            UsernameErrorCode.RESERVED.name,
            (result.exceptionOrNull() as UsernameValidationException).error.code
        )
    }

    @Test
    fun rejects_spaces_and_trailing_dot() {
        assertTrue(UsernameValidators.validate("user name").isFailure)
        assertTrue(UsernameValidators.validate("user.").isFailure)
        assertTrue(UsernameValidators.validate("user..name").isFailure)
        assertTrue(UsernameValidators.validate("_nouser").isFailure)
    }

    @Test
    fun setup_complete_requires_username_and_status() {
        val u = UsernameValidators.validate("okuser").getOrThrow()
        assertTrue(UsernameValidators.isSetupComplete(u, ProfileSetupStatus.COMPLETED))
        assertFalse(UsernameValidators.isSetupComplete(null, ProfileSetupStatus.COMPLETED))
        assertFalse(UsernameValidators.isSetupComplete(u, ProfileSetupStatus.NOT_STARTED))
    }

    @Test
    fun account_status_access_rules() {
        assertTrue(AccountStatusRules.canAccessMain(AccountStatus.ACTIVE))
        assertTrue(AccountStatusRules.canAccessMain(AccountStatus.RESTRICTED))
        assertFalse(AccountStatusRules.canAccessMain(AccountStatus.SUSPENDED))
        assertFalse(AccountStatusRules.canAccessMain(AccountStatus.BANNED))
        assertTrue(AccountStatusRules.canMutateContent(AccountStatus.ACTIVE))
        assertFalse(AccountStatusRules.canMutateContent(AccountStatus.RESTRICTED))
    }
}
