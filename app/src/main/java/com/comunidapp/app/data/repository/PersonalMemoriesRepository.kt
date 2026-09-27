package com.comunidapp.app.data.repository

import com.comunidapp.app.data.files.SignedUrlMintCoordinator
import com.comunidapp.app.data.remote.supabase.m08.M08RpcDecoding
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class PersonalMemoryPetGroup(
    val petId: String?,
    val petName: String,
    val memoryCount: Int,
    val photoCount: Int,
    val videoCount: Int,
    val coverAssetId: String?,
    val coverMimeType: String?,
    val lastMemoryAtEpochMs: Long,
    val canOpenPet: Boolean,
    val coverPreviewUrl: String? = null
)

data class PersonalMemory(
    val memoryId: String,
    val assetId: String,
    val mediaAssetIds: List<String>,
    val mimeType: String,
    val mediaMimeTypes: List<String>,
    val createdAtEpochMs: Long,
    val occurredAtEpochMs: Long,
    val petId: String?,
    val petName: String?,
    val caption: String?,
    val sharedWithVitaCora: Boolean,
    val canOpenPet: Boolean,
    val sourceSocialPostId: String? = null,
    val previewUrl: String? = null,
    val previewUrls: Map<String, String?> = emptyMap()
) {
    val isVideo: Boolean
        get() = mimeType.startsWith("video/", ignoreCase = true) ||
            mediaMimeTypes.any { it.startsWith("video/", ignoreCase = true) }

    val displayAtEpochMs: Long
        get() = if (occurredAtEpochMs > 0L) occurredAtEpochMs else createdAtEpochMs

    val coverPreviewUrl: String?
        get() = previewUrl ?: previewUrls[assetId] ?: mediaAssetIds.firstOrNull()?.let { previewUrls[it] }
}

data class PersonalMemoryPage(
    val items: List<PersonalMemory>,
    val nextCursorCreatedAt: String?,
    val nextCursorId: String?,
    val hasMore: Boolean
)

@Serializable
private data class PersonalMemoryPetGroupRow(
    @SerialName("pet_id") val petId: String? = null,
    @SerialName("pet_name") val petName: String? = null,
    @SerialName("memory_count") val memoryCount: Int = 0,
    @SerialName("photo_count") val photoCount: Int = 0,
    @SerialName("video_count") val videoCount: Int = 0,
    @SerialName("cover_asset_id") val coverAssetId: String? = null,
    @SerialName("cover_mime_type") val coverMimeType: String? = null,
    @SerialName("last_memory_at") val lastMemoryAt: String? = null,
    @SerialName("can_open_pet") val canOpenPet: Boolean = false
)

@Serializable
private data class PersonalMemoryRow(
    @SerialName("memory_id") val memoryId: String? = null,
    @SerialName("source_social_post_id") val sourceSocialPostId: String? = null,
    @SerialName("asset_id") val assetId: String? = null,
    @SerialName("cover_asset_id") val coverAssetId: String? = null,
    @SerialName("media_asset_ids") val mediaAssetIds: List<String>? = null,
    @SerialName("media_mime_types") val mediaMimeTypes: List<String>? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("cover_mime_type") val coverMimeType: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("occurred_at") val occurredAt: String? = null,
    @SerialName("pet_id") val petId: String? = null,
    @SerialName("pet_name") val petName: String? = null,
    val caption: String? = null,
    @SerialName("shared_with_vitacora") val sharedWithVitaCora: Boolean = false,
    @SerialName("can_open_pet") val canOpenPet: Boolean = false
)

