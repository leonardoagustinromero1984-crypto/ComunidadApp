package com.comunidapp.app.data.remote.supabase.m08

/**
 * In-memory fake for LeoVer M08 unit tests (no network).
 */
class FakePetM08RemoteDataSource : PetM08RemoteDataSource {

    var authRequired = false
    var forceProfileFail = false
    var forceHealthFail = false
    var forceCreateFailCode: String? = null
    var forceForbidden = false

    val pets = linkedMapOf<String, PetM08Row>()
    val accessible = linkedMapOf<String, AccessiblePetM08Row>()
    val contexts = linkedMapOf<String, PetAccessContextRow>()
    val responsibilities = mutableListOf<PetResponsibilityM08Row>()
    val authorizations = mutableListOf<PetAuthorizationM08Row>()
    val transfers = mutableListOf<PetTransferM08Row>()
    val history = mutableListOf<PetStatusHistoryM08Row>()
    val duplicates = mutableListOf<PetDuplicateCandidateRow>()

    var createCalls = 0
    var profileCalls = 0
    var healthCalls = 0
    var archiveCalls = 0
    var avatarCalls = 0
    var insertAttempted = false

    private fun guard() {
        if (authRequired) throw Exception("NOT_AUTHENTICATED")
        if (forceForbidden) throw Exception("FORBIDDEN")
    }

    fun seedAccessible(row: AccessiblePetM08Row) {
        accessible[row.id] = row
        pets[row.id] = PetM08Row(
            id = row.id,
            ownerId = row.ownerId,
            name = row.name,
            photoUrl = row.photoUrl,
            species = row.species,
            sex = row.sex,
            ageYears = row.ageYears,
            ageMonths = row.ageMonths,
            size = row.size,
            description = row.description,
            status = row.status,
            avatarFileAssetId = row.avatarFileAssetId,
            microchipId = row.microchipId,
            microchipNormalized = row.microchipNormalized
        )
        contexts[row.id] = PetAccessContextRow(
            petId = row.id,
            relationCode = row.relationCode,
            principalPersonId = row.principalPersonId,
            principalOrganizationId = row.principalOrganizationId,
            capabilities = row.capabilities,
            canRead = true,
            canUpdate = row.canUpdate,
            canManageHealth = row.canManageHealth,
            canManageMedia = row.canManageMedia,
            canArchive = row.canArchive,
            canMarkDeceased = row.canMarkDeceased
        )
    }

    override suspend fun listAccessiblePets(status: String?): List<AccessiblePetM08Row> {
        guard()
        return accessible.values.filter { status == null || it.status == status }.toList()
    }

    override suspend fun getPetById(petId: String): PetM08Row? {
        guard()
        return pets[petId]
    }

    override suspend fun createPetWithPrincipal(params: CreatePetWithPrincipalParams): PetM08Row {
        guard()
        forceCreateFailCode?.let { throw Exception(it) }
        if (params.name.isBlank()) throw Exception("PET_NAME_REQUIRED")
        createCalls++
        val id = "pet-${createCalls}"
        val ownerId = if (params.organizationId.isNullOrBlank()) "user-1" else null
        val row = PetM08Row(
            id = id,
            ownerId = ownerId,
            name = params.name,
            species = params.species,
            sex = params.sex,
            size = params.size,
            description = params.description,
            microchipId = params.microchipId,
            status = "ACTIVE"
        )
        pets[id] = row
        seedAccessible(
            AccessiblePetM08Row(
                id = id,
                ownerId = ownerId,
                name = params.name,
                species = params.species,
                sex = params.sex,
                size = params.size,
                description = params.description,
                relationCode = "PRINCIPAL",
                principalPersonId = ownerId,
                canUpdate = true,
                canManageHealth = true,
                canManageMedia = true,
                canArchive = true,
                canMarkDeceased = true,
                capabilities = listOf("pet.update", "pet.manage_health", "pet.archive")
            )
        )
        return row
    }

    override suspend fun updatePetProfile(params: UpdatePetProfileParams): PetM08Row {
        guard()
        profileCalls++
        if (forceProfileFail) throw Exception("FORBIDDEN")
        val existing = pets[params.petId] ?: throw Exception("PET_NOT_FOUND")
        if (existing.status != "ACTIVE") throw Exception("PET_NOT_ACTIVE")
        if (params.microchipId == "CONFLICT") throw Exception("PET_MICROCHIP_ACTIVE_CONFLICT")
        val updated = existing.copy(
            name = params.name,
            species = params.species,
            breed = params.breed,
            sex = params.sex,
            size = params.size,
            description = params.description,
            ageYears = params.ageYears,
            ageMonths = params.ageMonths,
            color = params.color,
            microchipId = params.microchipId
        )
        pets[params.petId] = updated
        return updated
    }

    override suspend fun updatePetHealth(params: UpdatePetHealthParams): PetM08Row {
        guard()
        healthCalls++
        if (forceHealthFail) throw Exception("FORBIDDEN")
        val existing = pets[params.petId] ?: throw Exception("PET_NOT_FOUND")
        val updated = existing.copy(
            vaccinations = params.vaccinations,
            reminders = params.reminders,
            lastDeworming = params.lastDeworming,
            dewormingProduct = params.dewormingProduct,
            lastFleaTreatment = params.lastFleaTreatment,
            fleaTreatmentProduct = params.fleaTreatmentProduct,
            sterilized = params.sterilized,
            lastVetVisit = params.lastVetVisit,
            healthNotes = params.healthNotes,
            weightKg = params.weightKg
        )
        pets[params.petId] = updated
        return updated
    }

    override suspend fun getPetAccessContext(petId: String): PetAccessContextRow {
        guard()
        return contexts[petId] ?: throw Exception("PET_NOT_FOUND")
    }

