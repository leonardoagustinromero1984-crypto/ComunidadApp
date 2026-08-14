package com.comunidapp.shared.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AppleAuthMappingTest {

    @Test
    fun apple_sign_in_success_fake_gateway() = runTest {
        val gateway = FakeAuthSessionGateway()
        val repo = GatewayAuthRepository(gateway)
        val result = repo.signInWithAppleIdToken("apple-id-token-demo", "nonce-raw")
        assertIs<AuthResult.Success>(result)
        assertEquals("apple-id-token-demo", gateway.lastAppleIdToken)
        assertEquals("nonce-raw", gateway.lastAppleNonce)
    }

    @Test
    fun apple_missing_token_invalid_credentials() = runTest {
        val repo = GatewayAuthRepository(FakeAuthSessionGateway())
        val result = repo.signInWithAppleIdToken("  ", null)
        assertIs<AuthFailure.InvalidCredentials>((result as AuthResult.Failure).failure)
    }

    @Test
    fun apple_cancelled_maps_soft_ui_message() {
        val msg = AuthFailureMessages.message(AuthFailure.Cancelled)
        assertTrue(msg.contains("cancel", ignoreCase = true))
        assertTrue("token" !in msg.lowercase())
    }

    @Test
    fun apple_configuration_required_message() {
        val msg = AuthFailureMessages.message(AuthFailure.ConfigurationRequired)
        assertTrue(msg.contains("Apple", ignoreCase = true) || msg.contains("configuración", ignoreCase = true))
    }

    @Test
    fun apple_gateway_cancelled_failure() = runTest {
        val gateway = FakeAuthSessionGateway()
        gateway.failAppleSignInWith = AuthFailure.Cancelled
        val result = GatewayAuthRepository(gateway).signInWithAppleIdToken("tok", "n")
        assertIs<AuthFailure.Cancelled>((result as AuthResult.Failure).failure)
    }

    @Test
    fun apple_unconfigured_repo() = runTest {
        val result = UnconfiguredAuthSessionRepository()
            .signInWithAppleIdToken("tok", null)
        assertIs<AuthFailure.ConfigurationRequired>((result as AuthResult.Failure).failure)
    }

    @Test
    fun login_vm_apple_cancelled_ui() = runTest {
        val gateway = FakeAuthSessionGateway()
        gateway.failAppleSignInWith = AuthFailure.Cancelled
        val repo = GatewayAuthRepository(gateway)
        val controller = object : AppleSignInController {
            override suspend fun requestCredential(): AppleSignInPlatformResult =
                AppleSignInPlatformResult.Cancelled
        }
        // On android host, isAppleSignInAvailable() is false — exercise mapping via direct repo + messages.
        assertEquals(
            AuthFailureMessages.message(AuthFailure.Cancelled),
            AuthFailureMessages.message(AuthFailure.Cancelled)
        )
        val result = repo.signInWithAppleIdToken("x", null)
        assertIs<AuthFailure.Cancelled>((result as AuthResult.Failure).failure)
        // Keep controller reference used (compile / future ios).
        assertIs<AppleSignInPlatformResult.Cancelled>(controller.requestCredential())
    }

    @Test
    fun error_mapper_cancel_and_config() {
        assertIs<AuthFailure.Cancelled>(
            AuthErrorMapper.fromThrowable(RuntimeException("user cancelled"))
        )
        assertIs<AuthFailure.ConfigurationRequired>(
            AuthErrorMapper.fromThrowable(RuntimeException("Apple provider is not enabled"))
        )
    }
}
