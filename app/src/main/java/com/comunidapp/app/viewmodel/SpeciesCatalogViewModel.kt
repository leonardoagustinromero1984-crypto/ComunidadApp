package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.CatalogBreed
import com.comunidapp.app.data.repository.CatalogSpecies
import com.comunidapp.app.data.repository.MasterCatalogRepository
import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.pets.SecondaryClassificationKind
import com.comunidapp.app.domain.pets.SpeciesLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpeciesCatalogListUiState(
    val accessChecked: Boolean = false,
    val accessAllowed: Boolean = false,
    val canManage: Boolean = false,
    val items: List<CatalogSpecies> = emptyList(),
    val message: String? = null,
    val loading: Boolean = false
)

data class SpeciesCatalogEditorUiState(
    val accessChecked: Boolean = false,
    val accessAllowed: Boolean = false,
    val canManage: Boolean = false,
    val loading: Boolean = false,
    val species: CatalogSpecies? = null,
    val items: List<CatalogBreed> = emptyList(),
    val name: String = "",
    val code: String = "",
    val status: String = SpeciesLifecycle.PREPARATION,
    val secondaryEnabled: Boolean = false,
    val secondaryKind: String = SecondaryClassificationKind.BREED,
    val labelSingular: String = "Raza",
    val labelPlural: String = "Razas",
    val newItemName: String = "",
    val confirmKindChange: Boolean = false,
    val pendingKind: String? = null,
    val message: String? = null,
    val saved: Boolean = false
)

class SpeciesCatalogListViewModel(
    private val catalog: MasterCatalogRepository = DataProvider.masterCatalogRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SpeciesCatalogListUiState())
    val uiState: StateFlow<SpeciesCatalogListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val user = AuthProvider.repository.getCurrentUser()
            if (user == null) {
                _uiState.update { it.copy(accessChecked = true) }
                return@launch
            }
            val repo = DataProvider.permissionRepository
            repo.refresh(user.id)
            val allowed = repo.hasPermission(user.id, PermissionCode.CATALOGS_VIEW)
            _uiState.update {
                it.copy(
                    accessChecked = true,
                    accessAllowed = allowed,
                    canManage = repo.hasPermission(user.id, PermissionCode.CATALOGS_MANAGE)
                )
            }
            if (allowed) refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val items = catalog.listSpecies(includeInactive = true)
            _uiState.update { it.copy(loading = false, items = items) }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    fun setStatus(code: String, status: String) {
        if (!_uiState.value.canManage) return
        viewModelScope.launch {
            catalog.setSpeciesStatus(code, status).fold(
                onSuccess = {
                    _uiState.update { it.copy(message = "Estado actualizado") }
                    refresh()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(message = err.message ?: "No se pudo cambiar el estado")
                    }
                }
            )
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SpeciesCatalogListViewModel() as T
        }
    }
}

