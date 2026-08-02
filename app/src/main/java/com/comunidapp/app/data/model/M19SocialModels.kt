package com.comunidapp.app.data.model

import com.comunidapp.app.domain.organization.OrganizationType

/** LeoVer M19 — Red social y contenido (Bloque 1 local/mock). */

enum class M19PostStatus {
    DRAFT,
    PUBLISHED,
    HIDDEN,
    REMOVED;

    val isPublic: Boolean get() = this == PUBLISHED
    val isTerminal: Boolean get() = this == REMOVED
}

enum class M19ReactionType {
    LIKE,
    SUPPORT,
    CELEBRATE
}

data class M19Post(
    val id: String,
    val organizationId: String,
    val organizationDisplayName: String,
    val authorUserId: String,
    val authorDisplayName: String,
    val title: String,
    val content: String,
    val status: M19PostStatus,
    val coverImageRef: String? = null,
    val moderationStatus: String? = null,
    val publishedAt: Long? = null,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toPublicPost(engagement: M19EngagementSummary): M19PublicPost =
        M19PrivacySanitizer.toPublicPost(this, engagement)
}

data class M19PublicPost(
    val id: String,
    val title: String,
    val content: String,
    val organizationDisplayName: String,
    val authorDisplayName: String,
    val status: M19PostStatus,
    val coverImageRef: String? = null,
    val likeCount: Int,
    val supportCount: Int,
    val celebrateCount: Int,
    val commentCount: Int,
    val publishedAt: Long?,
    val createdAt: Long
)

data class M19PostSummary(
    val id: String,
    val title: String,
    val organizationDisplayName: String,
    val authorDisplayName: String,
    val status: M19PostStatus,
    val commentCount: Int,
    val reactionCount: Int,
    val publishedAt: Long?
)

data class M19Comment(
    val id: String,
    val postId: String,
    val userId: String,
    val authorDisplayName: String,
    val content: String,
    val hidden: Boolean = false,
    val createdAt: Long
) {
    fun toPublicComment(): M19PublicComment = M19PrivacySanitizer.toPublicComment(this)
}

data class M19PublicComment(
    val id: String,
    val postId: String,
    val authorDisplayName: String,
    val content: String,
    val createdAt: Long
)

data class M19Reaction(
    val id: String,
    val postId: String,
    val userId: String,
    val reactionType: M19ReactionType,
    val createdAt: Long
)

data class M19EngagementSummary(
    val likeCount: Int = 0,
    val supportCount: Int = 0,
    val celebrateCount: Int = 0,
    val commentCount: Int = 0
) {
    val reactionCount: Int get() = likeCount + supportCount + celebrateCount
}

data class M19FeedFilter(
    val query: String = "",
    val organizationId: String? = null,
    val publishedOnly: Boolean = true
)

data class CreateM19PostInput(
    val organizationId: String,
    val title: String,
    val content: String,
    val coverImageRef: String? = null
)

data class UpdateM19PostInput(
    val postId: String,
    val title: String,
    val content: String,
    val coverImageRef: String? = null
)

object M19PermissionCodes {
    const val SOCIAL_VIEW = "social.view"
    const val SOCIAL_MANAGE = "social.manage"
}

object M19MockOrganizations {
    const val ORG_NORTE = M16MockOrganizations.ORG_NORTE
    const val ORG_SUR = M16MockOrganizations.ORG_SUR
    const val ORG_OESTE = M16MockOrganizations.ORG_OESTE

    val MANAGE_ORGANIZATION_IDS = M16MockOrganizations.MANAGE_ORGANIZATION_IDS
}

val M19_ELIGIBLE_ORGANIZATION_TYPES: Set<OrganizationType> = setOf(
    OrganizationType.SHELTER,
    OrganizationType.RESCUE_GROUP,
    OrganizationType.NGO,
    OrganizationType.TRAINING_CENTER,
    OrganizationType.VETERINARY_CLINIC
)

object M19EngagementCalculator {
    fun summarize(
        postId: String,
        reactions: List<M19Reaction>,
        comments: List<M19Comment>
    ): M19EngagementSummary {
        val postReactions = reactions.filter { it.postId == postId }
        val visibleComments = comments.filter { it.postId == postId && !it.hidden }
        return M19EngagementSummary(
            likeCount = postReactions.count { it.reactionType == M19ReactionType.LIKE },
            supportCount = postReactions.count { it.reactionType == M19ReactionType.SUPPORT },
            celebrateCount = postReactions.count { it.reactionType == M19ReactionType.CELEBRATE },
            commentCount = visibleComments.size
        )
    }
}

object M19PrivacySanitizer {
    private val emailPattern = Regex("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}")
    private val phonePattern = Regex("(?i)(\\+?\\d[\\d\\s().-]{6,}\\d)")

    fun scrubPublicText(text: String): String =
        text.replace(emailPattern, "[redactado]").replace(phonePattern, "[redactado]")

    fun toPublicPost(post: M19Post, engagement: M19EngagementSummary): M19PublicPost =
        M19PublicPost(
            id = post.id,
            title = scrubPublicText(post.title),
            content = scrubPublicText(post.content),
            organizationDisplayName = scrubPublicText(post.organizationDisplayName),
            authorDisplayName = scrubPublicText(post.authorDisplayName),
            status = post.status,
            coverImageRef = post.coverImageRef,
            likeCount = engagement.likeCount,
            supportCount = engagement.supportCount,
            celebrateCount = engagement.celebrateCount,
            commentCount = engagement.commentCount,
            publishedAt = post.publishedAt,
            createdAt = post.createdAt
        )

    fun toPublicComment(comment: M19Comment): M19PublicComment =
        M19PublicComment(
            id = comment.id,
            postId = comment.postId,
            authorDisplayName = scrubPublicText(comment.authorDisplayName),
            content = scrubPublicText(comment.content),
            createdAt = comment.createdAt
        )

    fun toSummary(post: M19Post, engagement: M19EngagementSummary): M19PostSummary =
        M19PostSummary(
            id = post.id,
            title = scrubPublicText(post.title),
            organizationDisplayName = scrubPublicText(post.organizationDisplayName),
            authorDisplayName = scrubPublicText(post.authorDisplayName),
            status = post.status,
            commentCount = engagement.commentCount,
            reactionCount = engagement.reactionCount,
            publishedAt = post.publishedAt
        )
}
