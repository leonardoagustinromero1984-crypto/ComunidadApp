package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.remote.supabase.m08.ArchivePetParams
import com.comunidapp.app.data.remote.supabase.m08.DetectPetDuplicateParams
import com.comunidapp.app.data.remote.supabase.m08.M08PetErrorMapper
import com.comunidapp.app.data.remote.supabase.m08.MarkPetDeceasedParams
import com.comunidapp.app.data.remote.supabase.m08.PetAccessContext
import com.comunidapp.app.data.remote.supabase.m08.PetDuplicateCandidateRow
import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers.toPet
import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers.toPetAndContext
import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers.toProfilePet
import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers.toUpdateHealthParams
import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers.toUpdateProfileParams
import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers.toCreateParams
import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers.toDomain
import com.comunidapp.app.data.remote.supabase.m08.PetM08RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m08.PetStatusHistoryM08Row
import com.comunidapp.app.data.remote.supabase.m08.RestorePetParams
import com.comunidapp.app.data.remote.supabase.m08.SetPetAvatarAssetParams
import com.comunidapp.app.data.remote.supabase.m08.SupabasePetM08RemoteDataSource
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.pets.PetHealthMerge
import com.comunidapp.app.domain.pets.PetInternalId
import com.comunidapp.app.domain.pets.PetHealthPersist
import com.comunidapp.app.domain.user.SessionEpoch
import com.comunidapp.app.domain.user.SessionGeneration
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

/**
 * LeoVer M08 Etapa 4B — legacy [PetRepository] adapter over M08 RPCs + SELECT RLS.
 * Does not call the legacy pets data-source create/update/delete path.
 */
