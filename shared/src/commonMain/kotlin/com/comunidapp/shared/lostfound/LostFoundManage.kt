package com.comunidapp.shared.lostfound

/**
 * Resultado de gestión owner (resolver / editar contenido) — sin hard delete.
 */
sealed interface LostFoundManageResult {
    data object Success : LostFoundManageResult
    data class Forbidden(val message: String) : LostFoundManageResult
    data class Unauthenticated(val message: String) : LostFoundManageResult
    data class Conflict(val message: String) : LostFoundManageResult
    data class BackendError(val message: String) : LostFoundManageResult
}
