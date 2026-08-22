package com.comunidapp.app.data.model

import com.comunidapp.app.domain.i18n.MarketUxPolicy

/**
 * Catálogo jerárquico de ubicación administrativa (UI-V2-03).
 *
 * LOCATION_BACKEND_MIGRATION_REQUIRED = YES
 *
 * Hoy no hay tablas ni FKs de geografía. Perfil, orgs, refugios, etc. persisten
 * strings (`province`, `city`, `location_text`, `public_zone_text`, …).
 * Este catálogo vive en memoria de app (semilla + CRUD admin de sesión) y la UI
 * muestra nombres / persiste nombres compatibles. Los IDs estables no se
 * guardan en backend hasta la migración SQL.
 *
 * Distinto de geolocalización sensible (M09/M13 lat/lng, direcciones privadas,
 * `zone_id` IANA de timezone).
 */
enum class LocationLevel {
    COUNTRY,
    PROVINCE,
    MUNICIPALITY,
    LOCALITY,
    ZONE
}

data class LocationNode(
    val id: String,
    val name: String,
    val level: LocationLevel,
    val parentId: String? = null,
    val active: Boolean = true,
    val order: Int = 0,
    val code: String? = null,
    val aliases: List<String> = emptyList(),
    val centroidLat: Double? = null,
    val centroidLng: Double? = null
) {
    fun matchesQuery(query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        if (name.lowercase().contains(q)) return true
        if (code?.lowercase()?.contains(q) == true) return true
        return aliases.any { it.lowercase().contains(q) }
    }

    fun matchesExact(value: String): Boolean {
        val v = value.trim().lowercase()
        if (v.isEmpty()) return false
        if (name.equals(value.trim(), ignoreCase = true)) return true
        if (code.equals(value.trim(), ignoreCase = true)) return true
        return aliases.any { it.equals(value.trim(), ignoreCase = true) }
    }
}

data class LocationSelection(
    val countryId: String? = null,
    val provinceId: String? = null,
    val municipalityId: String? = null,
    val localityId: String? = null,
    val zoneId: String? = null
) {
    val isEmpty: Boolean
        get() = countryId == null && provinceId == null && municipalityId == null &&
            localityId == null && zoneId == null

    fun withCountry(id: String?): LocationSelection =
        if (id == countryId) this
        else copy(countryId = id, provinceId = null, municipalityId = null, localityId = null, zoneId = null)

    fun withProvince(id: String?): LocationSelection =
        if (id == provinceId) this
        else copy(provinceId = id, municipalityId = null, localityId = null, zoneId = null)

    fun withMunicipality(id: String?): LocationSelection =
        if (id == municipalityId) this
        else copy(municipalityId = id, localityId = null, zoneId = null)

    fun withLocality(id: String?): LocationSelection =
        if (id == localityId) this
        else copy(localityId = id, zoneId = null)

    fun withZone(id: String?): LocationSelection =
        copy(zoneId = id)
}

data class LocationDisplay(
    val countryName: String = "",
    val provinceName: String = "",
    val municipalityName: String = "",
    val localityName: String = "",
    val zoneName: String = "",
    val cityName: String = "",
    val label: String = "",
    val countryIso: String? = null
)

fun List<LocationNode>.byId(): Map<String, LocationNode> = associateBy { it.id }

fun List<LocationNode>.children(
    parentId: String?,
    level: LocationLevel,
    activeOnly: Boolean = true
): List<LocationNode> = filter { node ->
    node.level == level &&
        node.parentId == parentId &&
        (!activeOnly || node.active)
}.sortedWith(compareBy<LocationNode> { it.order }.thenBy { it.name })

/** Optional helper. Onboarding lists all provinces; GEOGRAPHY_SCOPE = ARGENTINA_COMPLETE. */
fun List<LocationNode>.selectableProvinces(activeOnly: Boolean = true): List<LocationNode> =
    filter { node ->
        node.level == LocationLevel.PROVINCE &&
            (!activeOnly || node.active) &&
            searchLocalitiesInProvince(node.id, query = "", activeOnly = activeOnly).isNotEmpty()
    }.sortedWith(compareBy<LocationNode> { it.order }.thenBy { it.name })

