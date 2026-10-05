package com.comunidapp.app.domain.lostfound

/**
 * canon_create_lost_found (1096): p_locality_id is location_nodes.id.
 * A human label belongs in p_location_label. Sending the label as the id
 * fails the FK and the RPC wraps it as LF-CREATE-ALERT.
 */
object LostFoundCreatePayload {
    private val uuid = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    )

    fun locationNodeIdOrNull(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        if (value.contains(' ') || value.contains(',')) return null
        if (value.startsWith("loc-") || uuid.matches(value)) return value
        return null
    }

    fun isMediaAssetId(raw: String?): Boolean = uuid.matches(raw?.trim().orEmpty())
}
