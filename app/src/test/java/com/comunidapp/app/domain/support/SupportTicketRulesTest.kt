package com.comunidapp.app.domain.support

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportTicketRulesTest {

    private val now = 1_700_000_000_000L

    @Test
    fun create_starts_open() {
        val ticket = SupportValidators.validateCreate(
            "u1",
            SupportCategory.TECHNICAL,
            "No puedo entrar",
            "Detalle del problema de acceso a la cuenta.",
            now
        ).getOrThrow()
        assertTrue(ticket.status == SupportTicketStatus.OPEN)
    }

    @Test
    fun subject_and_description_limits() {
        assertTrue(
            SupportValidators.validateCreate(
                "u1", SupportCategory.OTHER, "ab", "detalle", now
            ).isFailure
        )
        assertTrue(
            SupportValidators.validateCreate(
                "u1",
                SupportCategory.OTHER,
                "Asunto ok",
                "",
                now
            ).isFailure
        )
    }

    @Test
    fun close_requires_resolution_or_reason() {
        val open = SupportTicket(
            id = "t1",
            requesterUserId = "u1",
            category = SupportCategory.PROFILE,
            subject = "Asunto",
            description = "Descripción",
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        assertTrue(SupportValidators.canClose(open, null).isFailure)
        assertTrue(SupportValidators.canClose(open, "duplicate").isSuccess)
        assertTrue(
            SupportValidators.canClose(
                open.copy(status = SupportTicketStatus.RESOLVED),
                null
            ).isSuccess
        )
    }

    @Test
    fun internal_message_not_visible_to_requester() {
        assertFalse(
            SupportValidators.isVisibleToRequester(SupportMessageVisibility.INTERNAL)
        )
        assertTrue(
            SupportValidators.isVisibleToRequester(SupportMessageVisibility.REQUESTER_VISIBLE)
        )
    }

    @Test
    fun privacy_and_safety_are_sensitive() {
        assertTrue(SupportValidators.isSensitiveCategory(SupportCategory.PRIVACY))
        assertTrue(SupportValidators.isSensitiveCategory(SupportCategory.SAFETY))
        assertFalse(SupportValidators.isSensitiveCategory(SupportCategory.TECHNICAL))
    }

    @Test
    fun no_realtime_chat() {
        assertFalse(SupportValidators.usesRealtimeChat())
    }

    @Test
    fun can_reopen_only_resolved() {
        val resolved = SupportTicket(
            id = "t1",
            requesterUserId = "u1",
            category = SupportCategory.OTHER,
            subject = "Asunto",
            description = "Descripción",
            status = SupportTicketStatus.RESOLVED,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        assertTrue(SupportValidators.canReopen(resolved))
        assertFalse(SupportValidators.canReopen(resolved.copy(status = SupportTicketStatus.CLOSED)))
    }
}
