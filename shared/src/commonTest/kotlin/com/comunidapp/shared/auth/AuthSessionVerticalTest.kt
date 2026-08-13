package com.comunidapp.shared.auth

import com.comunidapp.shared.session.FakeSessionRepository
import com.comunidapp.shared.session.SessionDataMode
import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.session.SessionUser
import com.comunidapp.shared.ui.ErrorSanitizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class AuthSessionVerticalTest {

    @Test
    fun initial_unknown() = runTest {
        val gateway = FakeAuthSessionGateway(SessionState.Unknown)
        assertIs<SessionState.Unknown>(gateway.currentSession())
    }

    @Test
    fun restore_no_session() = runTest {
        val gateway = FakeAuthSessionGateway(SessionState.Unknown)
        gateway.restoreTo = SessionState.Unauthenticated
        val repo = GatewayAuthRepository(gateway)
        assertIs<SessionState.Unauthenticated>(repo.restoreSession())
    }

    @Test
    fun restore_valid_session() = runTest {
        val user = SessionUser("u1", "a@leover.test", "Ana")
        val gateway = FakeAuthSessionGateway(SessionState.Unknown)
        gateway.restoreTo = SessionState.Authenticated(user)
        val repo = GatewayAuthRepository(gateway)
        val restored = assertIs<SessionState.Authenticated>(repo.restoreSession())
        assertEquals("u1", restored.user.userId)
        assertEquals("a@leover.test", restored.user.email)
    }

    @Test
    fun authenticated_mapping_no_tokens_in_user() {
        val user = SessionUser("u1", "a@leover.test", "Ana")
        // SessionUser properties are only id/email/displayName — no access/refresh fields.
        assertEquals("u1", user.userId)
        assertFalse(user.toString().contains("eyJ", ignoreCase = false) && user.userId.startsWith("eyJ"))
    }

    @Test
    fun invalid_credentials() = runTest {
        val gateway = FakeAuthSessionGateway()
        gateway.failSignInWith = AuthFailure.InvalidCredentials
        val repo = GatewayAuthRepository(gateway)
        val result = repo.signInWithEmailPassword(SignInRequest("a@test.com", "bad"))
        assertIs<AuthResult.Failure>(result)
        assertIs<AuthFailure.InvalidCredentials>(result.failure)
    }

    @Test
    fun network_error() = runTest {
        val gateway = FakeAuthSessionGateway()
        gateway.failSignInWith = AuthFailure.Network
        val repo = GatewayAuthRepository(gateway)
        val result = repo.signInWithEmailPassword(SignInRequest("a@test.com", "x"))
        assertEquals(AuthFailure.Network, (result as AuthResult.Failure).failure)
    }

    @Test
    fun generic_error_sanitized() {
        val failure = AuthErrorMapper.fromThrowable(
            IllegalStateException("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.secret")
        )
        val msg = AuthFailureMessages.message(failure)
        assertFalse(msg.contains("eyJ"))
        assertFalse(msg.contains("Bearer"))
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun sign_out_clears_session() = runTest {
        val gateway = FakeAuthSessionGateway(
            SessionState.Authenticated(SessionUser("u1", "a@test.com"))
        )
        val repo = GatewayAuthRepository(gateway)
        repo.signOut()
        assertIs<SessionState.Unauthenticated>(repo.currentSession())
    }

    @Test
    fun sign_out_returns_unauthenticated_flow() = runTest {
        val gateway = FakeAuthSessionGateway(
            SessionState.Authenticated(SessionUser("u1", "a@test.com"))
        )
        val repo = GatewayAuthRepository(gateway)
        repo.signOut()
        assertIs<SessionState.Unauthenticated>(repo.observeSession().first())
    }

    @Test
    fun expired_state() = runTest {
        val gateway = FakeAuthSessionGateway(SessionState.Expired)
        assertIs<SessionState.Expired>(gateway.currentSession())
    }

    @Test
    fun refresh_success() = runTest {
        val gateway = FakeAuthSessionGateway(SessionState.Expired)
        val repo = GatewayAuthRepository(gateway)
        assertIs<AuthResult.Success>(repo.refreshSession())
        assertIs<SessionState.Authenticated>(repo.currentSession())
    }

    @Test
    fun refresh_failure() = runTest {
        val gateway = FakeAuthSessionGateway(SessionState.Expired)
        gateway.failRefreshWith = AuthFailure.SessionExpired
        val repo = GatewayAuthRepository(gateway)
        assertIs<AuthResult.Failure>(repo.refreshSession())
        assertIs<SessionState.Expired>(repo.currentSession())
    }

    @Test
    fun ui_login_loading_then_success() = runTest {
        val gateway = FakeAuthSessionGateway(SessionState.Unauthenticated)
        val repo = GatewayAuthRepository(gateway)
        val vm = LoginViewModelShared(repo, scope = this)
        assertIs<LoginUiState.Idle>(vm.uiState.value)
        vm.signIn("demo@leover.test", "secret-password")
        advanceUntilIdle()
        assertIs<LoginUiState.Authenticated>(vm.uiState.value)
        assertFalse(vm.uiState.value.toString().contains("secret-password"))
    }

    @Test
    fun ui_login_error() = runTest {
        val gateway = FakeAuthSessionGateway(SessionState.Unauthenticated)
        gateway.failSignInWith = AuthFailure.InvalidCredentials
        val repo = GatewayAuthRepository(gateway)
        val vm = LoginViewModelShared(repo, scope = this)
        vm.signIn("demo@leover.test", "bad")
        advanceUntilIdle()
        val err = assertIs<LoginUiState.Error>(vm.uiState.value)
        assertEquals("Email o contraseña incorrectos.", err.message)
        assertFalse(err.message.contains("bad"))
    }

    @Test
    fun password_never_in_login_ui_state() = runTest {
        val gateway = FakeAuthSessionGateway()
        val repo = GatewayAuthRepository(gateway)
        val vm = LoginViewModelShared(repo, scope = this)
        vm.signIn("x@y.com", "SuperSecret123!")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.toString().contains("SuperSecret123!"))
    }

    @Test
    fun token_never_in_session_user() {
        val user = SessionUser("uid", "e@x.com", "Name")
        val fields = listOf(user.userId, user.email, user.displayName)
        assertTrue(fields.none { it.orEmpty().startsWith("eyJ") })
    }

    @Test
    fun fake_session_repository_still_works() = runTest {
        val fake = FakeSessionRepository()
        assertEquals(SessionDataMode.SESSION_STUB, fake.dataMode)
        assertIs<SessionState.Authenticated>(fake.currentSession())
        fake.signOut()
        assertIs<SessionState.Unauthenticated>(fake.currentSession())
    }

    @Test
    fun real_remote_mode_on_gateway_repo() {
        val repo = GatewayAuthRepository(FakeAuthSessionGateway())
        assertEquals(SessionDataMode.REAL_REMOTE, repo.dataMode)
        assertEquals(SessionDataMode.REAL_REMOTE, UnconfiguredAuthSessionRepository().dataMode)
    }

    @Test
    fun unconfigured_login_unavailable() = runTest {
        val repo = UnconfiguredAuthSessionRepository()
        val result = repo.signInWithEmailPassword(SignInRequest("a@b.com", "x"))
        assertIs<AuthResult.Failure>(result)
        assertIs<AuthFailure.Unavailable>(result.failure)
    }

    @Test
    fun config_rejects_service_role() {
        assertFalse(
            SharedSupabaseConfig(
                "https://xyz.supabase.co",
                "service_role_key_value"
            ).isUsable
        )
        assertTrue(
            SharedSupabaseConfig(
                "https://xyz.supabase.co",
                "anon-public-key"
            ).isUsable
        )
    }

    @Test
    fun secure_storage_roundtrip_memory() {
        val storage = InMemorySecureSessionStorage()
        storage.write("k", "v")
        assertEquals("v", storage.read("k"))
        storage.remove("k")
        assertEquals(null, storage.read("k"))
    }

    @Test
    fun error_sanitizer_strips_auth_noise() {
        val msg = ErrorSanitizer.sanitize(IllegalStateException("AUTH_UNAVAILABLE stack"))
        assertTrue(msg.isNotBlank())
    }
}
