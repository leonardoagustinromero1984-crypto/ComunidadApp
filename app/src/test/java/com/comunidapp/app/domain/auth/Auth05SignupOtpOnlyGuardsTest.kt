package com.comunidapp.app.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Auth05SignupOtpOnlyGuardsTest {

    @Test
    fun signupConfirmationModeIsEmailOtpOnly() {
        assertEquals("EMAIL_OTP_ONLY", SignupSessionPolicy.SIGNUP_CONFIRMATION_MODE)
    }

    @Test
    fun verifyScreenHasOtpOnlyCopyAndNoConfirmationLinkUx() {
        val screen = File("src/main/java/com/comunidapp/app/ui/screens/login/ForgotPasswordScreen.kt")
            .readText()
        val verifyBlock = screen.substringAfter("fun EmailVerificationScreen")
        assertTrue(verifyBlock.contains("Verificá tu correo"))
        assertTrue(verifyBlock.contains("Te enviamos un código de verificación a"))
        assertTrue(verifyBlock.contains("Ingresalo para confirmar tu cuenta."))
        assertTrue(verifyBlock.contains("Código de verificación"))
        assertTrue(verifyBlock.contains("\"Verificar\""))
        assertTrue(verifyBlock.contains("Reenviar código"))
        assertTrue(verifyBlock.contains("Cambiar correo"))
        assertFalse(verifyBlock.contains("Abrí el enlace"))
        assertFalse(verifyBlock.contains("También podés abrir el enlace"))
        assertFalse(verifyBlock.contains("También podés tocar el enlace"))
        assertFalse(verifyBlock.contains("Ya confirmé desde el enlace"))
        assertFalse(verifyBlock.contains("Confirmar mediante enlace"))
        assertFalse(verifyBlock.contains("6 dígitos"))
        assertFalse(verifyBlock.contains("ConfirmationURL"))
    }

    @Test
    fun loginCallbackDeepLinkInfrastructureRemains() {
        val deepLink = File("src/main/java/com/comunidapp/app/domain/auth/AuthDeepLink.kt").readText()
        val main = File("src/main/java/com/comunidapp/app/MainActivity.kt").readText()
        val config = File("src/main/java/com/comunidapp/app/data/remote/supabase/SupabaseAuthConfig.kt")
            .readText()
        assertTrue(config.contains("login-callback"))
        assertTrue(main.contains("handleDeeplinks") || main.contains("handleAuthDeepLink"))
        assertTrue(deepLink.contains("PasswordRecovery") || deepLink.contains("recovery"))
    }

    @Test
    fun unverifiedAuthUserIsExpectedAndResendDoesNotSignupAgain() {
        val repo = File("src/main/java/com/comunidapp/app/data/repository/SupabaseAuthRepository.kt")
            .readText()
        val register = repo.substringAfter("override suspend fun register(").substringBefore("override suspend fun sendPasswordResetEmail")
        assertTrue(register.contains("mustReleaseUnconfirmedSession"))
        assertTrue(register.contains("signOut()"))
        assertFalse(register.contains("deleteUser"))
        assertFalse(register.contains("admin.delete"))
        val resend = repo.substringAfter("override suspend fun sendEmailVerification")
            .substringBefore("override suspend fun confirmEmailVerification")
        assertTrue(resend.contains("resendEmail"))
        assertFalse(resend.contains("signUpWith"))
    }

    @Test
    fun profileGreenIsLocalAndBrandGreensUnchanged() {
        val colors = File("src/main/java/com/comunidapp/app/ui/theme/Color.kt").readText()
        assertTrue(colors.contains("val ProfileGreen = Color(0xFF66B978)"))
        assertTrue(colors.contains("val BrandGreen = Color(0xFF49B749)"))
        assertTrue(colors.contains("val BrandGreenDark = Color(0xFF247A3D)"))
        assertTrue(colors.contains("val BrandBackground = Color(0xFFFAFBF8)"))
        assertTrue(colors.contains("val BrandCream = Color(0xFFFFF8E1)"))
        assertTrue(colors.contains("val BrandWhite = Color(0xFFFFFFFF)"))
        assertTrue(colors.contains("val BrandText = Color(0xFF263238)"))
        val profile = File("src/main/java/com/comunidapp/app/ui/screens/profile/ProfileScreen.kt").readText()
        assertTrue(profile.contains("VisualDirectionPilot"))
        assertFalse(profile.contains(".background(ProfileGreen)"))
        assertFalse(profile.contains("BrandGreenDark"))
    }
}
