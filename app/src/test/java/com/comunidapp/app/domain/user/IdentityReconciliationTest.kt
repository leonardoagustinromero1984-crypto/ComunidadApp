package com.comunidapp.app.domain.user

import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.User
import com.comunidapp.app.domain.LeoverModule
import com.comunidapp.app.domain.context.ActiveContextSelection
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.domain.context.OperationalContextKind
import com.comunidapp.app.domain.context.resolveActiveContext
import com.comunidapp.app.domain.resolveActiveModules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityReconciliationTest {

    @Test
    fun old_shelter_jwt_returns_person() {
        assertEquals(AccountType.PERSON, SessionIdentity.fromLegacyJwtClaim("SHELTER"))
    }

    @Test
    fun old_vet_jwt_returns_person() {
        assertEquals(AccountType.PERSON, SessionIdentity.fromLegacyJwtClaim("VET"))
    }

    @Test
    fun signup_returns_person() {
        assertEquals(AccountType.PERSON, SessionIdentity.signupAccountType())
    }

    @Test
    fun person_plus_organization_membership_stays_person() {
        assertEquals(
            AccountType.PERSON,
            SessionIdentity.afterCapabilities(
                storedAccountType = AccountType.SHELTER,
                hasOrganizationMembership = true
            )
        )
    }

    @Test
    fun person_plus_foster_profile_stays_person() {
        assertEquals(
            AccountType.PERSON,
            SessionIdentity.afterCapabilities(
                storedAccountType = AccountType.FOSTER_HOME,
                hasFosterProfile = true
            )
        )
    }

    @Test
    fun person_plus_provider_stays_person() {
        assertEquals(
            AccountType.PERSON,
            SessionIdentity.afterCapabilities(
                storedAccountType = AccountType.VET,
                hasProviderProfile = true
            )
        )
    }

    @Test
    fun leftover_shelter_account_type_does_not_expand_modules() {
        val user = User(
            id = "u1",
            name = "Test",
            email = "t@example.com",
            accountType = AccountType.SHELTER
        )
        val modules = resolveActiveModules(user.accountType, user.activeModules)
        assertTrue(LeoverModule.PET_PROFILE in modules)
        assertFalse(LeoverModule.SHELTERS in modules)
    }

    @Test
    fun contexts_come_from_real_entities_not_account_type() {
        val available = listOf(
            OperationalContext.Personal,
            OperationalContext.Organization("org-1", "Refugio QA", "SHELTER"),
            OperationalContext.Foster("fh-1", "Hogar QA")
        )
        val user = User(
            id = "u1",
            name = "Test",
            email = "t@example.com",
            accountType = AccountType.SHELTER
        )
        assertEquals(AccountType.PERSON, SessionIdentity.afterCapabilities(user.accountType, true, true))
        assertEquals(
            OperationalContext.Personal,
            resolveActiveContext(available, ActiveContextSelection(OperationalContextKind.SHOP, "nope"))
        )
        assertTrue(available.any { it is OperationalContext.Organization && it.entityId == "org-1" })
        assertFalse(available.any { it.kind.name == user.accountType.name })
    }
}
