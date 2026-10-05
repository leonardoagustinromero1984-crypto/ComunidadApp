package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.ChatContextType
import com.comunidapp.app.data.model.ChatMessage
import com.comunidapp.app.data.model.Conversation
import com.comunidapp.app.data.model.FeedMediaAvailability
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.LocationLevel
import com.comunidapp.app.data.model.LocationNode
import com.comunidapp.app.data.model.argentinaLocationSeed
import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.LostFoundStatus
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.PostComment
import com.comunidapp.app.data.model.PostType
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m08.M08RpcDecoding
import com.comunidapp.app.data.remote.supabase.m09.CreateAdoptionParams
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionErrorMapper
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionException
import com.comunidapp.app.data.remote.supabase.m09.UpdateAdoptionParams
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.canonical.CanonicalMedia
import com.comunidapp.app.domain.chat.ChatMessageMerge
import com.comunidapp.app.domain.files.authorization.FileAuthContext
import com.comunidapp.app.domain.user.ProfileAvatarResolver
import com.comunidapp.app.domain.user.SessionBoundState
import com.comunidapp.app.domain.user.SessionEpoch
import com.comunidapp.app.domain.user.SessionGeneration
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.time.Instant

@Serializable
private data class CanonicalLostFoundRow(
    val id: String,
    val kind: String,
    val status: String,
    @SerialName("public_code") val publicCode: String? = null,
    @SerialName("pet_id") val petId: String? = null,
    @SerialName("pet_name") val petName: String? = null,
    val species: String? = null,
    @SerialName("locality_id") val localityId: String? = null,
    @SerialName("location_label") val locationLabel: String? = null,
    val note: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("can_claim") val canClaim: Boolean? = null,
    @SerialName("claimed_by") val claimedBy: String? = null,
    @SerialName("incident_at") val incidentAt: String? = null,
    val sex: String? = null,
    val size: String? = null,
    @SerialName("is_custodian") val isCustodian: Boolean? = null
)

@Serializable
private data class CanonicalFoundMatchRow(
    val id: String,
    @SerialName("lost_alert_id") val lostAlertId: String? = null,
    @SerialName("asserted_by") val assertedBy: String? = null,
    val status: String,
    val score: Double? = null,
    @SerialName("match_reason") val matchReason: String? = null
)

