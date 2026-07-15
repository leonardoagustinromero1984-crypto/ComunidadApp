package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.domain.auth.EmailMasking
import com.comunidapp.app.domain.auth.LegalDocumentConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelsTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: MockAuthRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = MockAuthRepository()
        repo.resetForTests()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        MockAuthDatabase.resetToFixtures()
    }

    @Test
    fun register_requires_terms() = runTest(dispatcher) {
        val vm = RegisterViewModel(repo)
        vm.onNameChange("Ana")
        vm.onEmailChange("ana@email.com")
        vm.onPasswordChange("password1")
        vm.onConfirmPasswordChange("password1")
        vm.onAcceptedPrivacyChange(true)
        vm.register()
        advanceUntilIdle()
        assertNull(vm.uiState.value.registeredEmail)
        assertNotNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun register_requires_privacy() = runTest(dispatcher) {
        val vm = RegisterViewModel(repo)
        vm.onNameChange("Ana")
        vm.onEmailChange("ana2@email.com")
        vm.onPasswordChange("password1")
        vm.onConfirmPasswordChange("password1")
        vm.onAcceptedTermsChange(true)
        vm.register()
        advanceUntilIdle()
        assertNull(vm.uiState.value.registeredEmail)
    }

    @Test
    fun register_short_password() = runTest(dispatcher) {
        val vm = RegisterViewModel(repo)
        vm.onNameChange("Ana")
        vm.onEmailChange("ana3@email.com")
        vm.onPasswordChange("short")
        vm.onConfirmPasswordChange("short")
        vm.onAcceptedTermsChange(true)
        vm.onAcceptedPrivacyChange(true)
        vm.register()
        advanceUntilIdle()
        assertNull(vm.uiState.value.registeredEmail)
        assertTrue(vm.uiState.value.fieldErrors.containsKey("password") || vm.uiState.value.errorMessage != null)
    }

    @Test
    fun register_password_mismatch() = runTest(dispatcher) {
        val vm = RegisterViewModel(repo)
        vm.onNameChange("Ana")
        vm.onEmailChange("ana4@email.com")
        vm.onPasswordChange("password1")
        vm.onConfirmPasswordChange("password2")
        vm.onAcceptedTermsChange(true)
        vm.onAcceptedPrivacyChange(true)
        vm.register()
        advanceUntilIdle()
        assertNull(vm.uiState.value.registeredEmail)
    }

    @Test
    fun register_success_stores_consent_metadata() = runTest(dispatcher) {
        val vm = RegisterViewModel(repo)
        vm.onNameChange("Ana")
        vm.onEmailChange("ana5@email.com")
        vm.onPasswordChange("password1")
        vm.onConfirmPasswordChange("password1")
        vm.onAcceptedTermsChange(true)
        vm.onAcceptedPrivacyChange(true)
        vm.register()
        advanceUntilIdle()
        assertEquals("ana5@email.com", vm.uiState.value.registeredEmail)
        val consent = repo.consentFor("ana5@email.com")
        assertEquals(LegalDocumentConfig.terms.version, consent?.termsVersion)
        assertEquals(LegalDocumentConfig.privacy.version, consent?.privacyVersion)
    }

    @Test
    fun register_double_submit_ignored_while_loading() = runTest(dispatcher) {
        val vm = RegisterViewModel(repo)
        vm.onNameChange("Ana")
        vm.onEmailChange("ana6@email.com")
        vm.onPasswordChange("password1")
        vm.onConfirmPasswordChange("password1")
        vm.onAcceptedTermsChange(true)
        vm.onAcceptedPrivacyChange(true)
        // Simulate loading flag
        vm.register()
        vm.register()
        advanceUntilIdle()
        assertEquals("ana6@email.com", vm.uiState.value.registeredEmail)
    }

    @Test
    fun login_success() = runTest(dispatcher) {
        val vm = LoginViewModel(repo)
        vm.onEmailChange(MockData.currentUser.email)
        vm.onPasswordChange(MockAuthDatabase.DEMO_PASSWORD)
        vm.login()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isLoggedIn)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun login_error_uses_safe_message() = runTest(dispatcher) {
        val vm = LoginViewModel(repo)
        vm.onEmailChange(MockData.currentUser.email)
        vm.onPasswordChange("badbadbad")
        vm.login()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoggedIn)
        assertNotNull(vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.errorMessage!!.contains(MockAuthDatabase.DEMO_PASSWORD))
    }

    @Test
    fun login_unverified_redirects() = runTest(dispatcher) {
        repo.register(
            "U",
            "unverified@email.com",
            "password1",
            com.comunidapp.app.domain.auth.ConsentMetadata.forRegistration()
        )
        val vm = LoginViewModel(repo)
        vm.onEmailChange("unverified@email.com")
        vm.onPasswordChange("password1")
        vm.login()
        advanceUntilIdle()
        assertEquals("unverified@email.com", vm.uiState.value.needsEmailVerification)
    }

    @Test
    fun forgot_password_generic_success() = runTest(dispatcher) {
        val vm = ForgotPasswordViewModel(repo)
        vm.onEmailChange("nadie@email.com")
        vm.sendResetEmail()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.emailSent)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun email_verification_cooldown_constant() {
        assertEquals(60, EmailVerificationViewModel.RESEND_COOLDOWN_SECONDS)
    }

    @Test
    fun email_masking() {
        assertEquals("m***a@email.com", EmailMasking.mask("maria@email.com"))
        assertFalse(EmailMasking.mask("maria@email.com").contains("maria@"))
    }

    @Test
    fun legal_config_debug_allows_draft() {
        // Unit tests run with DEBUG BuildConfig typically
        assertTrue(LegalDocumentConfig.terms.version.isNotBlank())
        assertFalse(LegalDocumentConfig.terms.publishable)
        if (LegalDocumentConfig.isDebug) {
            assertTrue(LegalDocumentConfig.requireUsableForAuth().isSuccess)
        }
    }
}
