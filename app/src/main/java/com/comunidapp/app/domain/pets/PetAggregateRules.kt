package com.comunidapp.app.domain.pets

object PetAggregateRules {

    fun validateNew(
        pet: PetAggregate,
        principalResponsibility: PetResponsibility
    ): Result<Unit> {
        if (pet.displayName.isBlank()) return petFailure("PET_NAME_BLANK")
        if (principalResponsibility.petId != pet.id) return petFailure("PET_RESPONSIBILITY_PET_MISMATCH")
        if (principalResponsibility.role != PetResponsibilityRole.PRINCIPAL) {
            return petFailure("PET_PRINCIPAL_ROLE_REQUIRED")
        }
        if (principalResponsibility.status != PetLinkStatus.ACTIVE) {
            return petFailure("PET_PRINCIPAL_NOT_ACTIVE")
        }
        if (principalResponsibility.holder != pet.principal) {
            return petFailure("PET_PRINCIPAL_HOLDER_MISMATCH")
        }
        when (val holder = pet.principal) {
            is PetPrincipalHolder.Person -> {
                if (pet.legacyOwnerUserId != null && pet.legacyOwnerUserId != holder.userId) {
                    return petFailure("PET_LEGACY_OWNER_MISMATCH")
                }
            }
            is PetPrincipalHolder.Organization -> {
                // Transición: owner_id proyección nullable en Etapa 3; domain permite null.
                if (pet.legacyOwnerUserId != null) {
                    return petFailure("PET_ORG_PRINCIPAL_LEGACY_OWNER_MUST_BE_NULL")
                }
            }
        }
        if (pet.status == PetLifecycleStatus.DECEASED && pet.deceasedAtEpochMs == null) {
            return petFailure("PET_DECEASED_TIMESTAMP_REQUIRED")
        }
        if (pet.status == PetLifecycleStatus.ARCHIVED && pet.archivedAtEpochMs == null) {
            return petFailure("PET_ARCHIVED_TIMESTAMP_REQUIRED")
        }
        return Result.success(Unit)
    }

    fun canStartTransfer(pet: PetAggregate): Result<Unit> = when (pet.status) {
        PetLifecycleStatus.ACTIVE -> Result.success(Unit)
        PetLifecycleStatus.DECEASED -> petFailure("PET_TRANSFER_DECEASED")
        PetLifecycleStatus.ARCHIVED -> petFailure("PET_TRANSFER_ARCHIVED")
    }

    fun canGrantAuthorization(pet: PetAggregate): Result<Unit> = when (pet.status) {
        PetLifecycleStatus.ACTIVE -> Result.success(Unit)
        PetLifecycleStatus.DECEASED -> petFailure("PET_AUTH_DECEASED")
        PetLifecycleStatus.ARCHIVED -> petFailure("PET_AUTH_ARCHIVED")
    }

    fun canMarkDeceased(from: PetLifecycleStatus): Result<Unit> = when (from) {
        PetLifecycleStatus.ACTIVE, PetLifecycleStatus.ARCHIVED -> Result.success(Unit)
        PetLifecycleStatus.DECEASED -> petFailure("PET_ALREADY_DECEASED")
    }

    fun canArchive(from: PetLifecycleStatus): Result<Unit> = when (from) {
        PetLifecycleStatus.ACTIVE -> Result.success(Unit)
        PetLifecycleStatus.ARCHIVED -> petFailure("PET_ALREADY_ARCHIVED")
        PetLifecycleStatus.DECEASED -> petFailure("PET_ARCHIVE_DECEASED")
    }

    fun canRestore(from: PetLifecycleStatus): Result<Unit> = when (from) {
        PetLifecycleStatus.ARCHIVED -> Result.success(Unit)
        else -> petFailure("PET_RESTORE_NOT_ARCHIVED")
    }
}

object PetResponsibilityRules {

    fun activePrincipals(responsibilities: List<PetResponsibility>): List<PetResponsibility> =
        responsibilities.filter {
            it.role == PetResponsibilityRole.PRINCIPAL && it.status == PetLinkStatus.ACTIVE
        }

