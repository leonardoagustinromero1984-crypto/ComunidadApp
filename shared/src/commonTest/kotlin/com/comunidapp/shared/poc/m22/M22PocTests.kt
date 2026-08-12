package com.comunidapp.shared.poc.m22.domain

import com.comunidapp.shared.poc.m22.data.FakeM22PocCatalogRepository
import com.comunidapp.shared.poc.m22.model.M22PocCategory
import com.comunidapp.shared.poc.m22.model.M22PocOffering
import com.comunidapp.shared.poc.m22.model.M22PocPriceType
import com.comunidapp.shared.poc.m22.viewmodel.M22PocCatalogUiState
import com.comunidapp.shared.poc.m22.viewmodel.M22PocCatalogViewModel
import com.comunidapp.shared.poc.m22.viewmodel.M22PocDetailUiState
import com.comunidapp.shared.poc.m22.viewmodel.M22PocDetailViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class M22PocDomainTest {
    @Test
    fun scrubPublicText_redactsEmailAndPhone() {
        val scrubbed = M22PocPrivacy.scrubPublicText(
            "Escribí a demo@patitas.test o +54 11 5555-5555"
        )
        assertTrue(!scrubbed.contains("@"))
        assertTrue(scrubbed.contains("[redactado]"))
    }

    @Test
    fun pricing_summary_fromFixed() {
        val summary = M22PocPricing.summary(
            listOf(M22PocOffering("Baño", "x", M22PocPriceType.FIXED, 18000))
        )
        assertEquals("ARS 18000", summary)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class M22PocViewModelTest {
    @Test
    fun catalog_success_content() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val vm = M22PocCatalogViewModel(FakeM22PocCatalogRepository(), scope)
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state is M22PocCatalogUiState.Content)
        assertEquals(2, (state as M22PocCatalogUiState.Content).items.size)
        assertEquals(M22PocCategory.GROOMING, state.items.first().category)
        assertTrue(state.items.first().description.contains("[redactado]"))
        vm.clear()
    }

    @Test
    fun catalog_empty() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val vm = M22PocCatalogViewModel(FakeM22PocCatalogRepository(seed = emptyList()), scope)
        advanceUntilIdle()
        assertEquals(M22PocCatalogUiState.Empty, vm.uiState.value)
        vm.clear()
    }

    @Test
    fun catalog_error() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val vm = M22PocCatalogViewModel(FakeM22PocCatalogRepository(failCatalog = true), scope)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is M22PocCatalogUiState.Error)
        vm.clear()
    }

    @Test
    fun detail_success() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val vm = M22PocDetailViewModel("m22_provider_grooming", FakeM22PocCatalogRepository(), scope)
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state is M22PocDetailUiState.Content)
        assertEquals("Patitas Centro", (state as M22PocDetailUiState.Content).provider.displayName)
        vm.clear()
    }
}
