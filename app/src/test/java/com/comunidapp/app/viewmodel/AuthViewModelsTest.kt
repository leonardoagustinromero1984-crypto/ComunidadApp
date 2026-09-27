package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.repository.MockAdministrativeIdentityStore
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.data.repository.MockUserRepository
import com.comunidapp.app.domain.authorization.AdminSessionInfo
import com.comunidapp.app.domain.auth.EmailMasking
import com.comunidapp.app.domain.auth.LegalDocumentConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: MockAuthRepository
    private lateinit var userRepo: MockUserRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = MockAuthRepository()
        repo.resetForTests()
        userRepo = MockUserRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        MockAuthDatabase.resetToFixtures()
        MockAdministrativeIdentityStore.reset()
    }

    private fun kotlinx.coroutines.test.TestScope.fillValidForm(
        vm: RegisterViewModel,
        email: String,
        username: String,
        password: String = "password1",
        confirm: String = password
    ) {
        vm.onFirstNameChange("Ana")
        vm.onLastNameChange("Lopez")
        vm.onUsernameChange(username)
        advanceTimeBy(500)
        advanceUntilIdle()
        vm.onBirthDateChange("1990-01-15")
        vm.onEmailChange(email)
        vm.onPasswordChange(password)
        vm.onConfirmPasswordChange(confirm)
        vm.onAcceptedTermsChange(true)
        vm.onAcceptedPrivacyChange(true)
    }

    @Test
    fun register_requires_terms() = runTest(dispatcher) {
        val vm = RegisterViewModel(repo, userRepo)
        fillValidForm(vm, "ana@email.com", "ana_lopez")
        vm.onAcceptedTermsChange(false)
        vm.register()
        advanceUntilIdle()
        assertNull(vm.uiState.value.registeredEmail)
    }

    @Test
    fun register_requires_privacy() = runTest(dispatcher) {
        val vm = RegisterViewModel(repo, userRepo)
        fillValidForm(vm, "ana2@email.com", "ana_lopez2")
        vm.onAcceptedPrivacyChange(false)
        vm.register()
        advanceUntilIdle()
        assertNull(vm.uiState.value.registeredEmail)
    }

    @Test
    fun register_requires_username() = runTest(dispatcher) {
        val vm = RegisterViewModel(repo, userRepo)
        vm.onFirstNameChange("Ana")
        vm.onLastNameChange("Lopez")
        vm.onEmailChange("ana0@email.com")
        vm.onPasswordChange("password1")
        vm.onConfirmPasswordChange("password1")
        vm.onAcceptedTermsChange(true)
        vm.onAcceptedPrivacyChange(true)
        vm.register()
        advanceUntilIdle()
        assertNull(vm.uiState.value.registeredEmail)
        assertFalse(vm.uiState.value.canSubmit)
    }

    @Test
    fun register_short_password() = runTest(dispatcher) {
        val vm = RegisterViewModel(repo, userRepo)
        fillValidForm(vm, "ana3@email.com", "ana_lopez3", password = "short", confirm = "short")
        vm.register()
        advanceUntilIdle()
        assertNull(vm.uiState.value.registeredEmail)
        assertTrue(vm.uiState.value.fieldErrors.containsKey("password") || vm.uiState.value.errorMessage != null)
    }

    @Test
    fun register_password_mismatch() = runTest(dispatcher) {
        val vm = RegisterViewModel(repo, userRepo)
        fillValidForm(vm, "ana4@email.com", "ana_lopez4", password = "password1", confirm = "password2")
        vm.register()
        advanceUntilIdle()
        assertNull(vm.uiState.value.registeredEmail)
    }

    @Test
    fun register_success_stores_consent_metadata() = runTest(dispatcher) {
        val vm = RegisterViewModel(repo, userRepo)
        fillValidForm(vm, "ana5@email.com", "ana_lopez5")
        assertEquals(UsernameAvailabilityUi.AVAILABLE, vm.uiState.value.usernameAvailability)
        vm.register()
        advanceUntilIdle()
        assertEquals("ana5@email.com", vm.uiState.value.registeredEmail)
        val consent = repo.consentFor("ana5@email.com")
        assertEquals(LegalDocumentConfig.terms.version, consent?.termsVersion)
        assertEquals(LegalDocumentConfig.privacy.version, consent?.privacyVersion)
    }

    @Test
    fun register_success_without_verified_session_goes_to_verification() = runTest(dispatcher) {
        val vm = RegisterViewModel(repo, userRepo)
        fillValidForm(vm, "ana7@email.com", "ana_lopez7")
        vm.register()
        advanceUntilIdle()
        assertEquals("ana7@email.com", vm.uiState.value.registeredEmail)
        assertNull(vm.uiState.value.errorMessage)
        assertFalse(repo.getCurrentUser()?.emailVerified == true && repo.getCurrentUser()?.email == "ana7@email.com")
    }

    @Test
    fun register_existing_confirmed_email_does_not_succeed() = runTest(dispatcher) {
        val vm = RegisterViewModel(repo, userRepo)
        fillValidForm(vm, MockData.currentUser.email, "ana_existente")
        vm.register()
        advanceUntilIdle()
        assertNull(vm.uiState.value.registeredEmail)
        assertFalse(vm.uiState.value.offerResendConfirmation)
        assertTrue(vm.uiState.value.emailAlreadyRegistered)
        assertEquals("Este correo ya está registrado", vm.uiState.value.errorTitle)
        assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("Ya existe una cuenta"))
    }

    @Test
    fun register_double_submit_ignored_while_loading() = runTest(dispatcher) {
        val vm = RegisterViewModel(repo, userRepo)
        fillValidForm(vm, "ana6@email.com", "ana_lopez6")
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
    fun login_person_username_sets_logged_in() = runTest(dispatcher) {
        val vm = LoginViewModel(repo)
        vm.onEmailChange("maria.demo")
        vm.onPasswordChange(MockAuthDatabase.DEMO_PASSWORD)
        vm.login()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isLoggedIn)
        assertNull(vm.uiState.value.errorMessage)
        assertEquals(MockData.currentUser.id, repo.getCurrentUser()?.id)
    }

    @Test
    fun login_username_uses_backend_identity_without_person_flag() = runTest(dispatcher) {
        MockAdministrativeIdentityStore.seed(
            MockAdministrativeIdentityStore.Entry(
                username = "qa.admin",
                password = "test-admin-pass",
                user = User(
                    id = "admin-tech-1",
                    name = "",
                    email = "hidden-internal@invalid",
                    emailVerified = true
                ),
                session = AdminSessionInfo(
                    userId = "admin-tech-1",
                    mustChangePassword = false,
                    isRoot = true
                )
            )
        )
        val vm = LoginViewModel(repo)
        vm.onEmailChange("qa.admin")
        vm.onPasswordChange("test-admin-pass")
        vm.login()
        advanceUntilIdle()
        assertNull(vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isLoggedIn)
        assertEquals("admin-tech-1", repo.getCurrentUser()?.id)
    }

    @Test
    fun login_username_wrong_password_is_generic() = runTest(dispatcher) {
        MockAdministrativeIdentityStore.seed(
            MockAdministrativeIdentityStore.Entry(
                username = "qa.admin",
                password = "test-admin-pass",
                user = User(
                    id = "admin-tech-1",
                    name = "",
                    email = "hidden-internal@invalid",
                    emailVerified = true
                ),
                session = AdminSessionInfo(
                    userId = "admin-tech-1",
                    mustChangePassword = false,
                    isRoot = true
                )
            )
        )
        val vm = LoginViewModel(repo)
        vm.onEmailChange("qa.admin")
        vm.onPasswordChange("wrong")
        vm.login()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoggedIn)
        assertEquals(LoginViewModel.GENERIC_LOGIN_ERROR, vm.uiState.value.errorMessage)
    }

    @Test
    fun login_unknown_username_is_generic() = runTest(dispatcher) {
        val vm = LoginViewModel(repo)
        vm.onEmailChange("nobody")
        vm.onPasswordChange("whatever1")
        vm.login()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoggedIn)
        assertEquals(LoginViewModel.GENERIC_LOGIN_ERROR, vm.uiState.value.errorMessage)
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
            name = "U",
            email = "unverified@email.com",
            password = "password1",
            consent = com.comunidapp.app.domain.auth.ConsentMetadata.forRegistration(),
            username = "unverified_user",
            birthDate = "1990-01-15"
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
    fun email_verification_otp_six_digits_succeeds() = runTest(dispatcher) {
        val email = "otpvm@email.com"
        repo.register(
            "O",
            email,
            "password1",
            com.comunidapp.app.domain.auth.ConsentMetadata.forRegistration(),
            "otpvm_user"
        )
        val vm = EmailVerificationViewModel(repo)
        vm.confirmWithOtp(email, "123456")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isVerified)
        assertNull(vm.uiState.value.errorMessage)
        assertEquals(email, repo.getCurrentUser()?.email)
    }

    @Test
    fun email_verification_otp_eight_digits_succeeds() = runTest(dispatcher) {
        val email = "otp8vm@email.com"
        repo.register(
            "O",
            email,
            "password1",
            com.comunidapp.app.domain.auth.ConsentMetadata.forRegistration(),
            "otp8vm_user"
        )
        val vm = EmailVerificationViewModel(repo)
        vm.confirmWithOtp(email, "87654321")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isVerified)
        assertNull(vm.uiState.value.errorMessage)
        assertEquals(email, repo.getCurrentUser()?.email)
    }

    @Test
    fun email_verification_invalid_otp_keeps_pending_state() = runTest(dispatcher) {
        val email = "otpinvalid@email.com"
        repo.register(
            "O",
            email,
            "password1",
            com.comunidapp.app.domain.auth.ConsentMetadata.forRegistration(),
            "otpinvalid_user"
        )
        repo.verifyEmailOtpOverride = Result.failure(
            com.comunidapp.app.domain.auth.AuthErrorMapper.toException(
                com.comunidapp.app.domain.auth.AuthErrorCode.OTP_INVALID,
                "Invalid OTP token"
            )
        )
        val vm = EmailVerificationViewModel(repo)
        vm.confirmWithOtp(email, "12345678")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isVerified)
        assertEquals("El código no es válido. Revisalo e intentá de nuevo.", vm.uiState.value.errorMessage)
        assertFalse(repo.isEmailVerified(email))
        assertNull(repo.getCurrentUser())
    }

    @Test
    fun email_verification_resend_stays_on_verification_screen() = runTest(dispatcher) {
        val email = "otpresend@email.com"
        repo.register(
            "O",
            email,
            "password1",
            com.comunidapp.app.domain.auth.ConsentMetadata.forRegistration(),
            "otpresend_user"
        )
        val vm = EmailVerificationViewModel(repo)
        vm.resendVerification(email)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isVerified)
        assertEquals("Te enviamos un nuevo código.", vm.uiState.value.successMessage)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun email_verification_otp_five_digits_rejected_without_calling_success() = runTest(dispatcher) {
        val email = "otpshort@email.com"
        repo.register(
            "O",
            email,
            "password1",
            com.comunidapp.app.domain.auth.ConsentMetadata.forRegistration(),
            "otpshort_user"
        )
        val vm = EmailVerificationViewModel(repo)
        vm.confirmWithOtp(email, "12345")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isVerified)
        assertNotNull(vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.errorMessage!!.contains("12345"))
        vm.confirmWithOtp(email, "12345678901")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isVerified)
        assertFalse(repo.isEmailVerified(email))
    }

    @Test
    fun email_verification_clears_error_on_edit() = runTest(dispatcher) {
        val vm = EmailVerificationViewModel(repo)
        vm.confirmWithOtp("x@email.com", "12")
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.errorMessage)
        vm.clearOtpFeedback()
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun email_verification_resend_rate_limit_is_usable() = runTest(dispatcher) {
        repo.sendEmailVerificationOverride = Result.failure(
            com.comunidapp.app.domain.auth.AuthErrorMapper.toException(
                com.comunidapp.app.domain.auth.AuthErrorCode.RATE_LIMITED,
                "over_email_send_rate_limit"
            )
        )
        val vm = EmailVerificationViewModel(repo)
        vm.resendVerification("rate@email.com")
        advanceUntilIdle()
        assertEquals(
            "Esperá un momento antes de solicitar otro código.",
            vm.uiState.value.errorMessage
        )
        assertFalse(vm.uiState.value.isVerified)
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
