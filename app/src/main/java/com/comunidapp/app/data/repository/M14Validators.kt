package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.CreateM14CredentialInput
import com.comunidapp.app.data.model.CreateM14PassportInput
import com.comunidapp.app.data.model.IssueVerifiedM14CredentialInput
import com.comunidapp.app.data.model.M14CredentialStatus
import com.comunidapp.app.data.model.M14PassportStatus
import com.comunidapp.app.data.model.M14Visibility
import com.comunidapp.app.data.model.UpdateM14PassportInput
import com.comunidapp.app.data.remote.supabase.m14.M14ErrorMapper
import com.comunidapp.app.data.remote.supabase.m14.M14Exception
import com.comunidapp.app.domain.pets.MicrochipNormalizer
import java.util.Locale

/**
 * LeoVer M14 — validadores locales (Bloque 1).
 */
object M14Validators {
    private val safeMediaRef = Regex("^(m05://|file_asset:).{1,200}$")
    private const val MAX_TITLE = 120
    private const val MAX_MARKS = 240
    private const val MAX_NOTE = 500
    private const val MAX_NAME = 80

    fun validateCreatePassport(input: CreateM14PassportInput): String? {
        if (input.petId.isBlank()) return "PET_NOT_FOUND"
        if (input.displayName.isBlank() || input.displayName.length > MAX_NAME) {
            return "INVALID_PASSPORT_INPUT"
        }
        if (input.distinctiveMarks != null && input.distinctiveMarks.length > MAX_MARKS) {
            return "INVALID_PASSPORT_INPUT"
        }
        if (input.birthDateEpochMs != null && input.birthDateEpochMs > System.currentTimeMillis()) {
            return "INVALID_PASSPORT_INPUT"
        }
        return null
    }

    fun validateUpdatePassport(input: UpdateM14PassportInput): String? {
        input.displayName?.let {
            if (it.isBlank() || it.length > MAX_NAME) return "INVALID_PASSPORT_INPUT"
        }
        input.distinctiveMarks?.let {
            if (it.length > MAX_MARKS) return "INVALID_PASSPORT_INPUT"
        }
        input.birthDateEpochMs?.let {
            if (it > System.currentTimeMillis()) return "INVALID_PASSPORT_INPUT"
        }
        return null
    }

    fun validateCredential(input: CreateM14CredentialInput): String? {
        if (input.passportId.isBlank()) return "PASSPORT_NOT_FOUND"
        if (input.title.isBlank() || input.title.length > MAX_TITLE) return "INVALID_CREDENTIAL"
        if (input.notePrivate != null && input.notePrivate.length > MAX_NOTE) {
            return "INVALID_CREDENTIAL"
        }
        if (input.issuedAt != null && input.expiresAt != null && input.expiresAt <= input.issuedAt) {
            return "INVALID_CREDENTIAL_DATES"
        }
        input.mediaRefs.forEach { ref ->
            if (!isSafeMediaRef(ref)) return "INVALID_MEDIA_REFERENCE"
        }
        return null
    }

    fun validateIssueVerified(input: IssueVerifiedM14CredentialInput): String? {
        if (input.passportId.isBlank()) return "PASSPORT_NOT_FOUND"
        if (input.title.isBlank() || input.title.length > MAX_TITLE) return "INVALID_CREDENTIAL"
        if (input.issuerOrganizationId.isNullOrBlank() && input.issuerProfessionalId.isNullOrBlank()) {
            // Mock/local may rely on authority policy; remote RPC can derive professional id.
        }
        if (input.notePrivate != null && input.notePrivate.length > MAX_NOTE) {
            return "INVALID_CREDENTIAL"
        }
        if (input.issuedAt != null && input.expiresAt != null && input.expiresAt <= input.issuedAt) {
            return "INVALID_CREDENTIAL_DATES"
        }
        input.mediaRefs.forEach { ref ->
            if (!isSafeMediaRef(ref)) return "INVALID_MEDIA_REFERENCE"
        }
        return null
    }

