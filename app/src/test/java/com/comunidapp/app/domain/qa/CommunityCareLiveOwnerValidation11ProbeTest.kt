package com.comunidapp.app.domain.qa

import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner validation on the pinned staging pair is explicit and one-way.
 * The probe may assert and confirm only in that phase. It must not claim,
 * mark IN_CARE, or create a second LOST/FOUND pair once history exists.
 */
class CommunityCareLiveOwnerValidation11ProbeTest {

    @Test
    fun ownerValidationIsAnExplicitIdempotentPhase() {
        val script = source("scripts/qa/probe-community-care-live-lost-found.py")
        assertTrue(script.contains("mutate = \"--mutate-owner-validation\" in sys.argv"))
        assertTrue(script.contains("if not mutate:"))
        assertTrue(script.contains("232c473b-cbf3-485f-8f89-cb27132c8f89"))
        assertTrue(script.contains("2e0cf009-325d-4db0-be03-4ca48c1b2f67"))
        assertTrue(script.contains("d3488929-afd1-489b-bac8-fd8e33e6093f"))
        assertTrue(script.contains("5f487c6f-f7b5-4b68-9419-d018b89195f8"))
        assertTrue(script.contains("qa14adopter"))
        assertTrue(script.contains("qa06foster"))
        assertTrue(script.contains("canon_get_my_responder_base"))
        assertTrue(script.contains("ALREADY_DONE"))
        assertTrue(script.contains("REFUSING DUPLICATE PAIR"))
        assertTrue(script.contains("CLAIM_AFTER_OWNER_CONFIRM: NOT_VALID_BY_STATE_MACHINE"))
        assertTrue(script.contains("QA03 CLAIM: NOT_RUN"))
        assertTrue(script.contains("IN_CARE: NOT_RUN"))
        val mutation = script.substringAfter("def mutate_owner_validation")
        assertTrue(mutation.contains("canon_assert_found_might_be_mine"))
        assertTrue(mutation.contains("canon_confirm_found_owner_match"))
        assertTrue(mutation.contains("if state == \"CONFIRMED\":"))
        assertFalse(mutation.contains("canon_claim_lost_found"))
        assertFalse(mutation.contains("canon_mark_lost_found_in_care"))
        assertFalse(mutation.contains("canon_register_lost_found_claim_attempt"))
        assertFalse(script.contains("canon_claim_lost_found"))
        assertFalse(script.contains("canon_mark_lost_found_in_care"))
        assertFalse(script.contains("canon_register_lost_found_claim_attempt"))
        assertFalse(script.contains("SUPABASE_SERVICE_ROLE_KEY"))
        assertFalse(script.contains("insert into"))
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()
}
