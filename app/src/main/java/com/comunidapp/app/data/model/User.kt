package com.comunidapp.app.data.model

data class User(
    val id: String,
    val name: String,
    val email: String,
    val profileImageUrl: String? = null,
    val bio: String? = null,
    val location: String? = null,
    val petIds: List<String> = emptyList()
)
