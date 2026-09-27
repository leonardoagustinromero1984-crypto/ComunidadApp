package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.CreateM14PassportInput
import com.comunidapp.app.data.model.M14PassportHistory
import com.comunidapp.app.data.model.M14PassportStatus
import com.comunidapp.app.data.model.M14PetPassport
import com.comunidapp.app.data.model.M14PublicPassportProjection
import com.comunidapp.app.data.model.UpdateM14PassportInput
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m08.M08RpcDecoding
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.canonical.CanonicalVitaCoraProjection
import com.comunidapp.app.domain.vitacora.VitaCoraUserHistoryFilter
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

@Serializable
private data class CanonicalMomentRow(
    val id: String,
    val kind: String,
    val title: String? = null,
    val body: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

/**
 * QA VitaCora path: live pet + canonical Health. No Passport snapshot table.
 */
class CanonicalVitaCoraProjectionRepository(
    private val pets: () -> PetRepository = { DataProvider.petRepository }
) : M14PassportRepository {

    override fun observeMyPassports(): Flow<List<M14PetPassport>> = flow {
        emit(pets().observePets().value.mapNotNull { CanonicalVitaCoraProjection.fromPet(it) })
    }

    override fun observePassport(passportId: String): Flow<M14PetPassport?> = flow {
        emit(CanonicalVitaCoraProjection.fromPet(pets().fetchPetById(passportId) ?: pets().getPetById(passportId)))
    }

    override fun observePassportForPet(petId: String): Flow<M14PetPassport?> = flow {
        emit(CanonicalVitaCoraProjection.fromPet(pets().fetchPetById(petId) ?: pets().getPetById(petId)))
    }

    override suspend fun getPassport(passportId: String): Result<M14PetPassport> {
        val pet = pets().fetchPetById(passportId) ?: pets().getPetById(passportId)
        val projection = CanonicalVitaCoraProjection.fromPet(pet)
            ?: return Result.failure(IllegalStateException("PET_NOT_FOUND"))
        return Result.success(projection)
    }

    override suspend fun createPassport(input: CreateM14PassportInput): Result<M14PetPassport> {
        val pet = pets().fetchPetById(input.petId) ?: pets().getPetById(input.petId)
        val projection = CanonicalVitaCoraProjection.fromPet(pet)
            ?: return Result.failure(IllegalStateException("PET_NOT_FOUND"))
        return Result.success(projection)
    }

    override suspend fun updatePassport(
        passportId: String,
        input: UpdateM14PassportInput
    ): Result<M14PetPassport> = getPassport(passportId)

    override suspend fun activatePassport(passportId: String): Result<M14PetPassport> =
        getPassport(passportId)

    override suspend fun transitionPassport(
        passportId: String,
        to: M14PassportStatus,
        reason: String?
    ): Result<M14PetPassport> = getPassport(passportId)

    override fun observeHistory(passportId: String): Flow<List<M14PassportHistory>> = flow {
        val statusItems = pets().listStatusHistory(passportId).getOrNull().orEmpty()
            .filter { VitaCoraUserHistoryFilter.isUserFacing(it.reasonCode, it.newStatus) }
            .map { row ->
                M14PassportHistory(
                    id = row.id ?: "${row.petId}-${row.createdAt}",
                    passportId = passportId,
                    fromStatus = row.previousStatus?.let { runCatching { M14PassportStatus.valueOf(it) }.getOrNull() },
                    toStatus = runCatching { M14PassportStatus.valueOf(row.newStatus) }
                        .getOrDefault(M14PassportStatus.ACTIVE),
                    actorUserId = row.actorUserId,
                    reason = row.reasonCode,
                    createdAt = row.createdAt?.let {
                        runCatching { Instant.parse(it).toEpochMilli() }.getOrDefault(0L)
                    } ?: 0L,
                    metadataEvent = row.reasonCode
                )
            }
        val moments = runCatching { listUserMoments(passportId) }.getOrDefault(emptyList())
        emit((statusItems + moments).sortedByDescending { it.createdAt })
    }

    override suspend fun getPublicProjection(publicCode: String): Result<M14PublicPassportProjection> {
        val localPet = pets().observePets().value.firstOrNull { it.publicCode == publicCode }
        return runCatching {
            val element: JsonElement = supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_PUBLIC_PET,
                parameters = buildJsonObject { put("p_code", publicCode) }
            ).decodeAs()
            val row = M08RpcDecoding.decodeRow<CanonicalPublicPetRow>(element)
                ?: throw IllegalStateException("NOT_FOUND")
            M14PublicPassportProjection(
                publicCode = publicCode,
                displayName = row.name ?: localPet?.name ?: "Mascota",
                species = localPet?.species ?: com.comunidapp.app.data.model.PetSpecies.OTHER,
                breedText = localPet?.breed,
                sex = localPet?.sex,
                primaryColor = localPet?.color,
                distinctiveMarks = null,
                passportStatus = M14PassportStatus.ACTIVE,
                microchipMasked = null,
                credentialsPublic = emptyList(),
                updatedAtApproxDayEpochMs = localPet?.updatedAt ?: 0L
            )
        }.recoverCatching {
            val pet = localPet ?: throw it
            val projection = CanonicalVitaCoraProjection.fromPet(pet)
                ?: throw IllegalStateException("NOT_FOUND")
            M14PublicPassportProjection(
                publicCode = publicCode,
                displayName = projection.displayName,
                species = projection.species,
                breedText = projection.breedText,
                sex = projection.sex,
                primaryColor = projection.primaryColor,
                distinctiveMarks = projection.distinctiveMarks,
                passportStatus = M14PassportStatus.ACTIVE,
                microchipMasked = null,
                credentialsPublic = emptyList(),
                updatedAtApproxDayEpochMs = projection.updatedAt
            )
        }
    }

    @Serializable
    private data class CanonicalPublicPetRow(
        @SerialName("public_code") val publicCode: String? = null,
        val name: String? = null,
        val species: String? = null,
        val sex: String? = null,
        @SerialName("locality_id") val localityId: String? = null
    )

    override suspend fun rotatePublicCode(passportId: String): Result<M14PetPassport> =
        getPassport(passportId)

    private suspend fun listUserMoments(petId: String): List<M14PassportHistory> {
        val element: JsonElement = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_LIST_VITACORA_MOMENTS,
            parameters = buildJsonObject { put("p_pet_id", petId) }
        ).decodeAs()
        return M08RpcDecoding.decodeRows<CanonicalMomentRow>(element)
            .filter { row ->
                row.kind.uppercase() in setOf(
                    "SOCIAL", "PHOTO", "MEMORY", "NOTE", "MILESTONE", "ARRIVAL", "BIRTHDAY", "TRIP",
                    "CARE_CREATED", "CARE_TRANSFER"
                )
            }
            .map { row ->
                val social = if (row.kind.equals("SOCIAL", ignoreCase = true)) {
                    com.comunidapp.app.domain.vitacora.VitaCoraSocialMedia.resolve(row.body)
                } else {
                    null
                }
                val contentId = com.comunidapp.app.domain.vitacora.VitaCoraSocialMomentCodec
                    .decode(row.body)?.contentId?.trim()?.takeIf { it.isNotEmpty() }
                M14PassportHistory(
                    id = row.id,
                    passportId = petId,
                    fromStatus = null,
                    toStatus = M14PassportStatus.ACTIVE,
                    actorUserId = row.createdBy,
                    reason = row.title ?: row.kind,
                    createdAt = row.createdAt?.let {
                        runCatching { Instant.parse(it).toEpochMilli() }.getOrDefault(0L)
                    } ?: 0L,
                    metadataEvent = row.kind,
                    mediaDisplayUrl = social?.displayUrl,
                    mediaMime = social?.mime,
                    sourceContentKind = social?.contentKind,
                    mediaDisplayUrls = social?.allUrls.orEmpty()
                ) to contentId
            }
            .let { rows ->
                val socialGrouped = linkedMapOf<String, M14PassportHistory>()
                val others = mutableListOf<M14PassportHistory>()
                for ((item, contentId) in rows) {
                    val isSocial = item.metadataEvent.equals("SOCIAL", ignoreCase = true)
                    if (isSocial && !contentId.isNullOrBlank()) {
                        val key = contentId
                        val existing = socialGrouped[key]
                        if (existing == null) {
                            socialGrouped[key] = item
                        } else {
                            val mergedUrls = (existing.mediaDisplayUrls + item.mediaDisplayUrls)
                                .ifEmpty { listOfNotNull(existing.mediaDisplayUrl, item.mediaDisplayUrl) }
                                .distinct()
                            socialGrouped[key] = existing.copy(
                                mediaDisplayUrl = mergedUrls.firstOrNull() ?: existing.mediaDisplayUrl,
                                mediaDisplayUrls = mergedUrls,
                                createdAt = maxOf(existing.createdAt, item.createdAt)
                            )
                        }
                    } else {
                        others += item
                    }
                }
                (socialGrouped.values + others).sortedByDescending { it.createdAt }
            }
    }
}
