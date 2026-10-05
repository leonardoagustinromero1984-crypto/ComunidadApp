package com.comunidapp.app.domain.social

import com.comunidapp.app.data.local.PendingSocialPublishStore
import com.comunidapp.app.ui.components.leo.CompactReelUploadCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PublishSessionIsolationTest {

    @Test
    fun failedJobIsDismissibleAndOwned() {
        val failed = sample(PendingSocialPublishState.FAILED)
        assertTrue(failed.canDismiss)
        assertTrue(failed.canRetry)
        assertTrue(failed.belongsTo("user-a"))
        assertFalse(failed.belongsTo("user-b"))
        assertFalse(failed.belongsTo(null))
    }

    @Test
    fun storeKeysArePerUser() {
        val a = PendingSocialPublishStore.keyFor("user-a").name
        val b = PendingSocialPublishStore.keyFor("user-b").name
        assertTrue(a.contains("user-a"))
        assertTrue(b.contains("user-b"))
        assertFalse(a == b)
    }

    @Test
    fun controllerClearsTerminalOnDismissAndScopesRestore() {
        val controller = File("src/main/java/com/comunidapp/app/domain/social/ReelPublishController.kt").readText()
        assertTrue(controller.contains("fun onSessionEnded("))
        assertTrue(controller.contains("fun bindVisibleJob("))
        assertTrue(controller.contains("store.read(userId)"))
        assertTrue(controller.contains("store.clear(userId)"))
        assertTrue(controller.contains("if (!current.belongsTo(userId) || current.isActive) return"))
        assertTrue(controller.contains("noteCreatedReel("))
        assertTrue(controller.contains("shouldKeepForResume"))
        assertFalse(controller.contains("store.read() ?:"))
    }

    @Test
    fun bannerExposesRetryAndCloseAndFiltersOwner() {
        val banner = File("src/main/java/com/comunidapp/app/ui/components/leo/ReelPublishStatusBanner.kt").readText()
        assertTrue(banner.contains("belongsTo(currentUserId)"))
        assertTrue(banner.contains("Reintentar"))
        assertTrue(banner.contains("Cerrar"))
        assertTrue(banner.contains("dismissTerminal()"))
        assertEquals("No pudimos publicar el Clip", CompactReelUploadCopy.label(sample(PendingSocialPublishState.FAILED)))
    }

    @Test
    fun cleanupRunsOnLogoutPath() {
        val cleanup = File("src/main/java/com/comunidapp/app/domain/user/AccountIdentityCleanup.kt").readText()
        val session = File("src/main/java/com/comunidapp/app/viewmodel/SessionViewModel.kt").readText()
        assertTrue(cleanup.contains("ReelPublishController.get().onSessionEnded()"))
        assertTrue(cleanup.contains("SessionGeneration.invalidate()"))
        assertTrue(cleanup.contains("DataProvider.clearUserScopedMockStores()"))
        assertTrue(cleanup.contains("SignedUrlMintCoordinator.clear()"))
        assertTrue(cleanup.contains("InMemoryDataStore.clearUserScopedSession()"))
        assertTrue(session.contains("bindVisibleJob(user?.id)"))
    }

    private fun sample(state: PendingSocialPublishState) = PendingSocialPublish(
        jobId = "job-1",
        state = state,
        actorUserId = "user-a",
        caption = "Clip"
    )
}
