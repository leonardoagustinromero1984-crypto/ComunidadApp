package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.CreateM19PostInput
import com.comunidapp.app.data.model.M19Comment
import com.comunidapp.app.data.model.M19EngagementCalculator
import com.comunidapp.app.data.model.M19EngagementSummary
import com.comunidapp.app.data.model.M19FeedFilter
import com.comunidapp.app.data.model.M19MockOrganizations
import com.comunidapp.app.data.model.M19Post
import com.comunidapp.app.data.model.M19PostStatus
import com.comunidapp.app.data.model.M19PublicComment
import com.comunidapp.app.data.model.M19PublicPost
import com.comunidapp.app.data.model.M19Reaction
import com.comunidapp.app.data.model.M19ReactionType
import com.comunidapp.app.data.model.M19_ELIGIBLE_ORGANIZATION_TYPES
import com.comunidapp.app.data.model.UpdateM19PostInput
import com.comunidapp.app.data.remote.supabase.m19.M19Exception
import com.comunidapp.app.data.remote.supabase.m19.M19SocialErrorMapper
import com.comunidapp.app.domain.organization.OrganizationType
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** LeoVer M19 — store + contratos + mock (Bloque 1, sin red). */

class M19SocialMemoryStore {
    private val idSeq = AtomicLong(0)
    private val mutex = Mutex()
    private val _posts = MutableStateFlow<List<M19Post>>(emptyList())
    private val _comments = MutableStateFlow<List<M19Comment>>(emptyList())
    private val _reactions = MutableStateFlow<List<M19Reaction>>(emptyList())
    private val idempotentRetries = AtomicInteger(0)

    val organizationTypes = MutableStateFlow<Map<String, OrganizationType>>(emptyMap())
    val organizationManagers = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val organizationDisplayNames = MutableStateFlow<Map<String, String>>(emptyMap())
    var seeded: Boolean = false

    val posts: StateFlow<List<M19Post>> = _posts.asStateFlow()
    val comments: StateFlow<List<M19Comment>> = _comments.asStateFlow()
    val reactions: StateFlow<List<M19Reaction>> = _reactions.asStateFlow()

    fun nextId(prefix: String): String = "${prefix}_${idSeq.incrementAndGet()}"

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

    fun upsertPost(post: M19Post) {
        _posts.update { list ->
            (list.filterNot { it.id == post.id } + post).sortedByDescending { it.publishedAt ?: it.createdAt }
        }
    }

    fun upsertComment(comment: M19Comment) {
        _comments.update { list ->
            (list.filterNot { it.id == comment.id } + comment).sortedBy { it.createdAt }
        }
    }

    fun upsertReaction(reaction: M19Reaction) {
        _reactions.update { list ->
            (list.filterNot { it.id == reaction.id } + reaction).sortedByDescending { it.createdAt }
        }
    }

    fun removeReaction(reactionId: String) {
        _reactions.update { list -> list.filterNot { it.id == reactionId } }
    }

    fun recordIdempotentRetry() {
        idempotentRetries.incrementAndGet()
    }

    fun idempotentRetryCount(): Int = idempotentRetries.get()

    fun commentsFor(postId: String): List<M19Comment> =
        _comments.value.filter { it.postId == postId }

    fun reactionsFor(postId: String): List<M19Reaction> =
        _reactions.value.filter { it.postId == postId }

    fun reactionForUser(postId: String, userId: String): M19Reaction? =
        _reactions.value.firstOrNull { it.postId == postId && it.userId == userId }

    fun engagementFor(postId: String): M19EngagementSummary =
        M19EngagementCalculator.summarize(postId, _reactions.value, _comments.value)

