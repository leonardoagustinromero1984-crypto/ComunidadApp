package com.comunidapp.app.domain.location

import com.comunidapp.app.data.model.LocationLevel
import com.comunidapp.app.data.model.LocationNode
import com.comunidapp.app.data.model.ServiceProfile
import com.comunidapp.app.data.model.resolveSelection

object ServiceLocationFilter {

    fun matches(service: ServiceProfile, query: String, catalog: List<LocationNode>): Boolean {
        if (query.isBlank()) return true
        val selection = catalog.resolveSelection(label = query)
        val serviceLocalities = (service.localityIds + listOfNotNull(service.localityId)).toSet()
        if (selection.provinceId != null) {
            val inProvince = service.provinceId == selection.provinceId ||
                serviceLocalities.any { id -> catalog.provinceIdOf(id) == selection.provinceId } ||
                textMentionsProvince(service.location, selection.provinceId, catalog)
            if (!inProvince) return false
            if (selection.localityId != null) {
                return serviceLocalities.contains(selection.localityId) ||
                    textMentionsNode(service.location, selection.localityId, catalog)
            }
            return true
        }
        return query.split(',').map { it.trim() }.filter { it.isNotEmpty() }.all { part ->
            service.location.contains(part, ignoreCase = true) ||
                catalog.any { node ->
                    node.matchesExact(part) && (
                        service.provinceId == node.id ||
                            serviceLocalities.contains(node.id) ||
                            textMentionsNode(service.location, node.id, catalog)
                        )
                }
        }
    }

    private fun List<LocationNode>.provinceIdOf(nodeId: String): String? {
        val map = associateBy { it.id }
        var current = map[nodeId]
        while (current != null) {
            if (current.level == LocationLevel.PROVINCE) return current.id
            current = current.parentId?.let { map[it] }
        }
        return null
    }

    private fun textMentionsProvince(
        location: String,
        provinceId: String,
        catalog: List<LocationNode>
    ): Boolean {
        val node = catalog.firstOrNull { it.id == provinceId } ?: return false
        return textMentionsNode(location, node.id, catalog)
    }

    private fun textMentionsNode(location: String, nodeId: String, catalog: List<LocationNode>): Boolean {
        val node = catalog.firstOrNull { it.id == nodeId } ?: return false
        val haystack = location.lowercase()
        if (haystack.contains(node.name.lowercase())) return true
        return node.aliases.any { haystack.contains(it.lowercase()) }
    }
}
