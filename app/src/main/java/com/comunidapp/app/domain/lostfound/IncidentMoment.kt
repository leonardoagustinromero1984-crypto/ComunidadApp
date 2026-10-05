package com.comunidapp.app.domain.lostfound

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Date and time of a lost/found event or a sighting. Default is now. Editable. */
object IncidentMoment {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC)
    private val combined = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    fun dateText(epochMs: Long): String = dateFmt.format(Instant.ofEpochMilli(epochMs))

    fun timeText(epochMs: Long): String = timeFmt.format(Instant.ofEpochMilli(epochMs))

    fun combine(date: String, time: String, fallback: Long): Long {
        val parsed = runCatching {
            LocalDateTime.parse("${date.trim()}T${time.trim()}", combined)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        }.getOrNull()
        return parsed ?: fallback
    }

    fun stored(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()

    fun withNote(description: String, epochMs: Long): String {
        val line = "Hecho: ${dateText(epochMs)} ${timeText(epochMs)}"
        if (description.contains(line)) return description
        return if (description.isBlank()) line else "$description\n$line"
    }
}
