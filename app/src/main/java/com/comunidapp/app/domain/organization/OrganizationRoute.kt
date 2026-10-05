package com.comunidapp.app.domain.organization

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Organization scope travels on the route. A general entry has no id.
 */
object OrganizationRoute {
    const val ARG = "organizationId"

    fun pattern(base: String): String = "$base?$ARG={$ARG}"

    fun append(base: String, organizationId: String?): String {
        val id = organizationId?.trim().orEmpty()
        if (id.isEmpty()) return base
        val encoded = URLEncoder.encode(id, StandardCharsets.UTF_8.name())
        return "$base?$ARG=$encoded"
    }

    fun read(raw: String?): String? =
        raw?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }?.trim()?.takeIf { it.isNotEmpty() }
}