class LegacyPetRepositoryAdapter(
    private val remote: PetM08RemoteDataSource = SupabasePetM08RemoteDataSource(),
    private val authUidProvider: () -> String? = {
        supabase.auth.currentUserOrNull()?.id
    },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val epoch: SessionEpoch = SessionGeneration,
    private val startPolling: Boolean = true,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() }
) : PetRepository {

    private val _pets = MutableStateFlow<List<Pet>>(emptyList())
    private val refreshMutex = Mutex()
    @Volatile private var lastRefreshAtMs: Long = 0L
    private var pollJob: Job? = null

    init {
        if (startPolling) startPoll()
    }

    override fun clearAccountCache() {
        pollJob?.cancel()
        pollJob = null
        epoch.rotate {
            _pets.value = emptyList()
            lastRefreshAtMs = 0L
        }
        if (startPolling) startPoll()
    }

    private fun startPoll() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                val uidAtStart = authUidProvider()
                if (!uidAtStart.isNullOrBlank()) {
                    try {
                        // Light list only — no per-pet getPetById N+1 on background poll.
                        mergeAccessibleLight()
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        // Transient poll errors must not republish another session.
                    }
                }
                // Short slices so a login does not wait the full interval,
                // and a logout stops the next fetch without a stale write.
                var waited = 0L
                while (waited < POLL_INTERVAL_MS && isActive) {
                    delay(POLL_WAKE_MS)
                    waited += POLL_WAKE_MS
                    val uidNow = authUidProvider()
                    if (uidNow != uidAtStart) break
                }
            }
        }
    }

    override suspend fun refreshAccessiblePets() {
        refreshCache(force = false)
    }

    override fun observePets(): StateFlow<List<Pet>> = _pets.asStateFlow()

    override fun observePetsForOwner(ownerId: String): Flow<List<Pet>> = flow {
        emit(petsVisibleTo(ownerId))
        _pets.collect { emit(petsVisibleTo(ownerId)) }
    }

    override fun observePet(petId: String): Flow<Pet?> = flow {
        while (coroutineContext.isActive) {
            val uid = authUidProvider()
            try {
                emit(fetchPetById(petId))
            } catch (_: Exception) {
                // Transient network/decode errors must not emit another session's pet.
                emit(cachedFor(petId, uid))
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    override fun getPetsByOwner(ownerId: String): List<Pet> = petsVisibleTo(ownerId)

    override fun getPetById(petId: String): Pet? = cachedFor(petId, authUidProvider())

    override suspend fun fetchPetById(petId: String): Pet? {
        val internalId = PetInternalId.parseUuid(petId) ?: return null
        val uid = authUidProvider()?.takeIf { it.isNotBlank() } ?: return null
        val token = epoch.current()
        val cached = cachedFor(internalId, uid)
        return try {
            val fetched = remote.getPetById(internalId)?.toPet()?.copy(accessSubjectUserId = uid)
            val merged = when {
                fetched != null -> PetHealthMerge.preferRicherHealth(
                    cached?.takeIf { it.accessSubjectUserId == uid },
                    fetched
                )
                else -> null
            } ?: return null
            epoch.publishIfCurrent(token) {
                if (authUidProvider() != uid) return@publishIfCurrent null
                _pets.value = _pets.value.map { current ->
                    if (current.id == merged.id && current.accessSubjectUserId == uid) {
                        PetHealthMerge.preferRicherHealth(current, merged)
                    } else {
                        current
                    }
                }
                merged
            }
        } catch (_: Exception) {
            epoch.publishIfCurrent(token) {
                if (authUidProvider() != uid) null else cached?.takeIf { it.accessSubjectUserId == uid }
            }
        }
    }

    override suspend fun createPet(pet: Pet): Result<String> {
        val token = epoch.current()
        val uid = authUidProvider()
        return try {
            com.comunidapp.app.domain.pets.PetCreateDiagnostic.logStaging("PET-STAGE=CREATE-BEGIN")
            val created = remote.createPetWithPrincipal(
                pet.toCreateParams().let { params ->
                    val uid = authUidProvider().orEmpty()
                    val key = com.comunidapp.app.domain.pets.PetManagementContext.keyOf(
                        com.comunidapp.app.domain.context.OperationalContextProvider.active.value,
                        uid
                    )
                    params.copy(
                        managementContextKind = key.kind,
                        managementContextId = key.id
                    )
                }
            )
            val petId = created.id
            runCatching {
                remote.updatePetProfile(pet.copy(id = petId).toUpdateProfileParams())
            }
            if (PetHealthPersist.hasData(pet)) {
                runCatching {
                    remote.updatePetHealth(pet.copy(id = petId).toUpdateHealthParams())
                }
            }
            com.comunidapp.app.domain.pets.PetCreateDiagnostic.logStaging(
                "PET-STAGE=CREATE-RESULT CREATED petId=$petId"
            )
            refreshCache()
            val mapped = created.toPet().copy(accessSubjectUserId = uid)
            epoch.publishIfCurrent(token) {
                if (uid.isNullOrBlank() || authUidProvider() != uid) return@publishIfCurrent
                val kept = _pets.value.filter { it.accessSubjectUserId == uid }
                _pets.value = com.comunidapp.app.domain.pets.PetListMerge.withCreated(kept, mapped)
            }
            com.comunidapp.app.domain.pets.PetCreateDiagnostic.logStaging(
                "PET-STAGE=LIST-REFRESH count=${_pets.value.size} hasCreated=${_pets.value.any { it.id == petId }}"
            )
            Result.success(petId)
        } catch (e: Exception) {
            com.comunidapp.app.domain.pets.PetCreateDiagnostic.logStaging(
                "PET-STAGE=CREATE-RESULT FAIL type=${e::class.java.simpleName}"
            )
            M08PetErrorMapper.failure(e)
        }
    }

    override suspend fun updatePet(pet: Pet): Result<Unit> {
        return try {
            remote.updatePetProfile(pet.toUpdateProfileParams())
            if (PetHealthPersist.hasData(pet)) {
                remote.updatePetHealth(pet.toUpdateHealthParams())
            }
            refreshCache()
            Result.success(Unit)
        } catch (e: Exception) {
            M08PetErrorMapper.failure(e)
        }
    }

    override suspend fun deletePet(petId: String): Result<Unit> {
        return try {
            remote.archivePet(ArchivePetParams(petId = petId))
            refreshCache()
            Result.success(Unit)
        } catch (e: Exception) {
            M08PetErrorMapper.failure(e)
        }
    }

    override suspend fun getPetAccessContext(petId: String): Result<PetAccessContext> {
        val internalId = com.comunidapp.app.domain.pets.PetInternalId.parseUuid(petId)
            ?: return Result.failure(IllegalArgumentException("PET_NOT_FOUND"))
        return try {
            Result.success(remote.getPetAccessContext(internalId).toDomain())
        } catch (e: Exception) {
            val accessible = runCatching { remote.listAccessiblePets(status = "ACTIVE") }.getOrNull()
            val row = accessible?.firstOrNull { it.id == internalId }
            if (row != null) {
                val ctx = row.toPetAndContext().second
                val createdBy = row.createdByUserId ?: row.ownerId
                val uid = authUidProvider()
                val isCustodian = com.comunidapp.app.domain.pets.PetCustodyAuthority
                    .isCurrentPersonCustodian(uid, "PERSON", createdBy)
                Result.success(
                    ctx.copy(
                        relationCode = if (ctx.relationCode.equals("NONE", ignoreCase = true) ||
                            ctx.relationCode.isBlank()
                        ) {
                            "OWNER"
                        } else {
                            ctx.relationCode
                        },
                        canUpdate = true,
                        canManageResponsibilities = isCustodian,
                        canInitiateTransfer = isCustodian,
                        canArchive = isCustodian,
                        canMarkDeceased = isCustodian
                    )
                )
            } else {
                M08PetErrorMapper.failure(e)
            }
        }
    }

    override suspend fun setPetAvatarAsset(petId: String, assetId: String?): Result<Pet> {
        return try {
            val row = remote.setPetAvatarAsset(
                SetPetAvatarAssetParams(petId = petId, assetId = assetId)
            )
            refreshCache()
            Result.success(row.toPet())
        } catch (e: Exception) {
            M08PetErrorMapper.failure(e)
        }
    }

    override suspend fun markPetDeceased(petId: String, reason: String?): Result<Pet> {
        return try {
            val row = remote.markPetDeceased(MarkPetDeceasedParams(petId = petId, reason = reason))
            refreshCache()
            Result.success(row.toPet())
        } catch (e: Exception) {
            M08PetErrorMapper.failure(e)
        }
    }

    override suspend fun restorePet(petId: String): Result<Pet> {
        return try {
            val row = remote.restorePet(RestorePetParams(petId = petId))
            refreshCache()
            Result.success(row.toPet())
        } catch (e: Exception) {
            M08PetErrorMapper.failure(e)
        }
    }

    override suspend fun listStatusHistory(petId: String): Result<List<PetStatusHistoryM08Row>> {
        return try {
            Result.success(remote.listStatusHistory(petId))
        } catch (e: Exception) {
            M08PetErrorMapper.failure(e)
        }
    }

    override suspend fun detectDuplicateCandidates(
        microchip: String?,
        name: String?
    ): Result<List<PetDuplicateCandidateRow>> {
        return try {
            Result.success(
                remote.detectDuplicates(
                    DetectPetDuplicateParams(microchip = microchip, name = name)
                )
            )
        } catch (e: Exception) {
            M08PetErrorMapper.failure(e)
        }
    }

    override suspend fun listPetsForPersonProfile(personUserId: String): Result<List<Pet>> {
        return try {
            Result.success(remote.listPetsForPersonProfile(personUserId).map { it.toProfilePet() })
        } catch (e: Exception) {
            M08PetErrorMapper.failure(e)
        }
    }

    private suspend fun mergeAccessibleLight() {
        val uid = authUidProvider()?.takeIf { it.isNotBlank() } ?: return
        val token = epoch.current()
        val rows = remote.listAccessiblePets(status = "ACTIVE")
        if (!coroutineContext.isActive) return
        epoch.publishIfCurrent(token) {
            if (authUidProvider() != uid) return@publishIfCurrent
            val previous = _pets.value
                .filter { it.accessSubjectUserId == uid }
                .associateBy { it.id }
            _pets.value = rows.map { row ->
                val incoming = row.toPet().copy(accessSubjectUserId = uid)
                PetHealthMerge.preferRicherHealth(previous[row.id], incoming)
            }
            lastRefreshAtMs = nowEpochMs()
        }
    }

    private fun petsVisibleTo(ownerId: String): List<Pet> {
        val authUid = authUidProvider()
        if (authUid.isNullOrBlank() || authUid != ownerId) return emptyList()
        return _pets.value.filter { pet ->
            val subject = pet.accessSubjectUserId?.trim()?.takeIf { it.isNotEmpty() }
            subject == null || subject == ownerId
        }
    }

    private fun cachedFor(petId: String, uid: String?): Pet? {
        if (uid.isNullOrBlank()) return null
        return _pets.value.find { it.id == petId }?.takeIf { pet ->
            val subject = pet.accessSubjectUserId?.trim()?.takeIf { it.isNotEmpty() }
            subject == null || subject == uid
        }
    }

    private suspend fun refreshCache(force: Boolean = true) {
        refreshMutex.withLock {
            val now = nowEpochMs()
            if (!force && lastRefreshAtMs != 0L && now - lastRefreshAtMs < 1_500L) {
                return
            }
            try {
                com.comunidapp.app.domain.pets.PetCreateDiagnostic.logStaging("PET-STAGE=LIST-REFRESH")
                // Prefer light merge for responsiveness; detail screen fetches full pet.
                // Timestamp advances only inside a generation-matched commit.
                mergeAccessibleLight()
                com.comunidapp.app.domain.pets.PetCreateDiagnostic.logStaging(
                    "PET-STAGE=LIST-RESULT count=${_pets.value.size}"
                )
            } catch (error: Exception) {
                com.comunidapp.app.domain.pets.PetCreateDiagnostic.logStaging(
                    "PET-STAGE=LIST-RESULT FAIL type=${error::class.java.simpleName}"
                )
            }
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 12_000L
        const val POLL_WAKE_MS = 300L
    }
}
