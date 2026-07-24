package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.LostFoundStatus
import com.comunidapp.app.data.model.M13MatchCandidate
import com.comunidapp.app.data.model.M13MatchLevel
import com.comunidapp.app.data.model.M13MatchReason
import com.comunidapp.app.data.model.M13MatchStatus
import com.comunidapp.app.data.model.M13MatchingDefaults
import com.comunidapp.app.data.model.M13Sighting
import com.comunidapp.app.data.model.M13SightingStatus
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Matching local determinista y explicable (sin IA, sin autoconfirmación).
 */
object M13MatchingEngine {

    fun score(
        sighting: M13Sighting,
        casePost: LostFoundPost,
        nowMs: Long = System.currentTimeMillis()
    ): M13MatchCandidate? {
        if (sighting.status != M13SightingStatus.ACTIVE) return null
        if (casePost.status != LostFoundStatus.ACTIVE) return null
        if (sighting.species != casePost.species) return null

        val reasons = mutableListOf(M13MatchReason.SPECIES_MATCH)
        var score = M13MatchingDefaults.SCORE_SPECIES

        val caseTime = casePost.createdAt ?: nowMs
        val deltaDays = kotlin.math.abs(sighting.observedAt - caseTime) / (24.0 * 60 * 60 * 1000)
        if (deltaDays <= M13MatchingDefaults.TIME_WINDOW_DAYS) {
            reasons += M13MatchReason.TIME_PROXIMITY
            score += M13MatchingDefaults.SCORE_TIME
        } else {
            return null
        }

        val geoOk = proximityOk(sighting, casePost)
        val zoneOk = zoneTextOverlap(sighting.zoneText, casePost.location)
        if (geoOk || zoneOk) {
            reasons += M13MatchReason.ZONE_PROXIMITY
            score += M13MatchingDefaults.SCORE_ZONE
        } else if (sighting.latitudeApprox != null && casePost.latitude != null) {
            // Coordenadas presentes pero fuera de radio: no candidato.
            return null
        }

        val breed = sighting.breedText?.trim()?.lowercase().orEmpty()
        if (breed.isNotEmpty() && casePost.description.lowercase().contains(breed)) {
            reasons += M13MatchReason.BREED_MATCH
            score += M13MatchingDefaults.SCORE_BREED
        }

        val color = sighting.primaryColor.trim().lowercase()
        if (color.isNotEmpty() &&
            (casePost.description.lowercase().contains(color) ||
                casePost.petName.orEmpty().lowercase().contains(color))
        ) {
            reasons += M13MatchReason.COLOR_MATCH
            score += M13MatchingDefaults.SCORE_COLOR
        }

        if (sighting.sex != null && casePost.description.contains(sighting.sex.name, ignoreCase = true)) {
            reasons += M13MatchReason.SEX_MATCH
            score += M13MatchingDefaults.SCORE_SEX
        }

        if (sighting.size != null && casePost.description.contains(sighting.size.name, ignoreCase = true)) {
            reasons += M13MatchReason.SIZE_MATCH
            score += M13MatchingDefaults.SCORE_SIZE
        }

        if (!sighting.lostFoundCaseId.isNullOrBlank() && sighting.lostFoundCaseId == casePost.id) {
            reasons += M13MatchReason.MANUAL_LINK
            score += M13MatchingDefaults.SCORE_MANUAL_LINK
        }

        score = score.coerceIn(0, 100)
        val now = nowMs
        return M13MatchCandidate(
            id = "match_${casePost.id}_${sighting.id}",
            caseId = casePost.id,
            sightingId = sighting.id,
            score = score,
            level = M13MatchLevel.fromScore(score),
            reasons = reasons.distinct(),
            status = M13MatchStatus.PROPOSED,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun proximityOk(sighting: M13Sighting, casePost: LostFoundPost): Boolean {
        val lat1 = sighting.latitudeApprox ?: return false
        val lon1 = sighting.longitudeApprox ?: return false
        val lat2 = casePost.latitude ?: return false
        val lon2 = casePost.longitude ?: return false
        return haversineKm(lat1, lon1, lat2, lon2) <= M13MatchingDefaults.PROXIMITY_RADIUS_KM
    }

    private fun zoneTextOverlap(a: String, b: String): Boolean {
        val na = normalizeZone(a)
        val nb = normalizeZone(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        return na.contains(nb) || nb.contains(na) ||
            na.split(' ').any { it.length >= 3 && nb.contains(it) }
    }

    private fun normalizeZone(value: String): String =
        value.trim().lowercase().replace(Regex("\\s+"), " ")

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
