package com.comunidapp.app.domain.user

import com.comunidapp.app.data.model.AccountType

/**
 * Identidad de sesión. Siempre PERSON.
 * JWT, account_type persistido y capacidades de dominio no cambian la identidad humana.
 */
object SessionIdentity {

    fun fromLegacyJwtClaim(accountTypeClaim: String?): AccountType {
        @Suppress("UNUSED_PARAMETER")
        val ignored = accountTypeClaim
        return AccountType.PERSON
    }

    fun signupAccountType(): AccountType = AccountType.PERSON

    fun afterCapabilities(
        storedAccountType: AccountType = AccountType.PERSON,
        hasOrganizationMembership: Boolean = false,
        hasFosterProfile: Boolean = false,
        hasProviderProfile: Boolean = false
    ): AccountType {
        @Suppress("UNUSED_PARAMETER")
        val ignored = Triple(
            storedAccountType,
            hasOrganizationMembership || hasFosterProfile || hasProviderProfile,
            Unit
        )
        return AccountType.PERSON
    }
}
