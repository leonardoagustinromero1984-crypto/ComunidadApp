package com.comunidapp.app.domain.authorization

import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminMfaAal2ContractTest {

    @Test
    fun migrationRequiresAal2AndKeepsIdentityDetectionAtAal1() {
        val sql = source(
            "infra/supabase-canonical/supabase/migrations/20260902180000_1062_admin_mfa_aal2.sql"
        )
        assertTrue(sql.contains("_canon_require_admin_aal2"))
        assertTrue(sql.contains("auth.jwt()->>'aal'"))
        assertTrue(sql.contains("get_admin_auth_state"))
        assertTrue(sql.contains("MFA_REQUIRED"))
        assertTrue(sql.contains("staff_reset_mfa"))
        assertTrue(sql.contains("ADMIN_MFA_RESET"))
        assertTrue(sql.contains("ROOT_PROTECTED"))
        assertTrue(sql.contains("country_markets_select"))
        assertFalse(sql.contains("mfa_verified=true"))
        assertFalse(sql.contains("bypass password"))
        assertFalse(sql.contains("emergency_totp"))
    }

    @Test
    fun androidUsesNativeSupabaseMfaApis() {
        val repo = source("app/src/main/java/com/comunidapp/app/data/repository/AdminMfaRepository.kt")
        assertTrue(repo.contains("auth.mfa.enroll"))
        assertTrue(repo.contains("createChallenge"))
        assertTrue(repo.contains("verifyChallenge"))
        assertTrue(repo.contains("getAuthenticatorAssuranceLevel"))
        assertTrue(repo.contains("retrieveFactorsForCurrentUser"))
        assertTrue(repo.contains("FactorType.TOTP"))
        assertFalse(repo.contains("SharedPreferences"))
        assertFalse(repo.contains("DataStore"))
        val session = source("app/src/main/java/com/comunidapp/app/viewmodel/SessionViewModel.kt")
        assertTrue(session.contains("permissionRepository.refresh"))
        assertTrue(session.contains("confirmAal2"))
        assertTrue(session.indexOf("confirmAal2") < session.lastIndexOf("permissionRepository.refresh"))
    }

    @Test
    fun enrollmentScreenUsesFlagSecureAndDoesNotNameABrand() {
        val enroll = source("app/src/main/java/com/comunidapp/app/ui/screens/admin/AdminMfaEnrollmentScreen.kt")
        assertTrue(enroll.contains("SecureWindow"))
        assertTrue(enroll.contains("Protegé tu cuenta"))
        assertTrue(enroll.contains("LeoVer"))
        assertTrue(enroll.contains("Copiar clave"))
        assertTrue(enroll.contains("Clave copiada"))
        assertTrue(enroll.contains("EXTRA_IS_SENSITIVE"))
        assertTrue(enroll.contains("copyTotpSecretToClipboard"))
        assertFalse(enroll.contains("SharedPreferences"))
        assertFalse(enroll.contains("DataStore"))
        assertFalse(enroll.contains("Google Authenticator"))
        val challenge = source("app/src/main/java/com/comunidapp/app/ui/screens/admin/AdminMfaChallengeScreen.kt")
        assertTrue(challenge.contains("Código incorrecto. Intentá nuevamente."))
        assertFalse(challenge.contains("SecureWindow"))
    }

    @Test
    fun edgeEnforcesAal2BeforeServiceRole() {
        val fn = source("infra/supabase-canonical/supabase/functions/admin-staff/index.ts")
        val beforeService = fn.substringBefore("createClient(supabaseUrl, serviceKey)")
        assertTrue(beforeService.contains("get_admin_auth_state"))
        assertTrue(beforeService.contains("MFA_REQUIRED"))
        assertTrue(beforeService.contains("get_admin_session"))
        assertTrue(fn.contains("staff_reset_mfa"))
        assertFalse(fn.contains("mfa_verified"))
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()
}
