package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.domain.moderation.ModerationAction
import com.comunidapp.app.domain.moderation.ModerationActionRules
import com.comunidapp.app.domain.moderation.ModerationActionType
import com.comunidapp.app.domain.moderation.ModerationAppeal
import com.comunidapp.app.domain.moderation.ModerationAppealRules
import com.comunidapp.app.domain.moderation.ModerationAppealStatus
import com.comunidapp.app.domain.moderation.ModerationCase
import com.comunidapp.app.domain.moderation.ModerationCaseRules
import com.comunidapp.app.domain.moderation.ModerationCaseStatus
import com.comunidapp.app.domain.moderation.ModerationReport
import com.comunidapp.app.domain.moderation.ModerationReportRules
import com.comunidapp.app.domain.moderation.ModerationReportStatus
import com.comunidapp.app.domain.moderation.ModerationTargetRef
import com.comunidapp.app.domain.organization.OrganizationVerificationStatus
import com.comunidapp.app.domain.support.SupportCategory
import com.comunidapp.app.domain.support.SupportMessage
import com.comunidapp.app.domain.support.SupportMessageVisibility
import com.comunidapp.app.domain.support.SupportTicket
import com.comunidapp.app.domain.support.SupportTicketStatus
import com.comunidapp.app.domain.support.SupportValidators
import com.comunidapp.app.domain.verification.OrganizationVerificationDecision
import com.comunidapp.app.domain.verification.OrganizationVerificationReview
import com.comunidapp.app.domain.verification.OrganizationVerificationReviewStatus
import com.comunidapp.app.domain.verification.VerificationValidators
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface ModerationRepository {
    suspend fun createReport(
        reporterId: String,
        target: ModerationTargetRef,
        reasonCode: String,
        description: String?,
        nowEpochMs: Long
    ): AppResult<ModerationReport>

    suspend fun getMyReports(reporterId: String): AppResult<List<ModerationReport>>

    suspend fun getReportForStaff(reportId: String): AppResult<ModerationReport>

    suspend fun listModerationQueue(): AppResult<List<ModerationReport>>

    suspend fun createCase(
        title: String,
        createdByUserId: String,
        nowEpochMs: Long
    ): AppResult<ModerationCase>

    suspend fun attachReportToCase(reportId: String, caseId: String, nowEpochMs: Long): AppResult<Unit>

    suspend fun assignCase(
        caseId: String,
        assigneeUserId: String,
        nowEpochMs: Long
    ): AppResult<ModerationCase>

    suspend fun changeCaseStatus(
        caseId: String,
        status: ModerationCaseStatus,
        closeReasonCode: String?,
        nowEpochMs: Long
    ): AppResult<ModerationCase>

    suspend fun recordAction(
        caseId: String,
        target: ModerationTargetRef,
        actionType: ModerationActionType,
        reasonCode: String,
        reasonDetail: String?,
        appliedByUserId: String,
        nowEpochMs: Long,
        expiresAtEpochMs: Long?
    ): AppResult<ModerationAction>

    suspend fun submitAppeal(
        actionId: String,
        submittedByUserId: String,
        statement: String,
        nowEpochMs: Long
    ): AppResult<ModerationAppeal>

    suspend fun listAppeals(): AppResult<List<ModerationAppeal>>

    suspend fun reviewAppeal(
        appealId: String,
        decision: ModerationAppealStatus,
        decisionReason: String,
        reviewerUserId: String,
        nowEpochMs: Long
    ): AppResult<ModerationAppeal>
}

interface OrganizationVerificationRepository {
    suspend fun listPendingVerificationRequests(): AppResult<List<OrganizationVerificationReview>>

    suspend fun getVerificationReview(reviewId: String): AppResult<OrganizationVerificationReview>

    suspend fun assignVerificationReview(
        reviewId: String,
        assigneeUserId: String,
        nowEpochMs: Long
    ): AppResult<OrganizationVerificationReview>

    suspend fun recordVerificationDecision(
        reviewId: String,
        currentOrgStatus: OrganizationVerificationStatus,
        decision: OrganizationVerificationDecision,
        reviewNote: String?,
        actorUserId: String,
        actorIsOrgMember: Boolean,
        nowEpochMs: Long
    ): AppResult<OrganizationVerificationReview>
}

