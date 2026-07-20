package com.comunidapp.app.domain.pets

object PetTransferRules {

    private val ALLOWED: Map<PetTransferStatus, Set<PetTransferStatus>> = mapOf(
        PetTransferStatus.PENDING to setOf(
            PetTransferStatus.ACCEPTED,
            PetTransferStatus.REJECTED,
            PetTransferStatus.CANCELLED,
            PetTransferStatus.EXPIRED
        )
    )

    fun canTransition(from: PetTransferStatus, to: PetTransferStatus): Boolean =
        ALLOWED[from]?.contains(to) == true

    fun validateRequest(
        graph: PetResponsibilityGraph,
        transfer: PetTransfer,
        nowEpochMs: Long
    ): Result<Unit> {
        PetAggregateRules.canStartTransfer(graph.pet).getOrElse { return Result.failure(it) }
        PetResponsibilityRules.validateGraphInvariants(graph, nowEpochMs)
            .getOrElse { return Result.failure(it) }

        if (transfer.petId != graph.pet.id) return petFailure("PET_TRANSFER_PET_MISMATCH")
        if (transfer.status != PetTransferStatus.PENDING) return petFailure("PET_TRANSFER_MUST_START_PENDING")
        if (transfer.fromPrincipal != graph.pet.principal) {
            return petFailure("PET_TRANSFER_FROM_NOT_CURRENT_PRINCIPAL")
        }
        if (holderEquals(transfer.fromPrincipal, transfer.toPrincipal)) {
            return petFailure("PET_TRANSFER_SAME_PRINCIPAL")
        }
        if (transfer.expiresAtEpochMs <= transfer.requestedAtEpochMs) {
            return petFailure("PET_TRANSFER_INVALID_EXPIRY")
        }
        if (graph.pendingTransfer != null &&
            graph.pendingTransfer.status == PetTransferStatus.PENDING &&
            graph.pendingTransfer.id != transfer.id
        ) {
            return petFailure("PET_TRANSFER_ALREADY_PENDING")
        }
        return Result.success(Unit)
    }

    fun validateTransition(
        current: PetTransfer,
        target: PetTransferStatus,
        nowEpochMs: Long
    ): Result<PetTransferStatus> {
        if (!canTransition(current.status, target)) {
            return petFailure("PET_TRANSFER_INVALID_TRANSITION")
        }
        if (target == PetTransferStatus.EXPIRED && nowEpochMs < current.expiresAtEpochMs) {
            return petFailure("PET_TRANSFER_NOT_YET_EXPIRED")
        }
        if (target != PetTransferStatus.EXPIRED &&
            current.status == PetTransferStatus.PENDING &&
            nowEpochMs >= current.expiresAtEpochMs
        ) {
            return petFailure("PET_TRANSFER_ALREADY_EXPIRED_WINDOW")
        }
        return Result.success(target)
    }

    fun applyAcceptedPrincipal(
        pet: PetAggregate,
        transfer: PetTransfer
    ): Result<PetAggregate> {
        if (transfer.status != PetTransferStatus.PENDING) {
            return petFailure("PET_TRANSFER_ACCEPT_REQUIRES_PENDING")
        }
        val updatedLegacy = when (val to = transfer.toPrincipal) {
            is PetPrincipalHolder.Person -> to.userId
            is PetPrincipalHolder.Organization -> null
        }
        return Result.success(
            pet.copy(
                principal = transfer.toPrincipal,
                legacyOwnerUserId = updatedLegacy,
                updatedAtEpochMs = transfer.resolvedAtEpochMs ?: pet.updatedAtEpochMs
            )
        )
    }

    fun isExpired(transfer: PetTransfer, nowEpochMs: Long): Boolean =
        transfer.status == PetTransferStatus.EXPIRED ||
            (transfer.status == PetTransferStatus.PENDING && nowEpochMs >= transfer.expiresAtEpochMs)

    /** El destinatario persona puede aceptar sin ser PRINCIPAL aún. */
    fun canActorAccept(transfer: PetTransfer, actorUserId: String): Boolean {
        val to = transfer.toPrincipal as? PetPrincipalHolder.Person ?: return false
        return to.userId == actorUserId && transfer.status == PetTransferStatus.PENDING
    }

    private fun holderEquals(a: PetPrincipalHolder, b: PetPrincipalHolder): Boolean =
        PetResponsibilityRules.holderKey(a) == PetResponsibilityRules.holderKey(b)
}

object PetAuthorizationRules {

    fun isEffectivelyActive(auth: PetAuthorization, nowEpochMs: Long): Boolean {
        if (auth.status != PetLinkStatus.ACTIVE) return false
        if (nowEpochMs < auth.validFromEpochMs) return false
        val until = auth.validToEpochMs ?: return true
        return nowEpochMs < until
    }

    fun validateCreate(
        graph: PetResponsibilityGraph,
        auth: PetAuthorization,
        nowEpochMs: Long
    ): Result<Unit> {
        PetAggregateRules.canGrantAuthorization(graph.pet).getOrElse { return Result.failure(it) }
        if (auth.petId != graph.pet.id) return petFailure("PET_AUTH_PET_MISMATCH")
        if (auth.capabilities.isEmpty()) return petFailure("PET_AUTH_CAPABILITIES_EMPTY")
        if (auth.validToEpochMs != null && auth.validToEpochMs <= auth.validFromEpochMs) {
            return petFailure("PET_AUTH_INVALID_WINDOW")
        }
        val duplicate = graph.authorizations.any {
            isEffectivelyActive(it, nowEpochMs) &&
                it.granteeUserId == auth.granteeUserId &&
                it.capabilities == auth.capabilities
        }
        if (duplicate) return petFailure("PET_AUTH_DUPLICATE_ACTIVE")
        return Result.success(Unit)
    }
}
