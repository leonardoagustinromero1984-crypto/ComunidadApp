package com.comunidapp.app.domain.pets

import com.comunidapp.app.data.model.LostFoundType

/**
 * Presentation name. A found animal whose real name is unknown stays stored as
 * null or the legacy placeholder "Sin nombre" (origin FOUND_CASE). The visible
 * label is "Encontrado" and is never written back as the real name.
 */
object PetDisplayName {
    const val FOUND_UNKNOWN = "Encontrado"
    const val LEGACY_UNKNOWN = "Sin nombre"
    const val LOST_STATUS_ACTION = "Perdido"

    fun isUnknownFound(originKind: String?, storedName: String?): Boolean {
        if (!originKind.equals("FOUND_CASE", ignoreCase = true)) return false
        val name = storedName?.trim().orEmpty()
        return name.isEmpty() || name.equals(LEGACY_UNKNOWN, ignoreCase = true)
    }

    fun of(originKind: String?, storedName: String?): String {
        if (isUnknownFound(originKind, storedName)) return FOUND_UNKNOWN
        return storedName?.trim().orEmpty().ifBlank { LEGACY_UNKNOWN }
    }

    /**
     * Lost action copy follows origin, not a string compare against the display name.
     * A named pet keeps "Perdí a {name}". An unnamed found animal uses "Perdido".
     */
    fun lostActionLabel(originKind: String?, storedName: String?): String {
        if (isUnknownFound(originKind, storedName)) return LOST_STATUS_ACTION
        val visible = of(originKind, storedName)
        if (visible.isBlank() || visible == LEGACY_UNKNOWN) return LOST_STATUS_ACTION
        return "Perdí a $visible"
    }

    fun alertSubject(type: LostFoundType, petName: String?, speciesLabel: String): String {
        val raw = petName?.trim().orEmpty()
        val unknown = raw.isEmpty() || raw.equals(LEGACY_UNKNOWN, ignoreCase = true)
        if (type == LostFoundType.FOUND && unknown) return FOUND_UNKNOWN
        if (raw.isNotEmpty()) return raw
        return "$speciesLabel sin nombre"
    }

    /** Value safe to persist. Never the presentation label "Encontrado". */
    fun persistableName(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed.equals(FOUND_UNKNOWN, ignoreCase = true)) return null
        return trimmed
    }
}
