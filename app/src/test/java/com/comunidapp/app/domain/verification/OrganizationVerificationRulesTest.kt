package com.comunidapp.app.domain.verification

import com.comunidapp.app.domain.organization.OrganizationVerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrganizationVerificationRulesTest {

    @Test
    fun approve_pending_produces_verified() {
        val next = VerificationValidators.validateDecision(
            OrganizationVerificationStatus.PENDING,
            OrganizationVerificationDecision.APPROVE,
            reviewNote = "ok",
            actorUserId = "staff-1",
            actorIsOrgMember = false
        ).getOrThrow()
        assertEquals(OrganizationVerificationStatus.VERIFIED, next)
    }

    @Test
    fun reject_pending_produces_rejected() {
        val next = VerificationValidators.validateDecision(
            OrganizationVerificationStatus.PENDING,
            OrganizationVerificationDecision.REJECT,
            null,
            "staff-1",
            false
        ).getOrThrow()
        assertEquals(OrganizationVerificationStatus.REJECTED, next)
    }

    @Test
    fun request_more_info_keeps_pending_and_not_verified() {
        val next = VerificationValidators.validateDecision(
            OrganizationVerificationStatus.PENDING,
            OrganizationVerificationDecision.REQUEST_MORE_INFORMATION,
            "faltan docs",
            "staff-1",
            false
        ).getOrThrow()
        assertEquals(OrganizationVerificationStatus.PENDING, next)
        assertFalse(VerificationValidators.requestMoreInfoMarksVerified())
    }

    @Test
    fun org_member_cannot_self_review() {
        val result = VerificationValidators.validateDecision(
            OrganizationVerificationStatus.PENDING,
            OrganizationVerificationDecision.APPROVE,
            null,
            "owner-1",
            actorIsOrgMember = true
        )
        assertTrue(result.isFailure)
        assertEquals("CONFLICT_ORG_MEMBER", result.exceptionOrNull()?.message)
        assertFalse(VerificationValidators.orgCanSelfReview())
    }

    @Test
    fun revoke_only_verified() {
        assertTrue(
            VerificationValidators.validateDecision(
                OrganizationVerificationStatus.PENDING,
                OrganizationVerificationDecision.REVOKE,
                null,
                "staff-1",
                false
            ).isFailure
        )
        assertEquals(
            OrganizationVerificationStatus.EXPIRED,
            VerificationValidators.validateDecision(
                OrganizationVerificationStatus.VERIFIED,
                OrganizationVerificationDecision.REVOKE,
                "fraude",
                "staff-1",
                false
            ).getOrThrow()
        )
    }

    @Test
    fun document_ref_rejects_http_url() {
        var threw = false
        try {
            OrganizationVerificationDocumentRef(
                id = "d1",
                organizationId = "o1",
                logicalName = "cuit.pdf",
                storagePathHint = "http://evil/file"
            )
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }
}
