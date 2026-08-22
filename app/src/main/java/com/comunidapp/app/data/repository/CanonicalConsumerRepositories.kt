package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.ChatContextType
import com.comunidapp.app.data.model.ChatMessage
import com.comunidapp.app.data.model.Conversation
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
import com.comunidapp.app.data.remote.supabase.m08.M08RpcDecoding
import com.comunidapp.app.data.remote.supabase.m09.CreateAdoptionParams
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionErrorMapper
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionException
import com.comunidapp.app.data.remote.supabase.m09.UpdateAdoptionParams
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.canonical.CanonicalMedia
import com.comunidapp.app.domain.chat.ChatMessageMerge
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
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
    val note: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
private data class CanonicalAdoptionRow(
    val id: String,
    @SerialName("pet_id") val petId: String? = null,
    @SerialName("public_code") val publicCode: String? = null,
    val status: String,
    val note: String? = null,
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
private data class CanonicalSocialPostRow(
    val id: String,
    @SerialName("author_user_id") val authorUserId: String? = null,
    @SerialName("author_name") val authorName: String? = null,
    val body: String? = null,
    @SerialName("content_kind") val contentKind: String? = null,
    @SerialName("media_asset_id") val mediaAssetId: String? = null,
    @SerialName("media_bucket") val mediaBucket: String? = null,
    @SerialName("media_path") val mediaPath: String? = null,
    @SerialName("media_mime") val mediaMime: String? = null,
    @SerialName("pet_id") val petId: String? = null,
    @SerialName("locality_id") val localityId: String? = null,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null
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

private suspend inline fun <reified T : Any> rpcRows(function: String, params: kotlinx.serialization.json.JsonObject? = null): List<T> {
    val element: JsonElement = if (params == null) {
        supabase.postgrest.rpc(function).decodeAs()
    } else {
        supabase.postgrest.rpc(function, params).decodeAs()
    }
    return M08RpcDecoding.decodeRows(element)
}

private fun parseEpoch(value: String?): Long? =
    value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

class CanonicalLostFoundRepository : LostFoundRepository {
    private val posts = MutableStateFlow<List<LostFoundPost>>(emptyList())

    override fun observeLostFoundPosts(): StateFlow<List<LostFoundPost>> = posts.asStateFlow()

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
        val id: String = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_CREATE_LOST_FOUND,
            parameters = buildJsonObject {
                put("p_kind", post.type.name)
                if (!post.petId.isNullOrBlank()) put("p_pet_id", post.petId) else put("p_pet_id", JsonNull)
                put("p_locality_id", JsonNull)
                put("p_species", post.species.name)
                put(
                    "p_note",
                    listOf(post.location, post.description).filter { it.isNotBlank() }.joinToString(" · ")
                )
            }
        ).decodeAs()
        refresh()
        id
    }

    override suspend fun updateLostFoundPost(post: LostFoundPost): Result<Unit> =
        if (post.status == LostFoundStatus.RESOLVED) updateStatus(post.id, LostFoundStatus.RESOLVED)
        else Result.success(Unit)

    override suspend fun updateStatus(id: String, status: LostFoundStatus): Result<Unit> = runCatching {
        if (status == LostFoundStatus.RESOLVED) {
            supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_RESOLVE_LOST_FOUND,
                parameters = buildJsonObject { put("p_id", id) }
            )
        }
        refresh()
    }

    suspend fun refresh() {
        val rows = rpcRows<CanonicalLostFoundRow>(CanonicalBackend.RPC_LIST_LOST_FOUND)
        posts.value = rows.map { row ->
            LostFoundPost(
                id = row.id,
                authorId = row.createdBy.orEmpty(),
                authorName = "",
                type = if (row.kind.equals("FOUND", true)) LostFoundType.FOUND else LostFoundType.LOST,
                petName = row.petName,
                species = PetSpecies.fromString(row.species),
                location = row.localityId.orEmpty(),
                description = row.note.orEmpty(),
                contactInfo = "",
                status = if (row.status.equals("RESOLVED", true)) LostFoundStatus.RESOLVED else LostFoundStatus.ACTIVE,
                publicCode = row.publicCode,
                date = row.createdAt.orEmpty(),
                createdAt = parseEpoch(row.createdAt)
            )
        }
    }
}

class CanonicalAdoptionRepository : AdoptionRepository {
    private val posts = MutableStateFlow<List<AdoptionPost>>(emptyList())

    override fun observeAdoptionPosts(): StateFlow<List<AdoptionPost>> = posts.asStateFlow()

    override fun observePublishedAdoptions(): Flow<List<AdoptionPost>> =
        posts.map { list -> list.filter { it.status == AdoptionStatus.PUBLISHED } }

    override fun observeMyAdoptions(publisherId: String): Flow<List<AdoptionPost>> =
        posts.map { list ->
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
                publish = true
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
                put("p_note", params.description)
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
        val rows = rpcRows<CanonicalAdoptionRow>(CanonicalBackend.RPC_LIST_ADOPTIONS)
        posts.value = rows.map { row ->
            AdoptionPost(
                id = row.id,
                petId = row.petId,
                publisherId = row.publishedBy,
                publisherOrganizationId = row.organizationId,
                shelterId = row.organizationId,
                shelterName = "",
                title = row.name.orEmpty(),
                name = row.name.orEmpty(),
                species = PetSpecies.fromString(row.species),
                sex = runCatching { PetSex.valueOf(row.sex ?: "UNKNOWN") }.getOrDefault(PetSex.UNKNOWN),
                ageYears = 0,
                size = runCatching { PetSize.valueOf(row.size ?: "MEDIUM") }.getOrDefault(PetSize.MEDIUM),
                location = row.localityId.orEmpty(),
                description = row.note.orEmpty(),
                status = when (row.status.uppercase()) {
                    "CLOSED" -> AdoptionStatus.CLOSED
                    "HIDDEN" -> AdoptionStatus.PAUSED
                    else -> AdoptionStatus.PUBLISHED
                },
                publicCode = row.publicCode,
                createdAt = parseEpoch(row.createdAt)
            )
        }
    }
}

