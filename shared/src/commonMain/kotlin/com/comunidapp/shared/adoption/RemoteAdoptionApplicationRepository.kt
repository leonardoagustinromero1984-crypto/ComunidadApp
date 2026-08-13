package com.comunidapp.shared.adoption

import com.comunidapp.shared.remote.AdoptionApplicationRemoteGateway
import com.comunidapp.shared.remote.AdoptionWriteKind
import com.comunidapp.shared.remote.RemoteAdoptionApplicationRow
import com.comunidapp.shared.remote.RemoteSubmitApplicationParams
import com.comunidapp.shared.remote.classifyAdoptionWrite
import com.comunidapp.shared.remote.formatReportedAtLabel
import com.comunidapp.shared.remote.mapAdoptionThrowable
import com.comunidapp.shared.session.SessionRepository
import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.ui.ErrorSanitizer

internal class RemoteAdoptionApplicationRepository(
    private val gateway: AdoptionApplicationRemoteGateway,
    private val sessionRepository: SessionRepository
) : AdoptionApplicationRepository {
    override val dataMode: AdoptionDataMode = AdoptionDataMode.REAL_REMOTE

    override suspend fun submit(draft: AdoptionApplicationDraft): AdoptionApplicationResult {
        AdoptionApplicationDraftValidator.validate(draft).exceptionOrNull()?.let {
            return AdoptionApplicationResult.ValidationError(ErrorSanitizer.sanitize(it))
        }
        if (sessionRepository.currentSession() !is SessionState.Authenticated) {
            return AdoptionApplicationResult.Unauthenticated("Tu sesión no está disponible.")
        }
        return gateway.submit(
            RemoteSubmitApplicationParams(
                adoptionId = draft.adoptionId.value,
                message = draft.message.trim(),
                housingType = draft.housingType?.trim()?.takeIf { it.isNotEmpty() },
                hasOtherPets = draft.hasOtherPets,
                previousExperience = draft.previousExperience?.trim()?.takeIf { it.isNotEmpty() },
                contactPhone = draft.contactPhone?.trim()?.takeIf { it.isNotEmpty() }
            )
        ).fold(
            onSuccess = { AdoptionApplicationResult.Success(AdoptionApplicationId(it.id)) },
            onFailure = { mapAppResult(it) }
        )
    }

    override suspend fun withdraw(id: AdoptionApplicationId): AdoptionApplicationResult {
        if (sessionRepository.currentSession() !is SessionState.Authenticated) {
            return AdoptionApplicationResult.Unauthenticated("Tu sesión no está disponible.")
        }
        return gateway.withdraw(id.value).fold(
            onSuccess = { AdoptionApplicationResult.Success(AdoptionApplicationId(it.id)) },
            onFailure = { mapAppResult(it) }
        )
    }

    override suspend fun listMine(): Result<List<AdoptionApplicationSummary>> {
        if (sessionRepository.currentSession() !is SessionState.Authenticated) {
            return Result.failure(IllegalStateException("NOT_AUTHENTICATED"))
        }
        return gateway.listMine().map { rows ->
            rows.mapNotNull { it.toSummarySafe() }
        }
    }

    override suspend fun listReceived(
        statusFilter: String?
    ): Result<List<AdoptionApplicationReviewSummary>> {
        if (sessionRepository.currentSession() !is SessionState.Authenticated) {
            return Result.failure(IllegalStateException("NOT_AUTHENTICATED"))
        }
        return gateway.listReceived(statusFilter).map { rows ->
            rows.mapNotNull { it.toReviewSummarySafe() }
        }
    }

    override suspend fun getForReview(
        id: AdoptionApplicationId
    ): Result<AdoptionApplicationReviewDetail> {
        if (sessionRepository.currentSession() !is SessionState.Authenticated) {
            return Result.failure(IllegalStateException("NOT_AUTHENTICATED"))
        }
        return gateway.getApplication(id.value).fold(
            onSuccess = { row ->
                val detail = row.toReviewDetailSafe()
                    ?: return Result.failure(IllegalStateException("APPLICATION_INVALID"))
                Result.success(detail)
            },
            onFailure = { Result.failure(IllegalStateException(mapAdoptionThrowable(it))) }
        )
    }

    override suspend fun markUnderReview(id: AdoptionApplicationId): AdoptionApplicationResult {
        if (sessionRepository.currentSession() !is SessionState.Authenticated) {
            return AdoptionApplicationResult.Unauthenticated("Tu sesión no está disponible.")
        }
        return gateway.markUnderReview(id.value).fold(
            onSuccess = { AdoptionApplicationResult.Success(AdoptionApplicationId(it.id)) },
            onFailure = { mapAppResult(it) }
        )
    }

    override suspend fun accept(id: AdoptionApplicationId): AdoptionApplicationResult {
        if (sessionRepository.currentSession() !is SessionState.Authenticated) {
            return AdoptionApplicationResult.Unauthenticated("Tu sesión no está disponible.")
        }
        return gateway.accept(id.value).fold(
            onSuccess = { AdoptionApplicationResult.Success(AdoptionApplicationId(it.id)) },
            onFailure = { mapAppResult(it) }
        )
    }

    override suspend fun reject(
        id: AdoptionApplicationId,
        reason: String?
    ): AdoptionApplicationResult {
        if (sessionRepository.currentSession() !is SessionState.Authenticated) {
            return AdoptionApplicationResult.Unauthenticated("Tu sesión no está disponible.")
        }
        return gateway.reject(id.value, reason?.trim()?.takeIf { it.isNotEmpty() }).fold(
            onSuccess = { AdoptionApplicationResult.Success(AdoptionApplicationId(it.id)) },
            onFailure = { mapAppResult(it) }
        )
    }

    private fun mapAppResult(t: Throwable): AdoptionApplicationResult {
        val msg = mapAdoptionThrowable(t)
        return when (classifyAdoptionWrite(t)) {
            AdoptionWriteKind.UNAUTHENTICATED -> AdoptionApplicationResult.Unauthenticated(msg)
            AdoptionWriteKind.FORBIDDEN -> AdoptionApplicationResult.Forbidden(msg)
            AdoptionWriteKind.CONFLICT -> AdoptionApplicationResult.Conflict(msg)
            AdoptionWriteKind.VALIDATION -> AdoptionApplicationResult.ValidationError(msg)
            AdoptionWriteKind.BACKEND -> AdoptionApplicationResult.BackendError(msg)
        }
    }
}