interface SupportRepository {
    suspend fun createTicket(
        requesterUserId: String,
        category: SupportCategory,
        subject: String,
        description: String,
        nowEpochMs: Long
    ): AppResult<SupportTicket>

    suspend fun getMyTickets(requesterUserId: String): AppResult<List<SupportTicket>>

    suspend fun getTicket(ticketId: String): AppResult<SupportTicket>

    suspend fun listSupportQueue(): AppResult<List<SupportTicket>>

    suspend fun assignTicket(
        ticketId: String,
        assigneeUserId: String,
        nowEpochMs: Long
    ): AppResult<SupportTicket>

    suspend fun changeTicketStatus(
        ticketId: String,
        status: SupportTicketStatus,
        closeReasonCode: String?,
        nowEpochMs: Long
    ): AppResult<SupportTicket>

    suspend fun addRequesterMessage(
        ticketId: String,
        authorUserId: String,
        body: String,
        nowEpochMs: Long
    ): AppResult<SupportMessage>

    suspend fun addInternalMessage(
        ticketId: String,
        authorUserId: String,
        body: String,
        nowEpochMs: Long
    ): AppResult<SupportMessage>
}

interface AdministrativeAuditRepository {
    suspend fun recordAdministrativeEvent(
        actorUserId: String,
        action: String,
        resourceType: String,
        resourceId: String,
        reasonCode: String,
        nowEpochMs: Long
    ): AppResult<Unit>

    suspend fun listAdministrativeEvents(): AppResult<List<AdministrativeAuditEvent>>
}

data class AdministrativeAuditEvent(
    val id: String,
    val actorUserId: String,
    val action: String,
    val resourceType: String,
    val resourceId: String,
    val reasonCode: String,
    val createdAtEpochMs: Long
)

private fun fail(code: String, kind: AppErrorKind = AppErrorKind.VALIDATION): AppResult.Failure =
    AppResult.Failure(
        AppError(
            kind = kind,
            userMessage = "Operación no válida.",
            technicalMessage = code,
            code = code
        )
    )

class MockModerationRepository : ModerationRepository {
    private val reports = ConcurrentHashMap<String, ModerationReport>()
    private val cases = ConcurrentHashMap<String, ModerationCase>()
    private val actions = ConcurrentHashMap<String, ModerationAction>()
    private val appeals = ConcurrentHashMap<String, ModerationAppeal>()

    fun resetForTests() {
        reports.clear()
        cases.clear()
        actions.clear()
        appeals.clear()
    }

    override suspend fun createReport(
        reporterId: String,
        target: ModerationTargetRef,
        reasonCode: String,
        description: String?,
        nowEpochMs: Long
    ): AppResult<ModerationReport> {
        val draft = ModerationReportRules.validateNewReport(
            reporterId, target, reasonCode, description, nowEpochMs
        ).getOrElse { return fail(it.message ?: "VALIDATION") }
        val id = UUID.randomUUID().toString()
        val saved = draft.copy(id = id)
        reports[id] = saved
        return AppResult.Success(saved)
    }

    override suspend fun getMyReports(reporterId: String): AppResult<List<ModerationReport>> =
        AppResult.Success(reports.values.filter { it.reporterId == reporterId }.sortedByDescending { it.createdAtEpochMs })

    override suspend fun getReportForStaff(reportId: String): AppResult<ModerationReport> {
        val report = reports[reportId] ?: return fail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        return AppResult.Success(report)
    }

    override suspend fun listModerationQueue(): AppResult<List<ModerationReport>> =
        AppResult.Success(
            reports.values.filter {
                it.status in setOf(
                    ModerationReportStatus.OPEN,
                    ModerationReportStatus.TRIAGED,
                    ModerationReportStatus.IN_REVIEW,
                    ModerationReportStatus.ACTION_REQUIRED
                )
            }.sortedByDescending { it.createdAtEpochMs }
        )

    override suspend fun createCase(
        title: String,
        createdByUserId: String,
        nowEpochMs: Long
    ): AppResult<ModerationCase> {
        val draft = ModerationCaseRules.validateCreate(title, createdByUserId, nowEpochMs)
            .getOrElse { return fail(it.message ?: "VALIDATION") }
        val id = UUID.randomUUID().toString()
        val saved = draft.copy(id = id)
        cases[id] = saved
        return AppResult.Success(saved)
    }

