package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.LocationLevel
import com.comunidapp.app.data.model.LocationNode
import com.comunidapp.app.data.model.argentinaLocationSeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

enum class LocationCatalogLoadState {
    IDLE,
    LOADING,
    LOADED,
    EMPTY,
    ERROR
}

/**
 * Catálogo de ubicaciones administrativas.
 *
 * Persistencia real (SQL / RPCs) = LOCATION_BACKEND_MIGRATION_REQUIRED.
 * Esta implementación es de sesión: semilla Argentina + CRUD admin en memoria.
 * No borra físicamente: desactivar con [setActive].
 */
interface LocationCatalogRepository {
    val nodes: StateFlow<List<LocationNode>>
    val loadState: StateFlow<LocationCatalogLoadState>
    val loadErrorMessage: StateFlow<String?>
    suspend fun refresh(): Result<Unit> = Result.success(Unit)
    fun snapshot(): List<LocationNode>
    fun get(id: String): LocationNode?
    fun upsert(node: LocationNode)
    fun create(
        name: String,
        level: LocationLevel,
        parentId: String?,
        code: String? = null,
        aliases: List<String> = emptyList()
    ): LocationNode
    fun setActive(id: String, active: Boolean)
    fun moveOrder(id: String, delta: Int)
}

class InMemoryLocationCatalogRepository(
    seed: List<LocationNode> = argentinaLocationSeed()
) : LocationCatalogRepository {

    private val _nodes = MutableStateFlow(seed.sortedWith(compareBy({ it.level.ordinal }, { it.order }, { it.name })))
    override val nodes: StateFlow<List<LocationNode>> = _nodes.asStateFlow()
    override val loadState: StateFlow<LocationCatalogLoadState> =
        MutableStateFlow(LocationCatalogLoadState.LOADED).asStateFlow()
    override val loadErrorMessage: StateFlow<String?> =
        MutableStateFlow<String?>(null).asStateFlow()

    override fun snapshot(): List<LocationNode> = _nodes.value

    override fun get(id: String): LocationNode? = _nodes.value.firstOrNull { it.id == id }

    override fun upsert(node: LocationNode) {
        require(node.name.isNotBlank()) { "El nombre es obligatorio" }
        if (node.level == LocationLevel.COUNTRY) {
            require(node.parentId.isNullOrBlank()) { "El país no tiene padre" }
        } else if (node.level != LocationLevel.PROVINCE) {
            require(!node.parentId.isNullOrBlank()) { "El padre es obligatorio" }
            val parent = get(node.parentId)
            require(parent != null) { "El padre no existe" }
            val expectedParent = when (node.level) {
                LocationLevel.MUNICIPALITY -> LocationLevel.PROVINCE
                LocationLevel.LOCALITY -> LocationLevel.MUNICIPALITY
                LocationLevel.ZONE -> LocationLevel.LOCALITY
                LocationLevel.PROVINCE -> LocationLevel.COUNTRY
                LocationLevel.COUNTRY -> LocationLevel.COUNTRY
            }
            val localityOk = node.level == LocationLevel.LOCALITY &&
                (parent.level == LocationLevel.MUNICIPALITY || parent.level == LocationLevel.PROVINCE)
            require(parent.level == expectedParent || localityOk) { "Jerarquía inválida" }
        }
        _nodes.update { current ->
            val without = current.filterNot { it.id == node.id }
            (without + node).sortedWith(compareBy({ it.level.ordinal }, { it.order }, { it.name }))
        }
    }

    override fun create(
        name: String,
        level: LocationLevel,
        parentId: String?,
        code: String?,
        aliases: List<String>
    ): LocationNode {
        val siblings = snapshot().filter { it.level == level && it.parentId == parentId }
        val node = LocationNode(
            id = "loc-custom-${UUID.randomUUID()}",
            name = name.trim(),
            level = level,
            parentId = parentId,
            active = true,
            order = (siblings.maxOfOrNull { it.order } ?: 0) + 1,
            code = code?.trim()?.ifBlank { null },
            aliases = aliases.map { it.trim() }.filter { it.isNotEmpty() }
        )
        upsert(node)
        return node
    }

    override fun setActive(id: String, active: Boolean) {
        val node = get(id) ?: return
        upsert(node.copy(active = active))
    }

    override fun moveOrder(id: String, delta: Int) {
        val node = get(id) ?: return
        val siblings = snapshot()
            .filter { it.level == node.level && it.parentId == node.parentId }
            .sortedWith(compareBy({ it.order }, { it.name }))
            .toMutableList()
        val index = siblings.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = (index + delta).coerceIn(0, siblings.lastIndex)
        if (target == index) return
        siblings.add(target, siblings.removeAt(index))
        _nodes.update { current ->
            val reordered = siblings.mapIndexed { i, item -> item.copy(order = i + 1) }
            val ids = reordered.map { it.id }.toSet()
            (current.filterNot { it.id in ids } + reordered)
                .sortedWith(compareBy({ it.level.ordinal }, { it.order }, { it.name }))
        }
    }
}
