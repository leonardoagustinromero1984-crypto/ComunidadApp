package com.comunidapp.app.data.model

import java.util.concurrent.TimeUnit

/**
 * LeoVer M15 Bloque 4 — métricas operativas agregadas, consulta y privacidad.
 * Sin PII: sin userId, petId, homeId, notas, descripciones ni IDs en métricas.
 */

/** Zona horaria determinista para agregación local. */
data class M15MetricsPolicy(
    val zoneIdName: String = DEFAULT_ZONE,
    val maxRangeDays: Int = DEFAULT_MAX_RANGE_DAYS
) {
    init {
        require(maxRangeDays > 0)
    }

    companion object {
        const val DEFAULT_ZONE = "America/Argentina/Buenos_Aires"
        const val DEFAULT_MAX_RANGE_DAYS = 366
    }
}

/** Rango semiabierto [fromInclusive, toExclusive) en epoch ms UTC. */
data class M15OperationalMetricsQuery(
    val fromInclusive: Long,
    val toExclusive: Long,
    val zoneIdName: String = M15MetricsPolicy.DEFAULT_ZONE
) {
    fun validate(policy: M15MetricsPolicy = M15MetricsPolicy()): String? {
        if (fromInclusive >= toExclusive) return "M15_METRICS_INVALID_RANGE"
        val maxMs = TimeUnit.DAYS.toMillis(policy.maxRangeDays.toLong())
        if (toExclusive - fromInclusive > maxMs) return "M15_METRICS_INVALID_RANGE"
        return try {
            java.time.ZoneId.of(zoneIdName)
            null
        } catch (_: Exception) {
            "M15_METRICS_INVALID_RANGE"
        }
    }
}

/** Métricas agregadas sin PII ni payloads. */
data class M15OperationalMetrics(
    val fromInclusive: Long,
    val toExclusive: Long,
    val zoneIdName: String,
    val homesByStatus: Map<String, Int>,
    val homesByAvailability: Map<String, Int>,
    val totalCapacity: Int,
    val occupiedSlots: Int,
    val reservedSlots: Int,
    val availableSlots: Int,
    val requestsByStatus: Map<String, Int>,
    val requestsSubmitted: Int,
    val requestsAccepted: Int,
    val requestsRejected: Int,
    val requestsCancelled: Int,
    val requestsExpired: Int,
    val avgMinutesToResolution: Double?,
    val placementsByStatus: Map<String, Int>,
    val placementsReserved: Int,
    val placementsActive: Int,
    val placementsCompleted: Int,
    val placementsInterrupted: Int,
    val placementsCancelled: Int,
    val dischargesByReason: Map<String, Int>,
    val dischargesByOutcome: Map<String, Int>,
    val evolutionByType: Map<String, Int>,
    val evolutionHealthAlerts: Int,
    val evolutionIncidents: Int,
    val expensesByStatus: Map<String, Int>,
    val expensesByCategory: Map<String, Int>,
    val expenseSumByCurrency: Map<String, Long>,
    val helpByType: Map<String, Int>,
    val helpByStatus: Map<String, Int>,
    val helpByPriority: Map<String, Int>,
    val helpOpen: Int,
    val helpInProgress: Int,
    val helpResolved: Int,
    val helpCancelled: Int,
    val helpExpired: Int,
    val conflicts: Int,
    val idempotentRetries: Int,
    val remoteFallbacks: Int,
    val errorsByCode: Map<String, Int>
)

/** Resultado de intento M06 best-effort (sin PII). */
data class M15M06PublishResult(
    val eventKey: String,
    val published: Boolean,
    val code: String
)

/**
 * Sanitización defensiva de proyecciones y métricas M15.
 * Nunca expone dirección, contacto, IDs internos ni notas privadas en vistas públicas.
 */
object M15PrivacySanitizer {

    private val emailPattern = Regex("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}")
    private val phonePattern = Regex("(?i)(\\+?\\d[\\d\\s().-]{6,}\\d)")
    private val coordPattern = Regex("(?i)-?\\d{1,3}\\.\\d{3,}")
    private val forbiddenMetricKeys = setOf(
        "userid", "petid", "homeid", "requestid", "placementid", "organizationid",
        "address", "phone", "email", "coordinates", "microchip", "publiccode",
        "summary", "description", "receipt", "url", "name", "note"
    )

    fun sanitizePublicListing(source: M15FosterHome): M15FosterHomePublicListing =
        M15FosterHomePublicListing(
            id = source.id,
            displayName = scrubContact(source.displayName),
            description = source.description?.let { scrubContact(it) }?.takeIf { it.isNotEmpty() },
            availabilityStatus = source.availabilityStatus,
            totalCapacity = source.totalCapacity.coerceAtLeast(0),
            freeSlots = source.freeSlots,
            acceptedSpecies = source.acceptedSpecies,
            acceptedSizes = source.acceptedSizes,
            acceptsSpecialNeeds = source.acceptsSpecialNeeds,
            acceptsEmergencies = source.acceptsEmergencies,
            zoneText = scrubContact(source.zoneText),
            publicLocationText = source.publicLocationText?.let { scrubContact(it) }
                ?.takeIf { it.isNotEmpty() }
        )

    fun scrubContact(text: String): String =
        text.replace(emailPattern, "[redactado]")
            .replace(phonePattern, "[redactado]")
            .replace(coordPattern, "[zona]")

    /** Rechaza claves que podrían filtrar PII en logs o métricas. */
    fun assertMetricKeySafe(key: String): Boolean {
        val lower = key.lowercase()
        return forbiddenMetricKeys.none { lower.contains(it) }
    }

    /** Log seguro: solo código, categoría, estado, timestamp y contador. */
    fun safeLogLine(
        code: String,
        category: String,
        status: String,
        timestampMs: Long,
        count: Int
    ): String = "M15|$code|$category|$status|$timestampMs|count=$count"
}
