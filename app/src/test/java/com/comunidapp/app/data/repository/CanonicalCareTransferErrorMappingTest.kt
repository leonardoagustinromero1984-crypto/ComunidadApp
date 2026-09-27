package com.comunidapp.app.data.repository

import com.comunidapp.app.data.remote.supabase.m08.M08PetException
import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalCareTransferErrorMappingTest {

    @Test
    fun sharedNotFoundGetsOperationSpecificCode() {
        val backend = IllegalStateException("Postgrest error: NOT_FOUND")

        assertEquals(
            "PET_NOT_FOUND",
            canonicalCareTransferErrorCode(backend, missingCode = "PET_NOT_FOUND")
        )
        assertEquals(
            "PET_TRANSFER_NOT_FOUND",
            canonicalCareTransferErrorCode(backend, missingCode = "PET_TRANSFER_NOT_FOUND")
        )
    }

    @Test
    fun stableTransferCodePassesThroughUnchanged() {
        val backend = M08PetException(
            code = "PET_TRANSFER_SOURCE_STALE",
            message = "PET_TRANSFER_SOURCE_STALE"
        )

        assertEquals(
            "PET_TRANSFER_SOURCE_STALE",
            canonicalCareTransferErrorCode(backend, missingCode = "PET_TRANSFER_NOT_FOUND")
        )
    }
}