    fun resolvePrincipal(graph: PetResponsibilityGraph): Result<PetPrincipalHolder> {
        val principals = activePrincipals(graph.responsibilities)
        return when (principals.size) {
            0 -> petFailure("PET_PRINCIPAL_MISSING")
            1 -> {
                val holder = principals.single().holder
                if (holder != graph.pet.principal) petFailure("PET_PRINCIPAL_AGGREGATE_DRIFT")
                else Result.success(holder)
            }
            else -> petFailure("PET_PRINCIPAL_DUPLICATE")
        }
    }

    fun validateGraphInvariants(graph: PetResponsibilityGraph, nowEpochMs: Long): Result<Unit> {
        PetAggregateRules.validateNew(
            graph.pet,
            graph.responsibilities.firstOrNull {
                it.role == PetResponsibilityRole.PRINCIPAL && it.status == PetLinkStatus.ACTIVE
            } ?: return petFailure("PET_PRINCIPAL_MISSING")
        ).getOrElse { return Result.failure(it) }

        resolvePrincipal(graph).getOrElse { return Result.failure(it) }

        val actives = graph.responsibilities.filter { isEffectivelyActive(it, nowEpochMs) }
        val duplicateKeys = actives
            .groupBy { Triple(it.role, holderKey(it.holder), it.petId.value) }
            .filterValues { it.size > 1 }
        if (duplicateKeys.isNotEmpty()) return petFailure("PET_RESPONSIBILITY_DUPLICATE_ACTIVE")

        val principalHolder = graph.pet.principal
        val principalAsCo = actives.any {
            it.role == PetResponsibilityRole.CO_RESPONSIBLE && it.holder == principalHolder
        }
        if (principalAsCo) return petFailure("PET_PRINCIPAL_CANNOT_BE_CO_RESPONSIBLE")

        actives.filter { it.role == PetResponsibilityRole.TEMPORARY_CUSTODIAN }.forEach { temp ->
            if (temp.validToEpochMs == null) return petFailure("PET_TEMPORARY_CUSTODIAN_REQUIRES_VALID_TO")
            if (temp.validToEpochMs <= temp.validFromEpochMs) {
                return petFailure("PET_TEMPORARY_CUSTODIAN_INVALID_WINDOW")
            }
        }
        return Result.success(Unit)
    }

    fun canAssign(
        graph: PetResponsibilityGraph,
        candidate: PetResponsibility,
        nowEpochMs: Long
    ): Result<Unit> {
        validateGraphInvariants(graph, nowEpochMs).getOrElse { return Result.failure(it) }
        if (candidate.petId != graph.pet.id) return petFailure("PET_RESPONSIBILITY_PET_MISMATCH")
        if (candidate.role == PetResponsibilityRole.PRINCIPAL) {
            return petFailure("PET_ASSIGN_PRINCIPAL_USE_TRANSFER")
        }
        if (candidate.holder == graph.pet.principal &&
            candidate.role == PetResponsibilityRole.CO_RESPONSIBLE
        ) {
            return petFailure("PET_PRINCIPAL_CANNOT_BE_CO_RESPONSIBLE")
        }
        val conflict = graph.responsibilities.any {
            isEffectivelyActive(it, nowEpochMs) &&
                it.role == candidate.role &&
                holderKey(it.holder) == holderKey(candidate.holder)
        }
        if (conflict) return petFailure("PET_RESPONSIBILITY_DUPLICATE_ACTIVE")
        if (candidate.role == PetResponsibilityRole.TEMPORARY_CUSTODIAN &&
            candidate.validToEpochMs == null
        ) {
            return petFailure("PET_TEMPORARY_CUSTODIAN_REQUIRES_VALID_TO")
        }
        return Result.success(Unit)
    }

    fun isEffectivelyActive(link: PetResponsibility, nowEpochMs: Long): Boolean {
        if (link.status != PetLinkStatus.ACTIVE) return false
        if (nowEpochMs < link.validFromEpochMs) return false
        val until = link.validToEpochMs ?: return true
        return nowEpochMs < until
    }

    fun holderKey(holder: PetPrincipalHolder): String = when (holder) {
        is PetPrincipalHolder.Person -> "person:${holder.userId}"
        is PetPrincipalHolder.Organization -> "org:${holder.organizationId.value}"
    }
}
