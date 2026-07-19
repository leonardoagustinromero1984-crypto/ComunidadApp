package com.comunidapp.app.domain.observability

import com.comunidapp.app.domain.auth.AuthState
import com.comunidapp.app.domain.user.UsernameValidators
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards: Etapa 2 must not touch auth/username sources.
 */
class M07Stage2AuthUsernameIntactTest {

    @Test
    fun usernameValidators_stillPresentAndCallable() {
        assertNotNull(UsernameValidators)
        // Soft check: validation API still resolves (does not fix username bug)
        val result = runCatching { UsernameValidators::class.java.methods.size }
        assertTrue(result.isSuccess)
        assertTrue(result.getOrDefault(0) > 0)
    }

    @Test
    fun authState_domainIntact() {
        assertNotNull(AuthState.Initializing)
    }

    @Test
    fun authRepository_sourceFileUnchangedByStage2Markers() {
        val repo = File(
            "src/main/java/com/comunidapp/app/data/repository/SupabaseAuthRepository.kt"
        )
        // Path relative to app module when tests run from app/
        val candidates = listOf(
            repo,
            File("../app/src/main/java/com/comunidapp/app/data/repository/SupabaseAuthRepository.kt"),
            File("app/src/main/java/com/comunidapp/app/data/repository/SupabaseAuthRepository.kt")
        )
        val found = candidates.firstOrNull { it.exists() }
        assertNotNull("SupabaseAuthRepository.kt must exist", found)
        val text = found!!.readText()
        assertTrue(!text.contains("ObservabilityEventCatalog"))
        assertTrue(!text.contains("m07.event"))
    }
}
