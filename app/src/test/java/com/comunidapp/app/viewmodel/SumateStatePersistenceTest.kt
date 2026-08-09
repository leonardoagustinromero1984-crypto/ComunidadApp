package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SumateStatePersistenceTest {

    @Test
    fun category_search_and_filters_persist_in_saved_state() {
        val handle = SavedStateHandle()
        val vm = SumateViewModel(handle)
        vm.selectCategory(1)
        vm.setSearchQuery("Palermo")
        vm.setOrgFilter(true)
        vm.setAlertViewMode("MAP")

        assertEquals(1, vm.selectedCategory.value)
        assertEquals("Palermo", vm.searchQuery.value)
        assertTrue(vm.orgFilter.value)
        assertEquals("MAP", vm.alertViewMode.value)

        val restored = SumateViewModel(handle)
        assertEquals(1, restored.selectedCategory.value)
        assertEquals("Palermo", restored.searchQuery.value)
        assertTrue(restored.orgFilter.value)
        assertEquals("MAP", restored.alertViewMode.value)
    }

    @Test
    fun category_is_clamped() {
        val vm = SumateViewModel(SavedStateHandle())
        vm.selectCategory(99)
        assertEquals(4, vm.selectedCategory.value)
        vm.selectCategory(-3)
        assertEquals(0, vm.selectedCategory.value)
        assertFalse(vm.orgFilter.value)
    }
}
