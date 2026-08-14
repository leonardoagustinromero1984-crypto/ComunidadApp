package com.comunidapp.shared.remote

import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.app.domain.pets.PetLifecycleStatus
import com.comunidapp.shared.adoption.AdoptionDetail
import com.comunidapp.shared.adoption.AdoptionId
import com.comunidapp.shared.adoption.AdoptionSummary
import com.comunidapp.shared.domain.adoption.AdoptionListingStatus
import com.comunidapp.shared.domain.lostfound.LostFoundCaseStatus
import com.comunidapp.shared.domain.lostfound.LostFoundCaseType
import com.comunidapp.shared.location.ApproximateLocation
import com.comunidapp.shared.lostfound.LostFoundDetail
import com.comunidapp.shared.lostfound.LostFoundId
import com.comunidapp.shared.lostfound.LostFoundSummary
import com.comunidapp.shared.media.MediaRefParser
import com.comunidapp.shared.pets.PetDetailView
import com.comunidapp.shared.pets.PetSummary
import com.comunidapp.shared.profile.UserProfileSummary

internal object RemoteProfileMapper {
    fun toSummary(row: RemoteUserProfileRow, sessionEmail: String?): UserProfileSummary {
        val email = row.email?.takeIf { it.isNotBlank() } ?: sessionEmail?.takeIf { it.isNotBlank() }
        val display = row.displayName?.takeIf { it.isNotBlank() }
            ?: row.name?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: "Usuario"
        val location = buildList {
            row.city?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            row.province?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
        }.joinToString(", ").ifBlank {
            row.locationText?.trim().orEmpty()
        }.ifBlank { null }

        val avatarRef = row.avatarPath?.takeIf { it.isNotBlank() }
            ?: row.profileImageUrl?.takeIf { it.isNotBlank() }
        return UserProfileSummary(
            userId = row.id,
            displayName = display,
            email = email,
            approximateLocation = location,
            avatarRef = avatarRef,
            mediaRef = MediaRefParser.fromProfileFields(row.avatarPath, row.profileImageUrl),
            createdAtEpochMs = parseIso8601ToEpochMs(row.createdAt),
            updatedAtEpochMs = parseIso8601ToEpochMs(row.updatedAt)
        )
    }
}

internal object RemotePetsMapper {
    fun toSummary(row: RemoteAccessiblePetRow): PetSummary {
        val mediaRef = MediaRefParser.fromPetFields(row.avatarFileAssetId, row.photoUrl)
        return PetSummary(
            id = PetId(row.id),
            displayName = row.name.ifBlank { "Mascota" },
            speciesLabel = row.species.ifBlank { "—" },
            status = mapStatus(row.status),
            hasAvatar = hasAvatar(row.photoUrl, row.avatarFileAssetId),
            mediaRef = mediaRef
        )
    }

    fun toDetail(row: RemotePetRow): PetDetailView {
        val mediaRef = MediaRefParser.fromPetFields(row.avatarFileAssetId, row.photoUrl)
        return PetDetailView(
            id = PetId(row.id),
            displayName = row.name.ifBlank { "Mascota" },
            speciesLabel = row.species.ifBlank { "—" },
            breedText = row.breed?.takeIf { it.isNotBlank() },
            sexLabel = row.sex.takeIf { it.isNotBlank() },
            status = mapStatus(row.status),
            hasAvatar = hasAvatar(row.photoUrl, row.avatarFileAssetId),
            // Android PetDetail no carga M14 en el path productivo actual.
            passportHint = null,
            mediaRef = mediaRef,
            description = row.description.takeIf { it.isNotBlank() },
            sizeLabel = row.size.takeIf { it.isNotBlank() },
            ageYears = row.ageYears.takeIf { it > 0 },
            ageMonths = row.ageMonths.takeIf { it > 0 },
            color = row.color?.takeIf { it.isNotBlank() }
        )
    }

    fun accessibleToDetail(row: RemoteAccessiblePetRow): PetDetailView =
        toDetail(
            RemotePetRow(
                id = row.id,
                name = row.name,
                photoUrl = row.photoUrl,
                species = row.species,
                sex = row.sex,
                breed = row.breed,
                status = row.status,
                size = row.size,
                description = row.description,
                ageYears = row.ageYears,
                ageMonths = row.ageMonths,
                color = row.color,
                avatarFileAssetId = row.avatarFileAssetId,
                ownerId = row.ownerId
            )
        )

