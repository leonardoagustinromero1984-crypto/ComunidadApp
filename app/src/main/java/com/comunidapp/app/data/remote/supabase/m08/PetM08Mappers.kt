package com.comunidapp.app.data.remote.supabase.m08

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetReminder
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.SterilizationStatus
import com.comunidapp.app.data.model.VaccinationRecord
import com.comunidapp.app.data.remote.supabase.PetReminderDto
import com.comunidapp.app.data.remote.supabase.VaccinationRecordDto
import com.comunidapp.app.domain.organization.OrganizationId
import com.comunidapp.app.domain.pets.MicrochipNormalizer
import com.comunidapp.app.domain.pets.resolvedSpeciesCode
import com.comunidapp.app.domain.pets.PetAggregate
import com.comunidapp.app.domain.pets.PetAuthorization
import com.comunidapp.app.domain.pets.PetAuthorizationId
import com.comunidapp.app.domain.pets.PetAvatarRef
import com.comunidapp.app.domain.pets.PetCapability
import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.app.domain.pets.PetLifecycleStatus
import com.comunidapp.app.domain.pets.PetLinkStatus
import com.comunidapp.app.domain.pets.PetMediaBundle
import com.comunidapp.app.domain.pets.PetPrincipalHolder
import com.comunidapp.app.domain.pets.PetResponsibility
import com.comunidapp.app.domain.pets.PetResponsibilityId
import com.comunidapp.app.domain.pets.PetResponsibilityRole
import com.comunidapp.app.domain.pets.PetStatusHistoryEntry
import com.comunidapp.app.domain.pets.PetTransfer
import com.comunidapp.app.domain.pets.PetTransferId
import com.comunidapp.app.domain.pets.PetTransferStatus
import java.time.Instant

object PetM08Mappers {

    fun PetM08Row.toPet(): Pet = Pet(
        id = id,
        ownerId = ownerId?.takeIf { it.isNotBlank() },
        name = name,
        photoUrl = photoUrl,
        species = enumValueOrDefault(species, PetSpecies.OTHER),
        speciesCode = species.takeIf { it.isNotBlank() },
        speciesName = speciesName,
        secondaryLabelSingular = secondaryLabelSingular,
        sex = parsePetSex(sex),
        ageYears = ageYears,
        ageMonths = ageMonths,
        size = enumValueOrDefault(size, PetSize.MEDIUM),
        description = description,
        vaccinations = (vaccinations ?: emptyList()).mapNotNull { dto ->
            val name = dto.name.trim()
            val date = dto.date.trim()
            if (name.isEmpty() && date.isEmpty()) null
            else VaccinationRecord(
                name = name.ifBlank { "Vacuna" },
                date = date,
                nextDueDate = dto.nextDueDate?.trim()?.takeIf { it.isNotEmpty() }
            )
        },
        lastDeworming = lastDeworming,
        dewormingProduct = dewormingProduct,
        lastFleaTreatment = lastFleaTreatment,
        fleaTreatmentProduct = fleaTreatmentProduct,
        sterilized = SterilizationStatus.fromString(sterilized),
        microchipId = microchipId,
        lastVetVisit = lastVetVisit,
        healthNotes = healthNotes,
        weightKg = weightKg,
        allergies = allergies,
        medications = medications,
        conditions = conditions,
        color = color,
        breed = breed,
        breedId = breedId,
        personality = personality,
        locationText = locationText,
        reminders = (reminders ?: emptyList()).mapNotNull { dto ->
            val title = dto.title.trim()
            val date = dto.date.trim()
            if (title.isEmpty() && date.isEmpty()) null
            else PetReminder(
                id = dto.id.ifBlank { "reminder" },
                title = title.ifBlank { "Recordatorio" },
                date = date,
                type = dto.type
            )
        },
        createdAt = createdAt.toEpochMillis(),
        updatedAt = updatedAt.toEpochMillis(),
        status = status.ifBlank { "ACTIVE" },
        deceasedAt = deceasedAt.toEpochMillis(),
        archivedAt = archivedAt.toEpochMillis(),
        avatarFileAssetId = avatarFileAssetId,
        microchipNormalized = microchipNormalized
            ?: MicrochipNormalizer.normalizeOrNull(microchipId),
        publicCode = publicCode,
        publicVitacoraNumber = publicVitacoraNumber,
        createdByUserId = createdByUserId ?: ownerId,
        healthReadFailed = healthReadFailed,
        managementContextKind = managementContextKind,
        managementContextId = managementContextId,
        originKind = originKind
    )

    fun AccessiblePetM08Row.toPet(): Pet = toPetM08Row().toPet()

