package com.comunidapp.app.domain.location

import com.comunidapp.app.data.model.AdoptionApplicationStatus
import com.comunidapp.app.domain.verification.VerificationDisplayPolicy
import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LeoVerCommunityCare02FixContractTest {

    @Test
    fun MATCHING_HAS_THRESHOLD_CAP_AND_ORDER() {
        val sql = migration("20260921020000_1091_match_cap_claim_arbitration.sql")
        assertTrue(sql.contains("score >= 0.55"))
        assertTrue(sql.contains("limit 15"))
        assertTrue(sql.contains("order by score desc, meters asc nulls last"))
        assertFalse(sql.contains("v_score := 0.40;\n    insert"))
    }

    @Test
    fun CLAIM_WAITS_TWO_SECONDS_THEN_NEAREST() {
        val sql = migration("20260921020000_1091_match_cap_claim_arbitration.sql")
        assertTrue(sql.contains("interval '2 seconds'"))
        assertTrue(sql.contains("pg_sleep"))
        assertTrue(sql.contains("distance_meters asc, a.created_at asc, a.responder_person_id asc"))
        assertTrue(sql.contains("_canon_apply_lost_found_claim"))
    }

    @Test
    fun VERIFICATION_GATES_AND_BADGES() {
        val sql = migration("20260921021500_1092_verification_gates_community.sql")
        assertTrue(sql.contains("PROFILE_INCOMPLETE"))
        assertTrue(sql.contains("BASE_LOCATION_REQUIRED"))
        assertTrue(sql.contains("TERMS_REQUIRED"))
        assertTrue(VerificationDisplayPolicy.BADGE_VISIBLE)
        assertEquals("Aún no verificado", VerificationDisplayPolicy.UNVERIFIED_COPY)
    }

    @Test
    fun TRANSIT_AND_ADOPTION_CONTRACTS() {
        val sql = migration("20260921023000_1093_transit_adoption_invite.sql")
        assertTrue(sql.contains("canon_request_foster_for_pet"))
        assertTrue(sql.contains("canon_select_foster_applicant"))
        assertTrue(sql.contains("set status = 'PAUSED'"))
        assertTrue(sql.contains("ADOPTION_USE_CANONICAL_TRANSFER"))
        assertTrue(sql.contains("email_outbox"))
        assertTrue(sql.contains("_canon_link_pending_pet_owners_for_user"))
        assertTrue(sql.contains("canon_create_vet_patient"))
        assertEquals(AdoptionApplicationStatus.PAUSED.displayNameEs, "En pausa")
    }

    @Test
    fun ANDROID_SCREENS_EXIST() {
        assertTrue(source("app/src/main/java/com/comunidapp/app/ui/screens/verification/LeoverVerificationRequestScreen.kt").contains("Solicitar verificación"))
        assertTrue(source("app/src/main/java/com/comunidapp/app/ui/screens/adoptions/AdoptionGeneralProfileScreen.kt").contains("Mi perfil de adopción"))
        assertTrue(source("app/src/main/java/com/comunidapp/app/ui/screens/foster/FosterCareRequestScreens.kt").contains("Necesito hogar de tránsito"))
        assertTrue(source("app/src/main/java/com/comunidapp/app/ui/screens/m28/ProfessionalHubScreens.kt").contains("Nuevo paciente"))
        assertTrue(source("app/src/main/java/com/comunidapp/app/ui/screens/lostfound/AlertMapScreen.kt").contains("Procesando aceptación"))
        assertTrue(source("app/src/main/java/com/comunidapp/app/ui/screens/lostfound/AlertMapScreen.kt").contains("colaborador más cercano"))
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()

    private fun migration(name: String): String {
        val file = File(UiRegressionGateTest.repoRoot(), "infra/supabase-canonical/supabase/migrations/$name")
        assertTrue(file.exists())
        return file.readText()
    }
}
