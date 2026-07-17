package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.NotificationType
import com.comunidapp.app.data.remote.supabase.PlatformSupabaseDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class M06AndroidNotificationClientBoundaryTest {

    @Test
    fun `Supabase platform repository no longer creates client notifications`() = runTest {
        val result = PlatformSupabaseDataSource().createNotification(
            userId = "target-user",
            type = NotificationType.FRIEND_REQUEST,
            title = "Solicitud",
            body = "Body",
            relatedId = "friendship-1",
            relatedType = "friendship"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("CLIENT_INSERT_DENIED"))
    }

    @Test
    fun `Android client cannot enqueue outbox`() = runTest {
        val repo = ClientDeniedNotificationOutboxRepository()

        val claimed = repo.claimNext(Instant.parse("2026-07-16T00:00:00Z"))

        assertTrue(claimed is com.comunidapp.app.core.result.AppResult.Failure)
    }

    @Test
    fun `Android client cannot write deliveries`() = runTest {
        val repo = ClientDeniedNotificationDeliveryRepository()

        val delivered = repo.markDelivered("delivery-1", Instant.parse("2026-07-16T00:00:00Z"))
        val failed = repo.markFailure("delivery-1", "TRANSIENT", Instant.parse("2026-07-16T00:00:00Z"))

        assertTrue(delivered is com.comunidapp.app.core.result.AppResult.Failure)
        assertTrue(failed is com.comunidapp.app.core.result.AppResult.Failure)
    }

    @Test
    fun `NotificationDispatcher logs denied result instead of receiving success`() = runTest {
        val result = PlatformSupabaseDataSource().createNotification(
            userId = "other-user",
            type = NotificationType.CHAT_MESSAGE,
            title = "Chat",
            body = "Mensaje",
            relatedId = "conversation-1",
            relatedType = "chat"
        )

        assertFalse(result.isSuccess)
    }
}
