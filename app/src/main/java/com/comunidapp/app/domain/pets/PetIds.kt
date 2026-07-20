package com.comunidapp.app.domain.pets

/**
 * Identificadores tipados M08. Users siguen como [String] (convención M02/M03).
 * [com.comunidapp.app.domain.organization.OrganizationId] se reutiliza sin duplicar.
 */
@JvmInline
value class PetId(val value: String) {
    init {
        require(value.isNotBlank()) { "PET_ID_BLANK" }
    }
}

@JvmInline
value class PetResponsibilityId(val value: String) {
    init {
        require(value.isNotBlank()) { "PET_RESPONSIBILITY_ID_BLANK" }
    }
}

@JvmInline
value class PetAuthorizationId(val value: String) {
    init {
        require(value.isNotBlank()) { "PET_AUTHORIZATION_ID_BLANK" }
    }
}

@JvmInline
value class PetTransferId(val value: String) {
    init {
        require(value.isNotBlank()) { "PET_TRANSFER_ID_BLANK" }
    }
}

internal fun petFailure(code: String): Result<Nothing> =
    Result.failure(IllegalArgumentException(code))
