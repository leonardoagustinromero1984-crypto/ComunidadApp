package com.comunidapp.app.domain.location

import com.comunidapp.app.domain.onboarding.onb02.TutorialCatalog
import com.comunidapp.app.domain.onboarding.onb02.TutorialId
import com.comunidapp.app.domain.publish.LostFoundPublishError
import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LeoVerCommunityCare02PhysicalQa02ContractTest {

    @Test
    fun GEO_HELPER_QUALIFIES_POSTGIS() {
        val sql = migration("20260921200000_1095_geo_point_search_path.sql")
        assertTrue(sql.contains("_canon_geo_point"))
        assertTrue(sql.contains("extensions.ST_MakePoint"))
        assertTrue(sql.contains("LF-CREATE-LOCATION"))
        assertTrue(sql.contains("set search_path = public, extensions"))
        assertTrue(sql.contains("p_address"))
        assertTrue(sql.contains("has_base_location"))
    }

    @Test
    fun ST_MAKEPOINT_MAPS_TO_LOCATION_NOT_UNKNOWN() {
        val error = IllegalStateException("function st_makepoint(double precision, double precision) does not exist")
        assertEquals(LostFoundPublishError.LOCATION, LostFoundPublishError.codeOf(error))
        assertEquals(
            LostFoundPublishError.LOCATION,
            LostFoundPublishError.codeOf(IllegalStateException("LF-CREATE-LOCATION"))
        )
        assertEquals(
            LostFoundPublishError.DB,
            LostFoundPublishError.codeOf(IllegalStateException("SQLSTATE 23503 insert into lost_found"))
        )
        assertEquals(LostFoundPublishError.UNKNOWN, LostFoundPublishError.codeOf(IllegalStateException("boom")))
    }

    @Test
    fun SHARED_CHIPS_HAVE_SINGLE_LABEL() {
        val chips = source("app/src/main/java/com/comunidapp/app/ui/components/leo/LeoEnumChipRow.kt")
        assertTrue(chips.contains("LeoRequiredField.label(label, required)"))
        assertTrue(chips.contains("One visible label"))
        val lost = source("app/src/main/java/com/comunidapp/app/ui/screens/publish/PublishForms.kt")
        assertFalse(lost.contains("Text(com.comunidapp.app.ui.components.leo.LeoRequiredField.label(\"Especie\")"))
        val pet = source("app/src/main/java/com/comunidapp/app/ui/screens/pets/PetFormScreen.kt")
        assertTrue(pet.contains("LeoEnumChipRow"))
    }

    @Test
    fun ADDRESS_AND_USE_MY_LOCATION_SHARE_BASE() {
        val picker = source("app/src/main/java/com/comunidapp/app/ui/screens/location/LocationPinPicker.kt")
        assertTrue(picker.contains("Buscar domicilio"))
        assertTrue(picker.contains("Usar mi ubicación"))
        assertTrue(picker.contains("AddressGeocoder"))
        assertTrue(picker.contains("Dirección seleccionada"))
        val upsert = source("app/src/main/java/com/comunidapp/app/data/repository/CanonicalConsumerRepositories.kt")
        assertTrue(upsert.contains("p_address"))
        assertTrue(upsert.contains("getMyResponderBase"))
    }

    @Test
    fun SHELTER_TUTORIAL_EXPLAINS_VERIFICATION() {
        val t10b = TutorialCatalog.all.first { it.id == TutorialId.T10B_SHELTER }
        assertEquals(3, t10b.steps.size)
        assertTrue(t10b.steps.last().title.contains("Verificá tu refugio"))
        assertTrue(t10b.steps.last().body.contains("revisa"))
        assertTrue(t10b.steps.last().body.contains("no implica aprobación automática"))
        assertEquals("Completar perfil y solicitar verificación", t10b.steps.last().primaryCta)
        assertFalse(t10b.steps.any { it.body.contains("automática y inmediata") })
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()

    private fun migration(name: String): String {
        val file = File(UiRegressionGateTest.repoRoot(), "infra/supabase-canonical/supabase/migrations/$name")
        assertTrue(file.exists())
        return file.readText()
    }
}
