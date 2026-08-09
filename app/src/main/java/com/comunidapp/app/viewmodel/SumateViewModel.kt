package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * Estado del hub Sumate — sobrevive detalle/formularios y restauración de tab.
 */
class SumateViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val selectedCategory: StateFlow<Int> =
        savedStateHandle.getStateFlow(KEY_CATEGORY, 0)

    val searchQuery: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_SEARCH, "")

    val orgFilter: StateFlow<Boolean> =
        savedStateHandle.getStateFlow(KEY_ORG_FILTER, false)

    val alertViewMode: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_ALERT_VIEW, "LIST")

    fun selectCategory(index: Int) {
        savedStateHandle[KEY_CATEGORY] = index.coerceIn(0, CATEGORY_COUNT - 1)
    }

    fun setSearchQuery(value: String) {
        savedStateHandle[KEY_SEARCH] = value
    }

    fun setOrgFilter(enabled: Boolean) {
        savedStateHandle[KEY_ORG_FILTER] = enabled
    }

    fun setAlertViewMode(mode: String) {
        savedStateHandle[KEY_ALERT_VIEW] = mode
    }

    companion object {
        const val CATEGORY_COUNT = 5
        private const val KEY_CATEGORY = "sumate_selected_category"
        private const val KEY_SEARCH = "sumate_search_query"
        private const val KEY_ORG_FILTER = "sumate_org_filter"
        private const val KEY_ALERT_VIEW = "sumate_alert_view_mode"
    }
}