    private fun hasAvatar(photoUrl: String?, avatarFileAssetId: String?): Boolean =
        !photoUrl.isNullOrBlank() || !avatarFileAssetId.isNullOrBlank()

    private fun mapStatus(raw: String): PetLifecycleStatus =
        when (raw.trim().uppercase()) {
            "DECEASED" -> PetLifecycleStatus.DECEASED
            "ARCHIVED" -> PetLifecycleStatus.ARCHIVED
            else -> PetLifecycleStatus.ACTIVE
        }
}

/**
 * Lost/Found SAFE mapper.
 * No coords, contact_info, author_id ni UUID visible en campos de display.
 * Estados/tipos desconocidos → null (no forzar ACTIVE/LOST).
 */
internal object RemoteLostFoundMapper {
    fun toSummary(row: RemoteLostFoundRow): LostFoundSummary? {
        val type = mapType(row.type) ?: return null
        val status = mapStatus(row.status) ?: return null
        if (row.id.isBlank()) return null
        val mediaRef = MediaRefParser.fromPhotoField(row.photoUrl)
        return LostFoundSummary(
            id = LostFoundId(row.id),
            type = type,
            status = status,
            displayName = row.petName?.takeIf { it.isNotBlank() },
            speciesLabel = RemoteLabelMapper.speciesLabel(row.species),
            approximateLocation = approximateFromLocationText(row.location),
            reportedAtLabel = formatReportedAtLabel(row.createdAt),
            publicCode = row.publicCode?.takeIf { it.isNotBlank() },
            hasPhoto = !row.photoUrl.isNullOrBlank(),
            mediaRef = mediaRef
        )
    }

    fun toDetail(row: RemoteLostFoundRow): LostFoundDetail? {
        val type = mapType(row.type) ?: return null
        val status = mapStatus(row.status) ?: return null
        if (row.id.isBlank()) return null
        val mediaRef = MediaRefParser.fromPhotoField(row.photoUrl)
        return LostFoundDetail(
            id = LostFoundId(row.id),
            type = type,
            status = status,
            displayName = row.petName?.takeIf { it.isNotBlank() },
            speciesLabel = RemoteLabelMapper.speciesLabel(row.species),
            breedText = null,
            sexLabel = null,
            description = row.description.trim().ifBlank { "Sin descripción." },
            approximateLocation = approximateFromLocationText(row.location),
            reportedAtLabel = formatReportedAtLabel(row.createdAt),
            publicCode = row.publicCode?.takeIf { it.isNotBlank() },
            publisherDisplayName = row.authorName?.takeIf { it.isNotBlank() },
            hasPhoto = !row.photoUrl.isNullOrBlank(),
            mediaRef = mediaRef,
            viewerCanManage = false
        )
    }

    fun mapType(raw: String): LostFoundCaseType? =
        when (raw.trim().uppercase()) {
            "LOST" -> LostFoundCaseType.LOST
            "FOUND" -> LostFoundCaseType.FOUND
            else -> null
        }

    fun mapStatus(raw: String): LostFoundCaseStatus? =
        when (raw.trim().uppercase()) {
            "ACTIVE" -> LostFoundCaseStatus.ACTIVE
            "RESOLVED" -> LostFoundCaseStatus.RESOLVED
            "CLOSED" -> LostFoundCaseStatus.CLOSED
            else -> null
        }
}

/**
 * Adoption SAFE mapper (M09 publication row).
 * No publisher_id / organizationId / pet_id / coords / requirements clínicos.
 */
