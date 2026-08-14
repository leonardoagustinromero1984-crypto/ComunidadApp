package com.comunidapp.shared.publiccontent

import com.comunidapp.shared.deeplink.DeepLinkTarget
import com.comunidapp.shared.remote.PublicContentRemoteGateway
import com.comunidapp.shared.remote.PublicRpcOutcome
import com.comunidapp.shared.remote.isPublicNetworkError
import com.comunidapp.shared.remote.mapPublicContentThrowable
import com.comunidapp.shared.remote.toSafeContent

internal class RemotePublicContentRepository(
    private val gateway: PublicContentRemoteGateway
) : PublicContentRepository {

    override suspend fun resolve(target: DeepLinkTarget): PublicContentResult {
        return when (target) {
            is DeepLinkTarget.PetPublic -> resolvePet(target.publicCode)
            is DeepLinkTarget.Passport -> resolvePet(target.publicCode)
            is DeepLinkTarget.AdoptionPublic -> resolveAdoption(target.publicCode)
            is DeepLinkTarget.LostCase -> resolveLost(target.publicCode)
            is DeepLinkTarget.FoundCase -> resolveFound(target.publicCode)
            DeepLinkTarget.SafeHome -> PublicContentResult.NotFound
            is DeepLinkTarget.Unsupported ->
                PublicContentResult.Unavailable("Enlace no soportado.")
        }
    }

    private suspend fun resolvePet(code: String): PublicContentResult {
        if (code.isBlank()) return PublicContentResult.NotFound
        return when (val outcome = gateway.getPublicPet(code.trim())) {
            is PublicRpcOutcome.Ok -> PublicContentResult.Success(outcome.value.toSafeContent())
            PublicRpcOutcome.NotPublic -> PublicContentResult.NotFound
            is PublicRpcOutcome.Failed -> mapFailed(outcome.error)
        }
    }

    private suspend fun resolveAdoption(code: String): PublicContentResult {
        if (code.isBlank()) return PublicContentResult.NotFound
        return when (val outcome = gateway.getPublicAdoption(code.trim())) {
            is PublicRpcOutcome.Ok -> PublicContentResult.Success(outcome.value.toSafeContent())
            PublicRpcOutcome.NotPublic -> PublicContentResult.NotFound
            is PublicRpcOutcome.Failed -> mapFailed(outcome.error)
        }
    }

    private suspend fun resolveLost(code: String): PublicContentResult {
        if (code.isBlank()) return PublicContentResult.NotFound
        return when (val outcome = gateway.getPublicLostCase(code.trim())) {
            is PublicRpcOutcome.Ok -> {
                val content = outcome.value.toSafeContent()
                    ?: return PublicContentResult.NotFound
                PublicContentResult.Success(content)
            }
            PublicRpcOutcome.NotPublic -> PublicContentResult.NotFound
            is PublicRpcOutcome.Failed -> mapFailed(outcome.error)
        }
    }

    private suspend fun resolveFound(code: String): PublicContentResult {
        if (code.isBlank()) return PublicContentResult.NotFound
        return when (val outcome = gateway.getPublicFoundCase(code.trim())) {
            is PublicRpcOutcome.Ok -> {
                val content = outcome.value.toSafeContent()
                    ?: return PublicContentResult.NotFound
                PublicContentResult.Success(content)
            }
            PublicRpcOutcome.NotPublic -> PublicContentResult.NotFound
            is PublicRpcOutcome.Failed -> mapFailed(outcome.error)
        }
    }

    private fun mapFailed(t: Throwable): PublicContentResult {
        val msg = mapPublicContentThrowable(t)
        return if (isPublicNetworkError(t)) PublicContentResult.NetworkError(msg)
        else PublicContentResult.Unavailable(msg)
    }
}

internal class UnconfiguredPublicContentRepository : PublicContentRepository {
    override suspend fun resolve(target: DeepLinkTarget): PublicContentResult =
        PublicContentResult.Unconfigured("Servicio no configurado.")
}
