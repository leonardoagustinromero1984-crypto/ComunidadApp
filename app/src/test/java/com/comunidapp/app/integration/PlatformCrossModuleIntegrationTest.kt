package com.comunidapp.app.integration

import com.comunidapp.app.data.model.*
import com.comunidapp.app.data.repository.*
import com.comunidapp.app.domain.files.FileAsset
import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileAssetStatus
import com.comunidapp.app.domain.files.FileAssetVisibility
import com.comunidapp.app.domain.files.authorization.FileAccessDecision
import com.comunidapp.app.domain.files.authorization.FileAuthContext
import com.comunidapp.app.domain.files.authorization.FileAuthorization
import com.comunidapp.app.domain.m21.M21ReputationResilience
import com.comunidapp.app.domain.m25.M25OrderOperationsService
import com.comunidapp.app.domain.m27.M27PrivacySanitizer
import com.comunidapp.app.navigation.NavRoutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * RC1 — pruebas transversales focalizadas M00–M27 (mocks deterministas, sin red).
 */
class PlatformCrossModuleIntegrationTest {

    // 1–2 Login → Comunidad (rutas de sesión y shell principal)
    @Test fun loginToComunidadRoutesExist() {
        assertEquals("login", NavRoutes.LOGIN)
        assertEquals("main", NavRoutes.MAIN)
        assertEquals("comunidad", NavRoutes.COMUNIDAD)
        assertTrue(NavRoutes.M19_FEED.startsWith("m19/"))
    }

    // 3 Comunidad → adopción vía Sumate
    @Test fun sumateToAdoptionDetailRoute() {
        assertEquals("sumate", NavRoutes.SUMATE)
        assertTrue(NavRoutes.adoptionDetail("adopt-1").contains("adopt-1"))
        assertEquals("my_adoption_applications", NavRoutes.MY_ADOPTION_APPLICATIONS)
    }

    // 4 Refugio → donaciones sin pagos
    @Test fun shelterToDonationHubRoutes() {
        assertTrue(NavRoutes.M16_SHELTERS.startsWith("m16/"))
        assertEquals("m17/hub", NavRoutes.M17_HUB)
        assertFalse(NavRoutes.M17_HUB.contains("payment"))
    }

    // 5 Evento → comunidad
    @Test fun eventToCommunityRoutes() {
        assertEquals("m18/events", NavRoutes.M18_EVENTS)
        assertEquals("m19/feed", NavRoutes.M19_FEED)
    }

    // 6 Publicación → mensajería
    @Test fun socialToMessagingRoutes() {
        assertTrue(NavRoutes.M19_POSTS_CREATE.contains("create"))
        assertEquals("m20/inbox", NavRoutes.M20_INBOX)
        assertTrue(NavRoutes.m20Thread("conv-1").contains("conv-1"))
    }

    // 7 Servicio → reserva
    @Test fun providerToBookingRoutes() {
        assertEquals("m22/hub", NavRoutes.M22_HUB)
        assertEquals("m23/home", NavRoutes.M23_HOME)
        assertTrue(NavRoutes.m23BookingDetail("b1").contains("b1"))
    }

    // 8 Reserva completada → elegibilidad M21
    @Test fun completedBookingEligibleForM21Review() = runBlocking {
        val store = M23SchedulingMemoryStore().also {
            it.seedDefaults(Clock.fixed(Instant.parse("2029-06-01T12:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires")))
        }
        val booking = store.bookings.value.first { it.id == M23MockBookingIds.COMPLETED }
        assertEquals(M23BookingStatus.COMPLETED, booking.status)
        val record = M21EligibilityAdapter.findCompletedInteraction(
            reviewerUserId = M21MockUsers.ADMIN,
            subject = M21ReviewSubjectReference(
                M21ReviewTargetType.SERVICE,
                M21MockTargetIds.SERVICE,
                "Turno veterinario"
            ),
            contextId = M21MockEligibilityIds.SERVICE_COMPLETED
        )
        assertNotNull(record)
        assertFalse(record!!.cancelled)
    }

    // 9 Marketplace → pedido sin pago
    @Test fun marketplaceOrderWithoutPaymentFlow() = runBlocking {
        val store = M25MarketplaceMemoryStore().also { it.seedDefaults() }
        val order = store.orders.value.first { it.id == M25MockOrderIds.SUBMITTED }
        assertFalse(order.status.name.contains("PAID", ignoreCase = true))
        assertFalse(order.status.name.contains("REFUND", ignoreCase = true))
    }

    // 10 Pedido entregado → transición válida (elegibilidad operativa M21 vía contexto servicio)
    @Test fun deliveredOrderSupportsPostDeliveryActions() {
        assertNull(
            M25OrderOperationsService.validateOrderTransition(
                M25OrderStatus.DELIVERED,
                M25OrderStatus.RETURN_REQUESTED
            )
        )
    }

