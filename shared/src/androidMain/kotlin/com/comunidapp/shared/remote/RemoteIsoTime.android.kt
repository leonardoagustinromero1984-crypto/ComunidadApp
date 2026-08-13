package com.comunidapp.shared.remote

import java.time.Instant
import java.time.format.DateTimeParseException

internal actual fun parseIso8601ToEpochMs(iso: String?): Long {
    if (iso.isNullOrBlank()) return 0L
    return try {
        Instant.parse(iso.trim()).toEpochMilli()
    } catch (_: DateTimeParseException) {
        try {
            Instant.parse(iso.trim().replace(" ", "T")).toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    } catch (_: Exception) {
        0L
    }
}
