package com.comunidapp.app.data.model

data class AuthAccount(
    val email: String,
    val password: String,
    val name: String,
    val emailVerified: Boolean = false,
    val resetToken: String? = null
)