internal object RemoteAdoptionMapper {
    fun toSummary(row: RemoteAdoptionPublicationRow): AdoptionSummary? {
        val status = mapStatus(row.status) ?: return null
        if (row.id.isBlank()) return null
        val display = row.name.takeIf { it.isNotBlank() }
            ?: row.title?.takeIf { it.isNotBlank() }
            ?: return null
        val mediaRef = MediaRefParser.fromPhotoField(row.photoUrl)
        return AdoptionSummary(
            id = AdoptionId(row.id),
            status = status,
            displayName = display,
            speciesLabel = RemoteLabelMapper.speciesLabel(row.species),
            approximateAgeLabel = RemoteLabelMapper.ageLabel(row.ageYears, row.ageMonths),
            sexLabel = RemoteLabelMapper.sexLabel(row.sex),
            approximateLocation = approximateFromLocationText(
                row.locationText?.takeIf { it.isNotBlank() } ?: row.location
            ),
            publicCode = row.publicCode?.takeIf { it.isNotBlank() },
            hasPhoto = !row.photoUrl.isNullOrBlank(),
            mediaRef = mediaRef
        )
    }

    fun toDetail(row: RemoteAdoptionPublicationRow): AdoptionDetail? {
        val status = mapStatus(row.status) ?: return null
        if (row.id.isBlank()) return null
        val display = row.name.takeIf { it.isNotBlank() }
            ?: row.title?.takeIf { it.isNotBlank() }
            ?: return null
        val mediaRef = MediaRefParser.fromPhotoField(row.photoUrl)
        return AdoptionDetail(
            id = AdoptionId(row.id),
            status = status,
            displayName = display,
            speciesLabel = RemoteLabelMapper.speciesLabel(row.species),
            breedText = null,
            approximateAgeLabel = RemoteLabelMapper.ageLabel(row.ageYears, row.ageMonths),
            sexLabel = RemoteLabelMapper.sexLabel(row.sex),
            description = row.description.trim().ifBlank { "Sin descripción." },
            approximateLocation = approximateFromLocationText(
                row.locationText?.takeIf { it.isNotBlank() } ?: row.location
            ),
            publisherDisplayName = row.publisherName?.takeIf { it.isNotBlank() },
            publicCode = row.publicCode?.takeIf { it.isNotBlank() },
            hasPhoto = !row.photoUrl.isNullOrBlank(),
            mediaRef = mediaRef
        )
    }

    fun mapStatus(raw: String): AdoptionListingStatus? =
        when (raw.trim().uppercase()) {
            "DRAFT" -> AdoptionListingStatus.DRAFT
            "PUBLISHED", "AVAILABLE" -> AdoptionListingStatus.PUBLISHED
            "ADOPTED" -> AdoptionListingStatus.ADOPTED
            "CLOSED" -> AdoptionListingStatus.CLOSED
            // PAUSED / IN_PROCESS / desconocidos: no forzar a PUBLISHED
            else -> null
        }
}

internal object RemoteLabelMapper {
    fun speciesLabel(raw: String): String =
        when (raw.trim().uppercase()) {
            "DOG", "PERRO" -> "Perro"
            "CAT", "GATO" -> "Gato"
            "OTHER", "OTRO", "" -> "Otro"
            else -> raw.trim().ifBlank { "Otro" }
        }

    fun sexLabel(raw: String): String? =
        when (raw.trim().uppercase()) {
            "MALE", "MACHO" -> "Macho"
            "FEMALE", "HEMBRA" -> "Hembra"
            "UNKNOWN", "DESCONOCIDO", "" -> null
            else -> raw.trim().takeIf { it.isNotBlank() }
        }

    fun ageLabel(years: Int, months: Int): String? {
        if (years <= 0 && months <= 0) return null
        return when {
            years <= 0 -> if (months == 1) "1 mes" else "$months meses"
            months <= 0 -> if (years == 1) "1 año" else "$years años"
            else -> {
                val y = if (years == 1) "1 año" else "$years años"
                val m = if (months == 1) "1 mes" else "$months meses"
                "$y $m"
            }
        }
    }
}

/** Solo texto de zona → ApproximateLocation. Nunca lat/lng. */
internal fun approximateFromLocationText(location: String): ApproximateLocation {
    val locality = location.trim().ifBlank { "Zona no especificada" }
    return ApproximateLocation(locality = locality)
}

/** Fecha reportada legible sin exponer epoch ni ISO crudo. */
internal fun formatReportedAtLabel(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    val datePart = iso.trim().take(10)
    if (datePart.length == 10 && datePart[4] == '-' && datePart[7] == '-') {
        val y = datePart.substring(0, 4)
        val m = datePart.substring(5, 7)
        val d = datePart.substring(8, 10)
        return "$d/$m/$y"
    }
    return "—"
}
