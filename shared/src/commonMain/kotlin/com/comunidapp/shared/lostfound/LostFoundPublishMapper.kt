package com.comunidapp.shared.lostfound

import com.comunidapp.shared.domain.lostfound.LostFoundCaseStatus
import com.comunidapp.shared.domain.lostfound.LostFoundCaseType
import com.comunidapp.shared.session.SessionUser

/**
 * Mapeo draft → wire insert. Status inicial = ACTIVE (contrato Android/DB).
 * author_* solo desde sesión — nunca desde UI libre.
 */
internal object LostFoundPublishMapper {
    fun initialStatus(): LostFoundCaseStatus = LostFoundCaseStatus.ACTIVE

    fun speciesWire(label: String): String =
        when (label.trim().uppercase()) {
            "PERRO", "DOG" -> "DOG"
            "GATO", "CAT" -> "CAT"
            "OTRO", "OTHER" -> "OTHER"
            else -> label.trim().uppercase().ifBlank { "OTHER" }
        }

    fun resolveContactInfo(draft: LostFoundDraft, session: SessionUser): String {
        draft.contactNote?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        val who = session.displayName?.takeIf { it.isNotBlank() }
            ?: session.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
        return if (who != null) "Contactar por LeoVer ($who)" else "Contactar por LeoVer"
    }

    fun authorName(session: SessionUser): String =
        session.displayName?.takeIf { it.isNotBlank() }
            ?: session.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: "Usuario LeoVer"

    fun locationText(draft: LostFoundDraft): String =
        draft.approximateLocation.displayLabel()

    fun typeWire(type: LostFoundCaseType): String = type.name
}
