package com.comunidapp.app.domain.pets

import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.ui.screens.admin.secondaryClassificationSectionTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeciesCatalogContractTest {

    @Test
    fun rpcCodePassesThroughAdminCreatedSpecies() {
        assertEquals("NUEVA_ESPECIE_QA", PetSpeciesCatalog.toRpcCode("Nueva_especie_QA"))
        assertEquals("FERRET", PetSpeciesCatalog.toRpcCode("FERRET"))
        assertEquals("DOG", PetSpeciesCatalog.toRpcCode("perro"))
    }

    @Test
    fun unknownCodeDoesNotCollapseToOther() {
        assertEquals("HEDGEHOG", PetSpeciesCatalog.toRpcCode("HEDGEHOG"))
        assertEquals(PetSpecies.OTHER, PetSpeciesCatalog.toPetSpecies("HEDGEHOG"))
    }

    @Test
    fun displayLabelPrefersCatalogName() {
        assertEquals(
            "Nueva especie QA",
            PetSpeciesCatalog.displayLabel("NUEVA_ESPECIE_QA", "Nueva especie QA", PetSpecies.OTHER)
        )
        assertEquals("Ave", PetSpeciesCatalog.displayLabel("BIRD", null, PetSpecies.BIRD))
        assertEquals("Hurón", PetSpeciesCatalog.displayLabel("FERRET", null, PetSpecies.OTHER))
    }

    @Test
    fun secondaryLabelsComeFromKindNotHardcodedSpecies() {
        assertEquals("Raza" to "Razas", SecondaryClassificationKind.defaultLabels("BREED"))
        assertEquals("Tipo" to "Tipos", SecondaryClassificationKind.defaultLabels("TYPE"))
        assertEquals("Variedad" to "Variedades", SecondaryClassificationKind.defaultLabels("VARIETY"))
        assertEquals("Raza", SecondaryClassificationKind.kindLabel("BREED"))
        assertEquals("Tipo", SecondaryClassificationKind.kindLabel("TYPE"))
        assertEquals("Variedad", SecondaryClassificationKind.kindLabel("VARIETY"))
    }

    @Test
    fun catalogDetailSectionUsesConfiguredSecondaryClassification() {
        assertEquals(
            "Clasificación secundaria",
            secondaryClassificationSectionTitle(false, SecondaryClassificationKind.NONE, "")
        )
        assertEquals(
            "Razas",
            secondaryClassificationSectionTitle(true, SecondaryClassificationKind.BREED, "")
        )
        assertEquals(
            "Linajes",
            secondaryClassificationSectionTitle(true, SecondaryClassificationKind.CUSTOM, "Linajes")
        )
    }

    @Test
    fun lifecycleLabelsAndNewPetVisibility() {
        assertEquals("En preparación", SpeciesLifecycle.label("PREPARATION"))
        assertEquals("Activa", SpeciesLifecycle.label("ACTIVE"))
        assertEquals("Inactiva", SpeciesLifecycle.label("INACTIVE"))
        assertTrue(SpeciesLifecycle.isSelectableForNewPet("ACTIVE"))
        assertFalse(SpeciesLifecycle.isSelectableForNewPet("PREPARATION"))
        assertFalse(SpeciesLifecycle.isSelectableForNewPet("INACTIVE"))
    }
}
