package com.comunidapp.app.domain.pets

import com.comunidapp.app.domain.organization.OrganizationId

/** Estado vital/administrativo de la mascota (no strings libres). */
enum class PetLifecycleStatus {
    ACTIVE,
    DECEASED,
    ARCHIVED
}

/**
 * Principal canónico: persona u organización.
 * Exactamente un principal activo por mascota (invariante de agregado).
 */
sealed interface PetPrincipalHolder {
    data class Person(val userId: String) : PetPrincipalHolder {
        init {
            require(userId.isNotBlank()) { "PET_PRINCIPAL_USER_BLANK" }
        }
    }

    data class Organization(val organizationId: OrganizationId) : PetPrincipalHolder
}

enum class PetResponsibilityRole {
    PRINCIPAL,
    CO_RESPONSIBLE,
    TEMPORARY_CUSTODIAN
}

enum class PetLinkStatus {
    ACTIVE,
    PENDING_ACCEPTANCE,
    REVOKED,
    EXPIRED,
    SUPERSEDED
}

enum class PetTransferStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    EXPIRED
}

/**
 * Capacidades semánticas M08 (catálogo tipado).
 * Misma forma dotted que [com.comunidapp.app.domain.authorization.PermissionCode];
 * no equivalen a AccountType / active_modules.
 */
enum class PetCapability(val code: String) {
    READ("pet.read"),
    CREATE("pet.create"),
    UPDATE("pet.update"),
    MANAGE_RESPONSIBILITIES("pet.manage_responsibilities"),
    MANAGE_AUTHORIZATIONS("pet.manage_authorizations"),
    INITIATE_TRANSFER("pet.initiate_transfer"),
    ACCEPT_TRANSFER("pet.accept_transfer"),
    CANCEL_TRANSFER("pet.cancel_transfer"),
    MARK_DECEASED("pet.mark_deceased"),
    ARCHIVE("pet.archive"),
    RESTORE("pet.restore"),
    MANAGE_MEDIA("pet.manage_media"),
    VIEW_HISTORY("pet.view_history"),
    MANAGE_HEALTH("pet.manage_health");

    companion object {
        fun fromCode(raw: String): PetCapability? =
            entries.firstOrNull { it.code.equals(raw.trim(), ignoreCase = true) }

        fun allCodes(): Set<String> = entries.map { it.code }.toSet()
    }
}