    override suspend fun archivePet(params: ArchivePetParams): PetM08Row {
        guard()
        archiveCalls++
        val existing = pets[params.petId] ?: throw Exception("PET_NOT_FOUND")
        val updated = existing.copy(status = "ARCHIVED", archivedAt = "2026-01-01T00:00:00Z")
        pets[params.petId] = updated
        accessible.remove(params.petId)
        return updated
    }

    override suspend fun restorePet(params: RestorePetParams): PetM08Row {
        guard()
        val existing = pets[params.petId] ?: throw Exception("PET_NOT_FOUND")
        val updated = existing.copy(status = "ACTIVE", archivedAt = null)
        pets[params.petId] = updated
        return updated
    }

    override suspend fun markPetDeceased(params: MarkPetDeceasedParams): PetM08Row {
        guard()
        val existing = pets[params.petId] ?: throw Exception("PET_NOT_FOUND")
        val updated = existing.copy(status = "DECEASED", deceasedAt = "2026-01-01T00:00:00Z")
        pets[params.petId] = updated
        return updated
    }

    override suspend fun setPetAvatarAsset(params: SetPetAvatarAssetParams): PetM08Row {
        guard()
        avatarCalls++
        val existing = pets[params.petId] ?: throw Exception("PET_NOT_FOUND")
        // Never write asset id into photo_url
        val updated = existing.copy(avatarFileAssetId = params.assetId, photoUrl = existing.photoUrl)
        pets[params.petId] = updated
        return updated
    }

    override suspend fun detectDuplicates(
        params: DetectPetDuplicateParams
    ): List<PetDuplicateCandidateRow> {
        guard()
        return duplicates.toList()
    }

    override suspend fun assignResponsibility(
        params: AssignPetResponsibilityParams
    ): PetResponsibilityM08Row {
        guard()
        val row = PetResponsibilityM08Row(
            id = "resp-${responsibilities.size + 1}",
            petId = params.petId,
            roleCode = params.roleCode,
            personId = params.personId,
            organizationId = params.organizationId,
            createdBy = "user-1"
        )
        responsibilities += row
        return row
    }

    override suspend fun revokeResponsibility(
        params: RevokePetResponsibilityParams
    ): PetResponsibilityM08Row {
        guard()
        val idx = responsibilities.indexOfFirst { it.id == params.responsibilityId }
        if (idx < 0) throw Exception("PET_RESPONSIBILITY_NOT_FOUND")
        val updated = responsibilities[idx].copy(status = "REVOKED", revokedAt = "2026-01-01T00:00:00Z")
        responsibilities[idx] = updated
        return updated
    }

    override suspend fun listResponsibilities(petId: String): List<PetResponsibilityM08Row> =
        responsibilities.filter { it.petId == petId }

    override suspend fun grantAuthorization(
        params: GrantPetAuthorizationParams
    ): PetAuthorizationM08Row {
        guard()
        val row = PetAuthorizationM08Row(
            id = "authz-${authorizations.size + 1}",
            petId = params.petId,
            personId = params.personId,
            grantedBy = "user-1",
            capabilities = params.capabilities
        )
        authorizations += row
        return row
    }

    override suspend fun revokeAuthorization(
        params: RevokePetAuthorizationParams
    ): PetAuthorizationM08Row {
        guard()
        val idx = authorizations.indexOfFirst { it.id == params.authorizationId }
        if (idx < 0) throw Exception("PET_AUTHORIZATION_NOT_FOUND")
        val updated = authorizations[idx].copy(status = "REVOKED", revokedAt = "2026-01-01T00:00:00Z")
        authorizations[idx] = updated
        return updated
    }

    override suspend fun listAuthorizations(petId: String): List<PetAuthorizationM08Row> =
        authorizations.filter { it.petId == petId }

    override suspend fun initiateTransfer(params: InitiatePetTransferParams): PetTransferM08Row {
        guard()
        val row = PetTransferM08Row(
            id = "xfer-${transfers.size + 1}",
            petId = params.petId,
            toPersonId = params.toPersonId,
            toOrganizationId = params.toOrganizationId,
            fromPersonId = "user-1",
            requestedBy = "user-1",
            status = "PENDING",
            expiresAt = params.expiresAt
        )
        transfers += row
        return row
    }

    override suspend fun acceptTransfer(params: AcceptPetTransferParams): PetTransferM08Row {
        guard()
        val idx = transfers.indexOfFirst { it.id == params.transferId }
        if (idx < 0) throw Exception("PET_TRANSFER_NOT_FOUND")
        val updated = transfers[idx].copy(status = "ACCEPTED")
        transfers[idx] = updated
        return updated
    }

    override suspend fun rejectTransfer(params: RejectPetTransferParams): PetTransferM08Row {
        guard()
        val idx = transfers.indexOfFirst { it.id == params.transferId }
        if (idx < 0) throw Exception("PET_TRANSFER_NOT_FOUND")
        val updated = transfers[idx].copy(status = "REJECTED")
        transfers[idx] = updated
        return updated
    }

    override suspend fun cancelTransfer(params: CancelPetTransferParams): PetTransferM08Row {
        guard()
        val idx = transfers.indexOfFirst { it.id == params.transferId }
        if (idx < 0) throw Exception("PET_TRANSFER_NOT_FOUND")
        val updated = transfers[idx].copy(status = "CANCELLED")
        transfers[idx] = updated
        return updated
    }

    override suspend fun listTransfers(petId: String): List<PetTransferM08Row> =
        transfers.filter { it.petId == petId }

    override suspend fun listStatusHistory(petId: String): List<PetStatusHistoryM08Row> =
        history.filter { it.petId == petId }
}
