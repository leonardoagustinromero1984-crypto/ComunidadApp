package com.comunidapp.app.domain.ux

import com.comunidapp.app.data.model.LocationLevel
import com.comunidapp.app.data.model.LocationNode
import com.comunidapp.app.data.model.argentinaLocationSeed
import com.comunidapp.app.domain.qa.PhysicalQaFix03Contracts

/**
 * User-visible location text. Never show catalog keys, UUIDs, or DB codes.
 */
object HumanLocationLabel {
    const val RAW_LOCATION_ID_VISIBLE = PhysicalQaFix03Contracts.RAW_LOCATION_ID_VISIBLE

    private val UUID_RE =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun looksRawId(value: String?): Boolean {
        val v = value?.trim().orEmpty()
        if (v.isEmpty()) return false
        if (v.startsWith("loc-")) return true
        if (UUID_RE.matches(v)) return true
        return false
    }

    fun visible(raw: String?, nodes: List<LocationNode> = argentinaLocationSeed()): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return ""
        if (!looksRawId(value)) return value
        val node = nodes.firstOrNull { it.id.equals(value, ignoreCase = true) } ?: return humanizeKey(value)
        val province = ancestor(nodes, node, LocationLevel.PROVINCE)
        return listOfNotNull(node.name, province?.name?.takeIf { it != node.name })
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
    }

    private fun ancestor(
        nodes: List<LocationNode>,
        start: LocationNode,
        level: LocationLevel
    ): LocationNode? {
        val map = nodes.associateBy { it.id }
        var current: LocationNode? = start
        while (current != null) {
            if (current.level == level) return current
            current = current.parentId?.let { map[it] }
        }
        return null
    }

    private fun humanizeKey(raw: String): String {
        val tail = raw.substringAfterLast('-').substringAfterLast('_').replace('_', ' ').trim()
        if (tail.isEmpty()) return ""
        return tail.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
