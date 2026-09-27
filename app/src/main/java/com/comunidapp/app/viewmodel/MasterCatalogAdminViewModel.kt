package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.CatalogBreed
import com.comunidapp.app.data.repository.CatalogHealthProduct
import com.comunidapp.app.data.repository.CatalogServiceCategory
import com.comunidapp.app.data.repository.CatalogSpecies
import com.comunidapp.app.data.repository.MasterCatalogRepository
import com.comunidapp.app.data.repository.PermissionRepository
import com.comunidapp.app.domain.authorization.PermissionCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MasterCatalogTab {
    SPECIES,
    BREEDS,
    VACCINES,
    FLEA,
    DEWORMERS,
    SERVICE_CATEGORIES;

    companion object {
        fun fromKey(key: String): MasterCatalogTab = when (key.trim().lowercase()) {
            "breeds" -> BREEDS
            "vaccines" -> VACCINES
            "flea" -> FLEA
            "dewormers" -> DEWORMERS
            "service_categories" -> SERVICE_CATEGORIES
            else -> SPECIES
        }

        fun title(tab: MasterCatalogTab): String = when (tab) {
            SPECIES -> "Especies"
            BREEDS -> "Razas"
            VACCINES -> "Vacunas"
            FLEA -> "Antipulgas"
            DEWORMERS -> "Desparasitantes"
            SERVICE_CATEGORIES -> "Categorías de servicios"
        }
    }
}

data class MasterCatalogAdminUiState(
    val accessChecked: Boolean = false,
    val accessAllowed: Boolean = false,
    val canManage: Boolean = false,
    val lockTab: Boolean = false,
    val tab: MasterCatalogTab = MasterCatalogTab.SPECIES,
    val query: String = "",
    val includeInactive: Boolean = true,
    val species: List<CatalogSpecies> = emptyList(),
    val breeds: List<CatalogBreed> = emptyList(),
    val products: List<CatalogHealthProduct> = emptyList(),
    val serviceCategories: List<CatalogServiceCategory> = emptyList(),
    val parentSpeciesCode: String = "DOG",
    val editingSpecies: CatalogSpecies? = null,
    val editingBreed: CatalogBreed? = null,
    val editingProduct: CatalogHealthProduct? = null,
    val editingServiceCategory: CatalogServiceCategory? = null,
    val draftCode: String = "",
    val draftName: String = "",
    val draftSort: String = "0",
    val draftActive: Boolean = true,
    val draftSpeciesCode: String = "DOG",
    val draftSpeciesCodes: Set<String> = emptySet(),
    val showEditor: Boolean = false,
    val message: String? = null,
    val loading: Boolean = false
)

class MasterCatalogAdminViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository,
    private val catalog: MasterCatalogRepository = DataProvider.masterCatalogRepository,
    initialTab: MasterCatalogTab = MasterCatalogTab.SPECIES,
    lockTab: Boolean = false
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MasterCatalogAdminUiState(tab = initialTab, lockTab = lockTab)
    )
    val uiState: StateFlow<MasterCatalogAdminUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            if (user == null) {
                _uiState.update { it.copy(accessChecked = true, accessAllowed = false) }
                return@launch
            }
            permissionRepository.refresh(user.id)
            val allowed = permissionRepository.hasPermission(user.id, PermissionCode.CATALOGS_VIEW)
            val canManage = permissionRepository.hasPermission(user.id, PermissionCode.CATALOGS_MANAGE)
            _uiState.update {
                it.copy(accessChecked = true, accessAllowed = allowed, canManage = canManage)
            }
            if (allowed) refresh()
        }
    }

    fun onTab(tab: MasterCatalogTab) {
        if (_uiState.value.lockTab) return
        _uiState.update { it.copy(tab = tab, showEditor = false, message = null) }
        refresh()
    }

    fun onQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun onIncludeInactive(value: Boolean) {
        _uiState.update { it.copy(includeInactive = value) }
        refresh()
    }

    fun onParentSpecies(code: String) {
        _uiState.update { it.copy(parentSpeciesCode = code, showEditor = false) }
        refresh()
    }

    fun startCreate() {
        if (!_uiState.value.canManage) return
        val state = _uiState.value
        _uiState.update {
            it.copy(
                showEditor = true,
                editingSpecies = null,
                editingBreed = null,
                editingProduct = null,
                editingServiceCategory = null,
                draftCode = "",
                draftName = "",
                draftSort = ((visibleItems().maxOfOrNull { itemSort(it) } ?: 0) + 1).toString(),
                draftActive = true,
                draftSpeciesCode = state.parentSpeciesCode,
                draftSpeciesCodes = emptySet(),
                message = null
            )
        }
    }

    fun startEditSpecies(row: CatalogSpecies) {
        _uiState.update {
            it.copy(
                showEditor = true,
                editingSpecies = row,
                editingBreed = null,
                editingProduct = null,
                editingServiceCategory = null,
                draftCode = row.code,
                draftName = row.name,
                draftSort = row.sortKey.toString(),
                draftActive = row.active,
                draftSpeciesCode = row.code
            )
        }
    }

    fun startEditBreed(row: CatalogBreed) {
        _uiState.update {
            it.copy(
                showEditor = true,
                editingSpecies = null,
                editingBreed = row,
                editingProduct = null,
                editingServiceCategory = null,
                draftCode = row.id,
                draftName = row.name,
                draftSort = row.sortKey.toString(),
                draftActive = row.active,
                draftSpeciesCode = row.speciesCode
            )
        }
    }

    fun startEditProduct(row: CatalogHealthProduct) {
        _uiState.update {
            it.copy(
                showEditor = true,
                editingSpecies = null,
                editingBreed = null,
                editingProduct = row,
                editingServiceCategory = null,
                draftCode = row.code,
                draftName = row.displayName,
                draftSort = row.sortOrder.toString(),
                draftActive = row.active,
                draftSpeciesCode = row.speciesCode ?: "",
                draftSpeciesCodes = row.speciesCodes.toSet()
            )
        }
    }

    fun startEditServiceCategory(row: CatalogServiceCategory) {
        _uiState.update {
            it.copy(
                showEditor = true,
                editingSpecies = null,
                editingBreed = null,
                editingProduct = null,
                editingServiceCategory = row,
                draftCode = row.code,
                draftName = row.name,
                draftSort = row.sortKey.toString(),
                draftActive = row.active
            )
        }
    }

    fun cancelEditor() = _uiState.update {
        it.copy(
            showEditor = false,
            editingSpecies = null,
            editingBreed = null,
            editingProduct = null,
            editingServiceCategory = null
        )
    }

    fun onDraftCode(value: String) = _uiState.update { it.copy(draftCode = value) }
    fun onDraftName(value: String) = _uiState.update { it.copy(draftName = value) }
    fun onDraftSort(value: String) = _uiState.update { it.copy(draftSort = value.filter { ch -> ch.isDigit() || ch == '-' }) }
    fun onDraftActive(value: Boolean) = _uiState.update { it.copy(draftActive = value) }
    fun onDraftSpeciesCode(value: String) = _uiState.update { it.copy(draftSpeciesCode = value) }
    fun toggleDraftSpecies(code: String) = _uiState.update {
        val next = if (code in it.draftSpeciesCodes) it.draftSpeciesCodes - code else it.draftSpeciesCodes + code
        it.copy(draftSpeciesCodes = next)
    }
    fun clearMessage() = _uiState.update { it.copy(message = null) }

    fun saveEditor() {
        if (!_uiState.value.canManage) return
        val state = _uiState.value
        val name = state.draftName.trim()
        if (name.length < 2) {
            _uiState.update { it.copy(message = "El nombre es obligatorio") }
            return
        }
        val sort = state.draftSort.toIntOrNull() ?: 0
        viewModelScope.launch {
            val result = when (state.tab) {
                MasterCatalogTab.SPECIES -> catalog.upsertSpecies(
                    code = state.draftCode.trim().ifBlank { name }.uppercase().replace(' ', '_'),
                    name = name,
                    sortKey = sort,
                    active = state.draftActive
                )
                MasterCatalogTab.BREEDS -> catalog.upsertBreed(
                    id = state.editingBreed?.id,
                    speciesCode = state.draftSpeciesCode.ifBlank { state.parentSpeciesCode },
                    name = name,
                    sortKey = sort,
                    active = state.draftActive
                )
                MasterCatalogTab.VACCINES,
                MasterCatalogTab.FLEA,
                MasterCatalogTab.DEWORMERS -> {
                    val productCode = state.draftCode.trim().ifBlank { name }.uppercase().replace(' ', '_')
                    catalog.upsertHealthProduct(
                        id = state.editingProduct?.id,
                        kind = productKind(state.tab),
                        code = productCode,
                        displayName = name,
                        speciesCode = state.draftSpeciesCodes.firstOrNull() ?: state.draftSpeciesCode.trim().ifBlank { null },
                        sortOrder = sort,
                        active = state.draftActive
                    ).onSuccess {
                        val id = state.editingProduct?.id ?: catalog.listHealthProducts(
                            productKind(state.tab),
                            includeInactive = true
                        ).firstOrNull { it.code == productCode }?.id
                        if (!id.isNullOrBlank()) {
                            catalog.setHealthProductSpecies(id, state.draftSpeciesCodes.toList())
                        }
                    }
                }
                MasterCatalogTab.SERVICE_CATEGORIES -> catalog.upsertServiceCategory(
                    code = state.draftCode.trim().ifBlank { name }.uppercase().replace(' ', '_'),
                    name = name,
                    sortKey = sort,
                    active = state.draftActive
                )
            }
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(showEditor = false, message = "Guardado") }
                    refresh()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(message = err.message ?: "No se pudo guardar") }
                }
            )
        }
    }

    fun toggleSpecies(row: CatalogSpecies) {
        if (!_uiState.value.canManage) return
        viewModelScope.launch {
            catalog.upsertSpecies(row.code, row.name, row.sortKey, !row.active)
            refresh()
        }
    }

    fun toggleBreed(row: CatalogBreed) {
        if (!_uiState.value.canManage) return
        viewModelScope.launch {
            catalog.upsertBreed(row.id, row.speciesCode, row.name, row.sortKey, !row.active)
            refresh()
        }
    }

    fun toggleProduct(row: CatalogHealthProduct) {
        if (!_uiState.value.canManage) return
        viewModelScope.launch {
            catalog.upsertHealthProduct(
                id = row.id,
                kind = row.kind,
                code = row.code,
                displayName = row.displayName,
                speciesCode = row.speciesCode,
                sortOrder = row.sortOrder,
                active = !row.active
            )
            refresh()
        }
    }

    fun toggleServiceCategory(row: CatalogServiceCategory) {
        if (!_uiState.value.canManage) return
        viewModelScope.launch {
            catalog.upsertServiceCategory(row.code, row.name, row.sortKey, !row.active)
            refresh()
        }
    }

    fun moveSpecies(row: CatalogSpecies, delta: Int) {
        if (!_uiState.value.canManage) return
        viewModelScope.launch {
            catalog.upsertSpecies(row.code, row.name, row.sortKey + delta, row.active)
            refresh()
        }
    }

    fun moveBreed(row: CatalogBreed, delta: Int) {
        if (!_uiState.value.canManage) return
        viewModelScope.launch {
            catalog.upsertBreed(row.id, row.speciesCode, row.name, row.sortKey + delta, row.active)
            refresh()
        }
    }

    fun moveProduct(row: CatalogHealthProduct, delta: Int) {
        if (!_uiState.value.canManage) return
        viewModelScope.launch {
            catalog.upsertHealthProduct(
                id = row.id,
                kind = row.kind,
                code = row.code,
                displayName = row.displayName,
                speciesCode = row.speciesCode,
                sortOrder = row.sortOrder + delta,
                active = row.active
            )
            refresh()
        }
    }

    fun moveServiceCategory(row: CatalogServiceCategory, delta: Int) {
        if (!_uiState.value.canManage) return
        viewModelScope.launch {
            catalog.upsertServiceCategory(row.code, row.name, row.sortKey + delta, row.active)
            refresh()
        }
    }

    fun visibleItems(): List<Any> {
        val state = _uiState.value
        val q = state.query.trim()
        return when (state.tab) {
            MasterCatalogTab.SPECIES -> state.species.filter {
                (state.includeInactive || it.active) &&
                    (q.isBlank() || it.name.contains(q, true) || it.code.contains(q, true))
            }
            MasterCatalogTab.BREEDS -> state.breeds.filter {
                (state.includeInactive || it.active) &&
                    (q.isBlank() || it.name.contains(q, true))
            }
            MasterCatalogTab.SERVICE_CATEGORIES -> state.serviceCategories.filter {
                (state.includeInactive || it.active) &&
                    (q.isBlank() || it.name.contains(q, true) || it.code.contains(q, true))
            }
            else -> state.products.filter {
                (state.includeInactive || it.active) &&
                    (q.isBlank() || it.displayName.contains(q, true) || it.code.contains(q, true))
            }
        }
    }

    private fun itemSort(item: Any): Int = when (item) {
        is CatalogSpecies -> item.sortKey
        is CatalogBreed -> item.sortKey
        is CatalogHealthProduct -> item.sortOrder
        is CatalogServiceCategory -> item.sortKey
        else -> 0
    }

    private fun productKind(tab: MasterCatalogTab): String = when (tab) {
        MasterCatalogTab.VACCINES -> "VACCINE"
        MasterCatalogTab.FLEA -> "FLEA"
        MasterCatalogTab.DEWORMERS -> "DEWORMER"
        else -> "VACCINE"
    }

    private fun refresh() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val species = catalog.listSpecies(includeInactive = true)
            val parent = state.parentSpeciesCode.ifBlank { species.firstOrNull()?.code.orEmpty() }
            val breeds = if (state.tab == MasterCatalogTab.BREEDS) {
                catalog.listBreeds(parent, includeInactive = true)
            } else {
                emptyList()
            }
            val products = when (state.tab) {
                MasterCatalogTab.VACCINES -> catalog.listHealthProducts("VACCINE", includeInactive = true)
                MasterCatalogTab.FLEA -> catalog.listHealthProducts("FLEA", includeInactive = true)
                MasterCatalogTab.DEWORMERS -> catalog.listHealthProducts("DEWORMER", includeInactive = true)
                else -> emptyList()
            }
            val serviceCategories = if (state.tab == MasterCatalogTab.SERVICE_CATEGORIES) {
                catalog.listServiceCategories(includeInactive = true)
            } else {
                emptyList()
            }
            _uiState.update {
                it.copy(
                    loading = false,
                    species = species,
                    breeds = breeds,
                    products = products,
                    serviceCategories = serviceCategories,
                    parentSpeciesCode = parent.ifBlank { it.parentSpeciesCode }
                )
            }
        }
    }

    companion object {
        fun factory(
            initialTab: MasterCatalogTab = MasterCatalogTab.SPECIES,
            lockTab: Boolean = false
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MasterCatalogAdminViewModel(
                        initialTab = initialTab,
                        lockTab = lockTab
                    ) as T
                }
            }
    }
}
