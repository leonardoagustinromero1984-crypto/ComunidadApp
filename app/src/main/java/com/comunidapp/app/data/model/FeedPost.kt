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
    val location: String? = null,
    val date: String,
    val likes: Int = 0,
    val comments: Int = 0
)