fun List<LocationNode>.search(
    query: String,
    level: LocationLevel,
    parentId: String?,
    activeOnly: Boolean = true,
    limit: Int = 64
): List<LocationNode> {
    val pool = if (level == LocationLevel.PROVINCE && parentId == null) {
        // Canonical provinces parent a COUNTRY node (loc-ar). In-memory seed uses parent=null.
        filter { node ->
            node.level == LocationLevel.PROVINCE && (!activeOnly || node.active)
        }.sortedWith(compareBy<LocationNode> { it.order }.thenBy { it.name })
    } else {
        children(parentId, level, activeOnly)
    }
    val q = query.trim()
    val filtered = if (q.isEmpty()) pool else pool.filter { it.matchesQuery(q) }
    return filtered.take(limit)
}

/** Localidades de toda la provincia (atraviesa Partido/Municipio internamente). */
fun List<LocationNode>.searchLocalitiesInProvince(
    provinceId: String?,
    query: String,
    activeOnly: Boolean = true,
    limit: Int = 64
): List<LocationNode> {
    if (provinceId.isNullOrBlank()) return emptyList()
    val municipalityIds = children(provinceId, LocationLevel.MUNICIPALITY, activeOnly)
        .map { it.id }
        .toSet()
    val pool = filter { node ->
        node.level == LocationLevel.LOCALITY &&
            (node.parentId == provinceId || node.parentId in municipalityIds) &&
            (!activeOnly || node.active)
    }.sortedWith(compareBy<LocationNode> { it.order }.thenBy { it.name })
    val q = query.trim()
    val filtered = if (q.isEmpty()) pool else pool.filter { it.matchesQuery(q) }
    return filtered.take(limit)
}

fun List<LocationNode>.selectionForLocality(localityId: String?): LocationSelection {
    if (localityId.isNullOrBlank()) return LocationSelection()
    val node = firstOrNull { it.id == localityId && it.level == LocationLevel.LOCALITY }
        ?: return LocationSelection()
    return clearIncompatible(walkToSelection(node))
}

fun List<LocationNode>.localityContextLabel(locality: LocationNode): String? {
    if (locality.level != LocationLevel.LOCALITY) return null
    val parent = locality.parentId?.let { id -> firstOrNull { it.id == id } } ?: return null
    if (parent.level == LocationLevel.PROVINCE) return null
    val municipality = parent
    val provinceId = municipality.parentId
    val duplicates = filter { node ->
        node.level == LocationLevel.LOCALITY &&
            node.active &&
            node.name.equals(locality.name, ignoreCase = true) &&
            node.parentId?.let { munId ->
                firstOrNull { it.id == munId }?.parentId == provinceId
            } == true
    }
    return if (duplicates.size > 1) municipality.name else null
}

/** Etiqueta visible Provincia + Localidad (sin país en el mercado inicial). */
fun List<LocationNode>.visibleLabel(selection: LocationSelection): String {
    val display = displayOf(selection)
    val parts = mutableListOf(display.localityName, display.provinceName)
    if (MarketUxPolicy.COUNTRY_UI_VISIBLE) parts += display.countryName
    return parts.filter { it.isNotBlank() }.distinct().joinToString(", ")
}

fun List<LocationNode>.clearIncompatible(selection: LocationSelection): LocationSelection {
    val map = byId()
    var countryId = selection.countryId?.takeIf { id ->
        map[id]?.let { it.level == LocationLevel.COUNTRY } == true
    }
    var provinceId = selection.provinceId?.takeIf { id ->
        val node = map[id]
        node != null &&
            node.level == LocationLevel.PROVINCE &&
            (countryId == null || node.parentId == countryId || node.parentId == null)
    }
    if (countryId == null && provinceId != null) {
        countryId = map[provinceId]?.parentId?.takeIf { parent ->
            map[parent]?.level == LocationLevel.COUNTRY
        }
    }
    var municipalityId = selection.municipalityId?.takeIf { id ->
        val node = map[id]
        node != null &&
            node.level == LocationLevel.MUNICIPALITY &&
            node.parentId == provinceId
    }
    var localityId = selection.localityId?.takeIf { id ->
        val node = map[id]
        node != null &&
            node.level == LocationLevel.LOCALITY &&
            (
                (municipalityId != null && node.parentId == municipalityId) ||
                    (municipalityId == null && node.parentId == provinceId)
            )
    }
    var zoneId = selection.zoneId?.takeIf { id ->
        val node = map[id]
        node != null &&
            node.level == LocationLevel.ZONE &&
            node.parentId == localityId
    }
    if (provinceId == null) {
        municipalityId = null
        localityId = null
        zoneId = null
    }
    return LocationSelection(countryId, provinceId, municipalityId, localityId, zoneId)
}

