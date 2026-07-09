package com.comunidapp.app.data.model

enum class FriendConnectionStatus {
    PENDING,
    ACCEPTED,
    REJECTED;

    companion object {
        fun fromString(value: String?): FriendConnectionStatus =
            entries.find { it.name == value } ?: PENDING
    }
}

data class FriendConnection(
    val id: String,
    val requesterId: String,
    val addresseeId: String,
    val status: FriendConnectionStatus = FriendConnectionStatus.PENDING,
    val createdAt: Long? = null
)

/** Relación entre el usuario actual y otro perfil. */
enum class ProfileRelation {
    SELF,
    PUBLIC_PROFILE,
    FRIENDS,
    PENDING_OUTGOING,
    PENDING_INCOMING,
    LOCKED
}