@Serializable
private data class CanonicalAdoptionRow(
    val id: String,
    @SerialName("pet_id") val petId: String? = null,
    @SerialName("public_code") val publicCode: String? = null,
    val status: String,
    val note: String? = null,
    val title: String? = null,
    val description: String? = null,
    @SerialName("location_text") val locationText: String? = null,
    @SerialName("accepts_children") val acceptsChildren: Boolean? = null,
    @SerialName("accepts_other_dogs") val acceptsOtherDogs: Boolean? = null,
    @SerialName("accepts_cats") val acceptsCats: Boolean? = null,
    @SerialName("needs_outdoor_space") val needsOutdoorSpace: Boolean? = null,
    @SerialName("needs_secure_enclosure") val needsSecureEnclosure: Boolean? = null,
    @SerialName("requires_escape_protection") val requiresEscapeProtection: Boolean? = null,
    @SerialName("requires_landlord_pet_permission") val requiresLandlordPetPermission: Boolean? = null,
    @SerialName("accepts_other_animals") val acceptsOtherAnimals: Boolean? = null,
    @SerialName("max_hours_alone") val maxHoursAlone: String? = null,
    @SerialName("max_hours_from") val maxHoursFrom: Int? = null,
    @SerialName("max_hours_to") val maxHoursTo: Int? = null,
    @SerialName("experience_required") val experienceRequired: String? = null,
    @SerialName("accepts_no_experience") val acceptsNoExperience: Boolean? = null,
    @SerialName("requires_special_care_experience") val requiresSpecialCareExperience: Boolean? = null,
    @SerialName("published_by") val publishedBy: String? = null,
    @SerialName("organization_id") val organizationId: String? = null,
    val name: String? = null,
    val species: String? = null,
    val sex: String? = null,
    val size: String? = null,
    @SerialName("locality_id") val localityId: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
private data class OwnReelIdRow(
    val id: String
)

@Serializable
private data class CanonicalSocialPostRow(
    val id: String,
    @SerialName("author_user_id") val authorUserId: String? = null,
    @SerialName("author_name") val authorName: String? = null,
    val body: String? = null,
    val visibility: String? = null,
    @SerialName("content_kind") val contentKind: String? = null,
    @SerialName("media_asset_id") val mediaAssetId: String? = null,
    @SerialName("media_bucket") val mediaBucket: String? = null,
    @SerialName("media_path") val mediaPath: String? = null,
    @SerialName("media_mime") val mediaMime: String? = null,
    @SerialName("pet_id") val petId: String? = null,
    @SerialName("pet_ids") val petIds: JsonElement? = null,
    @SerialName("pet_names") val petNames: JsonElement? = null,
    @SerialName("locality_id") val localityId: String? = null,
    val composition: JsonElement? = null,
    @SerialName("extra_media") val extraMedia: JsonElement? = null,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
private data class CanonicalFeedExtraMediaRow(
    val bucket: String? = null,
    val path: String? = null
)

@Serializable
private data class CanonicalStoryRow(
    val id: String,
    @SerialName("author_user_id") val authorUserId: String? = null,
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("author_username") val authorUsername: String? = null,
    @SerialName("asset_id") val assetId: String? = null,
    @SerialName("media_bucket") val mediaBucket: String? = null,
    @SerialName("media_path") val mediaPath: String? = null,
    @SerialName("media_mime") val mediaMime: String? = null,
    val caption: String? = null,
    @SerialName("pet_id") val petId: String? = null,
    @SerialName("locality_id") val localityId: String? = null,
    val composition: JsonElement? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null
)

@Serializable
private data class CanonicalSocialCommentRow(
    val id: String,
    @SerialName("post_id") val postId: String? = null,
    @SerialName("author_user_id") val authorUserId: String? = null,
    @SerialName("author_name") val authorName: String? = null,
    val body: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
private data class CanonicalSharePersonRow(
    @SerialName("user_id") val userId: String,
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null
)

@Serializable
private data class CanonicalConversationRow(
    val id: String,
    @SerialName("subject_kind") val subjectKind: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("peer_user_id") val peerUserId: String? = null,
    @SerialName("peer_name") val peerName: String? = null,
    @SerialName("peer_username") val peerUsername: String? = null,
    @SerialName("last_message_text") val lastMessageText: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null
)

@Serializable
private data class CanonicalMessageRow(
    val id: String,
    @SerialName("actor_user_id") val actorUserId: String? = null,
    val body: String? = null,
    val payload: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
private data class CanonicalLocationNodeRow(
    val id: String,
    val kind: String,
    @SerialName("parent_id") val parentId: String? = null,
    val name: String,
    @SerialName("iso_code") val isoCode: String? = null,
    @SerialName("sort_key") val sortKey: Int = 0,
    val active: Boolean = true
)

internal suspend inline fun <reified T : Any> rpcRows(function: String, params: kotlinx.serialization.json.JsonObject? = null): List<T> {
    val element: JsonElement = if (params == null) {
        supabase.postgrest.rpc(function).decodeAs()
    } else {
        supabase.postgrest.rpc(function, params).decodeAs()
    }
    return M08RpcDecoding.decodeRows(element)
}

internal fun parseEpoch(value: String?): Long? =
    value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

class CanonicalLostFoundRepository(
    private val epoch: SessionEpoch = SessionGeneration
) : LostFoundRepository {
    private val posts = SessionBoundState(emptyList<LostFoundPost>(), epoch)

    override fun observeLostFoundPosts(): StateFlow<List<LostFoundPost>> = posts.state

    override fun clearAccountCache() {
        epoch.rotate { posts.reset(emptyList()) }
    }

    override fun getFilteredLostFound(
        type: LostFoundType?,
        species: PetSpecies?,
        location: String?,
        status: LostFoundStatus?
    ): List<LostFoundPost> = posts.value.filter { post ->
        (type == null || post.type == type) &&
            (species == null || post.species == species) &&
            (location.isNullOrBlank() || post.location.contains(location, ignoreCase = true)) &&
            (status == null || post.status == status)
    }

    override suspend fun addLostFoundPost(post: LostFoundPost): Result<String> = runCatching {
        val kind = post.type.name
        val petId = if (kind == "FOUND") null else post.petId?.takeIf { it.isNotBlank() }
        val result = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_CREATE_LOST_FOUND,
            parameters = buildJsonObject {
                put("p_kind", kind)
                if (!petId.isNullOrBlank()) put("p_pet_id", petId) else put("p_pet_id", JsonNull)
                val humanLocation = com.comunidapp.app.domain.lostfound.LostFoundLocationDisplay.humanLabel(
                    locationLabel = post.location
                )
                val localityId = com.comunidapp.app.domain.lostfound.LostFoundCreatePayload
                    .locationNodeIdOrNull(post.location)
                if (localityId != null) put("p_locality_id", localityId) else put("p_locality_id", JsonNull)
                if (humanLocation.isNotBlank()) put("p_location_label", humanLocation)
                put("p_species", post.species.name)
                put(
                    "p_note",
                    com.comunidapp.app.domain.lostfound.LostFoundLocationDisplay.noteWithoutPlaceholder(
                        listOf(humanLocation, post.description).filter { it.isNotBlank() }.joinToString(" · ")
                    )
                )
                if (post.latitude != null) put("p_lat", post.latitude) else put("p_lat", JsonNull)
                if (post.longitude != null) put("p_lng", post.longitude) else put("p_lng", JsonNull)
                if (kind == "FOUND") {
                    put("p_name", post.petName?.trim().orEmpty())
                    put("p_sex", post.sex?.name ?: "UNKNOWN")
                    put("p_size", post.size?.name ?: "UNKNOWN")
                    if (post.estimatedAgeMonths != null) {
                        put("p_estimated_age_months", post.estimatedAgeMonths)
                    } else {
                        put("p_estimated_age_months", JsonNull)
                    }
                    val photo = post.photoUrl?.trim().orEmpty()
                    if (com.comunidapp.app.domain.lostfound.LostFoundCreatePayload.isMediaAssetId(photo)) {
                        put("p_photo_asset_id", photo)
                    } else {
                        put("p_photo_asset_id", JsonNull)
                    }
                }
            }
        )
        val id = com.comunidapp.app.data.remote.supabase.m08.CanonCreatePetUuid
            .decode(result.data, CanonicalBackend.RPC_CREATE_LOST_FOUND)
            .id
            ?: throw IllegalStateException("LF-CREATE-ALERT")
        runCatching { refresh() }
        id
    }

    override suspend fun updateLostFoundPost(post: LostFoundPost): Result<Unit> {
        val photo = post.photoUrl?.trim().orEmpty()
        if (photo.isNotBlank()) {
            attachLostFoundPhoto(post.id, photo).getOrThrow()
        }
        return if (post.status == LostFoundStatus.RESOLVED) updateStatus(post.id, LostFoundStatus.RESOLVED)
        else Result.success(Unit)
    }

    override suspend fun attachLostFoundPhoto(alertId: String, assetId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_ATTACH_LOST_FOUND_PHOTO,
            parameters = buildJsonObject {
                put("p_id", alertId)
                put("p_photo_asset_id", assetId)
            }
        )
    }

    override suspend fun assertFoundMightBeMine(foundId: String, lostId: String): Result<String> = runCatching {
        val id: String = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_ASSERT_FOUND_MIGHT_BE_MINE,
            parameters = buildJsonObject {
                put("p_found_id", foundId)
                put("p_lost_id", lostId)
            }
        ).decodeAs()
        id
    }

    override suspend fun listFoundMatchCandidates(foundId: String): Result<List<LostFoundMatchCandidate>> = runCatching {
        rpcRows<CanonicalFoundMatchRow>(
            CanonicalBackend.RPC_LIST_FOUND_MATCH_CANDIDATES,
            buildJsonObject { put("p_found_id", foundId) }
        ).map { row ->
            LostFoundMatchCandidate(
                id = row.id,
                lostAlertId = row.lostAlertId,
                assertedBy = row.assertedBy,
                status = row.status,
                score = row.score,
                matchReason = row.matchReason
            )
        }
    }

    override suspend fun confirmFoundOwnerMatch(candidateId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_CONFIRM_FOUND_OWNER_MATCH,
            parameters = buildJsonObject { put("p_candidate_id", candidateId) }
        )
        refresh()
    }

    override suspend fun rejectFoundMightBeMine(foundId: String, lostId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_REJECT_FOUND_MIGHT_BE_MINE,
            parameters = buildJsonObject {
                put("p_found_id", foundId)
                put("p_lost_id", lostId)
            }
        )
    }

    override suspend fun rejectFoundOwnerMatch(candidateId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_REJECT_FOUND_OWNER_MATCH,
            parameters = buildJsonObject { put("p_candidate_id", candidateId) }
        )
        refresh()
    }

    override suspend fun refreshAlerts(): Result<Unit> = runCatching { refresh() }

    override suspend fun markLostFoundInCare(alertId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_MARK_LOST_FOUND_IN_CARE,
            parameters = buildJsonObject { put("p_id", alertId) }
        )
        refresh()
    }

    override suspend fun updateStatus(id: String, status: LostFoundStatus): Result<Unit> = runCatching {
        if (status == LostFoundStatus.RESOLVED) {
            supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_RESOLVE_LOST_FOUND,
                parameters = buildJsonObject { put("p_id", id) }
            )
        }
        refresh()
    }

    override suspend fun claimLostFound(alertId: String): Result<LostFoundClaimResult> = runCatching {
        val claimed = claimAlert(alertId).getOrThrow()
        refresh()
        claimed
    }

    suspend fun refresh() {
        val token = epoch.current()
        val rows = rpcRows<CanonicalLostFoundRow>(CanonicalBackend.RPC_LIST_LOST_FOUND)
            .filter { row ->
                com.comunidapp.app.domain.lostfound.CanonLostFoundListRule.include(
                    status = row.status.orEmpty(),
                    caseCreatedAtEpochMs = parseEpoch(row.createdAt) ?: 0L,
                    viewerAccountCreatedAtEpochMs = 0L
                )
            }
        val mapped = rows.map { row ->
            LostFoundPost(
                id = row.id,
                authorId = row.createdBy.orEmpty(),
                authorName = "",
                type = if (row.kind.equals("FOUND", true)) LostFoundType.FOUND else LostFoundType.LOST,
                petName = row.petName,
                species = PetSpecies.fromString(row.species),
                location = com.comunidapp.app.domain.lostfound.LostFoundLocationDisplay.visibleOrNearby(
                    locationLabel = row.locationLabel,
                    localityId = row.localityId,
                    note = row.note
                ),
                description = com.comunidapp.app.domain.lostfound.LostFoundLocationDisplay.noteWithoutPlaceholder(row.note),
                contactInfo = "",
                status = LostFoundStatus.fromString(row.status),
                publicCode = row.publicCode,
                date = row.createdAt.orEmpty(),
                createdAt = parseEpoch(row.createdAt),
                petId = row.petId,
                canClaim = row.canClaim == true,
                claimedBy = row.claimedBy,
                sex = row.sex?.let { runCatching { PetSex.valueOf(it) }.getOrNull() },
                size = row.size?.let { runCatching { PetSize.valueOf(it) }.getOrNull() },
                isCustodian = row.isCustodian == true
            )
        }
        posts.tryWrite(token, mapped)
    }

    companion object {
        suspend fun recordLocationConsent(): Result<Unit> = runCatching {
            supabase.postgrest.rpc(function = CanonicalBackend.RPC_RECORD_LOCATION_CONSENT)
        }

        suspend fun setReceiveNearbyCases(receive: Boolean): Result<Unit> = runCatching {
            supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_SET_RECEIVE_NEARBY_CASES,
                parameters = buildJsonObject { put("p_receive", receive) }
            )
        }

        suspend fun upsertResponderBase(
            lat: Double,
            lng: Double,
            organizationId: String? = null,
            address: String? = null
        ): Result<Unit> = runCatching {
            supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_UPSERT_RESPONDER_BASE,
                parameters = buildJsonObject {
                    put("p_lat", lat)
                    put("p_lng", lng)
                    if (!organizationId.isNullOrBlank()) put("p_organization_id", organizationId)
                    else put("p_organization_id", JsonNull)
                    put("p_receive", true)
                    if (!address.isNullOrBlank()) put("p_address", address) else put("p_address", JsonNull)
                }
            )
        }

        suspend fun getMyResponderBase(organizationId: String? = null): Result<ResponderBaseSnapshot> = runCatching {
            val element: JsonElement = supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_GET_MY_RESPONDER_BASE,
                parameters = buildJsonObject {
                    if (!organizationId.isNullOrBlank()) put("p_organization_id", organizationId)
                    else put("p_organization_id", JsonNull)
                }
            ).decodeAs()
            val obj = (element as? JsonObject)?.let { root ->
                root["canon_get_my_responder_base"] as? JsonObject ?: root
            }
            fun flag(key: String): Boolean {
                val raw = obj?.get(key) as? JsonPrimitive ?: return false
                return raw.content.equals("true", ignoreCase = true)
            }
            fun num(key: String): Double? =
                obj?.get(key)?.let { (it as? JsonPrimitive)?.contentOrNull }?.toDoubleOrNull()
            ResponderBaseSnapshot(
                hasBaseLocation = flag("has_base_location") || (num("lat") != null && num("lng") != null),
                latitude = num("lat"),
                longitude = num("lng"),
                address = obj?.get("address")?.let { (it as? JsonPrimitive)?.contentOrNull }
            )
        }

        suspend fun claimAlert(alertId: String): Result<LostFoundClaimResult> = runCatching {
            runCatching {
                supabase.postgrest.rpc(
                    function = CanonicalBackend.RPC_REGISTER_LOST_FOUND_CLAIM_ATTEMPT,
                    parameters = buildJsonObject { put("p_id", alertId) }
                )
            }
            val element: JsonElement = supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_CLAIM_LOST_FOUND,
                parameters = buildJsonObject { put("p_id", alertId) }
            ).decodeAs()
            val obj = element as? kotlinx.serialization.json.JsonObject
            LostFoundClaimResult(
                alertId = obj?.get("id")?.let { (it as? JsonPrimitive)?.contentOrNull } ?: alertId,
                petId = obj?.get("pet_id")?.let { (it as? JsonPrimitive)?.contentOrNull },
                status = obj?.get("status")?.let { (it as? JsonPrimitive)?.contentOrNull } ?: "CLAIMED"
            )
        }.recoverCatching { error ->
            val message = error.message.orEmpty()
            if (message.contains("ALERT_ALREADY_CLAIMED", ignoreCase = true) ||
                message.contains("ALERT_CLAIM_NOT_NEAREST", ignoreCase = true)
            ) {
                LostFoundClaimResult(alertId, null, "CLAIMED", alreadyTaken = true)
            } else {
                throw error
            }
        }
    }
}