    fun ProfilePetRow.toProfilePet(): Pet {
        val birth = com.comunidapp.app.domain.pets.PetBirth(
            precision = runCatching {
                com.comunidapp.app.domain.pets.PetBirthPrecision.valueOf(birthPrecision)
            }.getOrDefault(com.comunidapp.app.domain.pets.PetBirthPrecision.UNKNOWN),
            birthDate = birthDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
            birthYear = birthYear,
            birthMonth = birthMonth,
            estimatedAgeMonths = estimatedAgeMonths,
            estimatedAsOf = estimatedAsOf?.let {
                runCatching { java.time.LocalDate.parse(it) }.getOrNull()
            }
        )
        val age = birth.approximateYearsMonths()
        return Pet(
            id = id,
            name = name,
            species = com.comunidapp.app.domain.pets.PetSpeciesCatalog.toPetSpecies(speciesCode),
            speciesCode = speciesCode.takeIf { it.isNotBlank() },
            sex = parsePetSex(sex),
            ageYears = age?.first ?: 0,
            ageMonths = age?.second ?: 0,
            size = PetSize.MEDIUM,
            description = "",
            photoUrl = avatarAssetId,
            avatarFileAssetId = avatarAssetId,
            publicCode = publicCode,
            birthPrecision = birth.precision.name,
            birthDate = birth.birthDate?.toString(),
            birthYear = birth.birthYear,
            birthMonth = birth.birthMonth,
            estimatedAgeMonths = birth.estimatedAgeMonths,
            estimatedAsOf = birth.estimatedAsOf?.toString()
        )
    }

    fun AccessiblePetM08Row.toPetAndContext(): Pair<Pet, PetAccessContext> {
        val pet = toPet()
        val context = PetAccessContext(
            petId = id,
            relationCode = relationCode,
            principalPersonId = principalPersonId,
            principalOrganizationId = principalOrganizationId,
            capabilities = capabilities,
            canRead = true,
            canUpdate = canUpdate,
            canManageHealth = canManageHealth,
            canManageMedia = canManageMedia,
            canArchive = canArchive,
            canMarkDeceased = canMarkDeceased
        )
        return pet to context
    }

    fun PetAccessContextRow.toDomain(): PetAccessContext = PetAccessContext(
        petId = petId,
        relationCode = relationCode,
        principalPersonId = principalPersonId,
        principalOrganizationId = principalOrganizationId,
        principalDisplayName = principalDisplayName,
        capabilities = capabilities,
        canRead = canRead,
        canUpdate = canUpdate,
        canManageHealth = canManageHealth,
        canManageMedia = canManageMedia,
        canManageResponsibilities = canManageResponsibilities,
        canManageAuthorizations = canManageAuthorizations,
        canInitiateTransfer = canInitiateTransfer,
        canAcceptTransfer = canAcceptTransfer,
        canCancelTransfer = canCancelTransfer,
        canArchive = canArchive,
        canRestore = canRestore,
        canMarkDeceased = canMarkDeceased,
        canViewHistory = canViewHistory
    )

    fun Pet.toUpdateProfileParams(): UpdatePetProfileParams = UpdatePetProfileParams(
        petId = id,
        name = name.trim(),
        species = resolvedSpeciesCode(),
        breed = breed,
        breedId = breedId,
        sex = sex.name,
        size = size.name,
        description = description,
        ageYears = ageYears,
        ageMonths = ageMonths,
        color = color,
        microchipId = microchipId
    )

    fun Pet.toUpdateHealthParams(): UpdatePetHealthParams = UpdatePetHealthParams(
        petId = id,
        vaccinations = vaccinations.map {
            VaccinationRecordDto(it.name, it.date, it.nextDueDate)
        },
        reminders = reminders.map {
            PetReminderDto(it.id, it.title, it.date, it.type)
        },
        lastDeworming = lastDeworming,
        dewormingProduct = dewormingProduct,
        lastFleaTreatment = lastFleaTreatment,
        fleaTreatmentProduct = fleaTreatmentProduct,
        sterilized = sterilized?.name,
        lastVetVisit = lastVetVisit,
        healthNotes = healthNotes,
        weightKg = weightKg,
        allergies = allergies,
        medications = medications,
        conditions = conditions
    )

    fun Pet.toCreateParams(organizationId: String? = null): CreatePetWithPrincipalParams {
        val birth = if (birthPrecision != "UNKNOWN" || !birthDate.isNullOrBlank()) {
            com.comunidapp.app.domain.pets.PetBirth(
                precision = runCatching {
                    com.comunidapp.app.domain.pets.PetBirthPrecision.valueOf(birthPrecision)
                }.getOrDefault(com.comunidapp.app.domain.pets.PetBirthPrecision.UNKNOWN),
                birthDate = birthDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
                birthYear = birthYear,
                birthMonth = birthMonth,
                estimatedAgeMonths = estimatedAgeMonths,
                estimatedAsOf = estimatedAsOf?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            )
        } else {
            com.comunidapp.app.domain.pets.PetBirth.estimatedFromDisplayAge(ageYears, ageMonths)
        }
        return CreatePetWithPrincipalParams(
            name = name.trim(),
            species = resolvedSpeciesCode(),
            sex = sex.name,
            size = size.name,
            description = description,
            organizationId = organizationId?.takeIf { it.isNotBlank() },
            microchipId = microchipId,
            birthPrecision = birth.precision.name,
            birthDate = birth.birthDate?.toString(),
            birthYear = birth.birthYear,
            birthMonth = birth.birthMonth,
            estimatedAgeMonths = birth.estimatedAgeMonths,
            estimatedAsOf = birth.estimatedAsOf?.toString()
        )
    }