    fun isSafeMediaRef(ref: String): Boolean {
        val trimmed = ref.trim()
        if (trimmed.isEmpty()) return false
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower.contains("/object/public/leover")) return false
        if (lower.startsWith("http://") || lower.startsWith("https://")) return false
        return safeMediaRef.matches(trimmed)
    }

    fun normalizeMicrochip(raw: String?): String? = MicrochipNormalizer.normalizeOrNull(raw)

    fun maskMicrochip(normalized: String?): String? {
        if (normalized.isNullOrBlank()) return null
        if (normalized.length <= 4) return "****"
        return "*".repeat((normalized.length - 4).coerceAtMost(12)) + normalized.takeLast(4)
    }

    fun canTransitionPassport(from: M14PassportStatus, to: M14PassportStatus): Boolean =
        when (from) {
            M14PassportStatus.DRAFT -> to == M14PassportStatus.ACTIVE ||
                to == M14PassportStatus.ARCHIVED ||
                to == M14PassportStatus.REVOKED
            M14PassportStatus.ACTIVE -> to == M14PassportStatus.SUSPENDED ||
                to == M14PassportStatus.REVOKED ||
                to == M14PassportStatus.ARCHIVED
            M14PassportStatus.SUSPENDED -> to == M14PassportStatus.ACTIVE ||
                to == M14PassportStatus.REVOKED ||
                to == M14PassportStatus.ARCHIVED
            M14PassportStatus.REVOKED, M14PassportStatus.ARCHIVED -> false
        }

    fun canTransitionCredential(from: M14CredentialStatus, to: M14CredentialStatus): Boolean =
        when (from) {
            M14CredentialStatus.DRAFT ->
                to == M14CredentialStatus.PENDING_VERIFICATION || to == M14CredentialStatus.REVOKED
            M14CredentialStatus.PENDING_VERIFICATION ->
                to == M14CredentialStatus.VERIFIED ||
                    to == M14CredentialStatus.REJECTED ||
                    to == M14CredentialStatus.REVOKED
            M14CredentialStatus.VERIFIED ->
                to == M14CredentialStatus.EXPIRED || to == M14CredentialStatus.REVOKED
            M14CredentialStatus.REJECTED,
            M14CredentialStatus.EXPIRED,
            M14CredentialStatus.REVOKED -> false
        }

    fun publicCodeLooksLikePii(code: String): Boolean {
        val lower = code.lowercase(Locale.ROOT)
        if (lower.contains("@")) return true
        if (lower.contains("dni") || lower.contains("cuit") || lower.contains("whatsapp")) return true
        if (Regex("\\+?\\d[\\d\\s\\-]{9,}\\d").containsMatchIn(code)) return true
        return false
    }

    fun isPublicVisibility(v: M14Visibility): Boolean = v == M14Visibility.PUBLIC_REDACTED

    /** Guarda estática: proyección pública no debe incluir estos campos sensibles. */
    fun publicProjectionHasNoSensitiveLeak(blob: String): Boolean {
        val lower = blob.lowercase(Locale.ROOT)
        if (lower.contains("userid") || lower.contains("user_id")) return false
        if (lower.contains("petid") || lower.contains("pet_id")) return false
        if (lower.contains("passportnumber") || lower.contains("passport_number")) return false
        if (lower.contains("noteprivate") || lower.contains("note_private")) return false
        if (lower.contains("@") || lower.contains("whatsapp")) return false
        if (lower.contains("organizationid") || lower.contains("organization_id")) return false
        return true
    }

    fun maskPublicCodeForLogs(code: String?): String? {
        if (code.isNullOrBlank()) return null
        if (code.length <= 6) return "***"
        return code.take(4) + "…" + code.takeLast(2)
    }
}

internal fun resultFailM14(code: String): Result<Nothing> =
    Result.failure(M14Exception(code, M14ErrorMapper.userMessage(code)))