internal fun RemoteAdoptionApplicationRow.toSummarySafe(): AdoptionApplicationSummary? {
    val status = AdoptionApplicationStatus.parse(status) ?: return null
    if (id.isBlank() || adoptionId.isBlank()) return null
    val msg = message.trim()
    val preview = if (msg.length <= 80) msg else msg.take(79) + "…"
    return AdoptionApplicationSummary(
        id = AdoptionApplicationId(id),
        adoptionId = AdoptionId(adoptionId),
        status = status,
        adoptionTitle = adoptionTitle?.takeIf { it.isNotBlank() } ?: "Adopción",
        petName = petName?.takeIf { it.isNotBlank() } ?: "Mascota",
        submittedAtLabel = formatReportedAtLabel(submittedAt),
        messagePreview = preview
    )
}

internal fun RemoteAdoptionApplicationRow.toReviewSummarySafe(): AdoptionApplicationReviewSummary? {
    val status = AdoptionApplicationStatus.parse(status) ?: return null
    if (id.isBlank() || adoptionId.isBlank()) return null
    val msg = message.trim()
    val preview = if (msg.length <= 80) msg else msg.take(79) + "…"
    return AdoptionApplicationReviewSummary(
        id = AdoptionApplicationId(id),
        adoptionId = AdoptionId(adoptionId),
        status = status,
        adoptionTitle = adoptionTitle?.takeIf { it.isNotBlank() } ?: "Adopción",
        petName = petName?.takeIf { it.isNotBlank() } ?: "Mascota",
        submittedAtLabel = formatReportedAtLabel(submittedAt),
        applicantDisplayName = applicantName?.takeIf { it.isNotBlank() } ?: "Postulante",
        messagePreview = preview
    )
}

internal fun RemoteAdoptionApplicationRow.toReviewDetailSafe(): AdoptionApplicationReviewDetail? {
    val status = AdoptionApplicationStatus.parse(status) ?: return null
    if (id.isBlank() || adoptionId.isBlank()) return null
    return AdoptionApplicationReviewDetail(
        id = AdoptionApplicationId(id),
        adoptionId = AdoptionId(adoptionId),
        status = status,
        adoptionTitle = adoptionTitle?.takeIf { it.isNotBlank() } ?: "Adopción",
        petName = petName?.takeIf { it.isNotBlank() } ?: "Mascota",
        submittedAtLabel = formatReportedAtLabel(submittedAt),
        applicantDisplayName = applicantName?.takeIf { it.isNotBlank() } ?: "Postulante",
        message = message.trim(),
        housingType = housingType?.takeIf { it.isNotBlank() },
        hasOtherPets = hasOtherPets,
        previousExperience = previousExperience?.takeIf { it.isNotBlank() },
        contactPhone = contactPhone?.takeIf { it.isNotBlank() },
        rejectionReason = rejectionReason?.takeIf { it.isNotBlank() }
    )
}

