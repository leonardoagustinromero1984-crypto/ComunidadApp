package com.comunidapp.app.domain.onboarding.onb02

import com.comunidapp.app.domain.context.OperationalContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndependentVeterinaryActivationTest {

    @Test
    fun localExtrasDoNotOccupyCatalog() {
        assertFalse(IndependentVeterinaryActivation.occupyCatalog(persistedActive = false))
    }

    @Test
    fun persistedProviderOccupiesCatalog() {
        val contexts = listOf(
            OperationalContext.Provider(
                entityId = "11111111-1111-1111-1111-111111111111",
                displayName = IndependentVeterinaryActivation.DISPLAY_NAME,
                category = IndependentVeterinaryActivation.STORAGE_CATEGORY
            )
        )
        assertTrue(IndependentVeterinaryActivation.isPersistedActive(contexts))
        assertTrue(IndependentVeterinaryActivation.occupyCatalog(true))
    }

    @Test
    fun placeholderOnb02ContextIsNotPersisted() {
        val contexts = listOf(
            OperationalContext.Provider(
                entityId = "onb02:VETERINARY:user-1",
                displayName = IndependentVeterinaryActivation.DISPLAY_NAME,
                category = IndependentVeterinaryActivation.STORAGE_CATEGORY
            )
        )
        assertFalse(IndependentVeterinaryActivation.isPersistedActive(contexts))
    }
}
