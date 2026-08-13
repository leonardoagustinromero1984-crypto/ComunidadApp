package com.comunidapp.shared.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Insert/update productivo Android: `lost_found_posts` (sin RPC create).
 * Media: M05 vía [LostFoundMediaUploadGateway].
 */
@Serializable
internal data class LostFoundInsertRow(
    val id: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("author_name") val authorName: String,
    val type: String,
    @SerialName("pet_name") val petName: String? = null,
    val species: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    val location: String,
    val description: String,
    @SerialName("contact_info") val contactInfo: String,
    val status: String = "ACTIVE",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

internal data class LostFoundInsertCommand(
    val authorId: String,
    val authorName: String,
    val type: String,
    val petName: String?,
    val species: String,
    val location: String,
    val description: String,
    val contactInfo: String,
    val status: String = "ACTIVE",
    val photoUrl: String? = null
)

@Serializable
internal data class LostFoundPhotoUpdateRow(
    @SerialName("photo_url") val photoUrl: String,
    @SerialName("updated_at") val updatedAt: String
)

internal interface LostFoundWriteGateway {
    suspend fun insert(command: LostFoundInsertCommand): Result<String>
    suspend fun fetchPublicCode(id: String): Result<String?>
    suspend fun updatePhotoUrl(id: String, assetId: String): Result<Unit>
}

internal class SupabaseLostFoundWriteGateway(
    private val client: SupabaseClient
) : LostFoundWriteGateway {

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun insert(command: LostFoundInsertCommand): Result<String> {
        return try {
            val id = Uuid.random().toString()
            val now = currentIso8601Now()
            val row = LostFoundInsertRow(
                id = id,
                authorId = command.authorId,
                authorName = command.authorName,
                type = command.type,
                petName = command.petName,
                species = command.species,
                photoUrl = command.photoUrl,
                location = command.location,
                description = command.description,
                contactInfo = command.contactInfo,
                status = command.status,
                createdAt = now,
                updatedAt = now
            )
            client.from("lost_found_posts").insert(row)
            Result.success(id)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun fetchPublicCode(id: String): Result<String?> {
        return try {
            val row = client.from("lost_found_posts")
                .select {
                    filter { eq("id", id) }
                }
                .decodeSingleOrNull<RemoteLostFoundRow>()
            Result.success(row?.publicCode?.takeIf { it.isNotBlank() })
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun updatePhotoUrl(id: String, assetId: String): Result<Unit> {
        return try {
            client.from("lost_found_posts").update(
                LostFoundPhotoUpdateRow(
                    photoUrl = assetId,
                    updatedAt = currentIso8601Now()
                )
            ) {
                filter { eq("id", id) }
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}

internal class FakeLostFoundWriteGateway(
    var insertError: Throwable? = null,
    var inserted: MutableList<LostFoundInsertRow> = mutableListOf(),
    var publicCodes: MutableMap<String, String?> = mutableMapOf(),
    var insertCalls: Int = 0,
    var photoUpdates: MutableList<Pair<String, String>> = mutableListOf(),
    var photoUpdateError: Throwable? = null,
    var forcedId: String? = null
) : LostFoundWriteGateway {
    @OptIn(ExperimentalUuidApi::class)
    override suspend fun insert(command: LostFoundInsertCommand): Result<String> {
        insertCalls++
        insertError?.let { return Result.failure(it) }
        val id = forcedId ?: Uuid.random().toString()
        inserted += LostFoundInsertRow(
            id = id,
            authorId = command.authorId,
            authorName = command.authorName,
            type = command.type,
            petName = command.petName,
            species = command.species,
            photoUrl = command.photoUrl,
            location = command.location,
            description = command.description,
            contactInfo = command.contactInfo,
            status = command.status
        )
        if (!publicCodes.containsKey(id)) {
            publicCodes[id] = "PUB-TEST$id".take(20)
        }
        return Result.success(id)
    }

    override suspend fun fetchPublicCode(id: String): Result<String?> =
        Result.success(publicCodes[id])

    override suspend fun updatePhotoUrl(id: String, assetId: String): Result<Unit> {
        photoUpdateError?.let { return Result.failure(it) }
        photoUpdates += id to assetId
        val idx = inserted.indexOfFirst { it.id == id }
        if (idx >= 0) {
            inserted[idx] = inserted[idx].copy(photoUrl = assetId)
        }
        return Result.success(Unit)
    }
}

/**
 * Adapter Lost/Found → M05. REAL_REMOTE cuando el gateway M05 está cableado.
 */
internal interface LostFoundMediaUploadGateway {
    val writeMode: LostFoundMediaWriteCapability
    suspend fun uploadForCase(
        caseId: String,
        actorUserId: String,
        file: com.comunidapp.shared.poc.m08.model.FileRef
    ): Result<String>
}

internal enum class LostFoundMediaWriteCapability {
    REAL_REMOTE,
    PARTIAL,
    UNAVAILABLE
}

internal class M05BackedLostFoundMediaUploadGateway(
    private val m05: com.comunidapp.shared.media.M05MediaUploadGateway
) : LostFoundMediaUploadGateway {
    override val writeMode: LostFoundMediaWriteCapability =
        LostFoundMediaWriteCapability.REAL_REMOTE

    override suspend fun uploadForCase(
        caseId: String,
        actorUserId: String,
        file: com.comunidapp.shared.poc.m08.model.FileRef
    ): Result<String> =
        m05.uploadLostFoundMedia(
            com.comunidapp.shared.media.M05MediaUploadRequest(
                caseId = caseId,
                actorUserId = actorUserId,
                file = file
            )
        )
}

internal class PartialLostFoundMediaUploadGateway : LostFoundMediaUploadGateway {
    override val writeMode: LostFoundMediaWriteCapability = LostFoundMediaWriteCapability.PARTIAL
    override suspend fun uploadForCase(
        caseId: String,
        actorUserId: String,
        file: com.comunidapp.shared.poc.m08.model.FileRef
    ): Result<String> =
        Result.failure(IllegalStateException("MEDIA_UPLOAD_UNAVAILABLE"))
}

internal class FakeLostFoundMediaUploadGateway(
    var succeedWithAssetId: String? = null,
    var error: Throwable? = IllegalStateException("MEDIA_UPLOAD_FAILED"),
    var calls: Int = 0
) : LostFoundMediaUploadGateway {
    override val writeMode: LostFoundMediaWriteCapability =
        if (succeedWithAssetId != null) LostFoundMediaWriteCapability.REAL_REMOTE
        else LostFoundMediaWriteCapability.PARTIAL

    override suspend fun uploadForCase(
        caseId: String,
        actorUserId: String,
        file: com.comunidapp.shared.poc.m08.model.FileRef
    ): Result<String> {
        calls++
        succeedWithAssetId?.let { return Result.success(it) }
        return Result.failure(error ?: IllegalStateException("MEDIA_UPLOAD_FAILED"))
    }
}
