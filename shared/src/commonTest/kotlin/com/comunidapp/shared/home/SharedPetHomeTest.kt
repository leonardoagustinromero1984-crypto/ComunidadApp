package com.comunidapp.shared.home

import com.comunidapp.shared.platform.PlatformClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SharedPetHomeTest {
    @Test
    fun content_from_fake_repository() = runTest {
        val repo = FakeSharedPetHomeRepository(clock = PlatformClock { 1L })
        val content = assertIs<SharedHomeLoadState.Content>(
            repo.observePets().filterNot { it is SharedHomeLoadState.Loading }.first()
        )
        assertEquals(2, content.pets.size)
        assertTrue(content.pets.any { it.displayName == "Luna" })
    }

    @Test
    fun empty_state() = runTest {
        val repo = FakeSharedPetHomeRepository(seed = emptyList())
        assertIs<SharedHomeLoadState.Empty>(
            repo.observePets().filterNot { it is SharedHomeLoadState.Loading }.first()
        )
    }

    @Test
    fun error_state() = runTest {
        val repo = FakeSharedPetHomeRepository(fail = true)
        val err = assertIs<SharedHomeLoadState.Error>(
            repo.observePets().filterNot { it is SharedHomeLoadState.Loading }.first()
        )
        assertEquals("SHARED_HOME_UNAVAILABLE", err.message)
    }

    @Test
    fun session_stub() {
        assertTrue(SharedSessionStub.demoAuthenticated().isAuthenticated)
        assertTrue(!SharedSessionStub.guest().isAuthenticated)
    }
}
