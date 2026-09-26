package com.comunidapp.app.domain.auth

/**
 * Classifies the unified login identifier without naming administrative accounts.
 * Email-shaped values use PERSON email auth.
 * Anything else is resolved server-side: PERSON username first, then staff.
 */
object LoginIdentifier {
    fun looksLikeEmail(raw: String): Boolean = raw.trim().contains('@')
}
