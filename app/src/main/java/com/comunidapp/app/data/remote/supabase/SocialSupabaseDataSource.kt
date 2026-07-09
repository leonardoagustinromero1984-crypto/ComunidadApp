package com.comunidapp.app.data.remote.supabase

import com.comunidapp.app.data.model.AdoptionRequest
import com.comunidapp.app.data.model.AdoptionRequestStatus
import com.comunidapp.app.data.model.PostComment
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PostLikeRow(
    @SerialName("post_id") val postId: String,
    @SerialName("user_id") val userId: String
)

@Serializable
data class PostCommentRow(
    val id: String,
    @SerialName("post_id") val postId: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("author_name") val authorName: String,
    val content: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class AdoptionRequestRow(
    val id: String,
    @SerialName("adoption_id") val adoptionId: String,
    @SerialName("applicant_id") val applicantId: String,
    @SerialName("applicant_name") val applicantName: String,
    val message: String,
    val phone: String? = null,
    val status: String = AdoptionRequestStatus.PENDING.name,
    @SerialName("created_at") val createdAt: String? = null
)

class SocialSupabaseDataSource {

    private val likedCache = MutableStateFlow<Set<String>>(emptySet())

    suspend fun fetchLikedPostIds(userId: String): Set<String> {
        return try {
            supabase.from(SupabaseTables.POST_LIKES)
                .select {
                    filter { eq("user_id", userId) }
                }
                .decodeList<PostLikeRow>()
                .map { it.postId }
                .toSet()
                .also { likedCache.value = it }
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun observeLikedPostIds(userId: String): Flow<Set<String>> =
        kotlinx.coroutines.flow.flow {
            while (true) {
                emit(fetchLikedPostIds(userId))
                kotlinx.coroutines.delay(4_000)
            }
        }

    suspend fun toggleLike(postId: String, userId: String): Result<Boolean> {
        return try {
            val existing = supabase.from(SupabaseTables.POST_LIKES)
                .select {
                    filter {
                        eq("post_id", postId)
                        eq("user_id", userId)
                    }
                }
                .decodeList<PostLikeRow>()

            val liked = if (existing.isEmpty()) {
                supabase.from(SupabaseTables.POST_LIKES).insert(PostLikeRow(postId, userId))
                val post = supabase.from(SupabaseTables.POSTS)
                    .select { filter { eq("id", postId) } }
                    .decodeSingleOrNull<PostRow>()
                post?.let {
                    supabase.from(SupabaseTables.POSTS).update(
                        mapOf("like_count" to it.likeCount + 1, "updated_at" to nowIso())
                    ) { filter { eq("id", postId) } }
                }
                true
            } else {
                supabase.from(SupabaseTables.POST_LIKES).delete {
                    filter {
                        eq("post_id", postId)
                        eq("user_id", userId)
                    }
                }
                val post = supabase.from(SupabaseTables.POSTS)
                    .select { filter { eq("id", postId) } }
                    .decodeSingleOrNull<PostRow>()
                post?.let {
                    supabase.from(SupabaseTables.POSTS).update(
                        mapOf(
                            "like_count" to (it.likeCount - 1).coerceAtLeast(0),
                            "updated_at" to nowIso()
                        )
                    ) { filter { eq("id", postId) } }
                }
                false
            }
            likedCache.update { current ->
                if (liked) current + postId else current - postId
            }
            Result.success(liked)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchComments(postId: String): List<PostComment> {
        return try {
            supabase.from(SupabaseTables.POST_COMMENTS)
                .select {
                    filter { eq("post_id", postId) }
                    order("created_at", Order.ASCENDING)
                }
                .decodeList<PostCommentRow>()
                .map(::parseComment)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun observeComments(postId: String): Flow<List<PostComment>> =
        kotlinx.coroutines.flow.flow {
            while (true) {
                emit(fetchComments(postId))
                kotlinx.coroutines.delay(4_000)
            }
        }

    suspend fun addComment(
        postId: String,
        authorId: String,
        authorName: String,
        content: String
    ): Result<Unit> {
        return try {
            val row = PostCommentRow(
                id = java.util.UUID.randomUUID().toString(),
                postId = postId,
                authorId = authorId,
                authorName = authorName,
                content = content,
                createdAt = nowIso()
            )
            supabase.from(SupabaseTables.POST_COMMENTS).insert(row)
            val post = supabase.from(SupabaseTables.POSTS)
                .select { filter { eq("id", postId) } }
                .decodeSingleOrNull<PostRow>()
            post?.let {
                supabase.from(SupabaseTables.POSTS).update(
                    mapOf("comment_count" to it.commentCount + 1, "updated_at" to nowIso())
                ) { filter { eq("id", postId) } }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitAdoptionRequest(request: AdoptionRequest): Result<String> {
        return try {
            val row = if (request.id.isBlank()) {
                request.toAdoptionRequestRow().copy(id = java.util.UUID.randomUUID().toString())
            } else {
                request.toAdoptionRequestRow()
            }
            supabase.from(SupabaseTables.ADOPTION_REQUESTS).insert(row)
            Result.success(row.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAdoptionRequestsForAdoption(adoptionId: String): List<AdoptionRequest> {
        return try {
            supabase.from(SupabaseTables.ADOPTION_REQUESTS)
                .select {
                    filter { eq("adoption_id", adoptionId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<AdoptionRequestRow>()
                .map(::parseAdoptionRequest)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchAdoptionRequestsForPublisher(publisherId: String): List<AdoptionRequest> {
        return try {
            val adoptionIds = supabase.from(SupabaseTables.ADOPTIONS)
                .select {
                    filter { eq("publisher_id", publisherId) }
                }
                .decodeList<IdRow>()
                .map { it.id }
                .toSet()
            if (adoptionIds.isEmpty()) return emptyList()

            supabase.from(SupabaseTables.ADOPTION_REQUESTS)
                .select { order("created_at", Order.DESCENDING) }
                .decodeList<AdoptionRequestRow>()
                .map(::parseAdoptionRequest)
                .filter { it.adoptionId in adoptionIds }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun updateAdoptionRequestStatus(id: String, status: AdoptionRequestStatus): Result<Unit> {
        return try {
            supabase.from(SupabaseTables.ADOPTION_REQUESTS).update(
                mapOf("status" to status.name)
            ) { filter { eq("id", id) } }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

fun parseComment(row: PostCommentRow): PostComment = PostComment(
    id = row.id,
    postId = row.postId,
    authorId = row.authorId,
    authorName = row.authorName,
    content = row.content,
    createdAt = row.createdAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
)

fun PostComment.toCommentRow(): PostCommentRow = PostCommentRow(
    id = id,
    postId = postId,
    authorId = authorId,
    authorName = authorName,
    content = content,
    createdAt = createdAt?.let { java.time.Instant.ofEpochMilli(it).toString() }
)

fun parseAdoptionRequest(row: AdoptionRequestRow): AdoptionRequest = AdoptionRequest(
    id = row.id,
    adoptionId = row.adoptionId,
    applicantId = row.applicantId,
    applicantName = row.applicantName,
    message = row.message,
    phone = row.phone,
    status = AdoptionRequestStatus.fromString(row.status),
    createdAt = row.createdAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
)

fun AdoptionRequest.toAdoptionRequestRow(): AdoptionRequestRow = AdoptionRequestRow(
    id = id,
    adoptionId = adoptionId,
    applicantId = applicantId,
    applicantName = applicantName,
    message = message,
    phone = phone,
    status = status.name,
    createdAt = createdAt?.let { java.time.Instant.ofEpochMilli(it).toString() }
)
