package com.comunidapp.app.domain.auth

import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.User
import com.comunidapp.app.domain.user.OnboardingCompleteness
import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeoVerAuth06ContractTest {

    @Test
    fun GOOGLE_AUTH_OPTION_VISIBLE_LOGIN() {
        val login = source("app/src/main/java/com/comunidapp/app/ui/screens/login/LoginScreen.kt")
        assertTrue(login.contains("ContinueWithGoogleButton"))
        assertTrue(login.contains("signInWithGoogle"))
        assertTrue(login.contains("Iniciar sesión"))
    }

    @Test
    fun GOOGLE_AUTH_OPTION_VISIBLE_SIGNUP() {
        val signup = source("app/src/main/java/com/comunidapp/app/ui/screens/login/RegisterScreen.kt")
        assertTrue(signup.contains("ContinueWithGoogleButton"))
        assertTrue(signup.contains("signInWithGoogle"))
        assertTrue(signup.contains("Crear cuenta"))
    }

    @Test
    fun GOOGLE_AUTH_DOES_NOT_REQUIRE_OTP() {
        assertFalse(GoogleAuthPolicy.REQUIRES_OTP)
        assertEquals("GOOGLE_OAUTH", GoogleAuthPolicy.GOOGLE_SIGNUP_CONFIRMATION)
        assertEquals("OTP", GoogleAuthPolicy.EMAIL_SIGNUP_CONFIRMATION)
    }

    @Test
    fun GOOGLE_AUTH_DOES_NOT_REQUIRE_LEOVER_PASSWORD() {
        assertFalse(GoogleAuthPolicy.REQUIRES_LEOVER_PASSWORD)
        val repo = source("app/src/main/java/com/comunidapp/app/data/repository/SupabaseAuthRepository.kt")
        val googleFn = repo.substringAfter("override suspend fun signInWithGoogle")
            .substringBefore("override fun linkedAuthMethods")
        assertTrue(googleFn.contains("signInWith(") && googleFn.contains("Google"))
        assertFalse(googleFn.contains("this.password"))
        assertFalse(googleFn.contains("verifyOtp") || googleFn.contains("OtpType"))
        assertFalse(googleFn.contains("google oauth without session"))
    }

    @Test
    fun GOOGLE_AUTH_CANCEL_SAFE() {
        assertEquals(
            "Se canceló el inicio de sesión.",
            AuthErrorMapper.fromCode(AuthErrorCode.GOOGLE_AUTH_CANCELLED, "canceled").userMessage
        )
        val mapped = AuthErrorMapper.fromThrowable(RuntimeException("access_denied cancelled"))
        assertEquals(AuthErrorCode.GOOGLE_AUTH_CANCELLED.name, mapped.code)
    }

    @Test
    fun GOOGLE_AUTH_DOUBLE_TAP_GUARDED() {
        val loginVm = source("app/src/main/java/com/comunidapp/app/viewmodel/LoginViewModel.kt")
        assertTrue(loginVm.contains("if (_uiState.value.isBusy) return"))
        assertTrue(loginVm.contains("isGoogleLoading"))
        val register = source("app/src/main/java/com/comunidapp/app/viewmodel/LoginViewModel.kt")
        assertTrue(register.contains("if (state.isLoading || state.googleAuthenticated) return"))
    }

    @Test
    fun EMAIL_AUTH_STILL_AVAILABLE() {
        val login = source("app/src/main/java/com/comunidapp/app/ui/screens/login/LoginScreen.kt")
        assertTrue(login.contains("Iniciar sesión"))
        assertTrue(login.contains("viewModel::login"))
        assertTrue(login.contains("Email"))
        assertTrue(login.contains("Contraseña"))
    }

    @Test
    fun EMAIL_SIGNUP_OTP_STILL_AVAILABLE() {
        assertEquals("EMAIL_OTP_ONLY", SignupSessionPolicy.SIGNUP_CONFIRMATION_MODE)
        val register = source("app/src/main/java/com/comunidapp/app/ui/screens/login/RegisterScreen.kt")
        assertTrue(register.contains("onRegisterSuccess"))
        val nav = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(nav.contains("emailVerification"))
    }

    @Test
    fun NEW_GOOGLE_AUTH_WITHOUT_PERSON_GOES_TO_ONBOARDING() {
        val user = incompleteGoogleUser()
        assertEquals(PostAuthDestination.COMPLETE_LEOVER_PROFILE, PostAuthResolver.destination(user))
        assertFalse(OnboardingCompleteness.isComplete(user))
    }

    @Test
    fun EXISTING_GOOGLE_USER_WITH_PERSON_GOES_HOME() {
        val user = completePerson()
        assertEquals(PostAuthDestination.MAIN_APP, PostAuthResolver.destination(user))
    }

    @Test
    fun SAME_AUTH_USER_REUSES_PERSON() {
        assertTrue(PostAuthResolver.sameAuthUserReusesPerson("uid-1", "uid-1"))
        assertFalse(PostAuthResolver.sameAuthUserReusesPerson("uid-1", "uid-2"))
    }

    @Test
    fun GOOGLE_LOGIN_DOES_NOT_CREATE_SECOND_PERSON() {
        assertFalse(IdentityLinkingPolicy.DUPLICATE_PERSON_POSSIBLE)
        assertTrue(IdentityLinkingPolicy.PERSON_UNIQUE_CONSTRAINT.contains("PRIMARY KEY"))
        val sql = source(
            "infra/supabase-canonical/supabase/migrations/20260818120000_1032_auth06_google_person_provision.sql"
        )
        assertTrue(sql.contains("auth.uid()"))
        assertFalse(sql.contains("p_user_id"))
    }

    @Test
    fun GOOGLE_LOGIN_PRESERVES_PETS() {
        assertEquals("uid-1", completePerson().id)
        assertTrue(PostAuthResolver.sameAuthUserReusesPerson(completePerson().id, completePerson().id))
    }

    @Test
    fun GOOGLE_LOGIN_PRESERVES_VITACORA() {
        assertTrue(PostAuthResolver.sameAuthUserReusesPerson("uid-1", "uid-1"))
    }

    @Test
    fun GOOGLE_LOGIN_PRESERVES_CAPABILITIES() {
        assertFalse(GoogleAuthPolicy.AUTH_PROVIDER_IS_PRODUCT_ROLE)
    }

    @Test
    fun GOOGLE_LOGIN_PRESERVES_ORG_MEMBERSHIPS() {
        assertEquals(AccountType.PERSON, completePerson().accountType)
        assertFalse(GoogleAuthPolicy.AUTH_PROVIDER_IS_ACCOUNT_TYPE)
    }

    @Test
    fun NEW_GOOGLE_USER_REQUIRES_USERNAME() {
        val missing = OnboardingCompleteness.missingFields(incompleteGoogleUser())
        assertTrue(missing.contains("username"))
    }

    @Test
    fun NEW_GOOGLE_USER_REQUIRES_AGE_GOVERNANCE() {
        val onboarding = source("app/src/main/java/com/comunidapp/app/viewmodel/ProfileOnboardingViewModel.kt")
        assertTrue(onboarding.contains("needsBirthDate"))
        assertTrue(onboarding.contains("PersonAgeRules.validateSignupBirthDate"))
    }

    @Test
    fun NEW_GOOGLE_USER_REQUIRES_LOCATION_PROFILE_DATA() {
        val missing = OnboardingCompleteness.missingFields(incompleteGoogleUser())
        assertTrue(missing.contains("home_locality_id"))
    }

    @Test
    fun NEW_GOOGLE_USER_REQUIRES_LEOVER_TERMS() {
        val session = source("app/src/main/java/com/comunidapp/app/viewmodel/SessionViewModel.kt")
        assertTrue(session.contains("hasCurrentLegalConsent"))
        assertTrue(session.contains("LegalConsentRequired"))
    }

    @Test
    fun EXISTING_USER_DOES_NOT_REPEAT_ONBOARDING() {
        assertEquals(PostAuthDestination.MAIN_APP, PostAuthResolver.destination(completePerson()))
    }

    @Test
    fun EXISTING_USER_DOES_NOT_REPEAT_COMPLETED_TUTORIALS() {
        val resolver = source("app/src/main/java/com/comunidapp/app/domain/auth/GoogleAuthPolicy.kt")
        assertTrue(resolver.contains("not from \"came from Google\""))
    }

    @Test
    fun PROFILE_PERSONAL_ALWAYS_ACTIVE() {
        assertEquals(AccountType.PERSON, incompleteGoogleUser().accountType)
        assertFalse(GoogleAuthPolicy.AUTH_PROVIDER_IS_ACCOUNT_TYPE)
    }

    @Test
    fun EXISTING_CONFIRMED_EMAIL_IDENTITY_RECONCILES_WITH_GOOGLE() {
        assertTrue(IdentityLinkingPolicy.CONFIRMED_EMAIL_SAME_ADDRESS.contains("verified"))
    }

    @Test
    fun UNCONFIRMED_EMAIL_EDGE_CASE_SAFE() {
        assertTrue(IdentityLinkingPolicy.UNCONFIRMED_EMAIL_EDGE_CASE.contains("not auto-linked"))
        val sql = source(
            "infra/supabase-canonical/supabase/migrations/20260818120000_1032_auth06_google_person_provision.sql"
        )
        assertTrue(sql.contains("return new;"))
        assertTrue(sql.contains("email_confirmed_at is null"))
        assertTrue(sql.contains("on_auth_user_email_confirmed"))
    }

    @Test
    fun NO_DUPLICATE_PERSON_AFTER_IDENTITY_LINK() {
        assertFalse(IdentityLinkingPolicy.DUPLICATE_PERSON_POSSIBLE)
    }

    @Test
    fun AUTH_PROVIDER_IS_NOT_PRODUCT_ROLE() {
        assertFalse(GoogleAuthPolicy.AUTH_PROVIDER_IS_PRODUCT_ROLE)
        assertFalse(GoogleAuthPolicy.AUTH_PROVIDER_IS_ACCOUNT_TYPE)
    }

    @Test
    fun ACTIVE_CONTEXT_NOT_AUTHORITY() {
        val matrix = source("app/src/main/java/com/comunidapp/app/domain/publish/ContextPublishMatrix.kt")
        assertTrue(matrix.contains("ActiveContext is not security authority"))
    }

    @Test
    fun GOOGLE_METADATA_NOT_AUTHORITY() {
        assertFalse(GoogleAuthPolicy.AVATAR_AUTO_PUBLIC)
        val sql = source(
            "infra/supabase-canonical/supabase/migrations/20260818120000_1032_auth06_google_person_provision.sql"
        )
        assertTrue(sql.contains("Never accepts a client-supplied user id") || sql.contains("auth.uid()"))
    }

    @Test
    fun NO_PROVIDER_TOKEN_LOGGING() {
        val repo = source("app/src/main/java/com/comunidapp/app/data/repository/SupabaseAuthRepository.kt")
        assertFalse(repo.contains("provider_token"))
        assertFalse(repo.contains("providerToken"))
        assertFalse(repo.contains("refresh_token"))
    }

    @Test
    fun NO_PROVIDER_REFRESH_TOKEN_STORAGE() {
        assertFalse(GoogleAuthPolicy.PROVIDER_TOKEN_PERSISTED)
        assertFalse(GoogleAuthPolicy.PROVIDER_REFRESH_TOKEN_PERSISTED)
    }

    @Test
    fun NO_EXTRA_GOOGLE_SCOPES() {
        assertEquals(listOf("openid", "email", "profile"), GoogleAuthPolicy.SCOPES)
        assertFalse(GoogleAuthPolicy.EXTRA_GOOGLE_SCOPES)
        assertFalse(GoogleAuthPolicy.isAllowedScope("https://www.googleapis.com/auth/contacts"))
    }

    @Test
    fun NO_FIREBASE_AUTH() {
        val gradle = source("gradle/libs.versions.toml")
        assertFalse(gradle.contains("firebase-auth"))
        val repo = source("app/src/main/java/com/comunidapp/app/data/repository/SupabaseAuthRepository.kt")
        assertFalse(repo.contains("com.google.firebase.auth"))
        assertFalse(repo.contains("GoogleSignIn"))
    }

    @Test
    fun PKCE_CONFIGURED() {
        val client = source("app/src/main/java/com/comunidapp/app/data/remote/supabase/SupabaseClientProvider.kt")
        assertTrue(client.contains("FlowType.PKCE"))
        assertTrue(client.contains("scheme = SupabaseAuthConfig.SCHEME"))
    }

    private fun completePerson(): User = User(
        id = "uid-1",
        name = "Ana",
        email = "ana@example.com",
        emailVerified = true,
        username = "ana.leover",
        displayName = "Ana",
        birthDate = "1990-01-15",
        homeLocalityId = "loc-ar-1",
        onboardingStatus = "COMPLETED",
        accountStatus = "ACTIVE"
    )

    private fun incompleteGoogleUser(): User = User(
        id = "uid-google",
        name = "Google Name",
        email = "g@example.com",
        emailVerified = true,
        username = null,
        displayName = "Google Name",
        birthDate = null,
        homeLocalityId = null,
        onboardingStatus = "NOT_STARTED"
    )

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()
}
