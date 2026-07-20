package com.comunidapp.app.data.remote.supabase.m08

/**
 * LeoVer M08 — remote data source for pet RPCs + SELECT under RLS.
 * Never INSERT/UPDATE/DELETE on pets directly.
 */
interface PetM08RemoteDataSource {
    suspend fun listAccessiblePets(status: String? = "ACTIVE"): List<AccessiblePetM08Row>
    suspend fun getPetById(petId: String): PetM08Row?
    suspend fun createPetWithPrincipal(params: CreatePetWithPrincipalParams): PetM08Row
    suspend fun updatePetProfile(params: UpdatePetProfileParams): PetM08Row
    suspend fun updatePetHealth(params: UpdatePetHealthParams): PetM08Row
    suspend fun getPetAccessContext(petId: String): PetAccessContextRow
    suspend fun archivePet(params: ArchivePetParams): PetM08Row
    suspend fun restorePet(params: RestorePetParams): PetM08Row
    suspend fun markPetDeceased(params: MarkPetDeceasedParams): PetM08Row
    suspend fun setPetAvatarAsset(params: SetPetAvatarAssetParams): PetM08Row
    suspend fun detectDuplicates(params: DetectPetDuplicateParams): List<PetDuplicateCandidateRow>

    suspend fun assignResponsibility(params: AssignPetResponsibilityParams): PetResponsibilityM08Row
    suspend fun revokeResponsibility(params: RevokePetResponsibilityParams): PetResponsibilityM08Row
    suspend fun listResponsibilities(petId: String): List<PetResponsibilityM08Row>

    suspend fun grantAuthorization(params: GrantPetAuthorizationParams): PetAuthorizationM08Row
    suspend fun revokeAuthorization(params: RevokePetAuthorizationParams): PetAuthorizationM08Row
    suspend fun listAuthorizations(petId: String): List<PetAuthorizationM08Row>

    suspend fun initiateTransfer(params: InitiatePetTransferParams): PetTransferM08Row
    suspend fun acceptTransfer(params: AcceptPetTransferParams): PetTransferM08Row
    suspend fun rejectTransfer(params: RejectPetTransferParams): PetTransferM08Row
    suspend fun cancelTransfer(params: CancelPetTransferParams): PetTransferM08Row
    suspend fun listTransfers(petId: String): List<PetTransferM08Row>

    suspend fun listStatusHistory(petId: String): List<PetStatusHistoryM08Row>
}
