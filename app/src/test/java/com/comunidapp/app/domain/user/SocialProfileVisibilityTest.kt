package com.comunidapp.app.domain.user

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SocialProfileVisibilityTest {

    @Test
    fun activeValuesAreOnlyPublicAndPrivate() {
        assertEquals(
            listOf(ProfileVisibility.PUBLIC, ProfileVisibility.PRIVATE),
            SocialProfileVisibility.selectable
        )
        assertEquals(2, ProfileVisibility.entries.size)
    }

    @Test
    fun legacyFriendsMapsToPrivate() {
        assertEquals(ProfileVisibility.PRIVATE, SocialProfileVisibility.fromRaw("FRIENDS"))
        assertEquals(ProfileVisibility.PRIVATE, SocialProfileVisibility.fromRaw("FRIENDS_ONLY"))
        assertEquals(ProfileVisibility.PUBLIC, SocialProfileVisibility.fromRaw("PUBLIC_LIMITED"))
        assertEquals(ProfileVisibility.PUBLIC, UserProfileMapper.parseVisibility("PUBLIC"))
    }

    @Test
    fun privateRequiresFollowApprovalAndDoesNotInventAmigo() {
        assertTrue(SocialProfileVisibility.requiresFollowApproval(ProfileVisibility.PRIVATE))
        assertFalse(SocialProfileVisibility.requiresFollowApproval(ProfileVisibility.PUBLIC))
        assertEquals("PUBLIC_LIMITED", SocialProfileVisibility.toCanonicalPrivacyState(ProfileVisibility.PUBLIC))
        assertEquals("PRIVATE", SocialProfileVisibility.toCanonicalPrivacyState(ProfileVisibility.PRIVATE))
    }

    @Test
    fun onboardingScreenDoesNotOfferSoloAmigos() {
        val screen = File("src/main/java/com/comunidapp/app/ui/screens/onboarding/ProfileOnboardingScreen.kt")
            .readText()
        assertFalse(screen.contains("Solo amigos"))
        assertFalse(screen.contains("ProfileVisibility.FRIENDS"))
        assertTrue(screen.contains("Visibilidad del perfil social"))
        assertTrue(screen.contains("Cualquier persona puede ver tu perfil social y el contenido que publiques como público."))
        assertTrue(screen.contains("Solo los seguidores que apruebes pueden ver tu perfil social privado."))
        assertTrue(
            screen.contains(
                "Esta configuración no modifica la privacidad de VitaCora ni la información de tus mascotas."
            )
        )
    }
}