    override suspend fun attachReportToCase(
        reportId: String,
        caseId: String,
        nowEpochMs: Long
    ): AppResult<Unit> {
        val report = reports[reportId] ?: return fail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        if (cases[caseId] == null) return fail("CASE_NOT_FOUND", AppErrorKind.NOT_FOUND)
        ModerationCaseRules.canAttachReport(report, caseId)
            .getOrElse { return fail(it.message ?: "VALIDATION") }
        reports[reportId] = report.copy(caseId = caseId, updatedAtEpochMs = nowEpochMs)
        return AppResult.Success(Unit)
    }

    override suspend fun assignCase(
        caseId: String,
        assigneeUserId: String,
        nowEpochMs: Long
    ): AppResult<ModerationCase> {
        val current = cases[caseId] ?: return fail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        if (assigneeUserId.isBlank()) return fail("ASSIGNEE_REQUIRED")
        val updated = current.copy(assignedToUserId = assigneeUserId, updatedAtEpochMs = nowEpochMs)
        cases[caseId] = updated
        return AppResult.Success(updated)
    }

    override suspend fun changeCaseStatus(
        caseId: String,
        status: ModerationCaseStatus,
        closeReasonCode: String?,
        nowEpochMs: Long
    ): AppResult<ModerationCase> {
        val current = cases[caseId] ?: return fail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        if (status == ModerationCaseStatus.CLOSED) {
            ModerationCaseRules.canClose(current, closeReasonCode)
                .getOrElse { return fail(it.message ?: "VALIDATION") }
        }
        val updated = current.copy(
            status = status,
            updatedAtEpochMs = nowEpochMs,
            closedAtEpochMs = if (status == ModerationCaseStatus.CLOSED) nowEpochMs else current.closedAtEpochMs,
            closeReasonCode = closeReasonCode ?: current.closeReasonCode
        )
        cases[caseId] = updated
        return AppResult.Success(updated)
    }

    override suspend fun recordAction(
        caseId: String,
        target: ModerationTargetRef,
        actionType: ModerationActionType,
        reasonCode: String,
        reasonDetail: String?,
        appliedByUserId: String,
        nowEpochMs: Long,
        expiresAtEpochMs: Long?
    ): AppResult<ModerationAction> {
        if (cases[caseId] == null) return fail("CASE_NOT_FOUND", AppErrorKind.NOT_FOUND)
        val draft = ModerationActionRules.validateNew(
            caseId, target, actionType, reasonCode, reasonDetail,
            appliedByUserId, nowEpochMs, expiresAtEpochMs
        ).getOrElse { return fail(it.message ?: "VALIDATION") }
        val id = UUID.randomUUID().toString()
        val saved = draft.copy(id = id)
        actions[id] = saved
        return AppResult.Success(saved)
    }

    override suspend fun submitAppeal(
        actionId: String,
        submittedByUserId: String,
        statement: String,
        nowEpochMs: Long
    ): AppResult<ModerationAppeal> {
        val action = actions[actionId] ?: return fail("ACTION_NOT_FOUND", AppErrorKind.NOT_FOUND)
        val active = appeals.values.any {
            it.actionId == actionId && ModerationAppealRules.isActive(it.status)
        }
        val draft = ModerationAppealRules.validateSubmit(
            action, submittedByUserId, statement, nowEpochMs,
            existingActiveAppeal = active
        ).getOrElse { return fail(it.message ?: "VALIDATION") }
        val id = UUID.randomUUID().toString()
        val saved = draft.copy(id = id)
        appeals[id] = saved
        return AppResult.Success(saved)
    }

    override suspend fun listAppeals(): AppResult<List<ModerationAppeal>> =
        AppResult.Success(appeals.values.sortedByDescending { it.createdAtEpochMs })

    override suspend fun reviewAppeal(
        appealId: String,
        decision: ModerationAppealStatus,
        decisionReason: String,
        reviewerUserId: String,
        nowEpochMs: Long
    ): AppResult<ModerationAppeal> {
        val appeal = appeals[appealId] ?: return fail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        val action = actions[appeal.actionId] ?: return fail("ACTION_NOT_FOUND", AppErrorKind.NOT_FOUND)
        ModerationAppealRules.validateReview(
            appeal, decision, decisionReason, reviewerUserId, action.appliedByUserId
        ).getOrElse { return fail(it.message ?: "VALIDATION") }
        val updated = appeal.copy(
            status = decision,
            decisionReason = decisionReason.trim(),
            reviewedByUserId = reviewerUserId,
            reviewedAtEpochMs = nowEpochMs
        )
        appeals[appealId] = updated
        return AppResult.Success(updated)
    }
}