    fun PetM08Row.toAggregate(): PetAggregate {
        val principal = when {
            !principalPersonHint().isNullOrBlank() ->
                PetPrincipalHolder.Person(principalPersonHint()!!)
            ownerId?.isNotBlank() == true -> PetPrincipalHolder.Person(ownerId)
            else -> PetPrincipalHolder.Organization(
                OrganizationId(principalOrganizationHint() ?: "unknown-org")
            )
        }
        val avatar = avatarFileAssetId?.takeIf { it.isNotBlank() }?.let {
            PetAvatarRef(fileAssetId = it, legacyPhotoUrl = photoUrl)
        }
        return PetAggregate(
            id = PetId(id),
            displayName = name,
            status = lifecycleStatusOf(status),
            principal = principal,
            microchipNormalized = microchipNormalized
                ?: MicrochipNormalizer.normalizeOrNull(microchipId),
            media = PetMediaBundle(avatar = avatar),
            legacyOwnerUserId = ownerId?.takeIf { it.isNotBlank() },
            createdAtEpochMs = createdAt.toEpochMillis() ?: 0L,
            updatedAtEpochMs = updatedAt.toEpochMillis() ?: 0L,
            deceasedAtEpochMs = deceasedAt.toEpochMillis(),
            archivedAtEpochMs = archivedAt.toEpochMillis()
        )
    }

    fun AccessiblePetM08Row.toAggregate(): PetAggregate {
        val principal = when {
            !principalPersonId.isNullOrBlank() ->
                PetPrincipalHolder.Person(principalPersonId)
            !principalOrganizationId.isNullOrBlank() ->
                PetPrincipalHolder.Organization(OrganizationId(principalOrganizationId))
            ownerId?.isNotBlank() == true -> PetPrincipalHolder.Person(ownerId)
            else -> PetPrincipalHolder.Organization(OrganizationId("unknown-org"))
        }
        val avatar = avatarFileAssetId?.takeIf { it.isNotBlank() }?.let {
            PetAvatarRef(fileAssetId = it, legacyPhotoUrl = photoUrl)
        }
        return PetAggregate(
            id = PetId(id),
            displayName = name,
            status = lifecycleStatusOf(status),
            principal = principal,
            microchipNormalized = microchipNormalized
                ?: MicrochipNormalizer.normalizeOrNull(microchipId),
            media = PetMediaBundle(avatar = avatar),
            legacyOwnerUserId = ownerId?.takeIf { it.isNotBlank() },
            createdAtEpochMs = createdAt.toEpochMillis() ?: 0L,
            updatedAtEpochMs = updatedAt.toEpochMillis() ?: 0L,
            deceasedAtEpochMs = deceasedAt.toEpochMillis(),
            archivedAtEpochMs = archivedAt.toEpochMillis()
        )
    }

    fun PetResponsibilityM08Row.toDomain(): PetResponsibility {
        val holder = when {
            !personId.isNullOrBlank() -> PetPrincipalHolder.Person(personId)
            !organizationId.isNullOrBlank() ->
                PetPrincipalHolder.Organization(OrganizationId(organizationId))
            else -> error("PET_RESPONSIBILITY_HOLDER_MISSING")
        }
        return PetResponsibility(
            id = PetResponsibilityId(id),
            petId = PetId(petId),
            role = enumValueOrDefault(roleCode, PetResponsibilityRole.CO_RESPONSIBLE),
            status = enumValueOrDefault(status, PetLinkStatus.ACTIVE),
            holder = holder,
            validFromEpochMs = startsAt.toEpochMillis() ?: 0L,
            validToEpochMs = endsAt.toEpochMillis(),
            grantedByUserId = createdBy,
            acceptedAtEpochMs = acceptedAt.toEpochMillis(),
            revokedAtEpochMs = revokedAt.toEpochMillis(),
            revokeReason = reason,
            createdAtEpochMs = createdAt.toEpochMillis() ?: 0L,
            holderDisplayName = displayName,
            careRole = careRole
        )
    }

