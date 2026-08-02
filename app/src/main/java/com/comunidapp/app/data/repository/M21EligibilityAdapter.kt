package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M21MockEligibilityIds
import com.comunidapp.app.data.model.M21MockTargetIds
import com.comunidapp.app.data.model.M21MockUsers
import com.comunidapp.app.data.model.M21ReviewContextReference
import com.comunidapp.app.data.model.M21ReviewContextType
import com.comunidapp.app.data.model.M21ReviewSubjectReference
import com.comunidapp.app.data.model.M21ReviewTargetType

data class M21EligibilityRecord(
    val reviewerUserId: String,
    val subject: M21ReviewSubjectReference,
    val context: M21ReviewContextReference,
    val completedAt: Long,
    val expiresAt: Long? = null,
    val cancelled: Boolean = false,
    val rejected: Boolean = false
)

object M21EligibilityAdapter {

    private val records: List<M21EligibilityRecord> by lazy { buildSeedRecords() }

    fun findCompletedInteraction(
        reviewerUserId: String,
        subject: M21ReviewSubjectReference,
        contextId: String?,
        additionalRecords: List<M21EligibilityRecord> = emptyList()
    ): M21EligibilityRecord? =
        (records + additionalRecords).firstOrNull { record ->
            record.reviewerUserId == reviewerUserId &&
                record.subject.targetType == subject.targetType &&
                record.subject.targetId == subject.targetId &&
                (contextId == null || record.context.contextId == contextId)
        }

    fun allRecords(): List<M21EligibilityRecord> = records

    private fun buildSeedRecords(): List<M21EligibilityRecord> {
        val now = 1_700_000_000_000L
        return listOf(
            record(
                M21MockUsers.REVIEWER,
                M21ReviewTargetType.ADOPTION,
                M21MockTargetIds.ADOPTION,
                "Adopción Luna",
                M21ReviewContextType.ADOPTION_COMPLETED,
                M21MockEligibilityIds.ADOPTION_COMPLETED,
                "Adopción completada",
                now - 90_000_000
            ),
            record(
                M21MockUsers.ADMIN,
                M21ReviewTargetType.SERVICE,
                M21MockTargetIds.SERVICE,
                "Turno veterinario",
                M21ReviewContextType.SERVICE_COMPLETED,
                M21MockEligibilityIds.SERVICE_COMPLETED,
                "Servicio completado",
                now - 80_000_000
            ),
            record(
                M21MockUsers.REVIEWER,
                M21ReviewTargetType.ORGANIZATION,
                M21MockTargetIds.ORGANIZATION,
                "Refugio Comunitario Norte",
                M21ReviewContextType.SHELTER_INTERACTION,
                M21MockEligibilityIds.SHELTER_INTERACTION,
                "Voluntariado confirmado",
                now - 70_000_000
            ),
            record(
                M21MockUsers.ADMIN,
                M21ReviewTargetType.DONATION,
                M21MockTargetIds.DONATION,
                "Campaña solidaria",
                M21ReviewContextType.DONATION_COMPLETED,
                M21MockEligibilityIds.DONATION_COMPLETED,
                "Donación confirmada",
                now - 60_000_000
            ),
            record(
                M21MockUsers.REVIEWER,
                M21ReviewTargetType.SERVICE,
                M21MockTargetIds.SERVICE,
                "Turno veterinario",
                M21ReviewContextType.SERVICE_COMPLETED,
                M21MockEligibilityIds.DUPLICATE_CONTEXT,
                "Contexto ya reseñado",
                now - 50_000_000
            ),
            M21EligibilityRecord(
                reviewerUserId = M21MockUsers.ADMIN,
                subject = subject(M21ReviewTargetType.SERVICE, "mock_service_cancelled", "Servicio cancelado"),
                context = context(M21ReviewContextType.SERVICE_COMPLETED, M21MockEligibilityIds.CANCELLED_CONTEXT, "Turno cancelado"),
                completedAt = now - 40_000_000,
                cancelled = true
            ),
            M21EligibilityRecord(
                reviewerUserId = M21MockUsers.REVIEWER,
                subject = subject(M21ReviewTargetType.ADOPTION, "mock_adoption_rejected", "Adopción rechazada"),
                context = context(M21ReviewContextType.ADOPTION_COMPLETED, M21MockEligibilityIds.REJECTED_CONTEXT, "Solicitud rechazada"),
                completedAt = now - 30_000_000,
                rejected = true
            ),
            M21EligibilityRecord(
                reviewerUserId = M21MockUsers.ADMIN,
                subject = subject(M21ReviewTargetType.ORGANIZATION, M21MockTargetIds.ORGANIZATION, "Refugio Comunitario Norte"),
                context = context(M21ReviewContextType.EVENT_ATTENDED, M21MockEligibilityIds.EXPIRED_CONTEXT, "Evento asistido"),
                completedAt = now - 200_000_000,
                expiresAt = now - 100_000
            ),
            M21EligibilityRecord(
                reviewerUserId = M21MockUsers.REVIEWER,
                subject = subject(M21ReviewTargetType.USER, M21MockUsers.ORG_MANAGER, "Gestor refugio"),
                context = context(M21ReviewContextType.SUPPORT_CONVERSATION, M21MockEligibilityIds.NOT_ELIGIBLE_VISIT, "Solo visita de perfil"),
                completedAt = now - 10_000_000
            )
        )
    }

    private fun record(
        reviewerUserId: String,
        targetType: M21ReviewTargetType,
        targetId: String,
        label: String,
        contextType: M21ReviewContextType,
        contextId: String,
        contextLabel: String,
        completedAt: Long
    ) = M21EligibilityRecord(
        reviewerUserId = reviewerUserId,
        subject = subject(targetType, targetId, label),
        context = context(contextType, contextId, contextLabel),
        completedAt = completedAt
    )

    private fun subject(type: M21ReviewTargetType, id: String, label: String) =
        M21ReviewSubjectReference(type, id, label)

    private fun context(type: M21ReviewContextType, id: String, label: String) =
        M21ReviewContextReference(type, id, label)
}
