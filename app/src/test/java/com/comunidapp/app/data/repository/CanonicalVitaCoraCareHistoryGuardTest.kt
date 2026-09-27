package com.comunidapp.app.data.repository

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CanonicalVitaCoraCareHistoryGuardTest {

    @Test
    fun projectionIncludesAcceptedCareEvents() {
        val repo = File(
            "src/main/java/com/comunidapp/app/data/repository/CanonicalVitaCoraProjectionRepository.kt"
        ).readText()
        assertTrue(repo.contains("CARE_TRANSFER"))
        assertTrue(repo.contains("CARE_CREATED"))
    }
}
