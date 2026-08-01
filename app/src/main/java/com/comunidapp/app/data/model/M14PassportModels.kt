package com.comunidapp.app.data.model

/**
 * LeoVer M14 — Pasaporte e identidad verificable (Bloque 1 local).
 * No es historia clínica. No duplica M08 ni M09.
 */

enum class M14PassportStatus {
    DRAFT,
    ACTIVE,
    SUSPENDED,
    REVOKED,
    ARCHIVED;

    val isTerminal: Boolean
        get() = this == REVOKED || this == ARCHIVED

    val isNonFinal: Boolean
        get() = !isTerminal
}

enum class M14CredentialStatus {
    DRAFT,
    PENDING_VERIFICATION,
    VERIFIED,
    REJECTED,
    EXPIRED,
    REVOKED;

    val isTerminal: Boolean
        get() = this == REJECTED || this == EXPIRED || this == REVOKED
}

enum class M14VerificationRequestStatus {
    PENDING,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    CANCELLED,
    EXPIRED;

    val isTerminal: Boolean
        get() = this == APPROVED ||
            this == REJECTED ||
            this == CANCELLED ||
            this == EXPIRED

    val isExpirable: Boolean
        get() = this == PENDING || this == UNDER_REVIEW
}

enum class M14Visibility {
    PRIVATE,
    RESPONSIBLES,
    AUTHORIZED_ORGANIZATIONS,
    PUBLIC_REDACTED
}

enum class M14CredentialType {
    IDENTITY,
    MICROCHIP,
    ADOPTION,
    OWNERSHIP,
    STERILIZATION_ATTESTATION,
    VACCINATION_ATTESTATION,
    TRAVEL_DOCUMENT,
    OTHER
}

object M14PermissionCodes {
    const val PASSPORT_READ = "passport.read"
    const val PASSPORT_CREATE = "passport.create"
    const val PASSPORT_MANAGE_OWN = "passport.manage_own"
    const val PASSPORT_MANAGE_ORGANIZATION = "passport.manage_organization"
    const val PASSPORT_VERIFY = "passport.verify"
    const val PASSPORT_MODERATE = "passport.moderate"
    const val PASSPORT_CREDENTIAL_ISSUE = "passport.credential.issue"
    const val PASSPORT_CREDENTIAL_VERIFY = "passport.credential.verify"
    const val PASSPORT_PUBLIC_READ = "passport.public.read"

    val all: Set<String> = setOf(
        PASSPORT_READ,
        PASSPORT_CREATE,
        PASSPORT_MANAGE_OWN,
        PASSPORT_MANAGE_ORGANIZATION,
        PASSPORT_VERIFY,
        PASSPORT_MODERATE,
        PASSPORT_CREDENTIAL_ISSUE,
        PASSPORT_CREDENTIAL_VERIFY,
        PASSPORT_PUBLIC_READ
    )
}

object M14AuditEvents {
    const val PASSPORT_CREATED = "m14.passport.created"
    const val PASSPORT_ACTIVATED = "m14.passport.activated"
    const val PASSPORT_STATUS_CHANGED = "m14.passport.status_changed"
    const val CREDENTIAL_ADDED = "m14.credential.added"
    const val CREDENTIAL_EXPIRED = "m14.credential.expired"
    const val VERIFICATION_REQUESTED = "m14.verification.requested"
    const val VERIFICATION_RESOLVED = "m14.verification.resolved"
    const val VERIFICATION_EXPIRED = "m14.verification.expired"
    const val PUBLIC_CODE_ROTATED = "m14.public_code.rotated"
}

/** Hooks M06 preparados (sin push real). */
object M14M06Hooks {
    const val PASSPORT_CREATED = "M14_PASSPORT_CREATED"
    const val PASSPORT_ACTIVATED = "M14_PASSPORT_ACTIVATED"
    const val CREDENTIAL_ADDED = "M14_CREDENTIAL_ADDED"
    const val VERIFICATION_REQUESTED = "M14_VERIFICATION_REQUESTED"
    const val VERIFICATION_REVIEW_OPENED = "M14_VERIFICATION_REVIEW_OPENED"
    const val VERIFICATION_APPROVED = "M14_VERIFICATION_APPROVED"
    const val VERIFICATION_REJECTED = "M14_VERIFICATION_REJECTED"
    const val VERIFICATION_EXPIRED = "M14_VERIFICATION_EXPIRED"
    const val CREDENTIAL_ISSUED = "M14_CREDENTIAL_ISSUED"
    const val CREDENTIAL_EXPIRED = "M14_CREDENTIAL_EXPIRED"
    const val CREDENTIAL_REVOKED = "M14_CREDENTIAL_REVOKED"
    const val PUBLIC_CODE_ROTATED = "M14_PUBLIC_CODE_ROTATED"
    const val INFRASTRUCTURE = "M14_NOTIFICATION_INFRASTRUCTURE"