class SpeciesCatalogEditorViewModel(
    private val speciesCode: String?,
    private val catalog: MasterCatalogRepository = DataProvider.masterCatalogRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SpeciesCatalogEditorUiState())
    val uiState: StateFlow<SpeciesCatalogEditorUiState> = _uiState.asStateFlow()
    private var originalKind: String = SecondaryClassificationKind.NONE

    init {
        viewModelScope.launch {
            val user = AuthProvider.repository.getCurrentUser()
            if (user == null) {
                _uiState.update { it.copy(accessChecked = true) }
                return@launch
            }
            val repo = DataProvider.permissionRepository
            repo.refresh(user.id)
            val allowed = repo.hasPermission(user.id, PermissionCode.CATALOGS_VIEW)
            _uiState.update {
                it.copy(
                    accessChecked = true,
                    accessAllowed = allowed,
                    canManage = repo.hasPermission(user.id, PermissionCode.CATALOGS_MANAGE)
                )
            }
            if (allowed) load()
        }
    }

    fun onName(value: String) = _uiState.update { it.copy(name = value, message = null) }
    fun onCode(value: String) = _uiState.update { it.copy(code = value, message = null) }
    fun onStatus(value: String) = _uiState.update { it.copy(status = value) }
    fun onSecondaryEnabled(value: Boolean) {
        val kind = if (value) {
            _uiState.value.secondaryKind.takeIf { it != SecondaryClassificationKind.NONE }
                ?: SecondaryClassificationKind.BREED
        } else {
            SecondaryClassificationKind.NONE
        }
        val labels = SecondaryClassificationKind.defaultLabels(kind)
        _uiState.update {
            it.copy(
                secondaryEnabled = value,
                secondaryKind = kind,
                labelSingular = if (value && it.labelSingular.isBlank()) labels.first else it.labelSingular,
                labelPlural = if (value && it.labelPlural.isBlank()) labels.second else it.labelPlural
            )
        }
    }

    fun onKindSelected(kind: String) {
        val current = _uiState.value
        if (
            current.species != null &&
            originalKind != SecondaryClassificationKind.NONE &&
            originalKind != kind &&
            kind != SecondaryClassificationKind.NONE
        ) {
            _uiState.update { it.copy(confirmKindChange = true, pendingKind = kind) }
            return
        }
        applyKind(kind)
    }

    fun confirmKindChange() {
        val kind = _uiState.value.pendingKind ?: return
        applyKind(kind)
        _uiState.update { it.copy(confirmKindChange = false, pendingKind = null) }
    }

    fun cancelKindChange() = _uiState.update { it.copy(confirmKindChange = false, pendingKind = null) }

    fun onLabelSingular(value: String) = _uiState.update { it.copy(labelSingular = value) }
    fun onLabelPlural(value: String) = _uiState.update { it.copy(labelPlural = value) }
    fun onNewItemName(value: String) = _uiState.update { it.copy(newItemName = value) }
    fun clearMessage() = _uiState.update { it.copy(message = null) }

    fun saveSpecies() {
        if (!_uiState.value.canManage) return
        val state = _uiState.value
        val name = state.name.trim()
        if (name.length < 2) {
            _uiState.update { it.copy(message = "El nombre es obligatorio") }
            return
        }
        val code = state.code.trim().ifBlank { name }.uppercase().replace(' ', '_')
        viewModelScope.launch {
            val result = catalog.upsertSpeciesConfig(
                code = code,
                name = name,
                sortKey = state.species?.sortKey ?: 100,
                status = state.status,
                secondaryEnabled = state.secondaryEnabled,
                secondaryKind = if (state.secondaryEnabled) state.secondaryKind else SecondaryClassificationKind.NONE,
                secondaryLabelSingular = state.labelSingular.trim().ifBlank { null },
                secondaryLabelPlural = state.labelPlural.trim().ifBlank { null }
            )
            result.fold(
                onSuccess = {
                    originalKind = if (state.secondaryEnabled) state.secondaryKind else SecondaryClassificationKind.NONE
                    _uiState.update {
                        it.copy(
                            saved = speciesCode == null,
                            message = "Guardado",
                            code = code
                        )
                    }
                    if (speciesCode != null) load()
                },
                onFailure = { err ->
                    val raw = err.message.orEmpty()
                    _uiState.update {
                        it.copy(
                            message = when {
                                raw.contains("SPECIES_SECONDARY_IN_USE") ->
                                    "No se puede quitar el segundo nivel: hay mascotas con clasificación asignada."
                                else -> raw.ifBlank { "No se pudo guardar" }
                            }
                        )
                    }
                }
            )
        }
    }

    fun addSecondaryItem() {
        if (!_uiState.value.canManage) return
        val name = _uiState.value.newItemName.trim()
        val species = _uiState.value.species ?: _uiState.value.code.takeIf { it.isNotBlank() }?.let {
            CatalogSpecies(it, it, 0, false)
        }
        if (name.length < 2 || species == null) {
            _uiState.update { it.copy(message = "Escribí el nombre del elemento") }
            return
        }
        viewModelScope.launch {
            catalog.upsertSecondaryItem(
                id = null,
                speciesId = species.code,
                name = name,
                sortOrder = (_uiState.value.items.maxOfOrNull { it.sortKey } ?: 0) + 1,
                active = true
            ).fold(
                onSuccess = {
                    _uiState.update { it.copy(newItemName = "", message = "Agregado") }
                    load()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(message = err.message ?: "No se pudo agregar") }
                }
            )
        }
    }

    fun toggleSecondaryItem(item: CatalogBreed) {
        if (!_uiState.value.canManage) return
        viewModelScope.launch {
            catalog.setSecondaryItemStatus(item.id, !item.active).fold(
                onSuccess = { load() },
                onFailure = { err ->
                    _uiState.update { it.copy(message = err.message ?: "No se pudo actualizar") }
                }
            )
        }
    }

    fun setStatus(status: String) {
        val code = _uiState.value.species?.code ?: return
        if (!_uiState.value.canManage) return
        viewModelScope.launch {
            catalog.setSpeciesStatus(code, status).fold(
                onSuccess = {
                    _uiState.update { it.copy(status = status, message = "Estado actualizado") }
                    load()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(message = err.message ?: "No se pudo cambiar el estado") }
                }
            )
        }
    }

    private fun applyKind(kind: String) {
        val labels = SecondaryClassificationKind.defaultLabels(kind)
        _uiState.update {
            val custom = kind == SecondaryClassificationKind.CUSTOM
            it.copy(
                secondaryKind = kind,
                labelSingular = if (custom) it.labelSingular.ifBlank { labels.first } else labels.first,
                labelPlural = if (custom) it.labelPlural.ifBlank { labels.second } else labels.second
            )
        }
    }

    private suspend fun load() {
        val code = speciesCode ?: return
        _uiState.update { it.copy(loading = true) }
        val species = catalog.getSpecies(code)
            ?: catalog.listSpecies(includeInactive = true).firstOrNull { it.code.equals(code, true) }
        val items = if (species != null) {
            catalog.listBreeds(species.code, includeInactive = true)
        } else {
            emptyList()
        }
        if (species != null) {
            originalKind = species.secondaryClassificationKind
        }
        _uiState.update {
            it.copy(
                loading = false,
                species = species,
                items = items,
                name = species?.name.orEmpty(),
                code = species?.code ?: code,
                status = species?.status ?: SpeciesLifecycle.PREPARATION,
                secondaryEnabled = species?.secondaryClassificationEnabled == true,
                secondaryKind = species?.secondaryClassificationKind ?: SecondaryClassificationKind.BREED,
                labelSingular = species?.secondaryLabelSingular
                    ?: SecondaryClassificationKind.defaultLabels(species?.secondaryClassificationKind).first,
                labelPlural = species?.secondaryLabelPlural
                    ?: SecondaryClassificationKind.defaultLabels(species?.secondaryClassificationKind).second
            )
        }
    }

    companion object {
        fun factory(speciesCode: String?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SpeciesCatalogEditorViewModel(speciesCode) as T
            }
    }
}