class CanonicalFeedRepository : FeedRepository {
    private val posts = MutableStateFlow<List<FeedPost>>(emptyList())
    private val stories = MutableStateFlow<List<FeedPost>>(emptyList())
    private val liked = MutableStateFlow<Set<String>>(emptySet())
    private val comments = MutableStateFlow<Map<String, List<PostComment>>>(emptyMap())

    override fun observeFeedPosts(): StateFlow<List<FeedPost>> = posts.asStateFlow()
    override fun observeActiveStories(): StateFlow<List<FeedPost>> = stories.asStateFlow()

    override suspend fun refreshPosts(): Result<Unit> = runCatching { refresh() }
    override suspend fun refreshStories(): Result<Unit> = runCatching { refreshStoriesInternal() }

    override suspend fun addFeedPost(post: FeedPost): Result<String> = runCatching {
        val mediaId = post.imageUrl?.takeIf { !it.startsWith("http", ignoreCase = true) }
        createSocialPost(post, mediaAssetId = mediaId, kind = "POST")
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

    override suspend fun updateFeedPost(post: FeedPost): Result<Unit> = Result.success(Unit)

    override suspend fun toggleLike(postId: String, userId: String): Result<Boolean> = runCatching {
        val likedNow: Boolean = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_REACT_SOCIAL_POST,
            parameters = buildJsonObject { put("p_post_id", postId) }
        ).decodeAs()
        liked.update { current -> if (likedNow) current + postId else current - postId }
        refresh()
        likedNow
    }

    override fun observeLikedPostIds(userId: String): Flow<Set<String>> = liked

    override fun observeComments(postId: String): Flow<List<PostComment>> =
        comments.map { it[postId].orEmpty() }

    override suspend fun refreshComments(postId: String): Result<Unit> = runCatching {
        val rows = rpcRows<CanonicalSocialCommentRow>(
            CanonicalBackend.RPC_LIST_SOCIAL_COMMENTS,
            buildJsonObject { put("p_post_id", postId) }
        )
        comments.update { current ->
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
        refresh()
    }

    override suspend fun deleteOwnComment(commentId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_DELETE_OWN_COMMENT,
            parameters = buildJsonObject { put("p_comment_id", commentId) }
        )
        comments.update { current ->
            current.mapValues { (_, list) -> list.filterNot { it.id == commentId } }
        }
        refresh()
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
                put("p_body", post.content.ifBlank { post.title })
                put("p_visibility", "PUBLIC")
                put("p_content_kind", kind)
                if (!mediaAssetId.isNullOrBlank()) put("p_media_asset_id", mediaAssetId) else put("p_media_asset_id", JsonNull)
                if (!post.petId.isNullOrBlank()) put("p_pet_id", post.petId) else put("p_pet_id", JsonNull)
                if (!post.localityId.isNullOrBlank()) put("p_locality_id", post.localityId) else put("p_locality_id", JsonNull)
                put("p_composition", Json.parseToJsonElement(post.compositionJson ?: "{}"))
            }
        ).decodeAs()
        refresh()
        return id
    }

    private suspend fun refresh() {
        val rows = rpcRows<CanonicalSocialPostRow>(CanonicalBackend.RPC_LIST_SOCIAL_FEED)
        posts.value = rows.map { row ->
            val kind = row.contentKind.orEmpty().uppercase()
            FeedPost(
                id = row.id,
                authorId = row.authorUserId.orEmpty(),
                authorName = row.authorName.orEmpty(),
                type = if (kind == "REEL") PostType.REEL else PostType.GENERAL,
                title = "",
                content = row.body.orEmpty(),
                imageUrl = resolveMediaUrl(row.mediaBucket, row.mediaPath),
                likeCount = row.likeCount,
                commentCount = row.commentCount,
                createdAt = parseEpoch(row.createdAt),
                petId = row.petId,
                localityId = row.localityId,
                mediaMime = row.mediaMime
            )
        }
    }

    private suspend fun refreshStoriesInternal() {
        val rows = rpcRows<CanonicalStoryRow>(CanonicalBackend.RPC_LIST_ACTIVE_STORIES)
        stories.value = rows.map { row ->
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

    private suspend fun refreshMessages(conversationId: String): List<ChatMessage> {
        val rows = runCatching {
            rpcRows<CanonicalMessageRow>(
                CanonicalBackend.RPC_LIST_MESSAGES,
                buildJsonObject { put("p_conversation_id", conversationId) }
            )
        }.getOrDefault(emptyList())
        val mapped = mapRows(conversationId, rows)
        messagesFlow(conversationId).value = mapped
        return mapped
    }

    override fun observeConversations(userId: String): Flow<List<Conversation>> = flow {
        val rows = runCatching { rpcRows<CanonicalConversationRow>(CanonicalBackend.RPC_LIST_CONVERSATIONS) }
            .getOrDefault(emptyList())
        emit(rows.map { row ->
            Conversation(
                id = row.id,
                peerUserId = row.peerUserId.orEmpty(),
                peerName = row.peerName.orEmpty().ifBlank { row.peerUsername.orEmpty().ifBlank { row.subjectKind ?: "Conversación" } },
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
                put("p_body", "Hola")
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