    fun PetAuthorizationM08Row.toDomain(): PetAuthorization = PetAuthorization(
        id = PetAuthorizationId(id),
        petId = PetId(petId),
        granteeUserId = personId,
        capabilities = capabilities.mapNotNull { PetCapability.fromCode(it) }.toSet(),
        status = enumValueOrDefault(status, PetLinkStatus.ACTIVE),
        validFromEpochMs = validFrom.toEpochMillis() ?: 0L,
        validToEpochMs = validUntil.toEpochMillis(),
        grantedByUserId = grantedBy,
        acceptedAtEpochMs = acceptedAt.toEpochMillis(),
        revokedAtEpochMs = revokedAt.toEpochMillis(),
        createdAtEpochMs = createdAt.toEpochMillis() ?: 0L
    )

    fun PetTransferM08Row.toDomain(): PetTransfer {
        val from = principalOf(fromPersonId, fromOrganizationId)
        val to = principalOf(toPersonId, toOrganizationId)
        return PetTransfer(
            id = PetTransferId(id),
            petId = PetId(petId),
            fromPrincipal = from,
            toPrincipal = to,
            status = enumValueOrDefault(status, PetTransferStatus.PENDING),
            requestedAtEpochMs = requestedAt.toEpochMillis() ?: 0L,
            expiresAtEpochMs = expiresAt.toEpochMillis() ?: 0L,
            requestedByUserId = requestedBy,
            resolvedAtEpochMs = respondedAt.toEpochMillis() ?: cancelledAt.toEpochMillis(),
            correlationId = correlationId
        )
    }

    fun PetStatusHistoryM08Row.toDomain(): PetStatusHistoryEntry = PetStatusHistoryEntry(
        petId = PetId(petId),
        fromStatus = previousStatus?.let { lifecycleStatusOf(it) },
        toStatus = lifecycleStatusOf(newStatus),
        reasonCode = reasonCode,
        actorUserId = actorUserId.orEmpty(),
        createdAtEpochMs = createdAt.toEpochMillis() ?: 0L,
        correlationId = correlationId
    )

    private fun AccessiblePetM08Row.toPetM08Row(): PetM08Row = PetM08Row(
        id = id,
        ownerId = ownerId,
        name = name,
        photoUrl = photoUrl,
        species = species,
        sex = sex,
        ageYears = ageYears,
        ageMonths = ageMonths,
        size = size,
        description = description,
        vaccinations = vaccinations,
        lastDeworming = lastDeworming,
        dewormingProduct = dewormingProduct,
        lastFleaTreatment = lastFleaTreatment,
        fleaTreatmentProduct = fleaTreatmentProduct,
        sterilized = sterilized,
        microchipId = microchipId,
        lastVetVisit = lastVetVisit,
        healthNotes = healthNotes,
        weightKg = weightKg,
        color = color,
        breed = breed,
        breedId = breedId,
        personality = personality,
        locationText = locationText,
        reminders = reminders,
        createdAt = createdAt,
        updatedAt = updatedAt,
        status = status,
        deceasedAt = deceasedAt,
        archivedAt = archivedAt,
        microchipNormalized = microchipNormalized,
        avatarFileAssetId = avatarFileAssetId,
        publicCode = publicCode,
        publicVitacoraNumber = publicVitacoraNumber,
        createdByUserId = createdByUserId,
        managementContextKind = managementContextKind,
        managementContextId = managementContextId,
        originKind = originKind
    )

    private fun PetM08Row.principalPersonHint(): String? = ownerId
    private fun PetM08Row.principalOrganizationHint(): String? = null

    private fun principalOf(personId: String?, organizationId: String?): PetPrincipalHolder =
        when {
            !personId.isNullOrBlank() -> PetPrincipalHolder.Person(personId)
            !organizationId.isNullOrBlank() ->
                PetPrincipalHolder.Organization(OrganizationId(organizationId))
            else -> PetPrincipalHolder.Person("unknown")
        }

    private fun parsePetSex(raw: String?): PetSex {
        val n = raw?.trim()?.uppercase().orEmpty()
        return when (n) {
            "MALE", "MACHO" -> PetSex.MALE
            "FEMALE", "HEMBRA" -> PetSex.FEMALE
            "UNKNOWN", "" -> PetSex.UNKNOWN
            else -> enumValueOrDefault(raw, PetSex.UNKNOWN)
        }
    }

    private fun lifecycleStatusOf(raw: String): PetLifecycleStatus =
        runCatching { PetLifecycleStatus.valueOf(raw.trim().uppercase()) }
            .getOrDefault(PetLifecycleStatus.ACTIVE)

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, default: T): T {
        if (raw.isNullOrBlank()) return default
        return runCatching { java.lang.Enum.valueOf(T::class.java, raw.trim().uppercase()) }
            .getOrDefault(default)
    }

    private fun String?.toEpochMillis(): Long? =
        this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
}
