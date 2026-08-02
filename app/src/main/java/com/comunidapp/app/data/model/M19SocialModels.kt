package com.comunidapp.app.data.model

import com.comunidapp.app.domain.organization.OrganizationType

/** LeoVer M19 — Red social y contenido (Bloques 1–3). */

enum class M19PostVisibility {
    PUBLIC,
    ORGANIZATION
}

enum class M19PostStatus {
    DRAFT,
    PUBLISHED,
    HIDDEN,
    ARCHIVED,
    REMOVED,
    REMOVED_BY_MODERATION;

    val isPublicFeed: Boolean get() = this == PUBLISHED
    val isTerminal: Boolean get() = this in setOf(REMOVED, REMOVED_BY_MODERATION, ARCHIVED)
}

enum class M19ReactionType {
    LIKE,
    LOVE,
    SUPPORT,
    CELEBRATE
}

enum class M19ContentReferenceType {
    PET,
    ORGANIZATION,
    SHELTER,
    CAMPAIGN,
    EVENT
}

enum class M19FeedFilterKind {
    ALL,
    ORGANIZATIONS,
    PETS,
    SHELTERS,
    CAMPAIGNS,
    EVENTS,
    MEDIA,
    TEXT
}

data class M19ContentReference(
    val type: M19ContentReferenceType,
    val targetId: String,
    val displayLabel: String,
    val isPublic: Boolean = true
)

data class M19PublicContentReference(
    val type: M19ContentReferenceType,
    val displayLabel: String,
    val routeHint: String
)

data class M19MediaAttachment(
    val ref: String,
    val isPublic: Boolean = true,
    val mimeHint: String? = null
)

data class M19Post(
    val id: String,
    val organizationId: String,
    val organizationDisplayName: String,
    val authorUserId: String,
    val authorDisplayName: String,
    val title: String,
    val content: String,
    val status: M19PostStatus,
    val visibility: M19PostVisibility = M19PostVisibility.PUBLIC,
    val coverImageRef: String? = null,
    val mediaAttachments: List<M19MediaAttachment> = emptyList(),
    val contentReferences: List<M19ContentReference> = emptyList(),
    val moderationStatus: String? = null,
    val publishedAt: Long? = null,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun isModeratedBlocked(): Boolean =
        moderationStatus in setOf("BLOCKED", "HIDDEN", "PENDING")

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
    val visibility: M19PostVisibility = M19PostVisibility.PUBLIC,
    val coverImageRef: String? = null,
    val mediaAttachments: List<M19MediaAttachment> = emptyList(),
    val contentReferences: List<M19PublicContentReference> = emptyList(),
    val likeCount: Int,
    val loveCount: Int,
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
    val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long = createdAt
) {
    fun toPublicComment(): M19PublicComment? =
        if (hidden || archived) null else M19PrivacySanitizer.toPublicComment(this)
}

data class M19PublicComment(
    val id: String,
    val postId: String,
    val authorDisplayName: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long
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
    val loveCount: Int = 0,
    val supportCount: Int = 0,
    val celebrateCount: Int = 0,
    val commentCount: Int = 0
) {
    val reactionCount: Int get() = likeCount + loveCount + supportCount + celebrateCount
}

data class M19FeedFilter(
    val query: String = "",
    val organizationId: String? = null,
    val kind: M19FeedFilterKind = M19FeedFilterKind.ALL,
    val publishedOnly: Boolean = true,
    val cursor: String? = null,
    val pageSize: Int = DEFAULT_PAGE_SIZE
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 10
    }
}

data class M19FeedPage(
    val items: List<M19PublicPost>,
    val nextCursor: String?,
    val hasMore: Boolean
)

data class CreateM19PostInput(
    val organizationId: String,
    val title: String,
    val content: String,
    val coverImageRef: String? = null,
    val visibility: M19PostVisibility = M19PostVisibility.PUBLIC,
    val mediaAttachments: List<M19MediaAttachment> = emptyList(),
    val contentReferences: List<M19ContentReference> = emptyList()
)