class CanonicalAdoptionRepository(
    private val epoch: SessionEpoch = SessionGeneration
) : AdoptionRepository {
    private val posts = SessionBoundState(emptyList<AdoptionPost>(), epoch)

    override fun observeAdoptionPosts(): StateFlow<List<AdoptionPost>> = posts.state

    override fun clearAccountCache() {
        epoch.rotate { posts.reset(emptyList()) }
    }

    override fun observePublishedAdoptions(): Flow<List<AdoptionPost>> =
        posts.state.map { list -> list.filter { it.status == AdoptionStatus.PUBLISHED } }

    override suspend fun refreshPublished(): Result<Unit> = runCatching { refresh() }

    override fun observeMyAdoptions(publisherId: String): Flow<List<AdoptionPost>> =
        posts.state.map { list ->
            if (publisherId.isBlank()) list else list.filter { it.publisherId == publisherId }
        }

    override fun getAdoptionPostById(id: String): AdoptionPost? = posts.value.firstOrNull { it.id == id }

    override suspend fun getAdoptionById(id: String): Result<AdoptionPost> {
        refresh()
        return getAdoptionPostById(id)?.let { Result.success(it) }
            ?: Result.failure(M09AdoptionException("ADOPTION_NOT_FOUND", M09AdoptionErrorMapper.userMessage("ADOPTION_NOT_FOUND")))
    }

    override fun getFilteredAdoptions(
        location: String?,
        sex: PetSex?,
        minAge: Int?,
        maxAge: Int?,
        size: PetSize?,
        status: AdoptionStatus?
    ): List<AdoptionPost> = posts.value.filter { post ->
        (location.isNullOrBlank() || post.location.contains(location, ignoreCase = true)) &&
            (sex == null || post.sex == sex) &&
            (size == null || post.size == size) &&
            (status == null || post.status == status)
    }

    override fun getAdoptionsByShelter(shelterId: String): List<AdoptionPost> =
        posts.value.filter { it.shelterId == shelterId || it.publisherOrganizationId == shelterId }

    override fun getAdoptionsByOrganization(organizationId: String): List<AdoptionPost> =
        posts.value.filter { it.publisherOrganizationId == organizationId }

    override suspend fun addAdoptionPost(post: AdoptionPost): Result<String> =
        createAdoption(
            CreateAdoptionParams(
                petId = post.petId.orEmpty(),
                title = post.displayTitle,
                description = post.description,
                requirements = post.requirements,
                locationText = post.location,
                publish = true,
                matchRequirements = post.matchRequirements
            )
        ).map { it.id }

    override suspend fun updateAdoptionPost(post: AdoptionPost): Result<Unit> = Result.success(Unit)

    override suspend fun updateAdoptionStatus(id: String, status: AdoptionStatus): Result<Unit> =
        when (status) {
            AdoptionStatus.PAUSED -> pauseAdoption(id).map { }
            AdoptionStatus.PUBLISHED -> resumeAdoption(id).map { }
            AdoptionStatus.CLOSED, AdoptionStatus.ADOPTED -> closeAdoption(id).map { }
            AdoptionStatus.DRAFT -> Result.success(Unit)
        }

    override suspend fun createAdoption(params: CreateAdoptionParams): Result<AdoptionPost> = runCatching {
        val id: String = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_CREATE_ADOPTION,
            parameters = buildJsonObject {
                put("p_pet_id", params.petId)
                params.organizationId?.let { put("p_organization_id", it) }
                put("p_note", params.requirements)
                put("p_title", params.title)
                put("p_description", params.description)
                put("p_location_text", params.locationText)
                put("p_accepts_children", params.matchRequirements.acceptsChildren)
                put("p_accepts_other_dogs", params.matchRequirements.acceptsOtherDogs)
                put("p_accepts_cats", params.matchRequirements.acceptsCats)
                put("p_needs_outdoor_space", params.matchRequirements.needsOutdoorSpace)
                put("p_needs_secure_enclosure", params.matchRequirements.needsSecureEnclosure)
                put("p_requires_escape_protection", params.matchRequirements.requiresEscapeProtection)
                put("p_requires_landlord_pet_permission", params.matchRequirements.requiresLandlordPetPermission)
                put("p_accepts_other_animals", params.matchRequirements.acceptsOtherAnimals)
                put("p_max_hours_alone", params.matchRequirements.maxHoursAlone?.legacyBandToken())
                put("p_max_hours_from", params.matchRequirements.maxHoursAlone?.fromHours)
                put("p_max_hours_to", params.matchRequirements.maxHoursAlone?.toHours)
                put("p_experience_required", params.matchRequirements.experienceRequired?.name)
                put("p_accepts_no_experience", params.matchRequirements.acceptsNoExperience)
                put("p_requires_special_care_experience", params.matchRequirements.requiresSpecialCareExperience)
            }
        ).decodeAs()
        refresh()
        getAdoptionPostById(id) ?: AdoptionPost(
            id = id,
            petId = params.petId,
            shelterName = "",
            title = params.title,
            name = params.title,
            species = PetSpecies.OTHER,
            sex = PetSex.UNKNOWN,
            ageYears = 0,
            size = PetSize.MEDIUM,
            location = params.locationText,
            description = params.description
        )
    }

    override suspend fun updateAdoption(params: UpdateAdoptionParams): Result<AdoptionPost> =
        getAdoptionById(params.adoptionId)

    override suspend fun pauseAdoption(id: String): Result<AdoptionPost> = setStatus(id, "HIDDEN")

    override suspend fun resumeAdoption(id: String): Result<AdoptionPost> = setStatus(id, "OPEN")

    override suspend fun closeAdoption(id: String): Result<AdoptionPost> = setStatus(id, "CLOSED")

    override suspend fun markAsAdopted(id: String): Result<AdoptionPost> = closeAdoption(id)

    private suspend fun setStatus(id: String, status: String): Result<AdoptionPost> = runCatching {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_SET_ADOPTION_STATUS,
            parameters = buildJsonObject {
                put("p_id", id)
                put("p_status", status)
            }
        )
        refresh()
        getAdoptionPostById(id) ?: error("ADOPTION_NOT_FOUND")
    }

    suspend fun refresh() {
        val token = epoch.current()
        val rows = rpcRows<CanonicalAdoptionRow>(CanonicalBackend.RPC_LIST_ADOPTIONS)
        val mapped = rows.map { row ->
            val traits = com.comunidapp.app.domain.adoption.AdoptionGeneralProfileCodec.decodeRequirements(
                acceptsChildren = row.acceptsChildren,
                acceptsOtherDogs = row.acceptsOtherDogs,
                acceptsCats = row.acceptsCats,
                needsOutdoorSpace = row.needsOutdoorSpace,
                needsSecureEnclosure = row.needsSecureEnclosure,
                requiresEscapeProtection = row.requiresEscapeProtection,
                requiresLandlordPetPermission = row.requiresLandlordPetPermission,
                acceptsOtherAnimals = row.acceptsOtherAnimals,
                maxHoursAlone = row.maxHoursAlone,
                maxHoursFrom = row.maxHoursFrom,
                maxHoursTo = row.maxHoursTo,
                experienceRequired = row.experienceRequired,
                acceptsNoExperience = row.acceptsNoExperience,
                requiresSpecialCareExperience = row.requiresSpecialCareExperience,
                additionalNotes = if (row.description.isNullOrBlank()) null else row.note
            )
            val description = row.description?.takeIf { it.isNotBlank() } ?: row.note.orEmpty()
            val location = row.locationText?.takeIf { it.isNotBlank() } ?: row.localityId.orEmpty()
            AdoptionPost(
                id = row.id,
                petId = row.petId,
                publisherId = row.publishedBy,
                publisherOrganizationId = row.organizationId,
                shelterId = row.organizationId,
                shelterName = "",
                title = row.title?.takeIf { it.isNotBlank() } ?: row.name.orEmpty(),
                name = row.name.orEmpty(),
                species = PetSpecies.fromString(row.species),
                sex = runCatching { PetSex.valueOf(row.sex ?: "UNKNOWN") }.getOrDefault(PetSex.UNKNOWN),
                ageYears = 0,
                size = runCatching { PetSize.valueOf(row.size ?: "MEDIUM") }.getOrDefault(PetSize.MEDIUM),
                location = location,
                description = description,
                requirements = traits.additionalNotes.orEmpty(),
                matchRequirements = traits,
                status = when (row.status.uppercase()) {
                    "CLOSED" -> AdoptionStatus.CLOSED
                    "HIDDEN" -> AdoptionStatus.PAUSED
                    else -> AdoptionStatus.PUBLISHED
                },
                publicCode = row.publicCode,
                createdAt = parseEpoch(row.createdAt)
            )
        }
        posts.tryWrite(token, mapped)
    }
}

