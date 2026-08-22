package com.comunidapp.app.domain.qa

import com.comunidapp.app.domain.auth.SignupSessionPolicy
import com.comunidapp.app.domain.user.PersonSearchQuery
import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeoVerPhysicalQaFix06ContractTest {

    @Test
    fun friendSearchNormalizesAtPrefix() {
        assertEquals("Luna", PersonSearchQuery.normalize("  @Luna "))
        assertEquals("luna", PersonSearchQuery.normalize("luna"))
        assertEquals("", PersonSearchQuery.normalize(" @ "))
    }

    @Test
    fun goTrueEmptyIdentitiesMeansExistingEmail() {
        assertTrue(SignupSessionPolicy.existingEmailHiddenByGoTrue(0, sessionPresent = false))
        assertFalse(SignupSessionPolicy.existingEmailHiddenByGoTrue(1, sessionPresent = false))
        assertFalse(SignupSessionPolicy.existingEmailHiddenByGoTrue(0, sessionPresent = true))
    }

    @Test
    fun friendSearchCallsCanonSearchPersons() {
        val source = source("app/src/main/java/com/comunidapp/app/data/remote/supabase/UserSupabaseDataSource.kt")
        assertTrue(source.contains("RPC_SEARCH_PERSONS"))
        assertTrue(source.contains("SearchPersonRpcRow"))
    }

    @Test
    fun googleSwitchDoesNotReuseJwtUsername() {
        val onboarding = source("app/src/main/java/com/comunidapp/app/viewmodel/ProfileOnboardingViewModel.kt")
        assertTrue(onboarding.contains("profile?.username"))
        assertFalse(onboarding.contains("authUser.username"))
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/onboarding/ProfileOnboardingScreen.kt")
        assertTrue(screen.contains("viewModel(key = sessionUserId)"))
    }

    @Test
    fun settingsPrivacyIsProfileNotLegalDraft() {
        val settings = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/SettingsScreen.kt")
        assertTrue(settings.contains("Quién puede ver tu perfil"))
        assertFalse(settings.contains("Cómo se ve tu perfil"))
        val graph = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(graph.contains("NavRoutes.PROFILE_PRIVACY"))
        val legal = source("app/src/main/java/com/comunidapp/app/ui/screens/legal/LegalDraftScreens.kt")
        assertFalse(legal.contains("BORRADOR"))
        val privacy = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/ProfilePrivacyScreen.kt")
        assertTrue(privacy.contains("Quién puede ver tu perfil"))
        assertFalse(privacy.contains("BORRADOR"))
    }

    @Test
    fun petAvatarGetFailureDoesNotAbortCreatedPet() {
        val ds = source("app/src/main/java/com/comunidapp/app/data/remote/supabase/m08/SupabasePetM08RemoteDataSource.kt")
        assertFalse(ds.contains("PET_AVATAR_EMPTY"))
        val form = source("app/src/main/java/com/comunidapp/app/viewmodel/PetFormViewModel.kt")
        assertTrue(form.contains("isEditMode = true"))
        val diag = source("app/src/main/java/com/comunidapp/app/domain/pets/PetCreateDiagnostic.kt")
        assertFalse(diag.contains("\"PHOTO\" in signal"))
    }

    @Test
    fun editProfileRestoresHomeLocalityId() {
        val vm = source("app/src/main/java/com/comunidapp/app/viewmodel/EditProfileViewModel.kt")
        assertTrue(vm.contains("homeLocalityId = profile.homeLocalityId"))
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/EditProfileScreen.kt")
        assertTrue(screen.contains("restoreSelection"))
        assertTrue(screen.contains("uiState.homeLocalityId"))
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()
}
