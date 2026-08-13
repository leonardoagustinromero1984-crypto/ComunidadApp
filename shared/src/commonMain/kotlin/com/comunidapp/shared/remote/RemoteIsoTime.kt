package com.comunidapp.shared.remote

/**
 * Parse ISO-8601 → epoch ms (plataforma).
 * Fallo → 0L (UI no depende de precisión absoluta).
 */
internal expect fun parseIso8601ToEpochMs(iso: String?): Long
