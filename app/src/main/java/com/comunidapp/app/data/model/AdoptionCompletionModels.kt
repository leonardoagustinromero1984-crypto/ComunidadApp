package com.comunidapp.app.data.model

/** LeoVer M09 — entrevista post-aceptación. */
data class AdoptionInterview(
    val id: String,
    val adoptionId: String,
    val applicationId: String,
    val scheduledAt: Long,
    val type: AdoptionInterviewType = AdoptionInterviewType.IN_PERSON,
    val locationOrLink: String? = null,
    val notes: String? = null,
    val status: AdoptionInterviewStatus = AdoptionInterviewStatus.SCHEDULED,
    val createdBy: String,
    val completedAt: Long? = null,
    val outcome: String? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)

enum class AdoptionInterviewType {
    IN_PERSON,
    VIDEO_CALL,
    PHONE;

    val displayNameEs: String
        get() = when (this) {
            IN_PERSON -> "Presencial"
            VIDEO_CALL -> "Videollamada"
            PHONE -> "Teléfono"
        }

    companion object {
        fun fromString(value: String?): AdoptionInterviewType {
            val raw = value?.trim()?.uppercase().orEmpty()
            return entries.find { it.name == raw } ?: IN_PERSON
        }
    }
}

enum class AdoptionInterviewStatus {
    SCHEDULED,
    CONFIRMED,
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    val displayNameEs: String
        get() = when (this) {
            SCHEDULED -> "Agendada"
            CONFIRMED -> "Confirmada"
            COMPLETED -> "Completada"
            CANCELLED -> "Cancelada"
            NO_SHOW -> "No asistió"
        }

    companion object {
        fun fromString(value: String?): AdoptionInterviewStatus {
            val raw = value?.trim()?.uppercase().orEmpty()
            return when (raw) {
                "DONE" -> COMPLETED
                else -> entries.find { it.name == raw } ?: SCHEDULED
            }
        }
    }
}

/** Requisito documental del adoptante (referencia lógica; sin bucket público). */
data class AdoptionDocumentRequirement(
    val id: String,
    val adoptionId: String,
    val applicationId: String,
    val type: AdoptionDocumentType,
    val required: Boolean = true,
    val status: AdoptionDocumentStatus = AdoptionDocumentStatus.PENDING,
    val storagePath: String? = null,
    val rejectionReason: String? = null,
    val submittedAt: Long? = null,
    val reviewedAt: Long? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)

enum class AdoptionDocumentType {
    IDENTITY,
    ADDRESS_PROOF,
    HOUSING_AUTHORIZATION,
    OTHER;

    val displayNameEs: String
        get() = when (this) {
            IDENTITY -> "Identidad"
            ADDRESS_PROOF -> "Comprobante de domicilio"
            HOUSING_AUTHORIZATION -> "Autorización de vivienda"
            OTHER -> "Otro"
        }

    companion object {
        fun fromString(value: String?): AdoptionDocumentType {
            val raw = value?.trim()?.uppercase().orEmpty()
            return entries.find { it.name == raw } ?: OTHER
        }
    }
}

enum class AdoptionDocumentStatus {
    PENDING,
    SUBMITTED,
    APPROVED,
    REJECTED,
    NOT_REQUIRED;

    val displayNameEs: String
        get() = when (this) {
            PENDING -> "Pendiente"
            SUBMITTED -> "Presentado"
            APPROVED -> "Aprobado"
            REJECTED -> "Rechazado"
            NOT_REQUIRED -> "No requerido"
        }

    companion object {
        fun fromString(value: String?): AdoptionDocumentStatus {
            val raw = value?.trim()?.uppercase().orEmpty()
            return entries.find { it.name == raw } ?: PENDING
        }
    }
}

/** Acuerdo/contrato interno de adopción (aceptaciones, no firma legal certificada). */
data class AdoptionAgreement(
    val id: String,
    val adoptionId: String,
    val applicationId: String,
    val adopterUserId: String,
    val publisherUserId: String? = null,
    val publisherOrganizationId: String? = null,
    val termsVersion: String,
    val termsSnapshot: String,
    val adopterAcceptedAt: Long? = null,
    val publisherAcceptedAt: Long? = null,
    val status: AdoptionAgreementStatus = AdoptionAgreementStatus.DRAFT,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)