    fun seedDefaults(actorUserId: String = "mock_user_admin") {
        if (seeded) return
        seeded = true
        val now = System.currentTimeMillis()
        organizationTypes.value = mapOf(
            M19MockOrganizations.ORG_NORTE to OrganizationType.SHELTER,
            M19MockOrganizations.ORG_SUR to OrganizationType.RESCUE_GROUP,
            M19MockOrganizations.ORG_OESTE to OrganizationType.NGO
        )
        organizationManagers.value = mapOf(
            M19MockOrganizations.ORG_NORTE to setOf(actorUserId),
            M19MockOrganizations.ORG_SUR to setOf(actorUserId),
            M19MockOrganizations.ORG_OESTE to setOf(actorUserId)
        )
        organizationDisplayNames.value = mapOf(
            M19MockOrganizations.ORG_NORTE to "Refugio Comunitario Norte",
            M19MockOrganizations.ORG_SUR to "Rescate Sur",
            M19MockOrganizations.ORG_OESTE to "Red Solidaria Oeste"
        )

        val pPublished1 = post(
            id = nextId("m19_post"),
            org = M19MockOrganizations.ORG_NORTE,
            title = "Historias de adopción en el refugio",
            content = "Compartimos el impacto de las adopciones del mes. Contacto: info@refugio.org",
            status = M19PostStatus.PUBLISHED,
            actor = actorUserId,
            now = now,
            publishedOffsetDays = -2
        )
        val pPublished2 = post(
            id = nextId("m19_post"),
            org = M19MockOrganizations.ORG_SUR,
            title = "Gracias a nuestros voluntarios",
            content = "La jornada de ayer fue un éxito. Sumate al próximo encuentro comunitario.",
            status = M19PostStatus.PUBLISHED,
            actor = actorUserId,
            now = now,
            publishedOffsetDays = -1
        )
        val pPublished3 = post(
            id = nextId("m19_post"),
            org = M19MockOrganizations.ORG_OESTE,
            title = "Tips de tenencia responsable",
            content = "Recordá vacunar y castrar. Más info en nuestra sede.",
            status = M19PostStatus.PUBLISHED,
            actor = actorUserId,
            now = now,
            publishedOffsetDays = 0
        )
        val pDraft = post(
            id = nextId("m19_post"),
            org = M19MockOrganizations.ORG_NORTE,
            title = "Borrador — campaña de invierno",
            content = "Contenido en preparación para la campaña de abrigo.",
            status = M19PostStatus.DRAFT,
            actor = actorUserId,
            now = now
        )
        val pHidden = post(
            id = nextId("m19_post"),
            org = M19MockOrganizations.ORG_SUR,
            title = "Publicación oculta por revisión",
            content = "Contenido temporalmente no visible en el feed.",
            status = M19PostStatus.HIDDEN,
            actor = actorUserId,
            now = now,
            publishedOffsetDays = -5
        )
        val pRemoved = post(
            id = nextId("m19_post"),
            org = M19MockOrganizations.ORG_OESTE,
            title = "Publicación eliminada",
            content = "Contenido retirado del feed.",
            status = M19PostStatus.REMOVED,
            actor = actorUserId,
            now = now - 604_800_000L
        )

        listOf(pPublished1, pPublished2, pPublished3, pDraft, pHidden, pRemoved).forEach { upsertPost(it) }

        seedCommentsAndReactions(
            publishedIds = listOf(pPublished1.id, pPublished2.id, pPublished3.id),
            actorUserId = actorUserId,
            now = now
        )
    }

