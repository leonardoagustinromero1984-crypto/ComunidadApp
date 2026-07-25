package com.comunidapp.app.data.repository

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Generador local de número de pasaporte LeoVer.
 * Formato: LV-AR-YYYY-XXXXXXXX — sin PII; passportNumber ≠ publicCode.
 */
class M14PassportNumberGenerator(
    private val clockYear: () -> Int = {
        java.time.Year.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires")).value
    },
    private val sequence: AtomicInteger = AtomicInteger(0)
) {
    private val publicSeq = AtomicLong(0)

    @Synchronized
    fun nextPassportNumber(): String {
        val year = clockYear()
        val n = sequence.incrementAndGet()
        require(n > 0) { "SEQUENCE_OVERFLOW" }
        return "LV-AR-$year-${n.toString().padStart(8, '0')}"
    }

    @Synchronized
    fun nextPublicCode(): String {
        val n = publicSeq.incrementAndGet()
        // Código público opaco alfanumérico, distinto del número de pasaporte.
        return "PUB-" + n.toString(36).uppercase().padStart(10, 'A')
    }
}
