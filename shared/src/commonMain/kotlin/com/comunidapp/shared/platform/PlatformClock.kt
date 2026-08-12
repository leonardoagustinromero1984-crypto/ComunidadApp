package com.comunidapp.shared.platform

/**
 * Reloj inyectable para reglas de dominio (epoch ms).
 * Evita System.currentTimeMillis / java.time en commonMain.
 */
fun interface PlatformClock {
    fun nowEpochMs(): Long

    companion object {
        val SYSTEM: PlatformClock = PlatformClock { defaultNowEpochMs() }
    }
}

internal expect fun defaultNowEpochMs(): Long
