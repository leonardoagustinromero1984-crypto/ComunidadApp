package com.comunidapp.app.data.mock

import com.comunidapp.app.data.model.AuthAccount
import java.util.UUID

/**
 * Base de datos mock en memoria para auth.
 * Cuando conectemos Firebase Auth, esto se reemplaza por Firebase (no guardar contraseñas localmente en producción).
 */
object MockAuthDatabase {

    private val accounts = mutableMapOf<String, AuthAccount>()

    init {
        // Usuario demo pre-verificado
        accounts[MockData.currentUser.email.lowercase()] = AuthAccount(
            email = MockData.currentUser.email,
            password = "123456",
            name = MockData.currentUser.name,
            emailVerified = true
        )
    }

    fun findByEmail(email: String): AuthAccount? =
        accounts[email.lowercase().trim()]

    fun save(account: AuthAccount) {
        accounts[account.email.lowercase().trim()] = account
    }

    fun updatePassword(email: String, newPassword: String) {
        val key = email.lowercase().trim()
        accounts[key]?.let { accounts[key] = it.copy(password = newPassword, resetToken = null) }
    }

    fun setEmailVerified(email: String, verified: Boolean) {
        val key = email.lowercase().trim()
        accounts[key]?.let { accounts[key] = it.copy(emailVerified = verified) }
    }

    fun setResetToken(email: String, token: String?) {
        val key = email.lowercase().trim()
        accounts[key]?.let { accounts[key] = it.copy(resetToken = token) }
    }

    fun generateResetToken(email: String): String {
        val token = UUID.randomUUID().toString().take(8).uppercase()
        setResetToken(email, token)
        return token
    }

    fun isValidResetToken(email: String, token: String): Boolean {
        val account = findByEmail(email) ?: return false
        return account.resetToken != null && account.resetToken.equals(token.trim(), ignoreCase = true)
    }
}
