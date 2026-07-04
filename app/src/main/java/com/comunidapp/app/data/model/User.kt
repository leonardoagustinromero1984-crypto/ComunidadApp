package com.comunidapp.app.data.model

data class User(
    val id: String,
    val name: String,
    val email: String,
    val accountType: AccountType = AccountType.PERSON,
    val profileImageUrl: String? = null,
    val bio: String? = null,
    val locationText: String? = null,
    val phone: String? = null,
    val phonePublic: Boolean = false,
    val emailVerified: Boolean = false,
    val fosterHomeActive: Boolean = false,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val petIds: List<String> = emptyList()
)
