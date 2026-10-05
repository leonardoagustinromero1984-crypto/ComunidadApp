package com.comunidapp.app.domain.lostfound

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Human date for a public lost/found card. Never the raw ISO instant. */
object LostFoundWhenLabel {
    private val formatter = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale("es", "AR"))

    fun of(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return "Fecha no disponible"
        val instant = runCatching { Instant.parse(value) }.getOrNull()
            ?: runCatching { Instant.parse(value.substringBefore("[")) }.getOrNull()
        if (instant != null) {
            return formatter.format(instant.atZone(ZoneId.systemDefault()))
        }
        if (value.contains("T") && value.length >= 16) return "Fecha no disponible"
        return value
    }
}
