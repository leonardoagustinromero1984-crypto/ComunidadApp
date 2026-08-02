package com.comunidapp.app.domain.m17

import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.MockM17DonationRepository
import com.comunidapp.app.data.repository.MockM17InKindRepository
import com.comunidapp.app.data.repository.SupabaseM17DonationRepository
import com.comunidapp.app.data.repository.SupabaseM17InKindRepository
import com.comunidapp.app.data.repository.SupabaseM17TransparencyRepository
import com.comunidapp.app.data.repository.SupabaseM17VolunteerRepository
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M17 Bloque 5 — guards de cierre remoto (estructura, sin dispositivo). */
class M17RemoteClosureGuardsTest {

    private fun readDataProvider(): String {
        val candidates = listOf(
            File("app/src/main/java/com/comunidapp/app/data/provider/DataProvider.kt"),
            File("../app/src/main/java/com/comunidapp/app/data/provider/DataProvider.kt")
        )
        return candidates.first { it.isFile }.readText()
    }

    @Test
    fun dataProviderBranchesOnUseSupabase() {
        val src = readDataProvider()
        assertTrue(src.contains("useSupabase"))
        assertTrue(src.contains("SupabaseM17DonationRepository"))
        assertTrue(src.contains("SupabaseM17InKindRepository"))
        assertTrue(src.contains("MockM17DonationRepository"))
    }

    @Test
    fun supabaseRepositoryTypesExist() {
        assertNotNull(SupabaseM17DonationRepository(actorUserId = { "u1" }))
        assertNotNull(SupabaseM17InKindRepository(actorUserId = { "u1" }))
        assertNotNull(SupabaseM17VolunteerRepository(actorUserId = { "u1" }))
        assertNotNull(SupabaseM17TransparencyRepository())
    }

    @Test
    fun mockRepositoriesStillOperative() {
        assertNotNull(MockM17DonationRepository(actorUserId = { "mock_user_admin" }))
        assertNotNull(MockM17InKindRepository(actorUserId = { "mock_user_admin" }))
    }

    @Test
    fun dataProviderUseSupabaseIsBoolean() {
        assertTrue(DataProvider.useSupabase is Boolean)
    }
}
