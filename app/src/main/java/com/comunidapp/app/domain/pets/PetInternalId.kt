package com.comunidapp.app.domain.pets

import com.comunidapp.app.data.model.Pet
import java.util.UUID

/**
 * Internal pet identity for owner flows (edit, grants, detail).
 * [publicCode] and VitaCora numbers are for public routes/QR only — never pass them as petId.
 */
object PetInternalId {

    private val UUID_PATTERN = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    )

    fun isInternalUuid(value: String): Boolean = UUID_PATTERN.matches(value.trim())

    fun parseUuid(value: String): String? = runCatching {
        UUID.fromString(value.trim()).toString()
    }.getOrNull()?.takeIf { isInternalUuid(it) }

    /**
     * Resolves navigation/repository input to the internal pet UUID.
     * Returns null when the value is clearly not an internal id (e.g. publicCode).
     */
    fun resolveForEdit(raw: String?, accessiblePets: List<Pet> = emptyList()): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        parseUuid(trimmed)?.let { return it }
        accessiblePets.firstOrNull { pet ->
            pet.publicCode?.equals(trimmed, ignoreCase = true) == true ||
                pet.publicVitacoraNumber?.toString() == trimmed
        }?.id?.let { return it }
        return null
    }

    fun rejectPublicIdentifiers(raw: String): Boolean = parseUuid(raw) != null
}
