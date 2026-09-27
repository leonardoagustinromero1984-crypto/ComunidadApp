package com.comunidapp.app.domain.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingSocialPublishTest {

    @Test
    fun cancelOnlyBeforePublishing() {
        val preparing = sample(PendingSocialPublishState.PREPARING)
        val uploading = sample(PendingSocialPublishState.UPLOADING)
        val publishing = sample(PendingSocialPublishState.PUBLISHING)
        assertTrue(preparing.canCancel)
        assertTrue(uploading.canCancel)
        assertFalse(publishing.canCancel)
        assertTrue(preparing.shouldKeepFiles)
        assertFalse(sample(PendingSocialPublishState.SUCCESS).shouldKeepFiles)
        assertFalse(sample(PendingSocialPublishState.CANCELLED).shouldKeepFiles)
    }

    @Test
    fun retryKeepsOutputExceptFileTooLarge() {
        val network = sample(PendingSocialPublishState.FAILED).copy(
            errorCategory = PendingSocialPublishErrors.UPLOAD_FAILED
        )
        val tooLarge = sample(PendingSocialPublishState.FAILED).copy(
            errorCategory = PendingSocialPublishErrors.FILE_TOO_LARGE
        )
        assertTrue(network.canRetry)
        assertTrue(network.shouldKeepFiles)
        assertFalse(tooLarge.canRetry)
        assertFalse(tooLarge.shouldKeepFiles)
    }

    @Test
    fun createUncertainDoesNotAllowBlindSecondCreate() {
        val uncertain = sample(PendingSocialPublishState.FAILED).copy(
            createStarted = true,
            createUncertain = true,
            registerCompleted = true,
            uploadCompleted = true,
            assetId = "asset-1",
            errorCategory = PendingSocialPublishErrors.CREATE_FAILED
        )
        assertTrue(uncertain.canRetry)
        assertTrue(uncertain.createUncertain)
        assertEquals("asset-1", uncertain.assetId)
    }

    @Test
    fun jobNeverStoresSecrets() {
        val raw = sample(PendingSocialPublishState.UPLOADING).toString()
        assertFalse(raw.contains("JWT", ignoreCase = true))
        assertFalse(raw.contains("service_role", ignoreCase = true))
        assertFalse(raw.contains("signed", ignoreCase = true))
    }

    private fun sample(state: PendingSocialPublishState) = PendingSocialPublish(
        jobId = "job-1",
        state = state,
        actorUserId = "user-1",
        caption = "Reel"
    )
}
