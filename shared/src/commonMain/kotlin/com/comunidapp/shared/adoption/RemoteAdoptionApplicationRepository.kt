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

class FakeAdoptionApplicationRepository(
    var submitResult: AdoptionApplicationResult = AdoptionApplicationResult.Success(
        AdoptionApplicationId("fake-app-1")
    ),
    var withdrawResult: AdoptionApplicationResult = AdoptionApplicationResult.Success(
        AdoptionApplicationId("fake-app-1")
    ),
    var mine: List<AdoptionApplicationSummary> = emptyList(),
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
}
