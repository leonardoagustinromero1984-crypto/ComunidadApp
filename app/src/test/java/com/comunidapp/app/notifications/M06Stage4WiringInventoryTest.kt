package com.comunidapp.app.notifications

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class M06Stage4WiringInventoryTest {

    @Test
    fun `legacy client dispatcher debt remains explicitly inventoried`() {
        val remaining = NotificationDispatcher.remainingClientCallSites()

        assertTrue(remaining.isNotEmpty())
        assertTrue(remaining.all { it.contains("PENDIENTE") })
    }

    @Test
    fun `push edge function is service authorized and claims governed deliveries`() {
        val edgeFunction = repositoryFile("supabase/functions/push/index.ts").readText()

        assertTrue(edgeFunction.contains("UNAUTHORIZED"))
        assertTrue(edgeFunction.contains("authorizeService"))
        assertTrue(edgeFunction.contains("m06_claim_push_deliveries"))
        assertTrue(edgeFunction.contains("Ignore arbitrary client payload fields"))
    }

    @Test
    fun `username and auth domain markers remain intact`() {
        val username = repositoryFile("app/src/main/java/com/comunidapp/app/domain/user/UsernameValidators.kt").readText()
        val authRepository = repositoryFile("app/src/main/java/com/comunidapp/app/data/repository/AuthRepository.kt").readText()

        assertTrue(username.contains("object UsernameValidators"))
        assertTrue(username.contains("\"login\""))
        assertTrue(username.contains("\"logout\""))
        assertTrue(authRepository.contains("interface AuthRepository"))
        assertTrue(authRepository.contains("suspend fun login"))
    }

    private fun repositoryFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath"),
            File(System.getProperty("user.dir"), relativePath),
            File(System.getProperty("user.dir"), "../$relativePath")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("$relativePath not found. cwd=${System.getProperty("user.dir")}")
    }
}
