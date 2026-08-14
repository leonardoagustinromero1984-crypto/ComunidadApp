package com.comunidapp.shared.publiccontent

import com.comunidapp.shared.deeplink.DeepLinkTarget
import com.comunidapp.shared.media.MediaRef

/**
 * Modelos SAFE de contenido público — sin email/phone/ownerId/coords/authorId.
 */
sealed interface PublicContent {
    data class Pet(
        val publicCode: String,
        val displayName: String,
        val species: String?,
        val breedText: String?,
        val sex: String?,
        val status: String,
        val photo: MediaRef?,
        val primaryColor: String? = null,
        val distinctiveMarks: String? = null,
        val microchipMasked: String? = null,
        val birthDate: String? = null
    ) : PublicContent

    data class Adoption(
        val publicCode: String,
        val title: String?,
        val name: String?,
        val description: String?,
        val species: String?,
        val sex: String?,
        val ageYears: Int?,
        val ageMonths: Int?,
        val size: String?,
        val status: String,
        val isActive: Boolean,
        val locationText: String?,
        val photo: MediaRef?,
        val publisherDisplayName: String?
    ) : PublicContent

    data class LostFound(
        val publicCode: String,
        val caseType: PublicLostFoundCaseType,
        val petName: String?,
        val species: String?,
        val description: String?,
        val zoneText: String?,
        val status: String,
        val isActive: Boolean,
        val photo: MediaRef?
    ) : PublicContent
}

enum class PublicLostFoundCaseType {
    LOST,
    FOUND
}

sealed interface PublicContentResult {
    data class Success(val content: PublicContent) : PublicContentResult
    /** NOT_PUBLIC / missing / código inválido. */
    data object NotFound : PublicContentResult
    data class Unavailable(val message: String) : PublicContentResult
    data class NetworkError(val message: String) : PublicContentResult
    data class Unconfigured(val message: String) : PublicContentResult
}

interface PublicContentRepository {
    suspend fun resolve(target: DeepLinkTarget): PublicContentResult
}

fun DeepLinkTarget.isPublicContentTarget(): Boolean = when (this) {
    is DeepLinkTarget.PetPublic,
    is DeepLinkTarget.AdoptionPublic,
    is DeepLinkTarget.LostCase,
    is DeepLinkTarget.FoundCase,
    is DeepLinkTarget.Passport -> true
    DeepLinkTarget.SafeHome,
    is DeepLinkTarget.Unsupported -> false
}
