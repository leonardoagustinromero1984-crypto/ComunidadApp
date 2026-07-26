package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.repository.M14PublicQrPayloadService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class M14Block3WorkflowTest {
    @Test
    fun qr_payload_contains_only_public_code_scheme() {
        val ok = M14PublicQrPayloadService.buildPayload("PUB-ABCDEF0123456789ABCDEF0123456789").getOrThrow()
        assertTrue(ok.startsWith("leover://passport/PUB-"))
        assertFalse(ok.contains("user"))
        assertFalse(ok.contains("microchip"))
        assertFalse(ok.contains("pet"))
        assertTrue(M14PublicQrPayloadService.buildPayload("BAD").isFailure)
        assertTrue(M14PublicQrPayloadService.buildPayload("PUB-john.doe@mail.com").isFailure)
    }

    @Test
    fun migration_052_has_ten_rpcs_and_for_update() {
        val root = listOf(File("."), File(".."), File("../.."))
            .first { File(it, "supabase/migrations").isDirectory }
        val sql = File(root, "supabase/migrations/052_m14_credential_verification_and_public_access.sql").readText()
        listOf(
            "m14_open_verification_review",
            "m14_approve_verification_request",
            "m14_reject_verification_request",
            "m14_expire_verification_request",
            "m14_get_verification_decision",
            "m14_list_verification_decisions",
            "m14_issue_verified_credential",
            "m14_revoke_verified_credential",
            "m14_rotate_public_code",
            "m14_list_passport_status_history"
        ).forEach { assertTrue(sql.contains(it)) }
        assertTrue(sql.contains("for update"))
        assertTrue(sql.contains("UNDER_REVIEW"))
        assertFalse(sql.contains("service_role"))
    }
}
