package com.comunidapp.app.domain.auth

import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonUsernameLoginContractTest {

    @Test
    fun unifiedLoginResolvesPersonUsernameBeforeStaff() {
        val vm = source("app/src/main/java/com/comunidapp/app/viewmodel/LoginViewModel.kt")
        assertTrue(vm.contains("loginWithUsername"))
        assertTrue(vm.contains("LoginIdentifier.looksLikeEmail"))
        assertFalse(vm.contains("loginAdministrative"))
        assertTrue(vm.contains("GENERIC_LOGIN_ERROR"))
        assertFalse(vm.contains("USERNAME_NOT_FOUND"))
        assertFalse(vm.contains("EMAIL_RESOLVED"))
        assertFalse(vm.contains("ADMIN_NOT_FOUND"))
    }

    @Test
    fun serverResolvesUsernameWithoutExposingServiceRole() {
        val sql = source(
            "infra/supabase-canonical/supabase/migrations/20260925220000_1097_person_username_login.sql"
        )
        assertTrue(sql.contains("canon_begin_username_login"))
        assertTrue(sql.contains("security definer"))
        assertTrue(sql.contains("from public.persons"))
        assertTrue(sql.contains("admin_begin_login"))
        assertTrue(sql.contains("INVALID_CREDENTIALS"))
        assertTrue(sql.contains("grant execute on function public.canon_begin_username_login"))
        assertFalse(sql.contains("service_role"))
        assertFalse(sql.contains("USERNAME_NOT_FOUND"))
        val android = source("app/src/main/java/com/comunidapp/app/data/repository/SupabaseAuthRepository.kt")
        assertTrue(android.contains("RPC_BEGIN_USERNAME_LOGIN"))
        assertTrue(android.contains("admin_begin_login"))
        assertFalse(android.contains("service_role"))
    }

    @Test
    fun staffLoginPathRemainsOnAdminBeginLogin() {
        val adminVm = source("app/src/main/java/com/comunidapp/app/viewmodel/AdminLoginViewModel.kt")
        assertTrue(adminVm.contains("loginAdministrative"))
        val repo = source("app/src/main/java/com/comunidapp/app/data/repository/SupabaseAuthRepository.kt")
        assertTrue(repo.contains("function = \"admin_begin_login\""))
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()
}