class MockOrganizationVerificationRepository : OrganizationVerificationRepository {
    private val reviews = ConcurrentHashMap<String, OrganizationVerificationReview>()

    fun resetForTests() = reviews.clear()

    fun seed(review: OrganizationVerificationReview) {
        reviews[review.id] = review
    }

    override suspend fun listPendingVerificationRequests(): AppResult<List<OrganizationVerificationReview>> =
        AppResult.Success(
            reviews.values.filter {
                it.status == OrganizationVerificationReviewStatus.PENDING_REVIEW ||
                    it.status == OrganizationVerificationReviewStatus.MORE_INFO_REQUESTED
            }
        )

    override suspend fun getVerificationReview(reviewId: String): AppResult<OrganizationVerificationReview> {
        val review = reviews[reviewId] ?: return fail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        return AppResult.Success(review)
    }

    override suspend fun assignVerificationReview(
        reviewId: String,
        assigneeUserId: String,
        nowEpochMs: Long
    ): AppResult<OrganizationVerificationReview> {
        val current = reviews[reviewId] ?: return fail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        val updated = current.copy(assignedToUserId = assigneeUserId, updatedAtEpochMs = nowEpochMs)
        reviews[reviewId] = updated
        return AppResult.Success(updated)
    }

    override suspend fun recordVerificationDecision(
        reviewId: String,
        currentOrgStatus: OrganizationVerificationStatus,
        decision: OrganizationVerificationDecision,
        reviewNote: String?,
        actorUserId: String,
        actorIsOrgMember: Boolean,
        nowEpochMs: Long
    ): AppResult<OrganizationVerificationReview> {
        val current = reviews[reviewId] ?: return fail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        VerificationValidators.validateDecision(
            currentOrgStatus, decision, reviewNote, actorUserId, actorIsOrgMember
        ).getOrElse { return fail(it.message ?: "VALIDATION") }
        val status = when (decision) {
            OrganizationVerificationDecision.APPROVE -> OrganizationVerificationReviewStatus.APPROVED
            OrganizationVerificationDecision.REJECT -> OrganizationVerificationReviewStatus.REJECTED
            OrganizationVerificationDecision.REQUEST_MORE_INFORMATION ->
                OrganizationVerificationReviewStatus.MORE_INFO_REQUESTED
            OrganizationVerificationDecision.REVOKE -> OrganizationVerificationReviewStatus.REVOKED
            OrganizationVerificationDecision.MARK_EXPIRED -> OrganizationVerificationReviewStatus.EXPIRED
        }
        val updated = current.copy(
            status = status,
            decision = decision,
            reviewNote = reviewNote?.trim()?.ifBlank { null },
            updatedAtEpochMs = nowEpochMs,
            decidedAtEpochMs = nowEpochMs,
            decidedByUserId = actorUserId
        )
        reviews[reviewId] = updated
        return AppResult.Success(updated)
    }
}

class MockSupportRepository : SupportRepository {
    private val tickets = ConcurrentHashMap<String, SupportTicket>()
    private val messages = ConcurrentHashMap<String, SupportMessage>()

    fun resetForTests() {
        tickets.clear()
        messages.clear()
    }

    fun messagesFor(ticketId: String): List<SupportMessage> =
        messages.values.filter { it.ticketId == ticketId }

    override suspend fun createTicket(
        requesterUserId: String,
        category: SupportCategory,
        subject: String,
        description: String,
        nowEpochMs: Long
    ): AppResult<SupportTicket> {
        val draft = SupportValidators.validateCreate(
            requesterUserId, category, subject, description, nowEpochMs
        ).getOrElse { return fail(it.message ?: "VALIDATION") }
        val id = UUID.randomUUID().toString()
        val saved = draft.copy(id = id)
        tickets[id] = saved
        return AppResult.Success(saved)
    }

    override suspend fun getMyTickets(requesterUserId: String): AppResult<List<SupportTicket>> =
        AppResult.Success(tickets.values.filter { it.requesterUserId == requesterUserId })

