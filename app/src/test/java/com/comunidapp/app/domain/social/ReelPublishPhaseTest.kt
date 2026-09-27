package com.comunidapp.app.domain.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelPublishPhaseTest {

    @Test
    fun createSuccessAndAttachSuccessReachesDone() {
        val done = base().copy(
            uploadCompleted = true,
            registerCompleted = true,
            assetId = "asset-1",
            backendPostId = "post-1",
            createCompleted = true,
            petsAttached = true,
            vitaCoraResolved = true,
            petIds = listOf("pet-1"),
            state = PendingSocialPublishState.SUCCESS
        )
        assertEquals(ReelPublishPhase.DONE, done.nextPhase())
        assertTrue(done.reelCreated)
    }

    @Test
    fun createSuccessAttachFailResumesAttachWithoutRecreate() {
        val failed = base().copy(
            uploadCompleted = true,
            registerCompleted = true,
            assetId = "asset-1",
            backendPostId = "post-1",
            createCompleted = true,
            petsAttached = false,
            petIds = listOf("pet-1"),
            state = PendingSocialPublishState.FAILED,
            errorCategory = PendingSocialPublishErrors.LINK_FAILED
        )
        assertEquals(ReelPublishPhase.ATTACH_PETS, failed.nextPhase())
        assertTrue(failed.reelCreated)
        assertTrue(failed.canRetry)
        assertEquals(ReelPublishPhase.CREATE, failed.copy(backendPostId = null, createCompleted = false).nextPhase())
    }

    @Test
    fun createSuccessVitaCoraFailResumesVitaCora() {
        val failed = base().copy(
            uploadCompleted = true,
            registerCompleted = true,
            assetId = "asset-1",
            backendPostId = "post-1",
            createCompleted = true,
            petsAttached = true,
            vitaCoraResolved = false,
            petIds = listOf("pet-1"),
            state = PendingSocialPublishState.FAILED,
            errorCategory = PendingSocialPublishErrors.LINK_FAILED
        )
        assertEquals(ReelPublishPhase.VITACORA, failed.nextPhase())
        assertEquals(
            "El Reel se publicó, pero no pudimos terminar de vincularlo con la mascota.",
            PendingSocialPublishErrors.bannerMessage(failed.errorCategory, failed.reelCreated)
        )
    }

    @Test
    fun retryIsIdempotentOnSameJobAssetAndPost() {
        val first = resumeAfterCreate()
        val second = resumeAfterCreate()
        assertEquals(first.jobId, second.jobId)
        assertEquals(first.assetId, second.assetId)
        assertEquals(first.backendPostId, second.backendPostId)
        assertEquals(ReelPublishPhase.VITACORA, first.nextPhase())
        assertEquals(ReelPublishPhase.VITACORA, second.nextPhase())
    }

    @Test
    fun restartResumesSameIncompletePhase() {
        val stored = resumeAfterCreate()
        assertTrue(stored.shouldKeepForResume)
        assertEquals(ReelPublishPhase.VITACORA, stored.nextPhase())
    }

    @Test
    fun accountSwitchNeverShowsForeignJob() {
        val job = resumeAfterCreate()
        assertTrue(job.belongsTo("user-a"))
        assertFalse(job.belongsTo("user-b"))
        assertFalse(job.belongsTo(null))
    }

    @Test
    fun reelWithoutPetSkipsVitacora() {
        val noPet = base().copy(
            uploadCompleted = true,
            registerCompleted = true,
            assetId = "asset-1",
            backendPostId = "post-1",
            createCompleted = true,
            petIds = emptyList()
        )
        assertEquals(ReelPublishPhase.DONE, noPet.nextPhase())
    }

    @Test
    fun publishedFailureIsNeverGenericUpload() {
        val category = PendingSocialPublishErrors.fromBlob(
            "REEL_MEDIA_ASSOCIATION_NOT_VISIBLE",
            reelAlreadyCreated = true
        )
        assertEquals(PendingSocialPublishErrors.LINK_FAILED, category)
        assertFalse(category == PendingSocialPublishErrors.UPLOAD_FAILED)
    }

    private fun resumeAfterCreate() = base().copy(
        uploadCompleted = true,
        registerCompleted = true,
        assetId = "asset-1",
        backendPostId = "post-1",
        createCompleted = true,
        petsAttached = true,
        vitaCoraResolved = false,
        petIds = listOf("pet-1"),
        state = PendingSocialPublishState.FAILED,
        errorCategory = PendingSocialPublishErrors.LINK_FAILED
    )

    private fun base() = PendingSocialPublish(
        jobId = "job-1",
        actorUserId = "user-a",
        caption = "Clip"
    )
}