    // 11 IA → entidad pública autorizada
    @Test fun aiPublicResultHasNoPii() = runBlocking {
        val repo = MockM26AiRepository({ M26MockUsers.MEMBER }, M26AiMemoryStore())
        val match = repo.observeVisualMatches().first().first()
        assertFalse(match.toString().contains("mock_user"))
    }

    // 12 API M27 → modelo público sanitizado
    @Test fun m27PublicModelSanitized() = runBlocking {
        val repo = MockM27IntegrationRepository({ M27MockUsers.DEVELOPER }, M27IntegrationMemoryStore())
        val webhook = repo.observeWebhooks().first().first()
        assertFalse(webhook.toString().contains("mock_user"))
        assertFalse(M27PrivacySanitizer.scrubPublicText("api_key=secret123").contains("secret123"))
    }

    // 13 Moderación → contenido oculto no en feed
    @Test fun moderatedHiddenPostNotInFeed() = runBlocking {
        val store = M19SocialMemoryStore()
        val repo = MockM19SocialRepository({ "mock_user_admin" }, store)
        val hidden = store.posts.value.first { it.status == M19PostStatus.HIDDEN }
        val feed = repo.searchFeed(M19FeedFilter()).getOrThrow()
        assertTrue(feed.none { it.id == hidden.id })
    }

    // 14 Archivo privado → no público
    @Test fun privateFileNotPublicWithoutOwner() {
        val asset = FileAsset(
            id = "private-1",
            owner = FileAssetOwner.User("u1"),
            purpose = FileAssetPurpose.USER_AVATAR,
            visibility = FileAssetVisibility.OWNER_ONLY,
            status = FileAssetStatus.READY,
            createdByUserId = "u1",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L
        )
        val publicAttempt = FileAuthorization.canRead(FileAuthContext(actorUserId = "u2"), asset)
        assertNotEquals(FileAccessDecision.ALLOWED, publicAttempt)
        val ownerAttempt = FileAuthorization.canRead(FileAuthContext(actorUserId = "u1"), asset)
        assertEquals(FileAccessDecision.ALLOWED, ownerAttempt)
    }

    // 15 Usuario ajeno → permiso denegado (reserva M23)
    @Test fun foreignUserCannotConfirmBooking() = runBlocking {
        val clock = Clock.fixed(Instant.parse("2029-06-01T12:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"))
        val store = M23SchedulingMemoryStore().also { it.seedDefaults(clock) }
        val repo = MockM23BookingRepository({ M23MockUsers.UNAUTHORIZED }, store, clock)
        assertTrue(repo.confirm(M23MockBookingIds.REQUESTED).isFailure)
    }

    // 16 Organización ajena → permiso denegado (M27)
    @Test fun foreignOrgCannotManageIntegration() = runBlocking {
        val repo = MockM27IntegrationRepository({ M27MockUsers.OTHER }, M27IntegrationMemoryStore())
        assertTrue(repo.pauseIntegrationApp(M27MockIds.APP_ACTIVE).isFailure)
    }

    // 17 Dependencia caída → error controlado (M06 no bloquea M25)
    @Test fun optionalDependencyFailureDoesNotBlockMarketplace() = runBlocking {
        val repo = MockM25MarketplaceRepository({ M25MockUsers.MERCHANT }, M25MarketplaceMemoryStore().also { it.seedDefaults() })
        assertFalse(repo.observeNotificationsHook().first().available)
        assertTrue(repo.observeCatalog().first().isNotEmpty())
    }

    // 18 Datos parciales → UI estable (M21 error sin payload)
    @Test fun partialErrorDoesNotExposePayload() {
        val message = M21ReputationResilience.safeUserMessage("user_id=secret reviewer_user_id=abc")
        assertFalse(message.contains("secret"))
    }

    // 19 Estado terminal → no reabre (M23 rechazo)
    @Test fun terminalRejectedBookingCannotConfirm() = runBlocking {
        val clock = Clock.fixed(Instant.parse("2029-06-01T12:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"))
        val store = M23SchedulingMemoryStore().also { it.seedDefaults(clock) }
        val provider = MockM23BookingRepository({ M23MockUsers.PROVIDER }, store, clock)
        provider.reject(M23BookingRejectRequest(M23MockBookingIds.REQUESTED, "No disponible")).getOrThrow()
        assertTrue(provider.confirm(M23MockBookingIds.REQUESTED).isFailure)
    }

    // 20 M24 → no existe navegación activa
    @Test fun m24PaymentRoutesDoNotExist() {
        val routeFields = NavRoutes::class.java.declaredFields
            .filter { it.name.startsWith("M24") || it.name.contains("PAYMENT", ignoreCase = true) }
        assertTrue(routeFields.isEmpty())
    }

    @Test fun adoptionApplyPopUpUsesRegisteredSumateRoute() {
        // Regresión RC1: popUpTo debe apuntar a ruta registrada (SUMATE), no ADOPTIONS huérfana.
        assertEquals("sumate", NavRoutes.SUMATE)
        assertNotEquals(NavRoutes.SUMATE, NavRoutes.ADOPTIONS)
    }
}
