package com.comunidapp.app.domain.location

import com.comunidapp.app.data.local.InMemoryOnb02Store
import com.comunidapp.app.data.local.Onb02Completion
import com.comunidapp.app.data.local.Onb02StoreProvider
import com.comunidapp.app.data.model.LostFoundStatus
import com.comunidapp.app.domain.onboarding.onb02.Onb02EntryPolicy
import com.comunidapp.app.domain.onboarding.onb02.Onb02FlowKind
import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LeoVerLocationAlerts01ContractTest {

    @Test
    fun NEW_USER_WITHOUT_REMOTE_FLOW_STARTS_TUTORIAL() {
        val store = InMemoryOnb02Store()
        Onb02StoreProvider.override = store
        try {
            assertEquals(
                Onb02FlowKind.FULL_ONBOARDING,
                Onb02StoreProvider.decideEntry(
                    userId = "new-google",
                    justCompletedProfileSetup = false,
                    personOnboardingComplete = true,
                    remoteTutorialFlowCompleted = false
                )
            )
            assertEquals(Onb02Completion.FULL_PENDING, store.completion("new-google"))
        } finally {
            Onb02StoreProvider.override = null
        }
        assertFalse(
            Onb02EntryPolicy.skipSelectorForExistingComplete(
                completion = Onb02Completion.NOT_STARTED,
                remoteTutorialFlowCompleted = false,
                justCompletedProfileSetup = false
            )
        )
    }

    @Test
    fun EXISTING_REMOTE_FLOW_SKIPS_TUTORIAL() {
        val store = InMemoryOnb02Store()
        Onb02StoreProvider.override = store
        try {
            assertEquals(
                null,
                Onb02StoreProvider.decideEntry(
                    userId = "reinstall",
                    justCompletedProfileSetup = false,
                    personOnboardingComplete = true,
                    remoteTutorialFlowCompleted = true
                )
            )
            assertEquals(Onb02Completion.COMPLETED, store.completion("reinstall"))
        } finally {
            Onb02StoreProvider.override = null
        }
    }

    @Test
    fun SESSION_DOES_NOT_AUTOCOMPLETE_TUTORIAL_FROM_PROFILE() {
        val session = source("app/src/main/java/com/comunidapp/app/viewmodel/SessionViewModel.kt")
        assertFalse(session.contains("store.markCompleted(user.id)"))
        val hydrate = source("app/src/main/java/com/comunidapp/app/data/repository/CanonicalTutorialProgressRepository.kt")
        assertTrue(hydrate.contains("Onb02RemoteKeys.FLOW"))
        assertTrue(hydrate.contains("flowCompleted"))
    }

    @Test
    fun NO_BACKGROUND_LOCATION() {
        val manifest = source("app/src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("ACCESS_FINE_LOCATION"))
        assertTrue(manifest.contains("ACCESS_COARSE_LOCATION"))
        assertFalse(manifest.contains("ACCESS_BACKGROUND_LOCATION"))
        assertFalse(LocationConsentContracts.BACKGROUND_LOCATION)
    }

    @Test
    fun LOCATION_COPY_AND_CONSENT_VERSION() {
        assertEquals("LOCATION_TREATMENT", LocationConsentContracts.CONSENT_CODE)
        assertEquals("1", LocationConsentContracts.CONSENT_VERSION)
        assertTrue(LocationConsentContracts.TITLE.contains("LeoVer"))
        assertTrue(LocationConsentContracts.NO_BACKGROUND.contains("segundo plano"))
        val onboarding = source("app/src/main/java/com/comunidapp/app/ui/screens/location/LocationPermissionOnboarding.kt")
        assertTrue(onboarding.contains("LocationConsentContracts.CTA_ALLOW"))
        assertFalse(onboarding.contains("ACCESS_BACKGROUND_LOCATION"))
    }

    @Test
    fun FOUND_PUBLISH_CREATES_PROVISIONAL_PET() {
        val repo = source("app/src/main/java/com/comunidapp/app/data/repository/CanonicalConsumerRepositories.kt")
        assertTrue(repo.contains("if (kind == \"FOUND\") null else post.petId"))
        assertTrue(repo.contains("p_name"))
        assertTrue(repo.contains("put(\"p_sex\""))
        assertTrue(repo.contains("RPC_ATTACH_LOST_FOUND_PHOTO"))
        val publish = source("app/src/main/java/com/comunidapp/app/viewmodel/PublishViewModel.kt")
        assertTrue(publish.contains("if (type == LostFoundType.FOUND) null else petId"))
        assertTrue(publish.contains("estimatedAgeMonths"))
        val form = source("app/src/main/java/com/comunidapp/app/ui/screens/publish/PublishForms.kt")
        assertFalse(form.contains("Al publicar no se crea una mascota ni una VitaCora"))
        assertTrue(form.contains("ficha provisional"))
        val sql = migration1087()
        assertTrue(sql.contains("_canon_create_found_identity"))
        assertTrue(sql.contains("FOUND_CASE"))
        assertTrue(sql.contains("interval '15 minutes'"))
        assertTrue(sql.contains("ALERT_CLAIM_NOT_NEAREST"))
        assertTrue(sql.contains("'new_pet', false"))
        assertTrue(sql.contains("canon_assert_found_might_be_mine"))
        assertTrue(sql.contains("canon_confirm_found_owner_match"))
        assertTrue(sql.contains("AUTHORIZED"))
        assertFalse(sql.contains("values (v_pet, 'PERSON', auth.uid(), 'OWNER'"))
        val docs = source("docs/architecture/LOCATION-AND-ALERTS.md")
        assertFalse(docs.contains("Publish creates **only** the alert"))
        assertFalse(docs.contains("No pet. No VitaCora."))
        assertTrue(docs.contains("Claim does **not** create a pet"))
    }

    @Test
    fun NEAREST_TEN_IS_BACKEND_POSTGIS() {
        val sql = migration1086()
        assertTrue(sql.contains("ST_Distance"))
        assertTrue(sql.contains("limit 10"))
        assertTrue(sql.contains("lost_found_alert_recipients"))
        assertTrue(sql.contains("persons_base_location_gix"))
        assertTrue(sql.contains("RESCUER"))
        assertTrue(sql.contains("'SHELTER', 'NGO'"))
        assertTrue(sql.contains("verification_status = 'VERIFIED'"))
        assertFalse(sql.contains("ACCESS_BACKGROUND_LOCATION"))
    }

    @Test
    fun EXACT_COORDS_NOT_IN_PUBLIC_LIST() {
        val sql = migration1086()
        assertTrue(sql.contains("canon_get_lost_found_exact"))
        assertTrue(sql.contains("_canon_alert_can_see_exact"))
        val list = sql.substringAfter("create or replace function public.canon_list_lost_found")
            .substringBefore("create or replace function public.canon_get_lost_found_exact")
        assertFalse(list.contains("precise_location"))
        assertFalse(list.contains("'lat'"))
        val pin = source("app/src/main/java/com/comunidapp/app/ui/screens/location/LocationPinPicker.kt")
        assertFalse(pin.contains("latitude:"))
        assertFalse(pin.contains("longitude:"))
        val publish = source("app/src/main/java/com/comunidapp/app/ui/screens/publish/PublishForms.kt")
        assertFalse(publish.contains("Latitude"))
        assertTrue(publish.contains("LocationPinPicker"))
    }

    @Test
    fun CLAIM_COPY_AND_STATUS() {
        assertEquals(LostFoundStatus.CLAIMED, LostFoundStatus.fromString("CLAIMED"))
        assertEquals(LostFoundStatus.ACTIVE, LostFoundStatus.fromString("OPEN"))
        val detail = source("app/src/main/java/com/comunidapp/app/ui/screens/lostfound/AlertMapScreen.kt")
        assertTrue(detail.contains("Tomar caso"))
        assertTrue(detail.contains("El caso fue asignado a un colaborador más cercano."))
        assertTrue(detail.contains("Completar datos del animal"))
    }

    @Test
    fun MAPS_API_KEY_NOT_HARDCODED() {
        val map = source("app/src/main/java/com/comunidapp/app/ui/map/LeoVerMap.kt")
        assertTrue(map.contains("BuildConfig.MAPS_API_KEY"))
        assertFalse(map.contains("AIza"))
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()

    private fun migration1086(): String {
        val root = UiRegressionGateTest.repoRoot()
        val file = File(root, "infra/supabase-canonical/supabase/migrations/20260920200000_1086_location_alerts_claim.sql")
        assertTrue(file.exists())
        return file.readText()
    }

    private fun migration1087(): String {
        val root = UiRegressionGateTest.repoRoot()
        val file = File(root, "infra/supabase-canonical/supabase/migrations/20260920210000_1087_found_creates_pet_waves_match.sql")
        assertTrue(file.exists())
        return file.readText()
    }
}