fun List<LocationNode>.displayOf(selection: LocationSelection): LocationDisplay {
    val map = byId()
    val countryNode = selection.countryId?.let { map[it] }
    val country = countryNode?.name.orEmpty()
    val province = selection.provinceId?.let { map[it] }?.name.orEmpty()
    val municipality = selection.municipalityId?.let { map[it] }?.name.orEmpty()
    val locality = selection.localityId?.let { map[it] }?.name.orEmpty()
    val zone = selection.zoneId?.let { map[it] }?.name.orEmpty()
    val city = locality.ifBlank { municipality }.ifBlank { province }
    val parts = mutableListOf(zone, locality, municipality, province)
    if (MarketUxPolicy.COUNTRY_UI_VISIBLE) parts += country
    val label = parts.filter { it.isNotBlank() }.distinct().joinToString(", ")
    return LocationDisplay(
        countryName = country,
        provinceName = province,
        municipalityName = municipality,
        localityName = locality,
        zoneName = zone,
        cityName = city,
        label = label,
        countryIso = countryNode?.code
    )
}

fun List<LocationNode>.restoreSelection(
    localityId: String? = null,
    province: String? = null,
    city: String? = null,
    label: String? = null
): LocationSelection {
    val fromId = selectionForLocality(localityId)
    if (!fromId.localityId.isNullOrBlank()) return fromId
    return resolveSelection(province = province, city = city, label = label)
}

fun List<LocationNode>.resolveSelection(
    province: String? = null,
    city: String? = null,
    label: String? = null
): LocationSelection {
    val parts = buildList {
        label?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.let { addAll(it) }
        city?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
        province?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
    }.distinct()
    if (parts.isEmpty()) return LocationSelection()

    fun matchLevel(level: LocationLevel, parentId: String?): LocationNode? {
        val pool = filter { it.level == level && (parentId == null || it.parentId == parentId) }
        return parts.firstNotNullOfOrNull { part -> pool.firstOrNull { it.matchesExact(part) } }
    }

    val provinceNode = matchLevel(LocationLevel.PROVINCE, null)
        ?: parts.firstNotNullOfOrNull { part ->
            firstOrNull { it.level == LocationLevel.PROVINCE && it.matchesExact(part) }
        }
    val municipalityNode = matchLevel(LocationLevel.MUNICIPALITY, provinceNode?.id)
        ?: parts.firstNotNullOfOrNull { part ->
            firstOrNull {
                it.level == LocationLevel.MUNICIPALITY &&
                    it.matchesExact(part) &&
                    (provinceNode == null || it.parentId == provinceNode.id)
            }
        }
    val localityNode = matchLevel(LocationLevel.LOCALITY, municipalityNode?.id)
        ?: parts.firstNotNullOfOrNull { part ->
            firstOrNull {
                it.level == LocationLevel.LOCALITY &&
                    it.matchesExact(part) &&
                    (
                        municipalityNode == null &&
                            (provinceNode == null || it.parentId == provinceNode.id) ||
                            it.parentId == municipalityNode?.id
                    )
            }
        }
    val zoneNode = matchLevel(LocationLevel.ZONE, localityNode?.id)

    val mostSpecific = zoneNode ?: localityNode ?: municipalityNode ?: provinceNode
        ?: parts.firstNotNullOfOrNull { part -> firstOrNull { it.matchesExact(part) } }

    return if (mostSpecific == null) {
        LocationSelection()
    } else {
        walkToSelection(mostSpecific)
    }.let { clearIncompatible(it) }
}

private fun List<LocationNode>.walkToSelection(node: LocationNode): LocationSelection {
    val map = byId()
    var current: LocationNode? = node
    var countryId: String? = null
    var provinceId: String? = null
    var municipalityId: String? = null
    var localityId: String? = null
    var zoneId: String? = null
    while (current != null) {
        when (current.level) {
            LocationLevel.ZONE -> zoneId = current.id
            LocationLevel.LOCALITY -> localityId = current.id
            LocationLevel.MUNICIPALITY -> municipalityId = current.id
            LocationLevel.PROVINCE -> provinceId = current.id
            LocationLevel.COUNTRY -> countryId = current.id
        }
        current = current.parentId?.let { map[it] }
    }
    return LocationSelection(countryId, provinceId, municipalityId, localityId, zoneId)
}
