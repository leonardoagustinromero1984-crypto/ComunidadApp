package com.comunidapp.shared.domain.adoption

/**
 * Dominio canónico de estado de publicación de adopción (KMP).
 * `AdoptionPost` Android permanece en app (WIP M09); estas reglas son compartidas.
 */
enum class AdoptionListingStatus {
    DRAFT,
    PUBLISHED,
    ADOPTED,
    CLOSED
}

object AdoptionStatusRules {
    fun isPubliclyVisible(status: AdoptionListingStatus): Boolean =
        status == AdoptionListingStatus.PUBLISHED ||
            status == AdoptionListingStatus.ADOPTED ||
            status == AdoptionListingStatus.CLOSED

    fun canPublish(from: AdoptionListingStatus): Boolean =
        from == AdoptionListingStatus.DRAFT

    fun canMarkAdopted(from: AdoptionListingStatus): Boolean =
        from == AdoptionListingStatus.PUBLISHED

    fun canClose(from: AdoptionListingStatus): Boolean =
        from == AdoptionListingStatus.PUBLISHED || from == AdoptionListingStatus.ADOPTED

    fun transition(from: AdoptionListingStatus, to: AdoptionListingStatus): Result<AdoptionListingStatus> {
        if (from == to) return Result.success(to)
        val ok = when (to) {
            AdoptionListingStatus.DRAFT -> false
            AdoptionListingStatus.PUBLISHED -> canPublish(from)
            AdoptionListingStatus.ADOPTED -> canMarkAdopted(from)
            AdoptionListingStatus.CLOSED -> canClose(from)
        }
        return if (ok) Result.success(to)
        else Result.failure(IllegalArgumentException("ADOPTION_INVALID_TRANSITION"))
    }
}
