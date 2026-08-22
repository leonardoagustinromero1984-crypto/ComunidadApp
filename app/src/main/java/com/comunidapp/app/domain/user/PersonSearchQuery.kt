package com.comunidapp.app.domain.user

/**
 * Friend / person search input. Does not change RLS; only normalizes UI text
 * before `canon_search_persons`.
 */
object PersonSearchQuery {
    fun normalize(raw: String): String {
        var query = raw.trim()
        if (query.startsWith("@")) {
            query = query.drop(1).trim()
        }
        return query
    }
}