class FakeAdoptionApplicationRepository(
    var submitResult: AdoptionApplicationResult = AdoptionApplicationResult.Success(
        AdoptionApplicationId("fake-app-1")
    ),
    var withdrawResult: AdoptionApplicationResult = AdoptionApplicationResult.Success(
        AdoptionApplicationId("fake-app-1")
    ),
    var mine: List<AdoptionApplicationSummary> = emptyList(),
    var received: MutableList<AdoptionApplicationReviewDetail> = mutableListOf(),
    var submitCalls: Int = 0
) : AdoptionApplicationRepository {
    override val dataMode: AdoptionDataMode = AdoptionDataMode.SHARED_FAKE

    override suspend fun submit(draft: AdoptionApplicationDraft): AdoptionApplicationResult {
        submitCalls++
        AdoptionApplicationDraftValidator.validate(draft).exceptionOrNull()?.let {
            return AdoptionApplicationResult.ValidationError(ErrorSanitizer.sanitize(it))
        }
        return submitResult
    }

    override suspend fun withdraw(id: AdoptionApplicationId) = withdrawResult

    override suspend fun listMine() = Result.success(mine)

    override suspend fun listReceived(
        statusFilter: String?
    ): Result<List<AdoptionApplicationReviewSummary>> {
        val filtered = if (statusFilter.isNullOrBlank()) received
        else received.filter { it.status.name.equals(statusFilter, ignoreCase = true) }
        return Result.success(filtered.map { it.toSummary() })
    }

    override suspend fun getForReview(
        id: AdoptionApplicationId
    ): Result<AdoptionApplicationReviewDetail> {
        val detail = received.firstOrNull { it.id == id }
            ?: return Result.failure(IllegalStateException("APPLICATION_NOT_FOUND"))
        return Result.success(detail)
    }

    override suspend fun markUnderReview(id: AdoptionApplicationId): AdoptionApplicationResult =
        transition(id) { current ->
            if (!AdoptionApplicationStatus.canMarkUnderReview(current.status) &&
                current.status != AdoptionApplicationStatus.UNDER_REVIEW
            ) {
                return@transition null
            }
            current.copy(status = AdoptionApplicationStatus.UNDER_REVIEW)
        }

    override suspend fun accept(id: AdoptionApplicationId): AdoptionApplicationResult =
        transition(id) { current ->
            if (!AdoptionApplicationStatus.canAccept(current.status) &&
                current.status != AdoptionApplicationStatus.ACCEPTED
            ) {
                return@transition null
            }
            current.copy(status = AdoptionApplicationStatus.ACCEPTED)
        }

    override suspend fun reject(
        id: AdoptionApplicationId,
        reason: String?
    ): AdoptionApplicationResult =
        transition(id) { current ->
            if (!AdoptionApplicationStatus.canReject(current.status) &&
                current.status != AdoptionApplicationStatus.REJECTED
            ) {
                return@transition null
            }
            current.copy(
                status = AdoptionApplicationStatus.REJECTED,
                rejectionReason = reason?.trim()?.takeIf { it.isNotEmpty() }
                    ?: current.rejectionReason
            )
        }

    private fun transition(
        id: AdoptionApplicationId,
        mutate: (AdoptionApplicationReviewDetail) -> AdoptionApplicationReviewDetail?
    ): AdoptionApplicationResult {
        val index = received.indexOfFirst { it.id == id }
        if (index < 0) {
            return AdoptionApplicationResult.BackendError("No encontramos ese contenido.")
        }
        val updated = mutate(received[index])
            ?: return AdoptionApplicationResult.ValidationError(
                "Esa transición de estado no está permitida."
            )
        received[index] = updated
        return AdoptionApplicationResult.Success(id)
    }

    private fun AdoptionApplicationReviewDetail.toSummary() = AdoptionApplicationReviewSummary(
        id = id,
        adoptionId = adoptionId,
        status = status,
        adoptionTitle = adoptionTitle,
        petName = petName,
        submittedAtLabel = submittedAtLabel,
        applicantDisplayName = applicantDisplayName,
        messagePreview = if (message.length <= 80) message else message.take(79) + "…"
    )
}