class CanonicalFeedRepository(
    private val epoch: SessionEpoch = SessionGeneration
) : FeedRepository {
    private val posts = SessionBoundState(emptyList<FeedPost>(), epoch)
    private val stories = SessionBoundState(emptyList<FeedPost>(), epoch)
    private val liked = SessionBoundState(emptySet<String>(), epoch)
    private val comments = SessionBoundState(emptyMap<String, List<PostComment>>(), epoch)
    @Volatile private var feedHasMore = false

    override fun observeFeedPosts(): StateFlow<List<FeedPost>> = posts.state
    override fun observeActiveStories(): StateFlow<List<FeedPost>> = stories.state
    override fun hasMorePosts(): Boolean = feedHasMore

    override fun clearAccountCache() {
        epoch.rotate {
            posts.reset(emptyList())
            stories.reset(emptyList())
            liked.reset(emptySet())
            comments.reset(emptyMap())
            feedHasMore = false
        }
    }

    override suspend fun refreshPosts(): Result<Unit> = runCatching { refresh(reset = true) }
    override suspend fun loadMorePosts(): Result<Unit> = runCatching {
        if (!feedHasMore) return@runCatching
        refresh(reset = false)
    }
    override suspend fun refreshStories(): Result<Unit> = runCatching { refreshStoriesInternal() }

    override suspend fun loadSavedPosts(): Result<List<FeedPost>> = runCatching {
        rpcRows<CanonicalSocialPostRow>(
            CanonicalBackend.RPC_LIST_SAVED_SOCIAL_POSTS,
            buildJsonObject {
                put("p_limit", CanonicalBackend.FEED_PAGE_LIMIT)
                put("p_cursor_created_at", JsonNull)
                put("p_cursor_id", JsonNull)
            }
        ).let { mapSocialPosts(it) }
    }

    override suspend fun ensureVisiblePost(postId: String): Result<FeedPost?> = runCatching {
        val token = epoch.current()
        // Never trust cache for access control: revoked visibility must drop last-good.
        val row = try {
            rpcRows<CanonicalSocialPostRow>(
                CanonicalBackend.RPC_GET_VISIBLE_SOCIAL_POST,
                buildJsonObject { put("p_post_id", postId) }
            ).firstOrNull()
        } catch (error: Throwable) {
            posts.tryUpdate(token) { current -> current.filterNot { it.id == postId } }
            throw error
        }
        if (row == null) {
            posts.tryUpdate(token) { current -> current.filterNot { it.id == postId } }
            return@runCatching null
        }
        val mapped = mapSocialPost(row)
        if (!posts.tryUpdate(token) { current -> listOf(mapped) + current.filterNot { it.id == mapped.id } }) {
            return@runCatching null
        }
        mapped
    }

    override suspend fun addFeedPost(post: FeedPost): Result<String> = runCatching {
        val token = epoch.current()
        val mediaId = post.mediaAssetId
            ?: post.imageUrl?.takeIf { !it.startsWith("http", ignoreCase = true) }
        val id = createSocialPost(post, mediaAssetId = mediaId, kind = "POST")
        val optimistic = post.copy(id = id)
        runCatching { refresh(reset = true) }
        posts.tryUpdate(token) { current ->
            com.comunidapp.app.domain.social.FeedAfterPublish.merge(current, optimistic)
        }
        id
    }

    override suspend fun addStory(post: FeedPost, mediaAssetId: String): Result<String> = runCatching {
        val id: String = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_CREATE_STORY,
            parameters = buildJsonObject {
                put("p_asset_id", mediaAssetId)
                put("p_caption", post.content)
                if (!post.petId.isNullOrBlank()) put("p_pet_id", post.petId) else put("p_pet_id", JsonNull)
                if (!post.localityId.isNullOrBlank()) put("p_locality_id", post.localityId) else put("p_locality_id", JsonNull)
                put("p_composition", Json.parseToJsonElement(post.compositionJson ?: "{}"))
            }
        ).decodeAs()
        refreshStoriesInternal()
        id
    }

    override suspend fun addReel(post: FeedPost, mediaAssetId: String): Result<String> = runCatching {
        createSocialPost(post, mediaAssetId = mediaAssetId, kind = "REEL")
    }

    override suspend fun findOwnReelIdByMediaAsset(assetId: String): Result<String?> = runCatching {
        if (assetId.isBlank()) return@runCatching null
        val result = supabase.from("social_posts")
            .select {
                filter {
                    eq("media_asset_id", assetId)
                    eq("content_kind", "REEL")
                }
            }
        val element = Json.parseToJsonElement(result.data)
        M08RpcDecoding.decodeRows<OwnReelIdRow>(element)
            .firstOrNull()
            ?.id
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    override suspend fun updateFeedPost(post: FeedPost): Result<Unit> = Result.success(Unit)

    override suspend fun toggleLike(postId: String, userId: String): Result<Boolean> = runCatching {
        val token = epoch.current()
        val likedNow: Boolean = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_REACT_SOCIAL_POST,
            parameters = buildJsonObject { put("p_post_id", postId) }
        ).decodeAs()
        liked.tryUpdate(token) { current -> if (likedNow) current + postId else current - postId }
        refresh(reset = true)
        likedNow
    }

    override fun observeLikedPostIds(userId: String): Flow<Set<String>> = liked.state

    override fun observeComments(postId: String): Flow<List<PostComment>> =
        comments.state.map { it[postId].orEmpty() }

    override suspend fun refreshComments(postId: String): Result<Unit> = runCatching {
        val token = epoch.current()
        val rows = rpcRows<CanonicalSocialCommentRow>(
            CanonicalBackend.RPC_LIST_SOCIAL_COMMENTS,
            buildJsonObject { put("p_post_id", postId) }
        )
        comments.tryUpdate(token) { current ->
            current + (postId to rows.map { row ->
                PostComment(
                    id = row.id,
                    postId = row.postId ?: postId,
                    authorId = row.authorUserId.orEmpty(),
                    authorName = row.authorName.orEmpty(),
                    content = row.body.orEmpty(),
                    createdAt = parseEpoch(row.createdAt),
                    parentId = row.parentId
                )
            })
        }
    }

    override suspend fun addComment(
        postId: String,
        authorId: String,
        authorName: String,
        content: String
    ): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_COMMENT_SOCIAL_POST,
            parameters = buildJsonObject {
                put("p_post_id", postId)
                put("p_body", content)
            }
        )
        refreshComments(postId)
        refresh(reset = true)
    }

    override suspend fun deleteOwnComment(commentId: String): Result<Unit> = runCatching {
        val token = epoch.current()
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_DELETE_OWN_COMMENT,
            parameters = buildJsonObject { put("p_comment_id", commentId) }
        )
        comments.tryUpdate(token) { current ->
            current.mapValues { (_, list) -> list.filterNot { it.id == commentId } }
        }
        refresh(reset = true)
    }

    override suspend fun searchPosts(query: String): List<FeedPost> {
        if (query.isBlank()) return emptyList()
        return posts.value.filter {
            it.title.contains(query, ignoreCase = true) || it.content.contains(query, ignoreCase = true)
        }
    }

    private suspend fun createSocialPost(post: FeedPost, mediaAssetId: String?, kind: String): String {
        val id: String = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_CREATE_SOCIAL_POST,
            parameters = buildJsonObject {
                put("p_body", com.comunidapp.app.domain.social.FeedBodyFormat.encode(post.title, post.content))
                put("p_visibility", post.visibility.toRpcValue())
                put("p_content_kind", kind)
                if (!mediaAssetId.isNullOrBlank()) put("p_media_asset_id", mediaAssetId) else put("p_media_asset_id", JsonNull)
                if (!post.petId.isNullOrBlank()) put("p_pet_id", post.petId) else put("p_pet_id", JsonNull)
                if (!post.localityId.isNullOrBlank()) put("p_locality_id", post.localityId) else put("p_locality_id", JsonNull)
                put("p_composition", Json.parseToJsonElement(post.compositionJson ?: "{}"))
            }
        ).decodeAs()
        return id
    }

    private suspend fun refresh(reset: Boolean) {
        val token = epoch.current()
        val cursor = if (reset) null else feedCursor()
        val rows = rpcRows<CanonicalSocialPostRow>(
            CanonicalBackend.RPC_LIST_SOCIAL_FEED,
            buildJsonObject {
                put("p_limit", CanonicalBackend.FEED_PAGE_LIMIT)
                if (cursor == null) {
                    put("p_cursor_created_at", JsonNull)
                    put("p_cursor_id", JsonNull)
                } else {
                    put("p_cursor_created_at", cursor.first)
                    put("p_cursor_id", cursor.second)
                }
            }
        )
        val mapped = mapSocialPosts(rows)
        val hasMore = rows.size >= CanonicalBackend.FEED_PAGE_LIMIT
        posts.tryUpdate(token) { current ->
            feedHasMore = hasMore
            if (reset) {
                mapped
            } else {
                val seen = current.map { it.id }.toHashSet()
                current + mapped.filter { it.id !in seen }
            }
        }
    }

    private fun feedCursor(): Pair<String, String>? {
        val last = posts.value.lastOrNull() ?: return null
        val createdAt = last.createdAt?.let { Instant.ofEpochMilli(it).toString() } ?: return null
        if (last.id.isBlank()) return null
        return createdAt to last.id
    }

    private suspend fun mapSocialPosts(rows: List<CanonicalSocialPostRow>): List<FeedPost> {
        if (rows.isEmpty()) return emptyList()
        return coroutineScope {
            val gate = Semaphore(6)
            rows.map { row ->
                async { gate.withPermit { mapSocialPost(row) } }
            }.awaitAll()
        }
    }

    private suspend fun mapSocialPost(row: CanonicalSocialPostRow): FeedPost {
        val kind = row.contentKind.orEmpty().uppercase()
        val (title, content) = com.comunidapp.app.domain.social.FeedBodyFormat.decode(row.body.orEmpty())
        val compositionJson = row.composition?.toString()
        val actorUserId = AuthProvider.repository.getCurrentUser()?.id
        val primaryAssetId = row.mediaAssetId?.trim()?.takeIf { it.isNotEmpty() }
        val primaryUrl = if (primaryAssetId != null) {
            resolveAssetDisplayUrl(primaryAssetId, actorUserId)
        } else {
            resolveMediaUrl(row.mediaBucket, row.mediaPath)
        }
        val extraAssetIds =
            com.comunidapp.app.domain.social.SocialPostMedia.extraMediaAssetIds(compositionJson)
        val extraUrls = if (extraAssetIds.isNotEmpty()) {
            coroutineScope {
                extraAssetIds.map { assetId ->
                    async { resolveAssetDisplayUrl(assetId, actorUserId) }
                }.awaitAll().filterNotNull()
            }
        } else {
            extraMediaUrls(row.extraMedia).ifEmpty {
                com.comunidapp.app.domain.social.SocialPostMedia.extraMediaUrls(compositionJson)
            }
        }
        val displayUrls = com.comunidapp.app.domain.social.SocialPostMedia.displayUrls(primaryUrl, extraUrls)
        val mediaDeclared = kind == "REEL" ||
            primaryAssetId != null ||
            (!row.mediaBucket.isNullOrBlank() && !row.mediaPath.isNullOrBlank()) ||
            extraAssetIds.isNotEmpty() ||
            extraMediaUrls(row.extraMedia).isNotEmpty()
        val typeHint = com.comunidapp.app.domain.social.SocialPostMedia.postType(compositionJson)
        val author = row.authorUserId?.let { userId ->
            runCatching { DataProvider.userRepository.getUser(userId) }.getOrNull()
        }
        val authorAvatar = resolveAuthorAvatar(author, actorUserId)
        return FeedPost(
            id = row.id,
            authorId = row.authorUserId.orEmpty(),
            authorName = row.authorName.orEmpty().ifBlank { author?.name.orEmpty() },
            authorImageUrl = authorAvatar,
            type = when {
                kind == "REEL" -> PostType.REEL
                typeHint.equals("LOST_FOUND", ignoreCase = true) -> PostType.LOST_FOUND
                typeHint.equals("URGENT", ignoreCase = true) -> PostType.URGENT
                else -> PostType.GENERAL
            },
            title = title,
            content = content,
            mediaAssetId = primaryAssetId,
            imageUrl = primaryUrl,
            imageUrls = displayUrls,
            mediaAvailability = when {
                displayUrls.isNotEmpty() -> FeedMediaAvailability.AVAILABLE
                mediaDeclared -> FeedMediaAvailability.UNAVAILABLE
                else -> FeedMediaAvailability.NONE
            },
            locationText = com.comunidapp.app.domain.social.SocialPostMedia.locationLabel(compositionJson),
            likeCount = row.likeCount,
            commentCount = row.commentCount,
            createdAt = parseEpoch(row.createdAt),
            petId = row.petId ?: jsonTextList(row.petIds).firstOrNull(),
            petIds = jsonTextList(row.petIds).ifEmpty { listOfNotNull(row.petId) },
            petNames = jsonTextList(row.petNames),
            localityId = row.localityId,
            compositionJson = compositionJson,
            alertKind = com.comunidapp.app.domain.social.SocialPostMedia.alertKind(compositionJson),
            lostFoundCaseId = com.comunidapp.app.domain.social.SocialPostMedia.lostFoundCaseId(compositionJson),
            mediaMime = row.mediaMime,
            visibility = com.comunidapp.app.domain.social.CanonicalSocialPostVisibility.fromRaw(row.visibility)
        )
    }

    private suspend fun resolveAssetDisplayUrl(assetId: String, actorUserId: String?): String? =
        when (
            val resolved = DataProvider.fileDisplayResolver.resolve(
                assetId = assetId,
                legacyReference = null,
                context = FileAuthContext(actorUserId = actorUserId)
            )
        ) {
            is AppResult.Success -> resolved.data.displayValue.trim().takeIf { it.isNotEmpty() }
            is AppResult.Failure -> null
        }

    private suspend fun resolveAuthorAvatar(user: User?, actorUserId: String?): String? {
        ProfileAvatarResolver.httpOrLocalUrl(user)?.let { return it }
        val assetId = user?.avatarPath?.takeIf(ProfileAvatarResolver::isUuid) ?: return null
        return resolveAssetDisplayUrl(assetId, actorUserId)
    }

    private suspend fun refreshStoriesInternal() {
        val token = epoch.current()
        val rows = rpcRows<CanonicalStoryRow>(CanonicalBackend.RPC_LIST_ACTIVE_STORIES)
        val mapped = rows.map { row ->
            FeedPost(
                id = row.id,
                authorId = row.authorUserId.orEmpty(),
                authorName = row.authorName.orEmpty().ifBlank { row.authorUsername.orEmpty() },
                type = PostType.STORY,
                title = "Historia",
                content = row.caption.orEmpty(),
                imageUrl = resolveMediaUrl(row.mediaBucket, row.mediaPath),
                createdAt = parseEpoch(row.createdAt),
                expiresAt = parseEpoch(row.expiresAt),
                petId = row.petId,
                localityId = row.localityId,
                compositionJson = row.composition?.toString(),
                mediaMime = row.mediaMime
            )
        }
        stories.tryWrite(token, mapped)
    }

    private fun jsonTextList(element: JsonElement?): List<String> {
        if (element == null || element is JsonNull) return emptyList()
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { text -> text.isNotEmpty() }
        }
    }

    private fun extraMediaUrls(element: JsonElement?): List<String> {
        if (element == null || element is JsonNull) return emptyList()
        return runCatching {
            M08RpcDecoding.decodeRows<CanonicalFeedExtraMediaRow>(element).mapNotNull { row ->
                resolveMediaUrl(row.bucket, row.path)
            }
        }.getOrDefault(emptyList())
    }

    private fun resolveMediaUrl(bucket: String?, path: String?): String? {
        if (bucket.isNullOrBlank() || path.isNullOrBlank()) return null
        val base = com.comunidapp.app.core.config.AppConfigProvider.get().supabaseUrl
            ?: CanonicalBackend.STAGING_URL
        return runCatching { CanonicalMedia.publicObjectUrl(base, bucket, path) }.getOrNull()
    }
}

