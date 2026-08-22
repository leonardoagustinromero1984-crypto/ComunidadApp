package com.comunidapp.app.domain.foster

import com.comunidapp.app.data.model.FosterPlacement
import com.comunidapp.app.data.model.FosterPlacementStatus
import com.comunidapp.app.data.repository.M10FosterMemoryStore
import com.comunidapp.app.data.repository.MockFosterPlacementRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FosterDirectPlacementTest {

    @Test
    fun FOSTER_DIRECT_PLACEMENT_PERSISTS_IF_SUPPORTED() = runTest {
        val store = M10FosterMemoryStore()
        val repo = MockFosterPlacementRepository(actorUserId = { "foster-1" }, store = store)
        val created = repo.createDirectPlacement("pet-1", 1_000L, null).getOrThrow()
        assertEquals("pet-1", created.petId)
        assertEquals(FosterPlacementStatus.ACTIVE, created.status)
        val active = repo.observeActivePlacementsForUser("foster-1").first()
        assertEquals(1, active.size)
        assertFalse(active.single().petId.isBlank())
    }

    @Test
    fun FOSTER_DOES_NOT_DUPLICATE_PET() {
        val placement = FosterPlacement(
            id = "pl-1",
            fosterRequestId = "",
            fosterHomeId = "foster-1",
            petId = "pet-1",
            petName = "Luna",
            fosterUserId = "foster-1",
            status = FosterPlacementStatus.ACTIVE,
            startedAt = 1L,
            vitacoraAccessGranted = false
        )
        assertEquals("pet-1", placement.petId)
        assertTrue(placement.fosterRequestId.isEmpty())
    }
}
