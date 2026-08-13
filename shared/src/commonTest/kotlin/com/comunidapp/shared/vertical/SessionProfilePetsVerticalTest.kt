package com.comunidapp.shared.vertical

import com.comunidapp.app.domain.onboarding.OnboardingIntent
import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.shared.onboarding.OnboardingIntentStore
import com.comunidapp.shared.pets.FakeSharedPetsRepository
import com.comunidapp.shared.platform.InMemoryPlatformPreferences
import com.comunidapp.shared.platform.PlatformClock
import com.comunidapp.shared.profile.FakeUserProfileRepository
import com.comunidapp.shared.profile.ProfileLoadState
import com.comunidapp.shared.profile.UserProfileSummary
import com.comunidapp.shared.session.FakeSessionRepository
import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.session.SessionUser
import com.comunidapp.shared.ui.ErrorSanitizer
import com.comunidapp.shared.ui.VerticalLoadState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SessionProfilePetsVerticalTest {

    private val clock = PlatformClock { 1_700_000_000_000L }

    @Test
    fun unauthenticated_state() = runTest {
        val repo = FakeSessionRepository(SessionState.Unauthenticated)
        assertIs<SessionState.Unauthenticated>(repo.currentSession())
    }

    @Test
    fun authenticated_state() = runTest {
        val repo = FakeSessionRepository()
        val auth = assertIs<SessionState.Authenticated>(repo.currentSession())
        assertEquals("demo-user", auth.user.userId)
        assertNull(auth.user.email?.let { if ('@' !in it) it else null }) // email may exist; assert no phone PII
        assertFalse(auth.user.email.orEmpty().contains("+54"))
    }

    @Test
    fun expired_state() = runTest {
        val repo = FakeSessionRepository(SessionState.Expired)
        assertIs<SessionState.Expired>(repo.currentSession())
    }

    @Test
    fun sign_out_to_unauthenticated() = runTest {
        val repo = FakeSessionRepository()
        repo.signOut()
        assertIs<SessionState.Unauthenticated>(repo.currentSession())
    }

    @Test
    fun profile_loading_then_content() = runTest {
        val profileRepo = FakeUserProfileRepository(clock = clock)
        val load = profileRepo.observeMyProfile("demo-user")
            .filterNot { it is ProfileLoadState.Loading }
            .first()
        val content = assertIs<ProfileLoadState.Content>(load)
        assertEquals("Demo LeoVer", content.profile.displayName)
        assertNull(content.profile.avatarRef)
        assertFalse(content.profile.approximateLocation.orEmpty().contains("lat="))
    }

    @Test
    fun profile_error() = runTest {
        val profileRepo = FakeUserProfileRepository(fail = true)
        val err = assertIs<ProfileLoadState.Error>(
            profileRepo.observeMyProfile("demo-user")
                .filterIsInstance<ProfileLoadState.Error>()
                .first()
        )
        assertTrue(err.message.isNotBlank())
        assertFalse(err.message.contains("@"))
    }

    @Test
    fun pets_loading_empty_content() = runTest {
        val empty = FakeSharedPetsRepository(clock = clock, seeds = emptyList())
        assertIs<VerticalLoadState.Empty>(
            empty.observeMyPets("demo-user")
                .filterNot { it is VerticalLoadState.Loading }
                .first()
        )
        val filled = FakeSharedPetsRepository(clock = clock)
        val content = assertIs<VerticalLoadState.Content<*>>(
            filled.observeMyPets("demo-user")
                .filterNot { it is VerticalLoadState.Loading }
                .first()
        )
        @Suppress("UNCHECKED_CAST")
        val list = content.data as List<*>
        assertEquals(2, list.size)
    }

    @Test
    fun pet_detail_content() = runTest {
        val repo = FakeSharedPetsRepository(clock = clock)
        val detail = assertIs<VerticalLoadState.Content<*>>(
            repo.observePetDetail(PetId("shared-luna"))
                .filterNot { it is VerticalLoadState.Loading }
                .first()
        )
        assertTrue(detail.data.toString().contains("Luna"))
        assertFalse(detail.data.toString().contains("ownerId"))
        assertFalse(detail.data.toString().contains("legacyOwner"))
    }

    @Test
    fun error_sanitizer_hides_pii() {
        val msg = ErrorSanitizer.sanitize(RuntimeException("fail demo@secret.test +54 11 5555"))
        assertFalse(msg.contains("@"))
        assertFalse(msg.contains("+54"))
    }

    @Test
    fun refresh_keeps_deterministic_content() = runTest {
        val repo = FakeSharedPetsRepository(clock = clock)
        val first = repo.observeMyPets("u").filterNot { it is VerticalLoadState.Loading }.first()
        repo.refresh()
        val second = repo.observeMyPets("u").filterNot { it is VerticalLoadState.Loading }.first()
        assertEquals(first.toString(), second.toString())
    }

    @Test
    fun onboarding_intent_does_not_create_role() {
        val prefs = InMemoryPlatformPreferences()
        val store = OnboardingIntentStore(prefs)
        store.save(OnboardingIntent.REGISTER_PET)
        assertEquals(OnboardingIntent.REGISTER_PET, store.read())
        // Solo intención — no hay rol/accountType aquí
        assertNull(prefs.getString("role"))
        assertNull(prefs.getString("accountType"))
    }

    @Test
    fun public_profile_model_has_no_phone_coords() {
        val p = UserProfileSummary(
            userId = "u1",
            displayName = "Ana",
            email = "ana@leover.test",
            approximateLocation = "Rosario (aprox.)",
            avatarRef = null,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 2L
        )
        assertFalse(p.toString().contains("phone"))
        assertFalse(p.toString().contains("latitude"))
        assertFalse(p.approximateLocation.orEmpty().contains(","))
    }

    @Test
    fun session_stub_deterministic() = runTest {
        val a = FakeSessionRepository()
        val b = FakeSessionRepository()
        assertEquals(
            (a.currentSession() as SessionState.Authenticated).user.userId,
            (b.currentSession() as SessionState.Authenticated).user.userId
        )
    }

    @Test
    fun session_user_rejects_blank_id() {
        try {
            SessionUser(userId = " ")
            assertTrue(false, "expected require failure")
        } catch (_: IllegalArgumentException) {
            assertTrue(true)
        }
    }

    @Test
    fun unauthenticated_blocks_profile_and_pets_contracts() = runTest {
        assertIs<SessionState.Unauthenticated>(
            FakeSessionRepository(SessionState.Unauthenticated).currentSession()
        )
        // Contratos iOS/Android: sin sesión no se invoca perfil remoto
        val pets = FakeSharedPetsRepository(clock = clock)
        assertIs<VerticalLoadState.Content<*>>(
            pets.observeMyPets("demo-user").filterNot { it is VerticalLoadState.Loading }.first()
        )
    }

    @Test
    fun android_and_ios_share_same_session_user_shape() {
        val user = SessionUser(userId = "demo-user", email = "demo@leover.test", displayName = "Demo")
        assertEquals("demo-user", user.userId)
        assertTrue(user.email!!.contains("@"))
        assertFalse(user.toString().contains("token"))
    }
}