    private fun post(
        id: String,
        org: String,
        title: String,
        content: String,
        status: M19PostStatus,
        actor: String,
        now: Long,
        publishedOffsetDays: Int? = null
    ): M19Post {
        val publishedAt = publishedOffsetDays?.let { now + it * 86_400_000L }
        return M19Post(
            id = id,
            organizationId = org,
            organizationDisplayName = organizationDisplayNames.value[org] ?: org,
            authorUserId = actor,
            authorDisplayName = "Equipo mock",
            title = title,
            content = content,
            status = status,
            coverImageRef = "mock://m19/cover/$id",
            publishedAt = if (status == M19PostStatus.PUBLISHED) publishedAt else null,
            createdBy = actor,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun seedCommentsAndReactions(
        publishedIds: List<String>,
        actorUserId: String,
        now: Long
    ) {
        publishedIds.forEachIndexed { index, postId ->
            upsertComment(
                M19Comment(
                    id = nextId("m19_comment"),
                    postId = postId,
                    userId = "user_vol_$index",
                    authorDisplayName = "Voluntario ${index + 1}",
                    content = "¡Gracias por compartir! info@test.com",
                    createdAt = now - index * 3_600_000L
                )
            )
            upsertReaction(
                M19Reaction(
                    id = nextId("m19_reaction"),
                    postId = postId,
                    userId = actorUserId,
                    reactionType = M19ReactionType.LIKE,
                    createdAt = now
                )
            )
            upsertReaction(
                M19Reaction(
                    id = nextId("m19_reaction"),
                    postId = postId,
                    userId = "user_support_$index",
                    reactionType = M19ReactionType.SUPPORT,
                    createdAt = now
                )
            )
            if (index == 0) {
                upsertReaction(
                    M19Reaction(
                        id = nextId("m19_reaction"),
                        postId = postId,
                        userId = "user_celebrate",
                        reactionType = M19ReactionType.CELEBRATE,
                        createdAt = now
                    )
                )
            }
        }
    }
}

interface M19SocialRepository {
    fun observePostById(postId: String): Flow<M19Post?>
    fun observePostsForOrganization(organizationId: String): Flow<List<M19Post>>
    suspend fun searchFeed(filter: M19FeedFilter): Result<List<M19PublicPost>>
    suspend fun getPublicPostById(postId: String): Result<M19PublicPost>
    suspend fun createPost(input: CreateM19PostInput): Result<M19Post>
    suspend fun updatePost(input: UpdateM19PostInput): Result<M19Post>
    suspend fun publishPost(postId: String): Result<M19Post>
    suspend fun hidePost(postId: String): Result<M19Post>
    suspend fun removePost(postId: String): Result<M19Post>
    suspend fun listPublicComments(postId: String): Result<List<M19PublicComment>>
    suspend fun addComment(postId: String, content: String): Result<M19PublicComment>
    suspend fun addReaction(postId: String, type: M19ReactionType): Result<M19Reaction>
    suspend fun removeReaction(postId: String): Result<Unit>
    suspend fun getMyReaction(postId: String): M19ReactionType?
    suspend fun getEngagementSummary(postId: String): Result<M19EngagementSummary>
    suspend fun canManageOrganization(organizationId: String): Boolean
    suspend fun isOrganizationEligible(organizationId: String): Boolean
}

interface M19SocialAuthorityPolicy {
    fun canManageSocial(actorUserId: String, organizationId: String, store: M19SocialMemoryStore): Boolean
    fun isOrganizationEligible(organizationId: String, store: M19SocialMemoryStore): Boolean
}

class MockM19SocialAuthorityPolicy : M19SocialAuthorityPolicy {
    override fun canManageSocial(
        actorUserId: String,
        organizationId: String,
        store: M19SocialMemoryStore
    ): Boolean = store.organizationManagers.value[organizationId]?.contains(actorUserId) == true

    override fun isOrganizationEligible(organizationId: String, store: M19SocialMemoryStore): Boolean {
        val type = store.organizationTypes.value[organizationId] ?: return false
        return type in M19_ELIGIBLE_ORGANIZATION_TYPES
    }
}

private fun failM19(code: String): Nothing =
    throw M19Exception(code, M19SocialErrorMapper.userMessage(code))

class MockM19SocialRepository(
    private val actorUserId: () -> String?,
    private val store: M19SocialMemoryStore = M19SocialMemoryStore(),
    private val authority: M19SocialAuthorityPolicy = MockM19SocialAuthorityPolicy()
) : M19SocialRepository {

    init {
        store.seedDefaults(actorUserId() ?: "mock_user_admin")
    }

    private fun requireActor(): String =
        actorUserId() ?: failM19("NOT_AUTHENTICATED")

    private fun requireManage(orgId: String, actor: String) {
        if (!authority.isOrganizationEligible(orgId, store)) failM19("M19_ORGANIZATION_NOT_ELIGIBLE")
        if (!authority.canManageSocial(actor, orgId, store)) failM19("M19_PERMISSION_DENIED")
    }

    private fun getPostOrFail(id: String): M19Post =
        store.posts.value.firstOrNull { it.id == id } ?: failM19("M19_POST_NOT_FOUND")

    override fun observePostById(postId: String): Flow<M19Post?> =
        store.posts.map { list -> list.firstOrNull { it.id == postId } }

    override fun observePostsForOrganization(organizationId: String): Flow<List<M19Post>> =
        store.posts.map { list -> list.filter { it.organizationId == organizationId } }

    override suspend fun searchFeed(filter: M19FeedFilter): Result<List<M19PublicPost>> =
        runCatching {
            store.posts.value
                .filter { post ->
                    !filter.publishedOnly || post.status == M19PostStatus.PUBLISHED
                }
                .filter { post ->
                    filter.query.isBlank() ||
                        post.title.contains(filter.query, ignoreCase = true) ||
                        post.content.contains(filter.query, ignoreCase = true)
                }
                .filter { post ->
                    filter.organizationId == null || post.organizationId == filter.organizationId
                }
                .map { post -> post.toPublicPost(store.engagementFor(post.id)) }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { M19SocialErrorMapper.failure(it) }
        )

    override suspend fun getPublicPostById(postId: String): Result<M19PublicPost> =
        runCatching {
            val post = getPostOrFail(postId)
            M19SocialValidators.validatePublicRead(post.status)?.let { failM19(it) }
            post.toPublicPost(store.engagementFor(postId))
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { M19SocialErrorMapper.failure(it) }
        )

    override suspend fun createPost(input: CreateM19PostInput): Result<M19Post> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                requireManage(input.organizationId, actor)
                M19SocialValidators.validateTitle(input.title)?.let { failM19(it) }
                M19SocialValidators.validateContent(input.content)?.let { failM19(it) }
                val now = System.currentTimeMillis()
                val post = M19Post(
                    id = store.nextId("m19_post"),
                    organizationId = input.organizationId,
                    organizationDisplayName = store.organizationDisplayNames.value[input.organizationId]
                        ?: input.organizationId,
                    authorUserId = actor,
                    authorDisplayName = "Equipo mock",
                    title = input.title.trim(),
                    content = input.content.trim(),
                    status = M19PostStatus.DRAFT,
                    coverImageRef = input.coverImageRef,
                    createdBy = actor,
                    createdAt = now,
                    updatedAt = now
                )
                store.upsertPost(post)
                post
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M19SocialErrorMapper.failure(it) }
            )
        }

    override suspend fun updatePost(input: UpdateM19PostInput): Result<M19Post> =
        mutate(input.postId) { post, _ ->
            if (post.status == M19PostStatus.REMOVED) failM19("M19_STATE_ALREADY_FINAL")
            M19SocialValidators.validateTitle(input.title)?.let { failM19(it) }
            M19SocialValidators.validateContent(input.content)?.let { failM19(it) }
            post.copy(
                title = input.title.trim(),
                content = input.content.trim(),
                coverImageRef = input.coverImageRef,
                updatedAt = System.currentTimeMillis()
            )
        }

    override suspend fun publishPost(postId: String): Result<M19Post> =
        transition(postId, M19PostStatus.PUBLISHED)

    override suspend fun hidePost(postId: String): Result<M19Post> =
        transition(postId, M19PostStatus.HIDDEN)

    override suspend fun removePost(postId: String): Result<M19Post> =
        transition(postId, M19PostStatus.REMOVED)

    override suspend fun listPublicComments(postId: String): Result<List<M19PublicComment>> =
        runCatching {
            val post = getPostOrFail(postId)
            M19SocialValidators.validatePublicRead(post.status)?.let { failM19(it) }
            store.commentsFor(postId)
                .filterNot { it.hidden }
                .map { it.toPublicComment() }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { M19SocialErrorMapper.failure(it) }
        )

    override suspend fun addComment(postId: String, content: String): Result<M19PublicComment> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                val post = getPostOrFail(postId)
                M19SocialValidators.validatePublicRead(post.status)?.let { failM19(it) }
                M19SocialValidators.validateComment(content)?.let { failM19(it) }
                val comment = M19Comment(
                    id = store.nextId("m19_comment"),
                    postId = postId,
                    userId = actor,
                    authorDisplayName = "Participante mock",
                    content = content.trim(),
                    createdAt = System.currentTimeMillis()
                )
                store.upsertComment(comment)
                comment.toPublicComment()
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M19SocialErrorMapper.failure(it) }
            )
        }

    override suspend fun addReaction(postId: String, type: M19ReactionType): Result<M19Reaction> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                val post = getPostOrFail(postId)
                M19SocialValidators.validatePublicRead(post.status)?.let { failM19(it) }
                val existing = store.reactionForUser(postId, actor)
                if (existing != null) {
                    if (existing.reactionType == type) {
                        store.recordIdempotentRetry()
                        return@runCatching existing
                    }
                    store.removeReaction(existing.id)
                }
                val reaction = M19Reaction(
                    id = store.nextId("m19_reaction"),
                    postId = postId,
                    userId = actor,
                    reactionType = type,
                    createdAt = System.currentTimeMillis()
                )
                store.upsertReaction(reaction)
                reaction
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M19SocialErrorMapper.failure(it) }
            )
        }

    override suspend fun removeReaction(postId: String): Result<Unit> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                val existing = store.reactionForUser(postId, actor)
                    ?: run {
                        store.recordIdempotentRetry()
                        return@runCatching Unit
                    }
                store.removeReaction(existing.id)
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M19SocialErrorMapper.failure(it) }
            )
        }

    override suspend fun getMyReaction(postId: String): M19ReactionType? =
        actorUserId()?.let { userId ->
            store.reactionForUser(postId, userId)?.reactionType
        }

    override suspend fun getEngagementSummary(postId: String): Result<M19EngagementSummary> =
        runCatching {
            getPostOrFail(postId)
            store.engagementFor(postId)
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { M19SocialErrorMapper.failure(it) }
        )

    override suspend fun canManageOrganization(organizationId: String): Boolean {
        val actor = actorUserId() ?: return false
        return authority.isOrganizationEligible(organizationId, store) &&
            authority.canManageSocial(actor, organizationId, store)
    }

    override suspend fun isOrganizationEligible(organizationId: String): Boolean =
        authority.isOrganizationEligible(organizationId, store)

    private suspend fun mutate(
        postId: String,
        transform: (M19Post, String) -> M19Post
    ): Result<M19Post> = store.withLock {
        runCatching {
            val actor = requireActor()
            val post = getPostOrFail(postId)
            requireManage(post.organizationId, actor)
            val updated = transform(post, actor).copy(updatedAt = System.currentTimeMillis())
            store.upsertPost(updated)
            updated
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { M19SocialErrorMapper.failure(it) }
        )
    }

    private suspend fun transition(postId: String, target: M19PostStatus): Result<M19Post> =
        mutate(postId) { post, _ ->
            if (post.status == target) {
                store.recordIdempotentRetry()
                return@mutate post
            }
            M19SocialValidators.validateStateTransition(post.status, target)?.let { failM19(it) }
            val now = System.currentTimeMillis()
            post.copy(
                status = target,
                publishedAt = when {
                    target == M19PostStatus.PUBLISHED && post.publishedAt == null -> now
                    else -> post.publishedAt
                },
                updatedAt = now
            )
        }
}