enum class AdoptionAgreementStatus {
    DRAFT,
    PENDING_ADOPTER,
    PENDING_PUBLISHER,
    ACCEPTED,
    CANCELLED;

    val displayNameEs: String
        get() = when (this) {
            DRAFT -> "Borrador"
            PENDING_ADOPTER -> "Pendiente del adoptante"
            PENDING_PUBLISHER -> "Pendiente del responsable"
            ACCEPTED -> "Aceptado"
            CANCELLED -> "Cancelado"
        }

    companion object {
        fun fromString(value: String?): AdoptionAgreementStatus {
            val raw = value?.trim()?.uppercase().orEmpty()
            return entries.find { it.name == raw } ?: DRAFT
        }

        /** Activo = no cancelado (como máximo uno por publicación). */
        fun isActive(status: AdoptionAgreementStatus): Boolean =
            status != CANCELLED
    }
}

/** Registro de adopción finalizada. */
data class FinalizedAdoption(
    val id: String,
    val adoptionId: String,
    val applicationId: String,
    val petId: String?,
    val adopterUserId: String,
    val finalizedAt: Long,
    val finalizedBy: String,
    val followUpPlanId: String? = null
)

data class AdoptionFollowUpPlan(
    val id: String,
    val adoptionId: String,
    val adopterUserId: String,
    val status: AdoptionFollowUpPlanStatus = AdoptionFollowUpPlanStatus.ACTIVE,
    val createdAt: Long,
    val completedAt: Long? = null
)

enum class AdoptionFollowUpPlanStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED;

    companion object {
        fun fromString(value: String?): AdoptionFollowUpPlanStatus {
            val raw = value?.trim()?.uppercase().orEmpty()
            return entries.find { it.name == raw } ?: ACTIVE
        }
    }
}

data class AdoptionFollowUpCheck(
    val id: String,
    val planId: String,
    val adoptionId: String = "",
    val dueAt: Long,
    val completedAt: Long? = null,
    val status: AdoptionFollowUpStatus = AdoptionFollowUpStatus.PENDING,
    val notes: String? = null,
    val welfareStatus: AdoptionWelfareStatus? = null,
    val evidenceRef: String? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
) {
    fun withOverdueDetection(now: Long = System.currentTimeMillis()): AdoptionFollowUpCheck {
        if (status == AdoptionFollowUpStatus.PENDING && dueAt < now) {
            return copy(status = AdoptionFollowUpStatus.OVERDUE)
        }
        return this
    }
}

enum class AdoptionFollowUpStatus {
    PENDING,
    COMPLETED,
    OVERDUE,
    CANCELLED;

    val displayNameEs: String
        get() = when (this) {
            PENDING -> "Pendiente"
            COMPLETED -> "Completado"
            OVERDUE -> "Vencido"
            CANCELLED -> "Cancelado"
        }

    companion object {
        fun fromString(value: String?): AdoptionFollowUpStatus {
            val raw = value?.trim()?.uppercase().orEmpty()
            return entries.find { it.name == raw } ?: PENDING
        }
    }
}

enum class AdoptionWelfareStatus {
    GOOD,
    NEEDS_ATTENTION,
    CRITICAL,
    UNKNOWN;

    val displayNameEs: String
        get() = when (this) {
            GOOD -> "Bien"
            NEEDS_ATTENTION -> "Requiere atención"
            CRITICAL -> "Crítico"
            UNKNOWN -> "Desconocido"
        }

    companion object {
        fun fromString(value: String?): AdoptionWelfareStatus {
            val raw = value?.trim()?.uppercase().orEmpty()
            return entries.find { it.name == raw } ?: UNKNOWN
        }
    }
}

/** Snapshot del proceso post-aceptación para el panel. */
data class AdoptionProcessSnapshot(
    val adoptionId: String,
    val adoptionStatus: AdoptionStatus,
    val acceptedApplication: AdoptionApplication?,
    val interviews: List<AdoptionInterview>,
    val documents: List<AdoptionDocumentRequirement>,
    val agreement: AdoptionAgreement?,
    val finalized: FinalizedAdoption?,
    val followUpPlan: AdoptionFollowUpPlan?,
    val followUpChecks: List<AdoptionFollowUpCheck>,
    val canFinalize: Boolean,
    val finalizeBlockers: List<String>
)