data class UpdateM19PostInput(
    val postId: String,
    val title: String,
    val content: String,
    val coverImageRef: String? = null,
    val visibility: M19PostVisibility? = null,
    val mediaAttachments: List<M19MediaAttachment>? = null,
    val contentReferences: List<M19ContentReference>? = null
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

object M19MockReferenceIds {
    const val PET = "mock_pet_m19_1"
    const val SHELTER = "mock_shelter_m19_1"
    const val CAMPAIGN = "mock_campaign_m19_1"
    const val EVENT = "mock_event_m19_1"
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
        val visibleComments = comments.filter { it.postId == postId && !it.hidden && !it.archived }
        return M19EngagementSummary(
            likeCount = postReactions.count { it.reactionType == M19ReactionType.LIKE },
            loveCount = postReactions.count { it.reactionType == M19ReactionType.LOVE },
            supportCount = postReactions.count { it.reactionType == M19ReactionType.SUPPORT },
            celebrateCount = postReactions.count { it.reactionType == M19ReactionType.CELEBRATE },
            commentCount = visibleComments.size
        )
    }
}

object M19PrivacySanitizer {
    private val emailPattern = Regex("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}")
    private val phonePattern = Regex("(?i)(\\+?\\d[\\d\\s().-]{6,}\\d)")
    private val htmlTagPattern = Regex("<[^>]+>")
    private val scriptPattern = Regex("(?i)(javascript:|on\\w+\\s*=)")
    private val controlChars = Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]")

    fun scrubPublicText(text: String): String =
        text.replace(controlChars, "")
            .replace(htmlTagPattern, "")
            .replace(scriptPattern, "")
            .replace(emailPattern, "[redactado]")
            .replace(phonePattern, "[redactado]")
            .trim()

    fun publicMedia(attachments: List<M19MediaAttachment>): List<M19MediaAttachment> =
        attachments.filter { it.isPublic && it.ref.isNotBlank() }

    fun publicReferences(refs: List<M19ContentReference>): List<M19PublicContentReference> =
        refs.filter { it.isPublic }
            .map {
                M19PublicContentReference(
                    type = it.type,
                    displayLabel = scrubPublicText(it.displayLabel),
                    routeHint = routeHintFor(it.type, it.targetId)
                )
            }

    private fun routeHintFor(type: M19ContentReferenceType, targetId: String): String =
        when (type) {
            M19ContentReferenceType.PET -> "m08/pets/$targetId"
            M19ContentReferenceType.ORGANIZATION -> "m03/orgs/$targetId"
            M19ContentReferenceType.SHELTER -> "m16/shelters/$targetId"
            M19ContentReferenceType.CAMPAIGN -> "m17/campaigns/$targetId"
            M19ContentReferenceType.EVENT -> "m18/events/$targetId"
        }

    fun toPublicPost(post: M19Post, engagement: M19EngagementSummary): M19PublicPost {
        val publicCover = post.coverImageRef?.takeIf { ref ->
            post.mediaAttachments.none { it.ref == ref && !it.isPublic }
        }
        return M19PublicPost(
            id = post.id,
            title = scrubPublicText(post.title),
            content = scrubPublicText(post.content),
            organizationDisplayName = scrubPublicText(post.organizationDisplayName),
            authorDisplayName = scrubPublicText(post.authorDisplayName),
            status = post.status,
            visibility = post.visibility,
            coverImageRef = publicCover,
            mediaAttachments = publicMedia(post.mediaAttachments),
            contentReferences = publicReferences(post.contentReferences),
            likeCount = engagement.likeCount,
            loveCount = engagement.loveCount,
            supportCount = engagement.supportCount,
            celebrateCount = engagement.celebrateCount,
            commentCount = engagement.commentCount,
            publishedAt = post.publishedAt,
            createdAt = post.createdAt
        )
    }

    fun toPublicComment(comment: M19Comment): M19PublicComment =
        M19PublicComment(
            id = comment.id,
            postId = comment.postId,
            authorDisplayName = scrubPublicText(comment.authorDisplayName),
            content = scrubPublicText(comment.content),
            createdAt = comment.createdAt,
            updatedAt = comment.updatedAt
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
