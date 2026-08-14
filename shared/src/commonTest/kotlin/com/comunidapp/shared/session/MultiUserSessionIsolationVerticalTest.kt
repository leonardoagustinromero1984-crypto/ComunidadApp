package com.comunidapp.shared.session

import com.comunidapp.shared.auth.FakeAuthSessionGateway
import com.comunidapp.shared.auth.GatewayAuthRepository
import com.comunidapp.shared.auth.SignInRequest
import com.comunidapp.shared.deeplink.DeepLinkPendingStore
import com.comunidapp.shared.deeplink.DeepLinkTarget
import com.comunidapp.shared.media.CachingMediaResolver
import com.comunidapp.shared.media.FakeM05MediaReadGateway
import com.comunidapp.shared.media.MediaRef
import com.comunidapp.shared.media.MediaResolveResult
import com.comunidapp.shared.media.MediaResource
import com.comunidapp.shared.vertical.SessionViewModelShared
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class MultiUserSessionIsolationVerticalTest {

    private val sampleAssetId = "11111111-1111-1111-1111-111111111111"

    @BeforeTest
    fun clearPending() {
        DeepLinkPendingStore.clear()
    }

    @Test
    fun deep_link_pending_cleared_on_sign_out() = runTest {
        val testScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val gateway = FakeAuthSessionGateway(
            SessionState.Authenticated(SessionUser("user-a", "a@leover.test", "Ana"))
        )
        val repo = GatewayAuthRepository(gateway)
        val vm = SessionViewModelShared(sessionRepository = repo, scope = testScope)
        DeepLinkPendingStore.set(DeepLinkTarget.PetPublic("PUB-A"))
        assertEquals("PUB-A", assertIs<DeepLinkTarget.PetPublic>(DeepLinkPendingStore.peek()!!).publicCode)

        vm.signOut()

        assertNull(DeepLinkPendingStore.peek())
        assertIs<SessionState.Unauthenticated>(repo.currentSession())
        testScope.cancel()
    }

    @Test
    fun media_cache_cleared_on_unauthenticated_private_resolve() = runTest {
        val gw = FakeM05MediaReadGateway()
        gw.assetResults[sampleAssetId] = MediaResolveResult.Success(
            MediaResource(bytes = ByteArray(4) { 1 }, cacheKey = sampleAssetId, expiresAtEpochMs = 9_999_999_999L)
        )
        var authed = true
        val resolver = CachingMediaResolver(
            gateway = gw,
            clock = { 1_000L },
            checkAuthenticated = { authed }
        )
        assertIs<MediaResolveResult.Success>(resolver.resolve(MediaRef.Asset(sampleAssetId)))
        assertEquals(1, resolver.cachedCountForTests())

        authed = false
        resolver.clearCache()
        assertEquals(0, resolver.cachedCountForTests())
        assertEquals(
            MediaResolveResult.Unauthenticated,
            resolver.resolve(MediaRef.Asset(sampleAssetId))
        )
        assertEquals(0, resolver.cachedCountForTests())
    }

    @Test
    fun session_a_sign_out_then_b_sign_in_does_not_leak_user_a() = runTest {
        val gateway = FakeAuthSessionGateway(
            SessionState.Authenticated(SessionUser("user-a", "a@leover.test", "Ana"))
        )
        val repo = GatewayAuthRepository(gateway)
        val before = assertIs<SessionState.Authenticated>(repo.currentSession())
        assertEquals("user-a", before.user.userId)

        repo.signOut()
        assertIs<SessionState.Unauthenticated>(repo.currentSession())

        val result = repo.signInWithEmailPassword(SignInRequest("b@leover.test", "secret"))
        assertIs<com.comunidapp.shared.auth.AuthResult.Success>(result)
        val after = assertIs<SessionState.Authenticated>(repo.currentSession())
        assertTrue(after.user.userId != "user-a")
        assertEquals("b@leover.test", after.user.email)
        assertTrue(after.user.displayName != "Ana")
    }
}
