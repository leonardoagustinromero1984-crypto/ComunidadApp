package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M14Credential
import com.comunidapp.app.data.model.M14CredentialStatus
import com.comunidapp.app.data.model.M14PetPassport
import com.comunidapp.app.data.model.M14PublicCredentialSummary
import com.comunidapp.app.data.model.M14PublicPassportProjection
import com.comunidapp.app.data.model.M14Visibility

/**
 * Proyección pública redactada (servicio puro).
 */
object M14PublicProjectionService {
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    fun project(
        passport: M14PetPassport,
        credentials: List<M14Credential>
    ): M14PublicPassportProjection? {
        val code = passport.publicCode ?: return null
        val publicCreds = credentials
            .filter { it.visibility == M14Visibility.PUBLIC_REDACTED }
            .filter {
                it.status == M14CredentialStatus.VERIFIED ||
                    it.status == M14CredentialStatus.PENDING_VERIFICATION
            }
            .map { c ->
                M14PublicCredentialSummary(
                    type = c.type,
                    title = c.title,
                    statusLabel = when (c.status) {
                        M14CredentialStatus.VERIFIED -> "Verificado por una organización"
                        M14CredentialStatus.PENDING_VERIFICATION -> "Pendiente de verificación"
                        else -> c.status.name
                    },
                    issuedAtApproxDayEpochMs = c.issuedAt?.let { (it / DAY_MS) * DAY_MS }
                )
            }
        return M14PublicPassportProjection(
            publicCode = code,
            displayName = passport.displayName,
            species = passport.species,
            breedText = passport.breedText,
            sex = passport.sex,
            primaryColor = passport.primaryColor,
            distinctiveMarks = passport.distinctiveMarks,
            passportStatus = passport.status,
            microchipMasked = M14Validators.maskMicrochip(
                M14Validators.normalizeMicrochip(passport.microchipNumber)
            ),
            credentialsPublic = publicCreds,
            updatedAtApproxDayEpochMs = (passport.updatedAt / DAY_MS) * DAY_MS
        )
    }
}