class CanonicalChatRepository : ChatRepository {
    private val messagesByConversation =
        java.util.concurrent.ConcurrentHashMap<String, MutableStateFlow<List<ChatMessage>>>()

    private fun messagesFlow(conversationId: String): MutableStateFlow<List<ChatMessage>> =
        messagesByConversation.getOrPut(conversationId) { MutableStateFlow(emptyList()) }

    private fun mapRows(conversationId: String, rows: List<CanonicalMessageRow>): List<ChatMessage> =
        ChatMessageMerge.replaceFromServer(
            rows.map { row ->
                ChatMessage(
                    id = row.id,
                    conversationId = conversationId,
                    senderId = row.actorUserId.orEmpty(),
                    senderName = "",
                    content = row.body.orEmpty(),
                    createdAt = parseEpoch(row.createdAt),
                    payloadJson = row.payload?.toString()
                )
            }
        )

    private val olderExhausted = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    private suspend fun refreshMessages(conversationId: String): List<ChatMessage> {
        olderExhausted[conversationId] = false
        val rows = runCatching {
            rpcRows<CanonicalMessageRow>(
                CanonicalBackend.RPC_LIST_MESSAGES,
                buildJsonObject { put("p_conversation_id", conversationId) }
            )
        }.getOrDefault(emptyList())
        val mapped = mapRows(conversationId, rows)
        val current = messagesFlow(conversationId).value
        val merged = if (current.isEmpty()) {
            mapped
        } else {
            ChatMessageMerge.unionById(current, mapped)
        }
        messagesFlow(conversationId).value = merged
        if (rows.size < CanonicalBackend.MESSAGE_PAGE_LIMIT) {
            olderExhausted[conversationId] = true
        }
        return merged
    }