    override suspend fun getTicket(ticketId: String): AppResult<SupportTicket> {
        val ticket = tickets[ticketId] ?: return fail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        return AppResult.Success(ticket)
    }

    override suspend fun listSupportQueue(): AppResult<List<SupportTicket>> =
        AppResult.Success(
            tickets.values.filter { it.status != SupportTicketStatus.CLOSED }
                .sortedByDescending { it.createdAtEpochMs }
        )

    override suspend fun assignTicket(
        ticketId: String,
        assigneeUserId: String,
        nowEpochMs: Long
    ): AppResult<SupportTicket> {
        val current = tickets[ticketId] ?: return fail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        val updated = current.copy(
            assignedToUserId = assigneeUserId,
            status = if (current.status == SupportTicketStatus.OPEN) {
                SupportTicketStatus.IN_PROGRESS
            } else {
                current.status
            },
            updatedAtEpochMs = nowEpochMs
        )
        tickets[ticketId] = updated
        return AppResult.Success(updated)
    }

    override suspend fun changeTicketStatus(
        ticketId: String,
        status: SupportTicketStatus,
        closeReasonCode: String?,
        nowEpochMs: Long
    ): AppResult<SupportTicket> {
        val current = tickets[ticketId] ?: return fail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        if (status == SupportTicketStatus.CLOSED) {
            SupportValidators.canClose(current, closeReasonCode)
                .getOrElse { return fail(it.message ?: "VALIDATION") }
        }
        val updated = current.copy(
            status = status,
            updatedAtEpochMs = nowEpochMs,
            resolvedAtEpochMs = if (status == SupportTicketStatus.RESOLVED) nowEpochMs else current.resolvedAtEpochMs,
            closedAtEpochMs = if (status == SupportTicketStatus.CLOSED) nowEpochMs else current.closedAtEpochMs,
            closeReasonCode = closeReasonCode ?: current.closeReasonCode
        )
        tickets[ticketId] = updated
        return AppResult.Success(updated)
    }

    override suspend fun addRequesterMessage(
        ticketId: String,
        authorUserId: String,
        body: String,
        nowEpochMs: Long
    ): AppResult<SupportMessage> = addMessage(
        ticketId, authorUserId, body, SupportMessageVisibility.REQUESTER_VISIBLE, nowEpochMs
    )

    override suspend fun addInternalMessage(
        ticketId: String,
        authorUserId: String,
        body: String,
        nowEpochMs: Long
    ): AppResult<SupportMessage> = addMessage(
        ticketId, authorUserId, body, SupportMessageVisibility.INTERNAL, nowEpochMs
    )

    private fun addMessage(
        ticketId: String,
        authorUserId: String,
        body: String,
        visibility: SupportMessageVisibility,
        nowEpochMs: Long
    ): AppResult<SupportMessage> {
        if (tickets[ticketId] == null) return fail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        val text = SupportValidators.validateMessage(body, visibility)
            .getOrElse { return fail(it.message ?: "VALIDATION") }
        val id = UUID.randomUUID().toString()
        val msg = SupportMessage(
            id = id,
            ticketId = ticketId,
            authorUserId = authorUserId,
            visibility = visibility,
            body = text,
            createdAtEpochMs = nowEpochMs
        )
        messages[id] = msg
        return AppResult.Success(msg)
    }
}

class MockAdministrativeAuditRepository : AdministrativeAuditRepository {
    private val events = ConcurrentHashMap<String, AdministrativeAuditEvent>()

    fun resetForTests() = events.clear()

    override suspend fun recordAdministrativeEvent(
        actorUserId: String,
        action: String,
        resourceType: String,
        resourceId: String,
        reasonCode: String,
        nowEpochMs: Long
    ): AppResult<Unit> {
        if (actorUserId.isBlank() || action.isBlank()) {
            return fail("ARGS_REQUIRED")
        }
        val id = UUID.randomUUID().toString()
        events[id] = AdministrativeAuditEvent(
            id = id,
            actorUserId = actorUserId,
            action = action,
            resourceType = resourceType,
            resourceId = resourceId,
            reasonCode = reasonCode,
            createdAtEpochMs = nowEpochMs
        )
        return AppResult.Success(Unit)
    }

    override suspend fun listAdministrativeEvents(): AppResult<List<AdministrativeAuditEvent>> =
        AppResult.Success(events.values.sortedByDescending { it.createdAtEpochMs })
}
