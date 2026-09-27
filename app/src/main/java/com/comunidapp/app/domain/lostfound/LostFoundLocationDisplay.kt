package com.comunidapp.app.domain.lostfound

import com.comunidapp.app.domain.location.AddressSuggestion
import com.comunidapp.app.domain.ux.HumanLocationLabel

object LostFoundLocationDisplay {
    const val MAP_PLACEHOLDER = "Zona marcada en el mapa"
    const val NEARBY_FALLBACK = "Zona cercana"

    fun humanLabel(
        locationLabel: String? = null,
        localityId: String? = null,
        note: String? = null,
        suggestion: AddressSuggestion? = null
    ): String {
        suggestion?.let { fromSuggestion(it) }?.let { return it }
        locationLabel?.trim()?.takeIf { it.isNotBlank() && !isPlaceholder(it) }?.let { return it }
        HumanLocationLabel.visible(localityId).takeIf { it.isNotBlank() && !isPlaceholder(it) }?.let { return it }
        notePrefix(note)?.let { return it }
        return ""
    }

    fun visibleOrNearby(
        locationLabel: String? = null,
        localityId: String? = null,
        note: String? = null
    ): String = humanLabel(locationLabel, localityId, note).ifBlank { NEARBY_FALLBACK }

    fun fromSuggestion(suggestion: AddressSuggestion): String? {
        val locality = suggestion.locality?.trim().orEmpty()
        val province = suggestion.province?.trim().orEmpty()
        val composed = listOf(locality, province).filter { it.isNotBlank() }.distinct().joinToString(", ")
        if (composed.isNotBlank()) return composed
        val line = suggestion.label.trim()
        if (line.isBlank() || isPlaceholder(line)) return null
        return line.substringBefore(',').trim().ifBlank { line }
    }

    fun noteWithoutPlaceholder(note: String?): String {
        val raw = note?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        val parts = raw.split(" · ").map { it.trim() }.filter { it.isNotBlank() && !isPlaceholder(it) }
        return parts.joinToString(" · ")
    }

    fun isPlaceholder(value: String): Boolean {
        val v = value.trim().lowercase()
        return v == MAP_PLACEHOLDER.lowercase() || v == "zona marcada" || v == NEARBY_FALLBACK.lowercase()
    }

    private fun notePrefix(note: String?): String? {
        val first = note?.substringBefore(" · ")?.trim().orEmpty()
        if (first.isBlank() || isPlaceholder(first) || HumanLocationLabel.looksRawId(first)) return null
        return first
    }
}
