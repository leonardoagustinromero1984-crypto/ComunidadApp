package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.LocationLevel
import com.comunidapp.app.data.model.LocationNode
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.LocationCatalogRepository
import com.comunidapp.app.data.repository.PermissionRepository
import com.comunidapp.app.domain.authorization.PermissionCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LocationCatalogAdminUiState(
    val accessChecked: Boolean = false,
    val accessAllowed: Boolean = false,
    val level: LocationLevel = LocationLevel.PROVINCE,
    val parentId: String? = null,
    val query: String = "",
    val includeInactive: Boolean = true,
    val items: List<LocationNode> = emptyList(),
    val parents: List<LocationNode> = emptyList(),
    val editing: LocationNode? = null,
    val draftName: String = "",
    val draftCode: String = "",
    val draftAliases: String = "",
    val draftParentId: String? = null,
    val message: String? = null,
    val showEditor: Boolean = false
)

class LocationCatalogAdminViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository,
    private val catalog: LocationCatalogRepository = DataProvider.locationCatalogRepository
) : ViewModel() {

    private val _filters = MutableStateFlow(LocationCatalogAdminUiState())

    val uiState: StateFlow<LocationCatalogAdminUiState> = combine(
        _filters,
        catalog.nodes
    ) { filters, nodes ->
        val parentLevel = when (filters.level) {
            LocationLevel.COUNTRY -> null
            LocationLevel.PROVINCE -> LocationLevel.COUNTRY
            LocationLevel.MUNICIPALITY -> LocationLevel.PROVINCE
            LocationLevel.LOCALITY -> LocationLevel.MUNICIPALITY
            LocationLevel.ZONE -> LocationLevel.LOCALITY
        }
        val parents = if (parentLevel == null) {
            emptyList()
        } else {
            nodes.filter { it.level == parentLevel && (filters.includeInactive || it.active) }
                .sortedWith(compareBy({ it.order }, { it.name }))
        }
        val parentId = filters.parentId?.takeIf { id -> parents.any { it.id == id } }
        val items = nodes.filter { node ->
            node.level == filters.level &&
                (filters.level == LocationLevel.PROVINCE ||
                    filters.level == LocationLevel.COUNTRY ||
                    node.parentId == parentId) &&
                (filters.includeInactive || node.active) &&
                (filters.query.isBlank() || node.matchesQuery(filters.query))
        }.sortedWith(compareBy({ it.order }, { it.name }))
        filters.copy(items = items, parents = parents, parentId = parentId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocationCatalogAdminUiState())

    init {
        viewModelScope.launch {
            catalog.refresh()
            val user = authRepository.getCurrentUser()
            if (user == null) {
                _filters.update { it.copy(accessChecked = true, accessAllowed = false) }
                return@launch
            }
            permissionRepository.refresh(user.id)
            val allowed = permissionRepository.hasPermission(user.id, PermissionCode.ROLES_VIEW) ||
                permissionRepository.hasPermission(user.id, PermissionCode.USERS_CHANGE_STATUS) ||
                permissionRepository.hasPermission(user.id, PermissionCode.MODERATION_VIEW)
            _filters.update { it.copy(accessChecked = true, accessAllowed = allowed) }
        }
    }

    fun onLevel(level: LocationLevel) {
        _filters.update {
            it.copy(level = level, parentId = null, editing = null, showEditor = false)
        }
    }

    fun onParent(parentId: String?) {
        _filters.update { it.copy(parentId = parentId, editing = null, showEditor = false) }
    }

    fun onQuery(query: String) {
        _filters.update { it.copy(query = query) }
    }

    fun onIncludeInactive(value: Boolean) {
        _filters.update { it.copy(includeInactive = value) }
    }

    fun startCreate() {
        val state = _filters.value
        _filters.update {
            it.copy(
                showEditor = true,
                editing = null,
                draftName = "",
                draftCode = "",
                draftAliases = "",
                draftParentId = state.parentId,
                message = null
            )
        }
    }

    fun startEdit(node: LocationNode) {
        _filters.update {
            it.copy(
                showEditor = true,
                editing = node,
                draftName = node.name,
                draftCode = node.code.orEmpty(),
                draftAliases = node.aliases.joinToString(", "),
                draftParentId = node.parentId,
                message = null
            )
        }
    }

    fun cancelEditor() {
        _filters.update { it.copy(showEditor = false, editing = null) }
    }

    fun onDraftName(value: String) = _filters.update { it.copy(draftName = value) }
    fun onDraftCode(value: String) = _filters.update { it.copy(draftCode = value) }
    fun onDraftAliases(value: String) = _filters.update { it.copy(draftAliases = value) }
    fun onDraftParent(value: String?) = _filters.update { it.copy(draftParentId = value) }

    fun saveEditor() {
        val state = _filters.value
        val name = state.draftName.trim()
        if (name.length < 2) {
            _filters.update { it.copy(message = "El nombre es obligatorio") }
            return
        }
        if (state.level != LocationLevel.PROVINCE &&
            state.level != LocationLevel.COUNTRY &&
            state.draftParentId.isNullOrBlank()
        ) {
            _filters.update { it.copy(message = "Elegí el padre") }
            return
        }
        viewModelScope.launch {
            val kind = when (state.level) {
                LocationLevel.COUNTRY -> "COUNTRY"
                LocationLevel.PROVINCE -> "PROVINCE"
                else -> "LOCALITY"
            }
            val parentId = when (state.level) {
                LocationLevel.COUNTRY -> null
                LocationLevel.PROVINCE ->
                    state.draftParentId
                        ?: catalog.snapshot().firstOrNull { it.level == LocationLevel.COUNTRY }?.id
                else -> state.draftParentId
            }
            val existing = state.editing
            val result = DataProvider.masterCatalogRepository.upsertLocation(
                id = existing?.id?.takeIf { it.isNotBlank() },
                kind = kind,
                parentId = parentId,
                name = name,
                isoCode = state.draftCode.trim().ifBlank { null },
                sortKey = existing?.order ?: 0,
                active = existing?.active ?: true
            )
            result.fold(
                onSuccess = {
                    catalog.refresh()
                    _filters.update {
                        it.copy(showEditor = false, editing = null, message = "Guardado")
                    }
                },
                onFailure = { err ->
                    _filters.update { it.copy(message = err.message ?: "No se pudo guardar") }
                }
            )
        }
    }

    fun toggleActive(node: LocationNode) {
        catalog.setActive(node.id, !node.active)
        viewModelScope.launch {
            val kind = when (node.level) {
                LocationLevel.COUNTRY -> "COUNTRY"
                LocationLevel.PROVINCE -> "PROVINCE"
                else -> "LOCALITY"
            }
            DataProvider.masterCatalogRepository.upsertLocation(
                id = node.id,
                kind = kind,
                parentId = node.parentId,
                name = node.name,
                isoCode = node.code,
                sortKey = node.order,
                active = !node.active
            )
            catalog.refresh()
        }
    }

    fun move(node: LocationNode, delta: Int) {
        catalog.moveOrder(node.id, delta)
    }

    fun clearMessage() {
        _filters.update { it.copy(message = null) }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LocationCatalogAdminViewModel() as T
                }
            }
    }
}
