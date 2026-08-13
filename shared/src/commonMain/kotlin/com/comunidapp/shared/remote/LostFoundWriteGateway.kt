package com.comunidapp.shared.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Insert productivo Android: `lost_found_posts` (sin RPC).
 * Media M05 no vive aquí — KMP-8 MEDIA_WRITE = PARTIAL.
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

internal interface LostFoundWriteGateway {
    suspend fun insert(command: LostFoundInsertCommand): Result<String>
    suspend fun fetchPublicCode(id: String): Result<String?>
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
}

internal class FakeLostFoundWriteGateway(
    var insertError: Throwable? = null,
    var inserted: MutableList<LostFoundInsertRow> = mutableListOf(),
    var publicCodes: MutableMap<String, String?> = mutableMapOf(),
    var insertCalls: Int = 0,
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
}

/**
 * Upload M05 no portado a shared — nunca reporta éxito falso.
 */
internal interface LostFoundMediaUploadGateway {
    val writeMode: LostFoundMediaWriteCapability
    suspend fun uploadForCase(caseId: String, platformIdentifier: String): Result<String>
}

internal enum class LostFoundMediaWriteCapability {
    REAL_REMOTE,
    PARTIAL,
    UNAVAILABLE
}

internal class PartialLostFoundMediaUploadGateway : LostFoundMediaUploadGateway {
    override val writeMode: LostFoundMediaWriteCapability = LostFoundMediaWriteCapability.PARTIAL
    override suspend fun uploadForCase(caseId: String, platformIdentifier: String): Result<String> =
        Result.failure(IllegalStateException("MEDIA_UPLOAD_PARTIAL_M05_NOT_IN_SHARED"))
}

internal class FakeLostFoundMediaUploadGateway(
    var succeedWithAssetId: String? = null,
    var error: Throwable? = IllegalStateException("MEDIA_UPLOAD_PARTIAL_M05_NOT_IN_SHARED")
) : LostFoundMediaUploadGateway {
    override val writeMode: LostFoundMediaWriteCapability =
        if (succeedWithAssetId != null) LostFoundMediaWriteCapability.REAL_REMOTE
        else LostFoundMediaWriteCapability.PARTIAL

    override suspend fun uploadForCase(caseId: String, platformIdentifier: String): Result<String> {
        succeedWithAssetId?.let { return Result.success(it) }
        return Result.failure(error ?: IllegalStateException("MEDIA_UPLOAD_FAILED"))
    }
}
