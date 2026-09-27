package com.comunidapp.app.data.remote.supabase.canonical

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.remote.supabase.m08.AccessiblePetM08Row
import com.comunidapp.app.data.remote.supabase.m08.PetM08Row
import com.comunidapp.app.domain.pets.PetBirth
import com.comunidapp.app.domain.pets.PetBirthPrecision
import com.comunidapp.app.domain.pets.PetSpeciesCatalog
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class CanonicalVitacoraNumberRow(
    @SerialName("pet_id") val petId: String,
    @SerialName("public_vitacora_number") val publicVitacoraNumber: Long? = null
)

@Serializable
data class CanonicalPetHolderRow(
    @SerialName("link_id") val linkId: String,
    @SerialName("holder_kind") val holderKind: String,
    val role: String,
    val status: String,
    @SerialName("person_id") val personId: String? = null,
    @SerialName("organization_id") val organizationId: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_asset_id") val avatarAssetId: String? = null,
    @SerialName("care_role") val careRole: String? = null,
    val username: String? = null
)

@Serializable
data class CanonicalPetRow(
    val id: String,
    @SerialName("created_by_user_id") val createdByUserId: String? = null,
    val name: String,
    @SerialName("species_code") val speciesCode: String = "",
    val sex: String? = null,
    val size: String? = null,
    @SerialName("breed_id") val breedId: String? = null,
    @SerialName("avatar_asset_id") val avatarAssetId: String? = null,
    @SerialName("public_code") val publicCode: String? = null,
    @SerialName("home_locality_id") val homeLocalityId: String? = null,
    @SerialName("birth_precision") val birthPrecision: String = "UNKNOWN",
    @SerialName("birth_date") val birthDate: String? = null,
    @SerialName("birth_year") val birthYear: Int? = null,
    @SerialName("birth_month") val birthMonth: Int? = null,
    @SerialName("estimated_age_months") val estimatedAgeMonths: Int? = null,
    @SerialName("estimated_as_of") val estimatedAsOf: String? = null,
    @SerialName("lifecycle_status") val lifecycleStatus: String = "ACTIVE",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("public_vitacora_number") val publicVitacoraNumber: Long? = null,
    @SerialName("management_context_kind") val managementContextKind: String? = null,
    @SerialName("management_context_id") val managementContextId: String? = null,
    @SerialName("origin_kind") val originKind: String = "STANDARD"
)

fun CanonicalPetRow.toBirth(): PetBirth {
    val precision = runCatching { PetBirthPrecision.valueOf(birthPrecision) }
        .getOrDefault(PetBirthPrecision.UNKNOWN)
    return PetBirth(
        precision = precision,
        birthDate = birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        birthYear = birthYear,
        birthMonth = birthMonth,
        estimatedAgeMonths = estimatedAgeMonths,
        estimatedAsOf = estimatedAsOf?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    )
}

fun CanonicalPetRow.toPetM08Row(): PetM08Row {
    val birth = toBirth()
    val age = birth.approximateYearsMonths()
    return PetM08Row(
        id = id,
        ownerId = createdByUserId?.takeIf { it.isNotBlank() },
        name = name,
        photoUrl = null,
        species = speciesCode.ifBlank { "OTHER" },
        sex = sex ?: PetSex.UNKNOWN.name,
        ageYears = age?.first ?: 0,
        ageMonths = age?.second ?: 0,
        size = size ?: PetSize.MEDIUM.name,
        description = "",
        breed = null,
        breedId = breedId,
        status = lifecycleStatus,
        archivedAt = archivedAt,
        avatarFileAssetId = avatarAssetId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        publicCode = publicCode,
        publicVitacoraNumber = publicVitacoraNumber,
        createdByUserId = createdByUserId,
        managementContextKind = managementContextKind,
        managementContextId = managementContextId,
        originKind = originKind
    )
}

fun CanonicalPetRow.toAccessibleRow(): AccessiblePetM08Row {
    val row = toPetM08Row()
    return AccessiblePetM08Row(
        id = row.id,
        ownerId = null,
        name = row.name,
        photoUrl = null,
        species = row.species,
        sex = row.sex,
        ageYears = row.ageYears,
        ageMonths = row.ageMonths,
        size = row.size,
        description = "",
        breed = row.breed,
        breedId = row.breedId,
        status = row.status,
        archivedAt = row.archivedAt,
        avatarFileAssetId = row.avatarFileAssetId,
        createdAt = row.createdAt,
        updatedAt = row.updatedAt,
        relationCode = "OWNER",
        canUpdate = true,
        canManageHealth = true,
        canManageMedia = true,
        canArchive = false,
        canMarkDeceased = false,
        publicCode = row.publicCode,
        publicVitacoraNumber = publicVitacoraNumber,
        createdByUserId = createdByUserId,
        managementContextKind = managementContextKind,
        managementContextId = managementContextId,
        originKind = row.originKind
    )
}

fun CanonicalPetRow.toPet(): Pet {
    val birth = toBirth()
    val age = birth.approximateYearsMonths()
    return Pet(
        id = id,
        ownerId = createdByUserId?.takeIf { it.isNotBlank() },
        name = name,
        photoUrl = null,
        species = PetSpeciesCatalog.toPetSpecies(speciesCode),
        speciesCode = speciesCode.takeIf { it.isNotBlank() },
        sex = runCatching { PetSex.valueOf(sex ?: "UNKNOWN") }.getOrDefault(PetSex.UNKNOWN),
        ageYears = age?.first ?: 0,
        ageMonths = age?.second ?: 0,
        size = runCatching { PetSize.valueOf(size ?: "MEDIUM") }.getOrDefault(PetSize.MEDIUM),
        description = "",
        breed = null,
        breedId = breedId,
        status = lifecycleStatus,
        archivedAt = archivedAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() },
        avatarFileAssetId = avatarAssetId,
        createdByUserId = createdByUserId,
        birthPrecision = birth.precision.name,
        birthDate = birth.birthDate?.toString(),
        birthYear = birth.birthYear,
        birthMonth = birth.birthMonth,
        estimatedAgeMonths = birth.estimatedAgeMonths,
        estimatedAsOf = birth.estimatedAsOf?.toString(),
        publicCode = publicCode,
        homeLocalityId = homeLocalityId,
        publicVitacoraNumber = publicVitacoraNumber,
        managementContextKind = managementContextKind,
        managementContextId = managementContextId,
        originKind = originKind
    )
}
