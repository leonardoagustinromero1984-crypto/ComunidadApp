package com.comunidapp.shared.deeplink

/**
 * Mapea extras M06 (`deep_link_type` + `resource_id`) a [DeepLinkTarget].
 * Sin URI arbitraria — solo tipos allowlisted.
 */
object NotificationIntentParser {

    private val publicCodePattern = Regex("^[A-Za-z0-9_\\-.:]{1,128}$")

    fun fromPushExtras(deepLinkType: String?, resourceId: String?): DeepLinkTarget {
        val type = deepLinkType?.trim()?.uppercase().orEmpty()
        if (type.isEmpty()) {
            return DeepLinkTarget.SafeHome
        }
        val id = resourceId?.trim()?.takeIf { it.isNotEmpty() }
        if (id != null && !publicCodePattern.matches(id)) {
            return DeepLinkTarget.SafeHome
        }
        return when (type) {
            "SAFE_HOME", "NOTIFICATIONS_INBOX" -> DeepLinkTarget.SafeHome
            "PET" -> mapPublicOrSafe(id) { DeepLinkTarget.PetPublic(it) }
            "ADOPTION" -> mapPublicOrSafe(id) { DeepLinkTarget.AdoptionPublic(it) }
            "LOST_FOUND_CASE" -> mapPublicOrSafe(id) { DeepLinkTarget.LostCase(it) }
            "PASSPORT" -> mapPublicOrSafe(id) { DeepLinkTarget.Passport(it) }
            else -> DeepLinkTarget.SafeHome
        }
    }

    /**
     * Solo acepta códigos públicos estilo PUB-… para destinos tipados;
     * UUIDs internos caen a SafeHome (sin inventar fetch por id).
     */
    private fun mapPublicOrSafe(
        resourceId: String?,
        factory: (String) -> DeepLinkTarget
    ): DeepLinkTarget {
        if (resourceId == null) return DeepLinkTarget.SafeHome
        return if (looksLikePublicCode(resourceId)) {
            factory(resourceId)
        } else {
            DeepLinkTarget.SafeHome
        }
    }

    private fun looksLikePublicCode(value: String): Boolean =
        value.startsWith("PUB-", ignoreCase = true) && publicCodePattern.matches(value)
}
