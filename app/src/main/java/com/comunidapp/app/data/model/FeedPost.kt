package com.comunidapp.app.data.model

data class FeedPost(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorImageUrl: String? = null,
    val type: PostType,
    val title: String,
    val content: String,
    val imageUrl: String? = null,
    val locationText: String? = null,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val date: String = ""
)
