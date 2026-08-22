package com.comunidapp.app.domain.user

/**
 * Canonical social-profile visibility. Active product values are PUBLIC and PRIVATE only.
 *
 * Canonical PERSON column: `privacy_state` in (`PRIVATE`, `PUBLIC_LIMITED`).
 * PUBLIC maps to `PUBLIC_LIMITED`. PRIVATE maps to `PRIVATE`.
 *
 * PRIVATE is social-profile visibility only. It does not grant or revoke
 * VitaCora, pet responsibility, health, holders, messages, org membership,
 * precise location, or guardian access.
 *
 * Follow graph (existing requester/addressee connections) is the approval
 * mechanism. There is no AMIGO relationship.
 */
object SocialProfileVisibility {

    val selectable: List<ProfileVisibility> = listOf(
        ProfileVisibility.PUBLIC,
        ProfileVisibility.PRIVATE
    )

    fun fromRaw(raw: String?): ProfileVisibility = when (raw?.trim()?.uppercase()) {
        "PUBLIC", "PUBLIC_LIMITED" -> ProfileVisibility.PUBLIC
        "FRIENDS", "FRIENDS_ONLY", "SOLO_AMIGOS" -> ProfileVisibility.PRIVATE
        else -> ProfileVisibility.PRIVATE
    }

    fun toCanonicalPrivacyState(visibility: ProfileVisibility): String =
        if (visibility == ProfileVisibility.PUBLIC) "PUBLIC_LIMITED" else "PRIVATE"

    fun requiresFollowApproval(visibility: ProfileVisibility): Boolean =
        visibility == ProfileVisibility.PRIVATE

    fun label(visibility: ProfileVisibility): String = when (visibility) {
        ProfileVisibility.PUBLIC -> "Público"
        ProfileVisibility.PRIVATE -> "Privado"
    }
}
