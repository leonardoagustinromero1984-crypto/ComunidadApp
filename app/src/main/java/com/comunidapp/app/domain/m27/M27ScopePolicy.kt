package com.comunidapp.app.domain.m27

import com.comunidapp.app.data.model.M27ApiScopes

object M27ScopePolicy {
    fun validateRequested(granted: List<String>, requested: List<String>): String? {
        if (requested.isEmpty()) return "M27_INVALID_SCOPE"
        if (requested.any { !M27ApiScopes.isAllowed(it) }) return "M27_UNKNOWN_SCOPE"
        if (requested.any { it !in granted }) return "M27_SCOPE_DENIED"
        return null
    }

    fun filterAllowed(scopes: List<String>): List<String> = scopes.filter { M27ApiScopes.isAllowed(it) }

    fun validateGrantList(scopes: List<String>): String? = when {
        scopes.isEmpty() -> "M27_INVALID_SCOPE"
        scopes.any { !M27ApiScopes.isAllowed(it) } -> "M27_UNKNOWN_SCOPE"
        else -> null
    }
}
