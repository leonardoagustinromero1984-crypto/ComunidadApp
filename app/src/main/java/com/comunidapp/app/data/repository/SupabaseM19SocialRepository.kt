package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.CreateM19PostInput
import com.comunidapp.app.data.model.M19EngagementSummary
import com.comunidapp.app.data.model.M19FeedFilter
import com.comunidapp.app.data.model.M19FeedPage
import com.comunidapp.app.data.model.M19Post
import com.comunidapp.app.data.model.M19PostStatus
import com.comunidapp.app.data.model.M19PublicComment
import com.comunidapp.app.data.model.M19PublicPost
import com.comunidapp.app.data.model.M19Reaction
import com.comunidapp.app.data.model.M19ReactionType
import com.comunidapp.app.data.model.UpdateM19PostInput
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m19.M19SocialErrorMapper
import com.comunidapp.app.data.remote.supabase.m19.SupabaseM19RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m19.toM19EngagementSummary
import com.comunidapp.app.data.remote.supabase.m19.toM19Post
import com.comunidapp.app.data.remote.supabase.m19.toM19PublicComment
import com.comunidapp.app.data.remote.supabase.m19.toM19PublicPost
import com.comunidapp.app.data.remote.supabase.m19.toM19Reaction
import com.comunidapp.app.domain.organization.OrganizationId
import com.comunidapp.app.domain.organization.authorization.OrganizationPermissionCode
import com.comunidapp.app.domain.user.AccountStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SupabaseM19SocialRepository(
    private val remote: SupabaseM19RemoteDataSource = SupabaseM19RemoteDataSource(),
    private val actorUserId: () -> String? = { null }
) : M19SocialRepository {

    override fun observePostById(postId: String): Flow<M19Post?> = flow {
        emit(getPostInternal(postId).getOrNull())
    }

    override fun observePostsForOrganization(organizationId: String): Flow<List<M19Post>> =
        flow {
            emit(
                runCatching {
                    remote.listOrgPosts(organizationId).map { it.toM19Post() }
                }.getOrElse { emptyList() }
            )
        }

    override suspend fun searchFeed(filter: M19FeedFilter): Result<List<M19PublicPost>> =
        searchFeedPage(filter).map { it.items }

    override suspend fun searchFeedPage(filter: M19FeedFilter): Result<M19FeedPage> =
        try {
            val publicPosts = remote.listPublicFeed(
                buildJsonObject {
                    put("p_query", filter.query.takeIf { it.isNotBlank() })
                    put("p_organization_id", filter.organizationId)
                    put("p_published_only", filter.publishedOnly)
                }
            ).map { it.toM19PublicPost() }
            val posts = publicPosts.map { public ->
                M19Post(
                    id = public.id,
                    organizationId = "",
                    organizationDisplayName = public.organizationDisplayName,
                    authorUserId = "",
                    authorDisplayName = public.authorDisplayName,
                    title = public.title,
                    content = public.content,
                    status = public.status,
                    visibility = public.visibility,
                    coverImageRef = public.coverImageRef,
                    mediaAttachments = public.mediaAttachments,
                    contentReferences = emptyList(),
                    publishedAt = public.publishedAt,
                    createdBy = "",
                    createdAt = public.createdAt,
                    updatedAt = public.createdAt
                )
            }
            val page = M19FeedService.paginate(posts, filter) { post ->
                publicPosts.first { it.id == post.id }
            }
            Result.success(page)
        } catch (t: Throwable) {
            M19SocialErrorMapper.failure(t)
        }

    override suspend fun getPublicPostById(postId: String): Result<M19PublicPost> = try {
        if (postId.isBlank()) M19SocialErrorMapper.fail("M19_POST_NOT_FOUND")
        else Result.success(remote.getPublicPost(postId).toM19PublicPost())
    } catch (t: Throwable) {
        M19SocialErrorMapper.failure(t)
    }

    override suspend fun createPost(input: CreateM19PostInput): Result<M19Post> = try {
        Result.success(
            remote.createPost(
                buildJsonObject {
                    put("p_organization_id", input.organizationId)
                    put("p_title", input.title)
                    put("p_content", input.content)
                    put("p_cover_image_ref", input.coverImageRef)
                }
            ).toM19Post()
        )
    } catch (t: Throwable) {
        M19SocialErrorMapper.failure(t)
    }

    override suspend fun updatePost(input: UpdateM19PostInput): Result<M19Post> = try {
        Result.success(
            remote.updatePost(
                buildJsonObject {
                    put("p_post_id", input.postId)
                    put("p_title", input.title)
                    put("p_content", input.content)
                    put("p_cover_image_ref", input.coverImageRef)
                }
            ).toM19Post()
        )
    } catch (t: Throwable) {
        M19SocialErrorMapper.failure(t)
    }

    override suspend fun publishPost(postId: String): Result<M19Post> =
        transition(postId, M19PostStatus.PUBLISHED)

    override suspend fun hidePost(postId: String): Result<M19Post> =
        transition(postId, M19PostStatus.HIDDEN)

    override suspend fun archivePost(postId: String): Result<M19Post> =
        transition(postId, M19PostStatus.ARCHIVED)

    override suspend fun removePost(postId: String): Result<M19Post> =
        transition(postId, M19PostStatus.REMOVED)

    override suspend fun editComment(commentId: String, content: String): Result<M19PublicComment> =
        M19SocialErrorMapper.fail("M19_COMMENT_NOT_FOUND")

    override suspend fun archiveComment(commentId: String): Result<Unit> =
        M19SocialErrorMapper.fail("M19_COMMENT_NOT_FOUND")

    override suspend fun listPublicComments(postId: String): Result<List<M19PublicComment>> =
        try {
            Result.success(remote.listPublicComments(postId).map { it.toM19PublicComment() })
        } catch (t: Throwable) {
            M19SocialErrorMapper.failure(t)
        }

    override suspend fun addComment(postId: String, content: String): Result<M19PublicComment> =
        try {
            Result.success(remote.addComment(postId, content).toM19PublicComment())
        } catch (t: Throwable) {
            M19SocialErrorMapper.failure(t)
        }

    override suspend fun addReaction(postId: String, type: M19ReactionType): Result<M19Reaction> =
        try {
            Result.success(remote.addReaction(postId, type.name).toM19Reaction())
        } catch (t: Throwable) {
            M19SocialErrorMapper.failure(t)
        }

    override suspend fun removeReaction(postId: String): Result<Unit> =
        try {
            remote.removeReaction(postId)
            Result.success(Unit)
        } catch (t: Throwable) {
            M19SocialErrorMapper.failure(t)
        }

    override suspend fun getMyReaction(postId: String): M19ReactionType? =
        runCatching {
            remote.getMyReaction(postId)?.toM19Reaction()?.reactionType
        }.getOrNull()

    override suspend fun getEngagementSummary(postId: String): Result<M19EngagementSummary> =
        try {
            Result.success(remote.getEngagementSummary(postId).toM19EngagementSummary())
        } catch (t: Throwable) {
            M19SocialErrorMapper.failure(t)
        }

    override suspend fun canManageOrganization(organizationId: String): Boolean {
        val user = AuthProvider.repository.getCurrentUser() ?: return false
        val accountStatus = runCatching { AccountStatus.valueOf(user.accountStatus) }
            .getOrDefault(AccountStatus.ACTIVE)
        return runCatching {
            DataProvider.organizationPermissionRepository.hasPermission(
                organizationId = OrganizationId(organizationId),
                userId = user.id,
                accountStatus = accountStatus,
                permission = OrganizationPermissionCode.SOCIAL_MANAGE
            )
        }.getOrDefault(false)
    }

    override suspend fun isOrganizationEligible(organizationId: String): Boolean =
        runCatching { remote.isOrganizationEligible(organizationId) }.getOrDefault(false)

    private suspend fun getPostInternal(postId: String): Result<M19Post> = try {
        if (postId.isBlank()) M19SocialErrorMapper.fail("M19_POST_NOT_FOUND")
        else Result.success(remote.getPost(postId).toM19Post())
    } catch (t: Throwable) {
        M19SocialErrorMapper.failure(t)
    }

    private suspend fun transition(postId: String, status: M19PostStatus): Result<M19Post> =
        try {
            Result.success(remote.transitionPost(postId, status.name).toM19Post())
        } catch (t: Throwable) {
            M19SocialErrorMapper.failure(t)
        }
}
