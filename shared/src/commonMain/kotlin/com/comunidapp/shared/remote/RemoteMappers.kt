package com.comunidapp.shared.remote

import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.app.domain.pets.PetLifecycleStatus
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

        return UserProfileSummary(
            userId = row.id,
            displayName = display,
            email = email,
            approximateLocation = location,
            avatarRef = row.avatarPath?.takeIf { it.isNotBlank() }
                ?: row.profileImageUrl?.takeIf { it.isNotBlank() },
            createdAtEpochMs = parseIso8601ToEpochMs(row.createdAt),
            updatedAtEpochMs = parseIso8601ToEpochMs(row.updatedAt)
        )
    }
}

internal object RemotePetsMapper {
    fun toSummary(row: RemoteAccessiblePetRow): PetSummary =
        PetSummary(
            id = PetId(row.id),
            displayName = row.name.ifBlank { "Mascota" },
            speciesLabel = row.species.ifBlank { "—" },
            status = mapStatus(row.status),
            hasAvatar = hasAvatar(row.photoUrl, row.avatarFileAssetId)
        )

    fun toDetail(row: RemotePetRow): PetDetailView =
        PetDetailView(
            id = PetId(row.id),
            displayName = row.name.ifBlank { "Mascota" },
            speciesLabel = row.species.ifBlank { "—" },
            breedText = row.breed?.takeIf { it.isNotBlank() },
            sexLabel = row.sex.takeIf { it.isNotBlank() },
            status = mapStatus(row.status),
            hasAvatar = hasAvatar(row.photoUrl, row.avatarFileAssetId),
            // Android PetDetail no carga M14 en el path productivo actual.
            passportHint = null
        )

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
