package com.comunidapp.shared.remote

import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.app.domain.pets.PetLifecycleStatus
import com.comunidapp.shared.auth.FakeAuthSessionGateway
import com.comunidapp.shared.auth.GatewayAuthRepository
import com.comunidapp.shared.auth.SharedSupabaseConfig
import com.comunidapp.shared.auth.UnconfiguredAuthSessionRepository
import com.comunidapp.shared.auth.usableOrNull
import com.comunidapp.shared.pets.FakeSharedPetsRepository
import com.comunidapp.shared.pets.RemoteSharedPetsRepository
import com.comunidapp.shared.pets.UnconfiguredSharedPetsRepository
import com.comunidapp.shared.profile.FakeUserProfileRepository
import com.comunidapp.shared.profile.ProfileLoadState
import com.comunidapp.shared.profile.RemoteUserProfileRepository
import com.comunidapp.shared.profile.UnconfiguredUserProfileRepository
import com.comunidapp.shared.session.SessionDataMode
import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.session.SessionUser
import com.comunidapp.shared.ui.VerticalLoadState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class ProfilePetsRemoteVerticalTest {

    private fun authRepo(userId: String = "user-1", email: String? = "a@leover.test") =
        GatewayAuthRepository(
            FakeAuthSessionGateway(
                SessionState.Authenticated(SessionUser(userId, email, "Ana"))
            )
        )

    @Test
    fun profile_remote_loading_then_content() = runTest {
        val gw = FakeProfileRemoteGateway(
            row = RemoteUserProfileRow(
                id = "user-1",
                email = "a@leover.test",
                displayName = "Ana LeoVer",
                city = "Palermo",
                province = "CABA",
                avatarPath = "avatars/u1.png",
                createdAt = "2024-01-01T00:00:00Z",
                updatedAt = "2024-06-01T00:00:00Z"
            )
        )
        val repo = RemoteUserProfileRepository(gw, authRepo())
        val state = repo.observeMyProfile("user-1")
            .filterNot { it is ProfileLoadState.Loading }
            .first()
        val content = assertIs<ProfileLoadState.Content>(state)
        assertEquals("Ana LeoVer", content.profile.displayName)
        assertEquals("a@leover.test", content.profile.email)
        assertEquals("Palermo, CABA", content.profile.approximateLocation)
        assertEquals("avatars/u1.png", content.profile.avatarRef)
    }

    @Test
    fun profile_network_error() = runTest {
        val gw = FakeProfileRemoteGateway(error = IllegalStateException("NETWORK timeout"))
        val repo = RemoteUserProfileRepository(gw, authRepo())
        val err = assertIs<ProfileLoadState.Error>(
            repo.observeMyProfile("user-1").filterNot { it is ProfileLoadState.Loading }.first()
        )
        assertTrue(err.message.contains("conexión", ignoreCase = true))
    }

    @Test
    fun profile_unauthorized_wrong_user() = runTest {
        val gw = FakeProfileRemoteGateway(
            row = RemoteUserProfileRow(id = "other", displayName = "X", name = "X")
        )
        val repo = RemoteUserProfileRepository(gw, authRepo("user-1"))
        val err = assertIs<ProfileLoadState.Error>(
            repo.observeMyProfile("other").filterNot { it is ProfileLoadState.Loading }.first()
        )
        assertTrue(err.message.contains("permiso", ignoreCase = true))
    }

    @Test
    fun profile_malformed_missing_row() = runTest {
        val gw = FakeProfileRemoteGateway(row = null)
        val repo = RemoteUserProfileRepository(gw, authRepo())
        assertIs<ProfileLoadState.Error>(
            repo.observeMyProfile("user-1").filterNot { it is ProfileLoadState.Loading }.first()
        )
    }

    @Test
    fun profile_mapping_safe_no_phone_fields() {
        val summary = RemoteProfileMapper.toSummary(
            RemoteUserProfileRow(
                id = "user-1",
                email = "a@leover.test",
                name = "Ana",
                locationText = "Zona norte"
            ),
            sessionEmail = null
        )
        assertFalse(summary.toString().contains("phone", ignoreCase = true))
        assertEquals("Zona norte", summary.approximateLocation)
    }

    @Test
    fun profile_email_prefers_row_then_session() {
        val fromRow = RemoteProfileMapper.toSummary(
            RemoteUserProfileRow(id = "u", email = "row@leover.test", name = "N"),
            sessionEmail = "session@leover.test"
        )
        assertEquals("row@leover.test", fromRow.email)
        val fromSession = RemoteProfileMapper.toSummary(
            RemoteUserProfileRow(id = "u", name = "N"),
            sessionEmail = "session@leover.test"
        )
        assertEquals("session@leover.test", fromSession.email)
    }

    @Test
    fun profile_config_unavailable_real_remote() = runTest {
        val repo = UnconfiguredUserProfileRepository()
        assertEquals(com.comunidapp.shared.profile.ProfileDataMode.REAL_REMOTE, repo.dataMode)
        val err = assertIs<ProfileLoadState.Error>(
            repo.observeMyProfile("x").filterNot { it is ProfileLoadState.Loading }.first()
        )
        assertTrue(err.message.isNotBlank())
    }

    @Test
    fun profile_data_mode_real_remote() {
        val repo = RemoteUserProfileRepository(FakeProfileRemoteGateway(), authRepo())
        assertEquals(com.comunidapp.shared.profile.ProfileDataMode.REAL_REMOTE, repo.dataMode)
    }

    @Test
    fun fake_profile_still_for_tests() = runTest {
        val fake = FakeUserProfileRepository()
        assertEquals(com.comunidapp.shared.profile.ProfileDataMode.SHARED_FAKE, fake.dataMode)
        assertIs<ProfileLoadState.Content>(
            fake.observeMyProfile("demo-user").filterNot { it is ProfileLoadState.Loading }.first()
        )
    }

    @Test
    fun pets_loading_empty() = runTest {
        val gw = FakePetsRemoteGateway(list = emptyList())
        val repo = RemoteSharedPetsRepository(gw, authRepo())
        assertIs<VerticalLoadState.Empty>(
            repo.observeMyPets("user-1").filterNot { it is VerticalLoadState.Loading }.first()
        )
    }

    @Test
    fun pets_loading_content() = runTest {
        val gw = FakePetsRemoteGateway(
            list = listOf(
                RemoteAccessiblePetRow(
                    id = "pet-1",
                    name = "Luna",
                    species = "Perro",
                    sex = "Hembra",
                    status = "ACTIVE",
                    photoUrl = "https://cdn.example/luna.jpg",
                    ownerId = "user-1"
                )
            )
        )
        val repo = RemoteSharedPetsRepository(gw, authRepo())
        val content = assertIs<VerticalLoadState.Content<*>>(
            repo.observeMyPets("user-1").filterNot { it is VerticalLoadState.Loading }.first()
        )
        @Suppress("UNCHECKED_CAST")
        val items = content.data as List<com.comunidapp.shared.pets.PetSummary>
        assertEquals(1, items.size)
        assertEquals("Luna", items.first().displayName)
        assertTrue(items.first().hasAvatar)
        assertFalse(items.first().toString().contains("ownerId"))
    }

    @Test
    fun pet_detail_content() = runTest {
        val gw = FakePetsRemoteGateway(
            detail = RemotePetRow(
                id = "pet-1",
                name = "Luna",
                species = "Perro",
                sex = "Hembra",
                breed = "Mestiza",
                status = "ACTIVE",
                ownerId = "SECRET-OWNER"
            )
        )
        val repo = RemoteSharedPetsRepository(gw, authRepo())
        val content = assertIs<VerticalLoadState.Content<*>>(
            repo.observePetDetail(PetId("pet-1"))
                .filterNot { it is VerticalLoadState.Loading }
                .first()
        )
        val detail = content.data as com.comunidapp.shared.pets.PetDetailView
        assertEquals("Luna", detail.displayName)
        assertEquals("Mestiza", detail.breedText)
        assertNull(detail.passportHint)
        assertFalse(detail.toString().contains("SECRET-OWNER"))
    }

    @Test
    fun pet_not_found() = runTest {
        val gw = FakePetsRemoteGateway(detail = null, list = emptyList())
        val repo = RemoteSharedPetsRepository(gw, authRepo())
        assertIs<VerticalLoadState.Error>(
            repo.observePetDetail(PetId("missing"))
                .filterNot { it is VerticalLoadState.Loading }
                .first()
        )
    }

    @Test
    fun pets_unauthorized_session() = runTest {
        val auth = GatewayAuthRepository(FakeAuthSessionGateway(SessionState.Unauthenticated))
        val repo = RemoteSharedPetsRepository(FakePetsRemoteGateway(), auth)
        val err = assertIs<VerticalLoadState.Error>(
            repo.observeMyPets("user-1").filterNot { it is VerticalLoadState.Loading }.first()
        )
        assertTrue(err.message.contains("sesión", ignoreCase = true))
    }

    @Test
    fun pets_backend_error_sanitized() = runTest {
        val gw = FakePetsRemoteGateway(listError = IllegalStateException("403 RLS policy denied JWT eyJhbGciOi"))
        val repo = RemoteSharedPetsRepository(gw, authRepo())
        val err = assertIs<VerticalLoadState.Error>(
            repo.observeMyPets("user-1").filterNot { it is VerticalLoadState.Loading }.first()
        )
        assertFalse(err.message.contains("eyJ"))
        assertFalse(err.message.contains("RLS"))
    }

    @Test
    fun dto_to_pet_summary_mapping() {
        val summary = RemotePetsMapper.toSummary(
            RemoteAccessiblePetRow(
                id = "p1",
                name = "Michi",
                species = "Gato",
                status = "ARCHIVED",
                ownerId = "own"
            )
        )
        assertEquals(PetLifecycleStatus.ARCHIVED, summary.status)
        assertEquals("Gato", summary.speciesLabel)
    }

    @Test
    fun dto_to_pet_detail_mapping_no_owner() {
        val detail = RemotePetsMapper.toDetail(
            RemotePetRow(id = "p1", name = "Michi", species = "Gato", sex = "Macho", ownerId = "own")
        )
        assertNull(detail.passportHint)
        assertEquals("Macho", detail.sexLabel)
        assertFalse(detail.toString().contains("own"))
    }

    @Test
    fun passport_mapping_safe_null() {
        assertNull(
            RemotePetsMapper.toDetail(
                RemotePetRow(id = "p1", name = "X", species = "Perro")
            ).passportHint
        )
    }

    @Test
    fun refresh_triggers_new_list_read() = runTest {
        val gw = FakePetsRemoteGateway(
            list = listOf(RemoteAccessiblePetRow(id = "p1", name = "Luna", species = "Perro"))
        )
        val repo = RemoteSharedPetsRepository(gw, authRepo())
        repo.observeMyPets("user-1").filterNot { it is VerticalLoadState.Loading }.first()
        assertEquals(1, gw.listCalls)
        repo.refresh()
        repo.observeMyPets("user-1").filterNot { it is VerticalLoadState.Loading }.first()
        assertEquals(2, gw.listCalls)
    }

    @Test
    fun pets_data_mode_real_remote() {
        assertEquals(
            com.comunidapp.shared.pets.PetsDataMode.REAL_REMOTE,
            RemoteSharedPetsRepository(FakePetsRemoteGateway(), authRepo()).dataMode
        )
        assertEquals(
            com.comunidapp.shared.pets.PetsDataMode.REAL_REMOTE,
            UnconfiguredSharedPetsRepository().dataMode
        )
    }

    @Test
    fun fake_pets_still_for_tests() {
        assertEquals(
            com.comunidapp.shared.pets.PetsDataMode.SHARED_FAKE,
            FakeSharedPetsRepository().dataMode
        )
    }

    @Test
    fun unconfigured_runtime_modes_real_remote() {
        val runtime = SharedRemoteRuntime.create(
            config = null,
            storage = com.comunidapp.shared.auth.InMemorySecureSessionStorage()
        )
        assertEquals(SessionDataMode.REAL_REMOTE, runtime.authRepository.dataMode)
        assertEquals(
            com.comunidapp.shared.profile.ProfileDataMode.REAL_REMOTE,
            runtime.profileRepository.dataMode
        )
        assertEquals(
            com.comunidapp.shared.pets.PetsDataMode.REAL_REMOTE,
            runtime.petsRepository.dataMode
        )
        assertEquals(
            com.comunidapp.shared.lostfound.LostFoundDataMode.REAL_REMOTE,
            runtime.lostFoundRepository.dataMode
        )
        assertEquals(
            com.comunidapp.shared.adoption.AdoptionDataMode.REAL_REMOTE,
            runtime.adoptionRepository.dataMode
        )
        assertIs<UnconfiguredAuthSessionRepository>(runtime.authRepository)
    }

    @Test
    fun config_rejects_service_role_for_runtime() {
        assertNull(
            SharedSupabaseConfig(
                "https://x.supabase.co",
                "service_role_abc"
            ).usableOrNull()
        )
    }

    @Test
    fun auth_still_real_remote_gateway() {
        assertEquals(SessionDataMode.REAL_REMOTE, GatewayAuthRepository(FakeAuthSessionGateway()).dataMode)
    }
}
