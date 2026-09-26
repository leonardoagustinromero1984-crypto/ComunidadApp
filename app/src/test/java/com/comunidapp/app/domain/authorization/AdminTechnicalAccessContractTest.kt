package com.comunidapp.app.domain.authorization

import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminTechnicalAccessContractTest {

    @Test
    fun adminSessionRoutesToHubWithoutSocialHome() {
        val graph = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(graph.contains("SessionState.AdminSession ->"))
        assertTrue(graph.contains("AdminSessionNavHost"))
        assertTrue(graph.contains("SessionState.AdminPasswordChangeRequired ->"))
        assertTrue(graph.contains("AdminPasswordChangeScreen"))
        assertTrue(graph.contains("SessionState.AdminMfaEnrollmentRequired ->"))
        assertTrue(graph.contains("AdminMfaEnrollmentScreen"))
        assertTrue(graph.contains("SessionState.AdminMfaChallengeRequired ->"))
        assertTrue(graph.contains("AdminMfaChallengeScreen"))
        assertFalse(graph.contains("ADMIN_LOGIN"))
        assertFalse(graph.contains("AdminLoginScreen"))
        assertTrue(graph.contains("exclusiveAdminSession = true"))
        assertTrue(graph.contains("startDestination = NavRoutes.ADMIN_HUB"))
    }

    @Test
    fun loginIsUnifiedWithoutAdministrativeEntry() {
        val login = source("app/src/main/java/com/comunidapp/app/ui/screens/login/LoginScreen.kt")
        assertTrue(login.contains("Correo o usuario"))
        assertTrue(login.contains("Iniciar sesión"))
        assertTrue(login.contains("ContinueWithGoogleButton"))
        assertFalse(login.contains("Acceso administrativo"))
        assertFalse(login.contains("SUPERADMIN"))
        assertFalse(login.contains("AdminLogin"))
        val vm = source("app/src/main/java/com/comunidapp/app/viewmodel/LoginViewModel.kt")
        assertTrue(vm.contains("loginWithUsername"))
        assertTrue(vm.contains("LoginIdentifier.looksLikeEmail"))
        assertFalse(vm.contains("loginAdministrative"))
        assertFalse(vm.contains("superAdmin"))
        assertFalse(vm.contains("if username"))
    }

    @Test
    fun credentialsAreNotHardcodedInProductSources() {
        val roots = listOf(
            "app/src/main",
            "infra/supabase-canonical/supabase/migrations",
            "scripts/qa"
        )
        val forbidden = listOf("LeoVerAdmin")
        roots.forEach { root ->
            val hits = forbidden.flatMap { needle ->
                findNeedle(root, needle)
            }
            assertTrue("credencial operativa en $root: $hits", hits.isEmpty())
        }
    }

    @Test
    fun resetPreservesTechnicalAdminIdentity() {
        val reset = source("scripts/qa/reset-staging.sql")
        assertTrue(reset.contains("platform_admin_identities"))
        assertTrue(reset.contains("_qa_platform_admin_identities"))
        assertTrue(reset.contains("insert into public.platform_admin_identities"))
    }

    @Test
    fun usernameResolutionIsServerSide() {
        val sql = source(
            "infra/supabase-canonical/supabase/migrations/20260831180000_1056_admin_technical_identity.sql"
        )
        assertTrue(sql.contains("admin_begin_login"))
        assertTrue(sql.contains("grant execute on function public.admin_begin_login"))
        assertTrue(sql.contains("ROOT_PROTECTED"))
        assertTrue(sql.contains("LAST_SUPERADMIN_REQUIRED"))
        assertTrue(sql.contains("must_change_password"))
        assertFalse(sql.contains("insert into public.platform_admin_identities"))
        val android = source("app/src/main/java/com/comunidapp/app/data/repository/SupabaseAuthRepository.kt")
        assertTrue(android.contains("admin_begin_login"))
        assertFalse(android.contains("service_role"))
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()

    private fun findNeedle(root: String, needle: String): List<String> {
        val dir = java.io.File(UiRegressionGateTest.repoRoot(), root)
        if (!dir.exists()) return emptyList()
        return dir.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "kts", "sql", "ps1", "xml") }
            .flatMap { file ->
                val text = file.readText()
                if (text.contains(needle)) {
                    sequenceOf(file.relativeTo(UiRegressionGateTest.repoRoot()).path)
                } else {
                    emptySequence()
                }
            }
            .toList()
    }
}
