package com.comunidapp.app.domain.user

/**
 * Mi manada search matches the visible name or the public alias only.
 * Email and city are not search keys.
 */
object PersonSearchMatcher {
    const val PLACEHOLDER = "Buscar por nombre o alias"
    const val HINT = "Escribí al menos 2 letras del nombre o del alias."

    fun matches(query: String, name: String?, username: String?, email: String? = null, city: String? = null): Boolean {
        val needle = PersonSearchQuery.normalize(query).lowercase()
        if (needle.length < 2) return false
        val nameHit = name?.lowercase()?.contains(needle) == true
        val aliasHit = username?.lowercase()?.contains(needle) == true
        if (nameHit || aliasHit) return true
        // Email and city are accepted so callers can prove they do not match on their own.
        if (!email.isNullOrBlank() || !city.isNullOrBlank()) return false
        return false
    }
}