class PersonalMemoriesRepository {
    suspend fun listPetGroups(): List<PersonalMemoryPetGroup> {
        val element: JsonElement = supabase.postgrest.rpc(
            CanonicalBackend.RPC_LIST_MY_MEMORY_PETS
        ).decodeAs()
        return M08RpcDecoding.decodeRows<PersonalMemoryPetGroupRow>(element).map { row ->
            PersonalMemoryPetGroup(
                petId = row.petId?.takeIf { it.isNotBlank() },
                petName = row.petName?.trim().orEmpty().ifBlank {
                    if (row.petId.isNullOrBlank()) "Otros recuerdos" else "Mascota"
                },
                memoryCount = row.memoryCount,
                photoCount = row.photoCount,
                videoCount = row.videoCount,
                coverAssetId = row.coverAssetId?.takeIf { it.isNotBlank() },
                coverMimeType = row.coverMimeType,
                lastMemoryAtEpochMs = row.lastMemoryAt.toEpochMillis() ?: 0L,
                canOpenPet = row.canOpenPet
            )
        }
    }

    suspend fun listForPet(
        petId: String?,
        limit: Int = CanonicalBackend.MEMORY_PAGE_LIMIT,
        cursorCreatedAt: String? = null,
        cursorId: String? = null
    ): PersonalMemoryPage {
        val element: JsonElement = supabase.postgrest.rpc(
            CanonicalBackend.RPC_LIST_MY_PERSONAL_MEMORIES,
            buildJsonObject {
                if (petId.isNullOrBlank()) {
                    put("p_pet_id", JsonNull)
                } else {
                    put("p_pet_id", petId)
                }
                put("p_limit", limit)
                if (cursorCreatedAt.isNullOrBlank() || cursorId.isNullOrBlank()) {
                    put("p_cursor_created_at", JsonNull)
                    put("p_cursor_id", JsonNull)
                } else {
                    put("p_cursor_created_at", cursorCreatedAt)
                    put("p_cursor_id", cursorId)
                }
            }
        ).decodeAs()
        val rows = M08RpcDecoding.decodeRows<PersonalMemoryRow>(element)
        val items = rows.map { row ->
            val created = row.createdAt.toEpochMillis() ?: 0L
            val occurred = row.occurredAt.toEpochMillis() ?: created
            val assets = row.mediaAssetIds?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                .ifEmpty { listOfNotNull(row.coverAssetId ?: row.assetId) }
            val mimes = row.mediaMimeTypes.orEmpty().ifEmpty {
                listOfNotNull(row.coverMimeType ?: row.mimeType)
            }
            val cover = row.coverAssetId ?: row.assetId ?: assets.firstOrNull().orEmpty()
            PersonalMemory(
                memoryId = row.memoryId?.takeIf { it.isNotBlank() } ?: cover,
                assetId = cover,
                mediaAssetIds = assets.ifEmpty { listOfNotNull(cover.takeIf { it.isNotEmpty() }) },
                mimeType = row.coverMimeType ?: row.mimeType.orEmpty(),
                mediaMimeTypes = mimes,
                createdAtEpochMs = created,
                occurredAtEpochMs = occurred,
                petId = row.petId,
                petName = row.petName,
                caption = row.caption?.trim()?.takeIf { it.isNotEmpty() },
                sharedWithVitaCora = row.sharedWithVitaCora,
                canOpenPet = row.canOpenPet,
                sourceSocialPostId = row.sourceSocialPostId
            )
        }
        val last = rows.lastOrNull()
        val hasMore = rows.size >= limit
        return PersonalMemoryPage(
            items = items,
            nextCursorCreatedAt = if (hasMore) {
                last?.occurredAt ?: last?.createdAt
            } else {
                null
            },
            nextCursorId = if (hasMore) {
                last?.memoryId ?: last?.coverAssetId ?: last?.assetId
            } else {
                null
            },
            hasMore = hasMore
        )
    }

    suspend fun previewUrl(assetId: String): String? =
        SignedUrlMintCoordinator.mint(assetId)

    suspend fun previewUrls(assetIds: List<String>): Map<String, String?> =
        SignedUrlMintCoordinator.mintMany(assetIds)
}

private fun String?.toEpochMillis(): Long? {
    val raw = this?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrNull()
        ?: runCatching {
            java.time.LocalDate.parse(raw.take(10))
                .atStartOfDay(java.time.ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
}
