package com.comunidapp.app.data.mock

import com.comunidapp.app.data.model.AuthAccount
import java.util.UUID

/**
 * Fixture mock de autenticación (solo memoria).
 *
 * Cuenta demo documentada (no es credencial de producción):
 * - email: el de [MockData.currentUser]
 * - password: [DEMO_PASSWORD]
 */
object MockAuthDatabase {

    /** Contraseña mínima 8 caracteres (contrato M01). Solo fixture local. */
    const val DEMO_PASSWORD = "demo1234"

    private val accounts = mutableMapOf<String, AuthAccount>()

    init {
        seedDemoAccount()
    }

    private fun seedDemoAccount() {
        accounts.clear()
        accounts[MockData.currentUser.email.lowercase()] = AuthAccount(
            email = MockData.currentUser.email.lowercase(),
            password = DEMO_PASSWORD,
            name = MockData.currentUser.name,
            emailVerified = true
        )
    }

    /** Reinicia fixtures para pruebas deterministas. */
    fun resetToFixtures() {
        seedDemoAccount()
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

    fun generateResetToken(email: String): String? {
        val account = findByEmail(email) ?: return null
        val token = UUID.randomUUID().toString().take(8).uppercase()
        setResetToken(account.email, token)
        return token
    }

    fun isValidResetToken(email: String, token: String): Boolean {
        val account = findByEmail(email) ?: return false
        return account.resetToken != null && account.resetToken.equals(token.trim(), ignoreCase = true)
    }
}
