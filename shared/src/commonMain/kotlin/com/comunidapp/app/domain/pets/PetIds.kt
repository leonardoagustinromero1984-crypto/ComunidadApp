package com.comunidapp.app.domain.pets

/**
 * Identificadores tipados M08. Users siguen como [String] (convención M02/M03).
 * [com.comunidapp.app.domain.organization.OrganizationId] se reutiliza sin duplicar.
 *
 * Ver nota en OrganizationId: data class por portabilidad Kotlin 2.3 Native/JVM.
 */
data class PetId(val value: String) {
    init {
        require(value.isNotBlank()) { "PET_ID_BLANK" }
    }
}

data class PetResponsibilityId(val value: String) {
    init {
        require(value.isNotBlank()) { "PET_RESPONSIBILITY_ID_BLANK" }
    }
}

data class PetAuthorizationId(val value: String) {
    init {
        require(value.isNotBlank()) { "PET_AUTHORIZATION_ID_BLANK" }
    }
}

data class PetTransferId(val value: String) {
    init {
        require(value.isNotBlank()) { "PET_TRANSFER_ID_BLANK" }
    }
}

/** Visible para implementaciones Android (otro módulo) que reutilizan el código de error. */
fun petFailure(code: String): Result<Nothing> =
    Result.failure(IllegalArgumentException(code))