    val all: Set<String> = setOf(
        PASSPORT_CREATED,
        PASSPORT_ACTIVATED,
        CREDENTIAL_ADDED,
        VERIFICATION_REQUESTED,
        VERIFICATION_REVIEW_OPENED,
        VERIFICATION_APPROVED,
        VERIFICATION_REJECTED,
        VERIFICATION_EXPIRED,
        CREDENTIAL_ISSUED,
        CREDENTIAL_EXPIRED,
        CREDENTIAL_REVOKED,
        PUBLIC_CODE_ROTATED,
        INFRASTRUCTURE
    )
}

data class M14PetPassport(
    val id: String,
    val petId: String,
    val passportNumber: String,
    val publicCode: String? = null,
    val status: M14PassportStatus,
    val displayName: String,
    val species: PetSpecies,
    val breedText: String? = null,
    val sex: PetSex? = null,
    val birthDateEpochMs: Long? = null,
    val primaryColor: String? = null,
    val distinctiveMarks: String? = null,
    /** Valor completo solo en contexto autorizado; la proyección pública usa máscara. */
    val microchipNumber: String? = null,
    val visibility: M14Visibility,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class M14Credential(
    val id: String,
    val passportId: String,
    val type: M14CredentialType,
    val title: String,
    val issuerOrganizationId: String? = null,
    val issuerProfessionalId: String? = null,
    val issuedAt: Long? = null,
    val expiresAt: Long? = null,
    val status: M14CredentialStatus,
    val visibility: M14Visibility,
    val mediaRefs: List<String> = emptyList(),
    val externalReferenceMasked: String? = null,
    val notePrivate: String? = null,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class M14VerificationRequest(
    val id: String,
    val credentialId: String,
    val requestedBy: String,
    val targetOrganizationId: String? = null,
    val status: M14VerificationRequestStatus,
    val requestedAt: Long,
    val resolvedAt: Long? = null,
    val resolutionReason: String? = null
)

data class M14VerificationDecision(
    val id: String,
    val requestId: String,
    val decision: M14VerificationRequestStatus,
    val actorUserId: String,
    val actorAuthority: String,
    val reasonCode: String,
    val notePrivate: String? = null,
    val createdAt: Long
)

data class M14PassportHistory(
    val id: String,
    val passportId: String,
    val fromStatus: M14PassportStatus?,
    val toStatus: M14PassportStatus,
    val actorUserId: String?,
    val reason: String?,
    val createdAt: Long,
    val metadataEvent: String? = null
)

/** Vista pública redactada — sin petId, userId, docs, notas ni microchip completo. */
data class M14PublicPassportProjection(
    val publicCode: String,
    val displayName: String,
    val species: PetSpecies,
    val breedText: String?,
    val sex: PetSex?,
    val primaryColor: String?,
    val distinctiveMarks: String?,
    val passportStatus: M14PassportStatus,
    val microchipMasked: String?,
    val credentialsPublic: List<M14PublicCredentialSummary>,
    val updatedAtApproxDayEpochMs: Long
)

data class M14PublicCredentialSummary(
    val type: M14CredentialType,
    val title: String,
    val statusLabel: String,
    val issuedAtApproxDayEpochMs: Long?
)

data class CreateM14PassportInput(
    val petId: String,
    val displayName: String,
    val species: PetSpecies,
    val breedText: String? = null,
    val sex: PetSex? = null,
    val birthDateEpochMs: Long? = null,
    val primaryColor: String? = null,
    val distinctiveMarks: String? = null,
    val microchipNumber: String? = null,
    val visibility: M14Visibility = M14Visibility.PRIVATE
)

data class UpdateM14PassportInput(
    val displayName: String? = null,
    val breedText: String? = null,
    val sex: PetSex? = null,
    val birthDateEpochMs: Long? = null,
    val primaryColor: String? = null,
    val distinctiveMarks: String? = null,
    val microchipNumber: String? = null,
    val visibility: M14Visibility? = null
)

data class CreateM14CredentialInput(
    val passportId: String,
    val type: M14CredentialType,
    val title: String,
    val issuedAt: Long? = null,
    val expiresAt: Long? = null,
    val visibility: M14Visibility = M14Visibility.PRIVATE,
    val mediaRefs: List<String> = emptyList(),
    val externalReferenceMasked: String? = null,
    val notePrivate: String? = null
)

data class IssueVerifiedM14CredentialInput(
    val passportId: String,
    val type: M14CredentialType,
    val title: String,
    val issuerOrganizationId: String? = null,
    val issuerProfessionalId: String? = null,
    val issuedAt: Long? = null,
    val expiresAt: Long? = null,
    val visibility: M14Visibility = M14Visibility.PRIVATE,
    val mediaRefs: List<String> = emptyList(),
    val externalReferenceMasked: String? = null,
    val notePrivate: String? = null
)

/**
 * Política local de expiración (America/Argentina/Buenos_Aires).
 * Cron real = REQUIERE_INFRA_EXTERNA / PENDIENTE_EXTERNO.
 */
data class M14ExpirationPolicy(
    val pendingRequestTtlDays: Int = 14,
    val underReviewRequestTtlDays: Int = 7,
    val zoneIdName: String = "America/Argentina/Buenos_Aires"
) {
    init {
        require(pendingRequestTtlDays > 0 && underReviewRequestTtlDays > 0)
    }
}

data class M14ExpirationResult(
    val expiredRequests: Int,
    val expiredCredentials: Int,
    val alreadyApplied: Int = 0,
    val preservedTerminal: Int = 0,
    val infrastructureNote: String = "REQUIERE_INFRA_EXTERNA"
)

/** Métricas agregadas sin PII (sin userId, petId, publicCode completo, notas, contacto). */
data class M14OperationalMetrics(
    val fromEpochMs: Long,
    val toEpochMs: Long,
    val zoneIdName: String,
    val passportsByStatus: Map<String, Int>,
    val credentialsByStatus: Map<String, Int>,
    val credentialsByType: Map<String, Int>,
    val requestsByStatus: Map<String, Int>,
    val approvals: Int,
    val rejections: Int,
    val expirations: Int,
    val revocations: Int,
    val publicCodeRotations: Int,
    val conflicts: Int,
    val idempotentRetries: Int,
    val avgMinutesToResolution: Double?
)

enum class M14VerificationNextStep {
    OPEN_REVIEW,
    DECIDE,
    TERMINAL,
    EXPIRE_ELIGIBLE,
    NONE
}

fun M14VerificationRequestStatus.nextStep(): M14VerificationNextStep = when (this) {
    M14VerificationRequestStatus.PENDING -> M14VerificationNextStep.OPEN_REVIEW
    M14VerificationRequestStatus.UNDER_REVIEW -> M14VerificationNextStep.DECIDE
    M14VerificationRequestStatus.APPROVED,
    M14VerificationRequestStatus.REJECTED,
    M14VerificationRequestStatus.CANCELLED,
    M14VerificationRequestStatus.EXPIRED -> M14VerificationNextStep.TERMINAL
}

enum class M14CredentialNextStep {
    REQUEST_VERIFICATION,
    AWAIT_REVIEW,
    TERMINAL,
    EXPIRE_ELIGIBLE,
    REVOKE_ELIGIBLE,
    NONE
}

fun M14CredentialStatus.nextStep(expiresAt: Long? = null, nowEpochMs: Long = System.currentTimeMillis()): M14CredentialNextStep =
    when (this) {
        M14CredentialStatus.DRAFT -> M14CredentialNextStep.REQUEST_VERIFICATION
        M14CredentialStatus.PENDING_VERIFICATION -> M14CredentialNextStep.AWAIT_REVIEW
        M14CredentialStatus.VERIFIED ->
            if (expiresAt != null && expiresAt <= nowEpochMs) M14CredentialNextStep.EXPIRE_ELIGIBLE
            else M14CredentialNextStep.REVOKE_ELIGIBLE
        M14CredentialStatus.REJECTED,
        M14CredentialStatus.EXPIRED,
        M14CredentialStatus.REVOKED -> M14CredentialNextStep.TERMINAL
    }

/** Mensaje UI cuando 052/remoto aún no está disponible. */
object M14RemoteFallback {
    const val MESSAGE =
        "Validación remota pendiente: la migración 052 aún no está aplicada o el servicio no responde. Se usa lógica local cuando está disponible."
    const val CODE = "REMOTE_VALIDATION_PENDING"
}