    override suspend fun loadOlderMessages(conversationId: String): Result<Boolean> = runCatching {
        if (conversationId.isBlank() || olderExhausted[conversationId] == true) return@runCatching false
        val current = messagesFlow(conversationId).value
        val oldest = current.minWithOrNull(
            compareBy<ChatMessage> { it.createdAt ?: Long.MAX_VALUE }.thenBy { it.id }
        ) ?: return@runCatching false
        val createdAt = oldest.createdAt?.let { Instant.ofEpochMilli(it).toString() }
            ?: return@runCatching false
        val rows = rpcRows<CanonicalMessageRow>(
            CanonicalBackend.RPC_LIST_MESSAGES,
            buildJsonObject {
                put("p_conversation_id", conversationId)
                put("p_limit", CanonicalBackend.MESSAGE_PAGE_LIMIT)
                put("p_cursor_created_at", createdAt)
                put("p_cursor_id", oldest.id)
            }
        )
        if (rows.size < CanonicalBackend.MESSAGE_PAGE_LIMIT) {
            olderExhausted[conversationId] = true
        }
        messagesFlow(conversationId).value = ChatMessageMerge.unionById(
            current,
            mapRows(conversationId, rows)
        )
        rows.isNotEmpty()
    }

    override fun observeConversations(userId: String): Flow<List<Conversation>> = flow {
        val rows = runCatching { rpcRows<CanonicalConversationRow>(CanonicalBackend.RPC_LIST_CONVERSATIONS) }
            .getOrDefault(emptyList())
        emit(rows.map { row ->
            Conversation(
                id = row.id,
                peerUserId = row.peerUserId.orEmpty(),
                peerName = row.peerName.orEmpty().ifBlank { row.peerUsername.orEmpty().ifBlank { row.subjectKind ?: "Conversación" } },
                peerUsername = row.peerUsername?.takeIf { it.isNotBlank() },
                lastMessageText = row.lastMessageText,
                lastMessageAt = parseEpoch(row.lastMessageAt ?: row.createdAt)
            )
        })
    }

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> = flow {
        refreshMessages(conversationId)
        messagesFlow(conversationId).collect { emit(it) }
    }

