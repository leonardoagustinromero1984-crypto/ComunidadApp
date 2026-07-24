package com.comunidapp.app.data.model

/**
 * LeoVer M13 — Avistamientos y coincidencias (Bloque 1 dominio local).
 * Enriquecimiento sobre Lost/Found legacy; sin IA ni autoconfirmación.
 */

enum class M13SightingStatus {
    ACTIVE,
    CONFIRMED,
    DISMISSED,
    WITHDRAWN,
    EXPIRED;

    val isTerminal: Boolean
        get() = this == CONFIRMED || this == DISMISSED || this == WITHDRAWN || this == EXPIRED
}

enum class M13MatchStatus {
    PROPOSED,
    UNDER_REVIEW,
    CONFIRMED,
    REJECTED,
    INCONCLUSIVE,
    WITHDRAWN,
    EXPIRED;

    val isTerminal: Boolean
        get() = this == CONFIRMED || this == REJECTED || this == INCONCLUSIVE ||
            this == WITHDRAWN || this == EXPIRED
}

enum class M13MatchLevel {
    LOW,
    MEDIUM,
    HIGH;

    companion object {
        fun fromScore(score: Int): M13MatchLevel = when {
            score >= 70 -> HIGH
            score >= 40 -> MEDIUM
            else -> LOW
        }
    }
}

enum class M13MatchReason {
    SPECIES_MATCH,
    ZONE_PROXIMITY,
    TIME_PROXIMITY,
    BREED_MATCH,
    COLOR_MATCH,
    SEX_MATCH,
    SIZE_MATCH,
    MANUAL_LINK;

    val labelEs: String
        get() = when (this) {
            SPECIES_MATCH -> "Misma especie"
            ZONE_PROXIMITY -> "Zona cercana"
            TIME_PROXIMITY -> "Ventana temporal"
            BREED_MATCH -> "Raza compatible"
            COLOR_MATCH -> "Color compatible"
            SEX_MATCH -> "Sexo compatible"
            SIZE_MATCH -> "Tamaño compatible"
            MANUAL_LINK -> "Vinculado al caso"
        }
}

enum class M13MatchDecisionType {
    CONFIRMED,
    REJECTED,
    INCONCLUSIVE
}

enum class M13ActorAuthority {
    REPORTER,
    CASE_OWNER,
    ORG_MANAGER,
    MODERATOR
}

data class M13Sighting(
    val id: String,
    val reporterUserId: String,
    val lostFoundCaseId: String? = null,
    val species: PetSpecies,
    val breedText: String? = null,
    val primaryColor: String,
    val secondaryColor: String? = null,
    val sex: PetSex? = null,
    val size: PetSize? = null,
    val observedAt: Long,
    val zoneText: String,
    val latitudeApprox: Double? = null,
    val longitudeApprox: Double? = null,
    val accuracyMeters: Double? = null,
    val description: String,
    val mediaRefs: List<String> = emptyList(),
    val status: M13SightingStatus = M13SightingStatus.ACTIVE,
    val createdAt: Long,
    val updatedAt: Long
)

/** Vista pública redactada: sin coordenadas exactas ni identidad del reportante. */
data class M13SightingPublic(
    val id: String,
    val lostFoundCaseId: String? = null,
    val species: PetSpecies,
    val breedText: String? = null,
    val primaryColor: String,
    val secondaryColor: String? = null,
    val sex: PetSex? = null,
    val size: PetSize? = null,
    val observedAtApproxDay: Long,
    val zoneText: String,
    val descriptionPreview: String,
    val mediaRefs: List<String> = emptyList(),
    val status: M13SightingStatus,
    val hasApproximateLocation: Boolean
)

data class M13MatchCandidate(
    val id: String,
    val caseId: String,
    val sightingId: String,
    val score: Int,
    val level: M13MatchLevel,
    val reasons: List<M13MatchReason>,
    val status: M13MatchStatus = M13MatchStatus.PROPOSED,
    val createdAt: Long,
    val updatedAt: Long
)

data class M13MatchDecision(
    val id: String,
    val candidateId: String,
    val decision: M13MatchDecisionType,
    val actorUserId: String,
    val actorAuthority: M13ActorAuthority,
    val reasonCode: String,
    val notePrivate: String? = null,
    val createdAt: Long
)

object M13PermissionCodes {
    const val SIGHTING_READ = "lostfound.sighting.read"
    const val SIGHTING_CREATE = "lostfound.sighting.create"
    const val SIGHTING_MANAGE_OWN = "lostfound.sighting.manage_own"
    const val SIGHTING_MODERATE = "lostfound.sighting.moderate"
    const val MATCH_READ = "lostfound.match.read"
    const val MATCH_REVIEW = "lostfound.match.review"
    const val MATCH_CONFIRM = "lostfound.match.confirm"

    val all: Set<String> = setOf(
        SIGHTING_READ,
        SIGHTING_CREATE,
        SIGHTING_MANAGE_OWN,
        SIGHTING_MODERATE,
        MATCH_READ,
        MATCH_REVIEW,
        MATCH_CONFIRM
    )
}

object M13AuditEvents {
    const val SIGHTING_CREATED = "m13.sighting.created"
    const val SIGHTING_WITHDRAWN = "m13.sighting.withdrawn"
    const val MATCH_PROPOSED = "m13.match.proposed"
    const val MATCH_CONFIRMED = "m13.match.confirmed"
    const val MATCH_REJECTED = "m13.match.rejected"
    const val MATCH_INCONCLUSIVE = "m13.match.inconclusive"
}

object M13MatchingDefaults {
    const val TIME_WINDOW_DAYS = 30
    const val PROXIMITY_RADIUS_KM = 10.0
    const val SCORE_SPECIES = 20
    const val SCORE_ZONE = 25
    const val SCORE_TIME = 20
    const val SCORE_BREED = 10
    const val SCORE_COLOR = 15
    const val SCORE_SEX = 5
    const val SCORE_SIZE = 5
    const val SCORE_MANUAL_LINK = 20
}

fun M13Sighting.toPublic(): M13SightingPublic {
    val dayMs = 24L * 60L * 60L * 1000L
    val approxDay = (observedAt / dayMs) * dayMs
    val preview = description.trim().let { if (it.length <= 120) it else it.take(119) + "…" }
    return M13SightingPublic(
        id = id,
        lostFoundCaseId = lostFoundCaseId,
        species = species,
        breedText = breedText,
        primaryColor = primaryColor,
        secondaryColor = secondaryColor,
        sex = sex,
        size = size,
        observedAtApproxDay = approxDay,
        zoneText = zoneText,
        descriptionPreview = preview,
        mediaRefs = mediaRefs,
        status = status,
        hasApproximateLocation = latitudeApprox != null && longitudeApprox != null
    )
}
