package com.comunidapp.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileAvatarViewerContractTest {

    private val profile by lazy {
        UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/screens/profile/ProfileScreen.kt"
        ).readText()
    }

    @Test
    fun ownProfileAvatarOpensDismissibleProportionalViewer() {
        assertTrue(profile.contains(".clickable { showAvatarViewer = true }"))
        assertTrue(profile.contains("ProfileAvatarViewer("))
        assertTrue(profile.contains("onDismissRequest = onDismiss"))
        assertTrue(profile.contains("contentDescription = \"Cerrar foto de perfil\""))
        assertTrue(profile.contains("contentScale = ContentScale.Fit"))
    }

    @Test
    fun viewerReusesResolvedAvatarAndHandlesMissingPhoto() {
        assertTrue(profile.contains("val resolvedAvatarUrl = avatarUrl ?: user.profileImageUrl"))
        assertTrue(profile.contains("imageUrl = resolvedAvatarUrl"))
        assertTrue(profile.contains("if (imageUrl.isNullOrBlank())"))
        assertTrue(profile.contains("text = \"Sin foto de perfil\""))
        val avatarTap = profile
            .substringAfter(".size(84.dp)")
            .substringBefore("PetImage(")
        assertFalse(avatarTap.contains("onEditProfile"))
    }

    @Test
    fun narrowProfileSectionsUseConsistentHorizontalInsets() {
        val petsHeader = profile
            .substringAfter("item(key = \"pets_header\")")
            .substringBefore("item(key = \"pets_row\")")
        assertTrue(petsHeader.contains("Modifier.padding(horizontal = LeoDimens.SpaceMd)"))
        assertTrue(profile.contains("modifier = Modifier.padding(horizontal = LeoDimens.SpaceMd)"))
    }
}