    override suspend fun getOrCreateConversation(
        currentUser: User,
        peerUserId: String,
        peerName: String,
        contextType: ChatContextType?,
        contextId: String?
    ): Result<String> = runCatching {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_START_CONVERSATION,
            parameters = buildJsonObject {
                put("p_kind", "PERSON")
                put("p_other_person", peerUserId)
                put("p_org", JsonNull)
                put("p_body", "")
            }
        ).decodeAs()
    }

    override suspend fun sendMessage(
        conversationId: String,
        sender: User,
        content: String
    ): Result<String> = runCatching {
        val messageId: String = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_SEND_MESSAGE,
            parameters = buildJsonObject {
                put("p_conversation_id", conversationId)
                put("p_body", content)
                com.comunidapp.app.domain.social.InternalShareCodec.decode(content)?.let { ref ->
                    put(
                        "p_payload",
                        kotlinx.serialization.json.Json.encodeToJsonElement(
                            com.comunidapp.app.domain.social.SharedContentReference.serializer(),
                            ref
                        )
                    )
                }
            }
        ).decodeAs()
        val refreshed = refreshMessages(conversationId)
        if (messageId.isNotBlank() && refreshed.none { it.id == messageId }) {
            messagesFlow(conversationId).value = ChatMessageMerge.appendConfirmed(
                messagesFlow(conversationId).value,
                ChatMessage(
                    id = messageId,
                    conversationId = conversationId,
                    senderId = sender.id,
                    senderName = sender.name,
                    content = content.trim(),
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        messageId
    }

    override suspend fun searchPeople(query: String): List<ChatPersonHit> {
        if (query.trim().length < 2) return emptyList()
        return runCatching {
            rpcRows<CanonicalSharePersonRow>(
                CanonicalBackend.RPC_SEARCH_PERSONS,
                buildJsonObject { put("p_query", query.trim()) }
            ).map {
                ChatPersonHit(
                    userId = it.userId,
                    username = it.username.orEmpty(),
                    displayName = it.displayName.orEmpty()
                )
            }
        }.getOrDefault(emptyList())
    }
}

class CanonicalLocationCatalogRepository : LocationCatalogRepository {
    private val _nodes = MutableStateFlow<List<LocationNode>>(emptyList())
    override val nodes: StateFlow<List<LocationNode>> = _nodes.asStateFlow()
    private val _loadState = MutableStateFlow(LocationCatalogLoadState.IDLE)
    override val loadState: StateFlow<LocationCatalogLoadState> = _loadState.asStateFlow()
    private val _loadErrorMessage = MutableStateFlow<String?>(null)
    override val loadErrorMessage: StateFlow<String?> = _loadErrorMessage.asStateFlow()

    override suspend fun refresh(): Result<Unit> {
        _loadState.value = LocationCatalogLoadState.LOADING
        _loadErrorMessage.value = null
        return runCatching {
            val element: JsonElement = supabase.postgrest.rpc(CanonicalBackend.RPC_LIST_LOCATION_CATALOG).decodeAs()
            val rows = M08RpcDecoding.decodeRows<CanonicalLocationNodeRow>(element)
            val seedAliases = argentinaLocationSeed().associate { it.id to it.aliases }
            _nodes.value = rows.mapNotNull { row ->
                val level = when (row.kind) {
                    "COUNTRY" -> LocationLevel.COUNTRY
                    "PROVINCE" -> LocationLevel.PROVINCE
                    "MUNICIPALITY" -> LocationLevel.MUNICIPALITY
                    "LOCALITY" -> LocationLevel.LOCALITY
                    else -> return@mapNotNull null
                }
                LocationNode(
                    id = row.id,
                    name = row.name,
                    level = level,
                    parentId = row.parentId,
                    active = row.active,
                    order = row.sortKey,
                    code = row.isoCode,
                    aliases = seedAliases[row.id].orEmpty()
                )
            }
            val provinces = _nodes.value.count { it.level == LocationLevel.PROVINCE }
            _loadState.value = if (provinces == 0) {
                LocationCatalogLoadState.EMPTY
            } else {
                LocationCatalogLoadState.LOADED
            }
        }.onFailure { error ->
            _loadErrorMessage.value = error.message ?: "No se pudo cargar el catálogo de ubicaciones."
            _loadState.value = LocationCatalogLoadState.ERROR
        }
    }

    override fun snapshot(): List<LocationNode> = _nodes.value
    override fun get(id: String): LocationNode? = _nodes.value.firstOrNull { it.id == id }
    override fun upsert(node: LocationNode) = Unit
    override fun create(
        name: String,
        level: LocationLevel,
        parentId: String?,
        code: String?,
        aliases: List<String>
    ): LocationNode = LocationNode(id = "", name = name, level = level, parentId = parentId, code = code)
    override fun setActive(id: String, active: Boolean) = Unit
    override fun moveOrder(id: String, delta: Int) = Unit
}
