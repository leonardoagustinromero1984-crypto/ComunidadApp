package com.comunidapp.app.ui.util

import com.comunidapp.app.data.model.FeedPost
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val displayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())

fun isoDateFromMillis(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(isoFormatter)

fun millisFromIsoDate(isoDate: String): Long? = runCatching {
    LocalDate.parse(isoDate, isoFormatter)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}.getOrNull()

fun formatDisplayDate(isoDate: String): String = runCatching {
    LocalDate.parse(isoDate, isoFormatter).format(displayFormatter)
}.getOrElse { isoDate }

fun FeedPost.displayDate(): String {
    if (date.isNotBlank()) return date
    val millis = createdAt ?: return ""
    return formatRelativeTime(millis)
}

fun formatRelativeTime(millis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - millis
    if (diff < TimeUnit.MINUTES.toMillis(1)) return "Ahora"
    if (diff < TimeUnit.HOURS.toMillis(1)) {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        return "Hace ${minutes.coerceAtLeast(1)} min"
    }
    if (diff < TimeUnit.DAYS.toMillis(1)) {
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        return "Hace $hours h"
    }
    if (diff < TimeUnit.DAYS.toMillis(7)) {
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        return if (days == 1L) "Ayer" else "Hace $days días"
    }
    return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(millis))
}
