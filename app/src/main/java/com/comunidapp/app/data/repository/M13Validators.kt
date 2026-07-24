package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M13Sighting
import com.comunidapp.app.data.model.toPublic
import com.comunidapp.app.data.remote.supabase.m13.M13ErrorMapper
import com.comunidapp.app.data.remote.supabase.m13.M13Exception

object M13Validators {
    private val safeMediaRef = Regex("^m05:[A-Za-z0-9_./-]{1,200}$")

    fun validateCreate(
        description: String,
        zoneText: String,
        primaryColor: String,
        mediaRefs: List<String>,
        latitudeApprox: Double?,
        longitudeApprox: Double?,
        accuracyMeters: Double?
    ): String? {
        if (description.trim().length < 8) return "SIGHTING_INVALID"
        if (description.trim().length > 2000) return "SIGHTING_INVALID"
        if (zoneText.trim().length < 2) return "SIGHTING_INVALID"
        if (primaryColor.trim().isEmpty()) return "SIGHTING_INVALID"
        if (mediaRefs.size > 6) return "SIGHTING_INVALID"
        mediaRefs.forEach { ref ->
            if (!safeMediaRef.matches(ref.trim())) return "MEDIA_REF_INVALID"
        }
        if ((latitudeApprox == null) != (longitudeApprox == null)) return "SIGHTING_INVALID"
        if (latitudeApprox != null && (latitudeApprox < -90 || latitudeApprox > 90)) {
            return "SIGHTING_INVALID"
        }
        if (longitudeApprox != null && (longitudeApprox < -180 || longitudeApprox > 180)) {
            return "SIGHTING_INVALID"
        }
        if (accuracyMeters != null && accuracyMeters < 0) return "SIGHTING_INVALID"
        return null
    }

    /** Garantiza que la proyección pública no incluye lon/lat exactas. */
    fun publicProjectionHidesExactCoords(sighting: M13Sighting): Boolean {
        val publicView = sighting.toPublic()
        return publicView.id == sighting.id && publicView.zoneText.isNotBlank()
    }
}

internal fun <T> resultFailM13(code: String): Result<T> =
    Result.failure(M13Exception(code, M13ErrorMapper.userMessage(code)))
