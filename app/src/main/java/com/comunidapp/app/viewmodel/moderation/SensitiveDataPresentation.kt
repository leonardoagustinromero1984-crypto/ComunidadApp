package com.comunidapp.app.viewmodel.moderation

import com.comunidapp.app.domain.moderation.ModerationCaseNote
import com.comunidapp.app.domain.moderation.ModerationReport
import com.comunidapp.app.domain.support.SupportMessage
import com.comunidapp.app.domain.support.SupportMessageVisibility
import com.comunidapp.app.domain.support.SupportValidators

/**
 * Presentación segura de datos sensibles (puro / unit-testable).
 * No loguea PII ni cuerpos de notas internas.
 */
object SensitiveDataPresentation {

    /** reporterId solo con moderation.view_sensitive; sin placeholder inferible. */
    fun reporterIdOrNull(report: ModerationReport, canViewSensitive: Boolean): String? {
        if (!canViewSensitive) return null
        return report.reporterId.takeIf { it.isNotBlank() }
    }

    fun redactReporterId(report: ModerationReport, canViewSensitive: Boolean): ModerationReport {
        if (canViewSensitive) return report
        return report.copy(reporterId = "")
    }

    fun notesForStaff(
        notes: List<ModerationCaseNote>,
        canViewSensitive: Boolean
    ): List<ModerationCaseNote> =
        if (canViewSensitive) notes else emptyList()

    /** Mensajes INTERNAL nunca al solicitante. */
    fun messagesForRequester(messages: List<SupportMessage>): List<SupportMessage> =
        messages.filter { SupportValidators.isVisibleToRequester(it.visibility) }

    fun messagesForStaff(
        messages: List<SupportMessage>,
        includeInternal: Boolean
    ): List<SupportMessage> =
        if (includeInternal) {
            messages
        } else {
            messages.filter { it.visibility == SupportMessageVisibility.REQUESTER_VISIBLE }
        }

    fun isInternalMessage(message: SupportMessage): Boolean =
        message.visibility == SupportMessageVisibility.INTERNAL
}
