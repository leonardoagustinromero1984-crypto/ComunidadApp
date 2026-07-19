package com.comunidapp.app.viewmodel.moderation

import com.comunidapp.app.domain.moderation.ModerationCaseNote
import com.comunidapp.app.domain.moderation.ModerationReport
import com.comunidapp.app.domain.moderation.ModerationTargetRef
import com.comunidapp.app.domain.moderation.ModerationTargetType
import com.comunidapp.app.domain.support.SupportMessage
import com.comunidapp.app.domain.support.SupportMessageVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveDataPresentationTest {

    private val report = ModerationReport(
        id = "r1",
        reporterId = "reporter-1",
        target = ModerationTargetRef(ModerationTargetType.POST, "p1"),
        reasonCode = "spam",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L
    )

    @Test
    fun hides_reporter_without_sensitive() {
        assertNull(SensitiveDataPresentation.reporterIdOrNull(report, false))
        assertEquals("", SensitiveDataPresentation.redactReporterId(report, false).reporterId)
    }

    @Test
    fun shows_reporter_with_sensitive() {
        assertEquals("reporter-1", SensitiveDataPresentation.reporterIdOrNull(report, true))
    }

    @Test
    fun hides_notes_without_sensitive() {
        val notes = listOf(
            ModerationCaseNote("n1", "c1", "a", "secret note", 1L)
        )
        assertTrue(SensitiveDataPresentation.notesForStaff(notes, false).isEmpty())
        assertEquals(1, SensitiveDataPresentation.notesForStaff(notes, true).size)
    }

    @Test
    fun requester_never_sees_internal_messages() {
        val messages = listOf(
            SupportMessage("1", "t", "u", SupportMessageVisibility.REQUESTER_VISIBLE, "hi", 1L),
            SupportMessage("2", "t", "s", SupportMessageVisibility.INTERNAL, "secret", 2L)
        )
        val visible = SensitiveDataPresentation.messagesForRequester(messages)
        assertEquals(1, visible.size)
        assertEquals("hi", visible.first().body)
    }
}
