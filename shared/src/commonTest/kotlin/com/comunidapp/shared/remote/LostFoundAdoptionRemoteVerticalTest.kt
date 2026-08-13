package com.comunidapp.shared.remote

import com.comunidapp.shared.adoption.FakeAdoptionRepository
import com.comunidapp.shared.adoption.RemoteAdoptionRepository
import com.comunidapp.shared.adoption.UnconfiguredAdoptionRepository
import com.comunidapp.shared.auth.FakeAuthSessionGateway
import com.comunidapp.shared.auth.GatewayAuthRepository
import com.comunidapp.shared.auth.InMemorySecureSessionStorage
import com.comunidapp.shared.auth.UnconfiguredAuthSessionRepository
import com.comunidapp.shared.domain.adoption.AdoptionListingStatus
import com.comunidapp.shared.domain.lostfound.LostFoundCaseStatus
import com.comunidapp.shared.domain.lostfound.LostFoundCaseType
import com.comunidapp.shared.lostfound.FakeLostFoundRepository
import com.comunidapp.shared.lostfound.LostFoundId
import com.comunidapp.shared.lostfound.LostFoundListFilter
import com.comunidapp.shared.lostfound.RemoteLostFoundRepository
import com.comunidapp.shared.lostfound.UnconfiguredLostFoundRepository
import com.comunidapp.shared.pets.UnconfiguredSharedPetsRepository
import com.comunidapp.shared.profile.UnconfiguredUserProfileRepository
import com.comunidapp.shared.session.SessionDataMode
import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.session.SessionUser
import com.comunidapp.shared.ui.VerticalLoadState
import com.comunidapp.shared.adoption.AdoptionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class LostFoundAdoptionRemoteVerticalTest {

    private fun authRepo(userId: String = "user-1") =
        GatewayAuthRepository(
            FakeAuthSessionGateway(
                SessionState.Authenticated(SessionUser(userId, "a@leover.test", "Ana"))
            )
        )

    private fun lostRow(
        id: String = "lf-1",
        type: String = "LOST",
        status: String = "ACTIVE",
        petName: String? = "Luna",
        species: String = "DOG",
        location: String = "Palermo",
        description: String = "Collar rojo",
        authorName: String? = "Ana Pub",
        authorId: String? = "SECRET-AUTHOR",
        contactInfo: String? = "+541112345678",
        publicCode: String? = "LV-L-1001",
        photoUrl: String? = "https://cdn.example/luna.jpg",
        latitude: Double? = -34.5889,
        longitude: Double? = -58.4300,
        createdAt: String? = "2024-06-01T12:00:00Z"
    ) = RemoteLostFoundRow(
        id = id,
        authorId = authorId,
        authorName = authorName,
        type = type,
        petName = petName,
        species = species,
        photoUrl = photoUrl,
        location = location,
        description = description,
        contactInfo = contactInfo,
        status = status,
        publicCode = publicCode,
        latitude = latitude,
        longitude = longitude,
        createdAt = createdAt
    )

    private fun adoptionRow(
        id: String = "ad-1",
        status: String = "PUBLISHED",
        name: String = "Nube",
        title: String? = null,
        species: String = "CAT",
        sex: String = "FEMALE",
        ageYears: Int = 1,
        ageMonths: Int = 0,
        location: String = "",
        locationText: String? = "Villa Crespo",
        description: String = "Cariñosa",
        publisherName: String? = "Refugio Demo",
        publisherId: String? = "SECRET-PUB",
        publisherOrganizationId: String? = "org-secret",
        petId: String? = "pet-secret",
        publicCode: String? = "LV-A-3003",
        photoUrl: String? = "https://cdn.example/nube.jpg",
        requirements: String? = "private clinical note"
    ) = RemoteAdoptionPublicationRow(
        id = id,
        publisherId = publisherId,
        publisherName = publisherName,
        publisherOrganizationId = publisherOrganizationId,
        petId = petId,
        name = name,
        title = title,
        photoUrl = photoUrl,
        species = species,
        sex = sex,
        ageYears = ageYears,
        ageMonths = ageMonths,
        location = location,
        locationText = locationText,
        description = description,
        requirements = requirements,
        status = status,
        publicCode = publicCode
    )

    // --- Lost/Found remote ---

    @Test
    fun lf_remote_loading_then_empty() = runTest {
        val repo = RemoteLostFoundRepository(FakeLostFoundRemoteGateway(list = emptyList()), FakeLostFoundWriteGateway(), authRepo())
        assertIs<VerticalLoadState.Empty>(
            repo.observeList(LostFoundListFilter.ALL)
                .filterNot { it is VerticalLoadState.Loading }
                .first()
        )
    }

    @Test
    fun lf_remote_loading_then_content() = runTest {
        val gw = FakeLostFoundRemoteGateway(list = listOf(lostRow(), lostRow(id = "lf-2", type = "FOUND", petName = null)))
        val repo = RemoteLostFoundRepository(gw, FakeLostFoundWriteGateway(), authRepo())
        val content = assertIs<VerticalLoadState.Content<*>>(
            repo.observeList(LostFoundListFilter.ALL)
                .filterNot { it is VerticalLoadState.Loading }
                .first()
        )
        @Suppress("UNCHECKED_CAST")
        val items = content.data as List<com.comunidapp.shared.lostfound.LostFoundSummary>
        assertEquals(2, items.size)
    }

    @Test
    fun lf_filter_all() = runTest {
        val gw = FakeLostFoundRemoteGateway(
            list = listOf(
                lostRow(id = "1", type = "LOST"),
                lostRow(id = "2", type = "FOUND", petName = null)
            )
        )
        val repo = RemoteLostFoundRepository(gw, FakeLostFoundWriteGateway(), authRepo())
        val content = assertIs<VerticalLoadState.Content<*>>(
            repo.observeList(LostFoundListFilter.ALL).filterNot { it is VerticalLoadState.Loading }.first()
        )
        @Suppress("UNCHECKED_CAST")
        assertEquals(2, (content.data as List<*>).size)
    }

    @Test
    fun lf_filter_lost() = runTest {
        val gw = FakeLostFoundRemoteGateway(
            list = listOf(
                lostRow(id = "1", type = "LOST"),
                lostRow(id = "2", type = "FOUND", petName = null)
            )
        )
        val repo = RemoteLostFoundRepository(gw, FakeLostFoundWriteGateway(), authRepo())
        val content = assertIs<VerticalLoadState.Content<*>>(
            repo.observeList(LostFoundListFilter.LOST).filterNot { it is VerticalLoadState.Loading }.first()
        )
        @Suppress("UNCHECKED_CAST")
        val items = content.data as List<com.comunidapp.shared.lostfound.LostFoundSummary>
        assertTrue(items.all { it.type == LostFoundCaseType.LOST })
        assertEquals(1, items.size)
    }

    @Test
    fun lf_filter_found() = runTest {
        val gw = FakeLostFoundRemoteGateway(
            list = listOf(
                lostRow(id = "1", type = "LOST"),
                lostRow(id = "2", type = "FOUND", petName = null)
            )
        )
        val repo = RemoteLostFoundRepository(gw, FakeLostFoundWriteGateway(), authRepo())
        val content = assertIs<VerticalLoadState.Content<*>>(
            repo.observeList(LostFoundListFilter.FOUND).filterNot { it is VerticalLoadState.Loading }.first()
        )
        @Suppress("UNCHECKED_CAST")
        val items = content.data as List<com.comunidapp.shared.lostfound.LostFoundSummary>
        assertTrue(items.all { it.type == LostFoundCaseType.FOUND })
    }

    @Test
    fun lf_lost_detail() = runTest {
        val gw = FakeLostFoundRemoteGateway(detail = lostRow(id = "lf-1", type = "LOST"))
        val repo = RemoteLostFoundRepository(gw, FakeLostFoundWriteGateway(), authRepo())
        val content = assertIs<VerticalLoadState.Content<*>>(
            repo.observeDetail(LostFoundId("lf-1")).filterNot { it is VerticalLoadState.Loading }.first()
        )
        val detail = content.data as com.comunidapp.shared.lostfound.LostFoundDetail
        assertEquals(LostFoundCaseType.LOST, detail.type)
        assertEquals("Luna", detail.displayName)
    }

    @Test
    fun lf_found_detail() = runTest {
        val gw = FakeLostFoundRemoteGateway(
            detail = lostRow(id = "lf-2", type = "FOUND", petName = null, species = "CAT")
        )
        val repo = RemoteLostFoundRepository(gw, FakeLostFoundWriteGateway(), authRepo())
        val content = assertIs<VerticalLoadState.Content<*>>(
            repo.observeDetail(LostFoundId("lf-2")).filterNot { it is VerticalLoadState.Loading }.first()
        )
        val detail = content.data as com.comunidapp.shared.lostfound.LostFoundDetail
        assertEquals(LostFoundCaseType.FOUND, detail.type)
        assertNull(detail.displayName)
        assertEquals("Gato", detail.speciesLabel)
    }

    @Test
    fun lf_not_found() = runTest {
        val repo = RemoteLostFoundRepository(FakeLostFoundRemoteGateway(), FakeLostFoundWriteGateway(), authRepo())
        assertIs<VerticalLoadState.Error>(
            repo.observeDetail(LostFoundId("missing")).filterNot { it is VerticalLoadState.Loading }.first()
        )
    }

    @Test
    fun lf_network_error_sanitized() = runTest {
        val gw = FakeLostFoundRemoteGateway(listError = IllegalStateException("NETWORK timeout"))
        val repo = RemoteLostFoundRepository(gw, FakeLostFoundWriteGateway(), authRepo())
        val err = assertIs<VerticalLoadState.Error>(
            repo.observeList(LostFoundListFilter.ALL).filterNot { it is VerticalLoadState.Loading }.first()
        )
        assertTrue(err.message.contains("conexión", ignoreCase = true))
        assertFalse(err.message.contains("timeout", ignoreCase = true))
    }

    @Test
    fun lf_unauthorized_sanitized() = runTest {
        val gw = FakeLostFoundRemoteGateway(
            listError = IllegalStateException("401 JWT eyJhbGciOi denied")
        )
        val repo = RemoteLostFoundRepository(gw, FakeLostFoundWriteGateway(), authRepo())
        val err = assertIs<VerticalLoadState.Error>(
            repo.observeList(LostFoundListFilter.ALL).filterNot { it is VerticalLoadState.Loading }.first()
        )
        assertTrue(err.message.contains("sesión", ignoreCase = true))
        assertFalse(err.message.contains("eyJ"))
        assertFalse(err.message.contains("JWT"))
    }

    @Test
    fun lf_status_mapping() {
        assertEquals(LostFoundCaseStatus.ACTIVE, RemoteLostFoundMapper.mapStatus("ACTIVE"))
        assertEquals(LostFoundCaseStatus.RESOLVED, RemoteLostFoundMapper.mapStatus("RESOLVED"))
        assertEquals(LostFoundCaseStatus.CLOSED, RemoteLostFoundMapper.mapStatus("CLOSED"))
        assertNull(RemoteLostFoundMapper.mapStatus("WEIRD"))
        assertNull(RemoteLostFoundMapper.toSummary(lostRow(status = "PENDING")))
    }

    @Test
    fun lf_type_mapping() {
        assertEquals(LostFoundCaseType.LOST, RemoteLostFoundMapper.mapType("LOST"))
        assertEquals(LostFoundCaseType.FOUND, RemoteLostFoundMapper.mapType("FOUND"))
        assertNull(RemoteLostFoundMapper.mapType("OTHER"))
    }

    @Test
    fun lf_approximate_location_mapping() {
        val summary = assertNotNull(RemoteLostFoundMapper.toSummary(lostRow(location = "  Recoleta  ")))
        assertEquals("Recoleta", summary.approximateLocation.locality)
        assertNull(summary.approximateLocation.region)
        assertNull(summary.approximateLocation.country)
    }

    @Test
    fun lf_exact_coords_not_in_ui_model() {
        val summary = assertNotNull(
            RemoteLostFoundMapper.toSummary(lostRow(latitude = -34.5889, longitude = -58.4300))
        )
        val detail = assertNotNull(
            RemoteLostFoundMapper.toDetail(lostRow(latitude = -34.5889, longitude = -58.4300))
        )
        assertFalse(summary.toString().contains("-34.5889"))
        assertFalse(detail.toString().contains("-58.4300"))
        assertFalse(summary.toString().contains("latitude", ignoreCase = true))
    }

    @Test
    fun lf_pii_not_in_ui_model() {
        val detail = assertNotNull(
            RemoteLostFoundMapper.toDetail(
                lostRow(
                    authorId = "SECRET-AUTHOR",
                    contactInfo = "+541199999999",
                    authorName = "Vecino Público"
                )
            )
        )
        assertFalse(detail.toString().contains("SECRET-AUTHOR"))
        assertFalse(detail.toString().contains("+541199999999"))
        assertFalse(detail.toString().contains("contact", ignoreCase = true))
        assertEquals("Vecino Público", detail.publisherDisplayName)
    }

    @Test
    fun lf_public_code_mapping() {
        val summary = assertNotNull(RemoteLostFoundMapper.toSummary(lostRow(publicCode = "LV-L-1001")))
        assertEquals("LV-L-1001", summary.publicCode)
        assertNull(assertNotNull(RemoteLostFoundMapper.toSummary(lostRow(publicCode = "  "))).publicCode)
    }

    @Test
    fun lf_publisher_safe() {
        val detail = assertNotNull(
            RemoteLostFoundMapper.toDetail(lostRow(authorName = "Ana Pub", authorId = "uid-1"))
        )
        assertEquals("Ana Pub", detail.publisherDisplayName)
        assertFalse(detail.toString().contains("uid-1"))
    }

    @Test
    fun lf_refresh() = runTest {
        val gw = FakeLostFoundRemoteGateway(list = listOf(lostRow()))
        val repo = RemoteLostFoundRepository(gw, FakeLostFoundWriteGateway(), authRepo())
        repo.observeList(LostFoundListFilter.ALL).filterNot { it is VerticalLoadState.Loading }.first()
        assertEquals(1, gw.listCalls)
        repo.refresh()
        repo.observeList(LostFoundListFilter.ALL).filterNot { it is VerticalLoadState.Loading }.first()
        assertEquals(2, gw.listCalls)
    }

    @Test
    fun lf_data_mode_real_remote() {
        assertEquals(
            com.comunidapp.shared.lostfound.LostFoundDataMode.REAL_REMOTE,
            RemoteLostFoundRepository(FakeLostFoundRemoteGateway(), FakeLostFoundWriteGateway(), authRepo()).dataMode
        )
        assertEquals(
            com.comunidapp.shared.lostfound.LostFoundDataMode.REAL_REMOTE,
            UnconfiguredLostFoundRepository().dataMode
        )
    }

    @Test
    fun lf_fake_still_for_tests() {
        assertEquals(
            com.comunidapp.shared.lostfound.LostFoundDataMode.SHARED_FAKE,
            FakeLostFoundRepository().dataMode
        )
    }

    // --- Adoption remote ---

    @Test
    fun adoption_remote_loading_then_empty() = runTest {
        val repo = RemoteAdoptionRepository(FakeAdoptionRemoteGateway(list = emptyList()), authRepo())
        assertIs<VerticalLoadState.Empty>(
            repo.observeList().filterNot { it is VerticalLoadState.Loading }.first()
        )
    }

    @Test
    fun adoption_remote_loading_then_content() = runTest {
        val gw = FakeAdoptionRemoteGateway(list = listOf(adoptionRow()))
        val repo = RemoteAdoptionRepository(gw, authRepo())
        val content = assertIs<VerticalLoadState.Content<*>>(
            repo.observeList().filterNot { it is VerticalLoadState.Loading }.first()
        )
        @Suppress("UNCHECKED_CAST")
        val items = content.data as List<com.comunidapp.shared.adoption.AdoptionSummary>
        assertEquals(1, items.size)
        assertEquals("Nube", items.first().displayName)
    }

    @Test
    fun adoption_detail() = runTest {
        val gw = FakeAdoptionRemoteGateway(detail = adoptionRow(id = "ad-1"))
        val repo = RemoteAdoptionRepository(gw, authRepo())
        val content = assertIs<VerticalLoadState.Content<*>>(
            repo.observeDetail(AdoptionId("ad-1")).filterNot { it is VerticalLoadState.Loading }.first()
        )
        val detail = content.data as com.comunidapp.shared.adoption.AdoptionDetail
        assertEquals("Nube", detail.displayName)
        assertEquals("Refugio Demo", detail.publisherDisplayName)
    }

    @Test
    fun adoption_not_found() = runTest {
        val repo = RemoteAdoptionRepository(FakeAdoptionRemoteGateway(), authRepo())
        assertIs<VerticalLoadState.Error>(
            repo.observeDetail(AdoptionId("missing")).filterNot { it is VerticalLoadState.Loading }.first()
        )
    }

    @Test
    fun adoption_network_error() = runTest {
        val gw = FakeAdoptionRemoteGateway(listError = IllegalStateException("network failure"))
        val repo = RemoteAdoptionRepository(gw, authRepo())
        val err = assertIs<VerticalLoadState.Error>(
            repo.observeList().filterNot { it is VerticalLoadState.Loading }.first()
        )
        assertTrue(err.message.contains("conexión", ignoreCase = true))
    }

    @Test
    fun adoption_unauthorized() = runTest {
        val auth = GatewayAuthRepository(FakeAuthSessionGateway(SessionState.Unauthenticated))
        val repo = RemoteAdoptionRepository(FakeAdoptionRemoteGateway(list = listOf(adoptionRow())), auth)
        val err = assertIs<VerticalLoadState.Error>(
            repo.observeList().filterNot { it is VerticalLoadState.Loading }.first()
        )
        assertTrue(err.message.contains("sesión", ignoreCase = true))
    }

    @Test
    fun adoption_status_mapping() {
        assertEquals(AdoptionListingStatus.PUBLISHED, RemoteAdoptionMapper.mapStatus("PUBLISHED"))
        assertEquals(AdoptionListingStatus.PUBLISHED, RemoteAdoptionMapper.mapStatus("AVAILABLE"))
        assertEquals(AdoptionListingStatus.ADOPTED, RemoteAdoptionMapper.mapStatus("ADOPTED"))
        assertEquals(AdoptionListingStatus.CLOSED, RemoteAdoptionMapper.mapStatus("CLOSED"))
        assertEquals(AdoptionListingStatus.DRAFT, RemoteAdoptionMapper.mapStatus("DRAFT"))
        assertNull(RemoteAdoptionMapper.mapStatus("PAUSED"))
        assertNull(RemoteAdoptionMapper.mapStatus("WEIRD"))
    }

    @Test
    fun adoption_publicly_visible_filtering() = runTest {
        val gw = FakeAdoptionRemoteGateway(
            list = listOf(
                adoptionRow(id = "1", status = "PUBLISHED"),
                adoptionRow(id = "2", status = "DRAFT", name = "Borrador"),
                adoptionRow(id = "3", status = "PAUSED", name = "Pausada"),
                adoptionRow(id = "4", status = "ADOPTED", name = "Ya")
            )
        )
        val repo = RemoteAdoptionRepository(gw, authRepo())
        val content = assertIs<VerticalLoadState.Content<*>>(
            repo.observeList().filterNot { it is VerticalLoadState.Loading }.first()
        )
        @Suppress("UNCHECKED_CAST")
        val items = content.data as List<com.comunidapp.shared.adoption.AdoptionSummary>
        assertEquals(2, items.size)
        assertTrue(items.any { it.displayName == "Nube" })
        assertTrue(items.any { it.displayName == "Ya" })
        assertFalse(items.any { it.displayName == "Borrador" })
        assertFalse(items.any { it.displayName == "Pausada" })
    }

    @Test
    fun adoption_safe_approximate_location() {
        val summary = assertNotNull(
            RemoteAdoptionMapper.toSummary(
                adoptionRow(locationText = "Caballito", location = "exact street 123")
            )
        )
        assertEquals("Caballito", summary.approximateLocation.locality)
        assertFalse(summary.toString().contains("exact street"))
    }

    @Test
    fun adoption_no_pii() {
        val detail = assertNotNull(
            RemoteAdoptionMapper.toDetail(
                adoptionRow(
                    publisherId = "SECRET-PUB",
                    publisherOrganizationId = "org-secret",
                    petId = "pet-secret",
                    requirements = "clinical private"
                )
            )
        )
        assertFalse(detail.toString().contains("SECRET-PUB"))
        assertFalse(detail.toString().contains("org-secret"))
        assertFalse(detail.toString().contains("pet-secret"))
        assertFalse(detail.toString().contains("clinical"))
    }

    @Test
    fun adoption_public_code() {
        val summary = assertNotNull(RemoteAdoptionMapper.toSummary(adoptionRow(publicCode = "LV-A-3003")))
        assertEquals("LV-A-3003", summary.publicCode)
    }

    @Test
    fun adoption_publisher_safe() {
        val detail = assertNotNull(
            RemoteAdoptionMapper.toDetail(
                adoptionRow(publisherName = "Refugio Demo", publisherId = "uid-x")
            )
        )
        assertEquals("Refugio Demo", detail.publisherDisplayName)
        assertFalse(detail.toString().contains("uid-x"))
    }

    @Test
    fun adoption_media_boolean() {
        assertTrue(assertNotNull(RemoteAdoptionMapper.toSummary(adoptionRow(photoUrl = "x"))).hasPhoto)
        assertFalse(assertNotNull(RemoteAdoptionMapper.toSummary(adoptionRow(photoUrl = null))).hasPhoto)
        assertFalse(assertNotNull(RemoteAdoptionMapper.toSummary(adoptionRow(photoUrl = "  "))).hasPhoto)
    }

    @Test
    fun adoption_refresh() = runTest {
        val gw = FakeAdoptionRemoteGateway(list = listOf(adoptionRow()))
        val repo = RemoteAdoptionRepository(gw, authRepo())
        repo.observeList().filterNot { it is VerticalLoadState.Loading }.first()
        assertEquals(1, gw.listCalls)
        repo.refresh()
        repo.observeList().filterNot { it is VerticalLoadState.Loading }.first()
        assertEquals(2, gw.listCalls)
    }

    @Test
    fun adoption_data_mode_real_remote() {
        assertEquals(
            com.comunidapp.shared.adoption.AdoptionDataMode.REAL_REMOTE,
            RemoteAdoptionRepository(FakeAdoptionRemoteGateway(), authRepo()).dataMode
        )
        assertEquals(
            com.comunidapp.shared.adoption.AdoptionDataMode.REAL_REMOTE,
            UnconfiguredAdoptionRepository().dataMode
        )
    }

    @Test
    fun adoption_fake_still_for_tests() {
        assertEquals(
            com.comunidapp.shared.adoption.AdoptionDataMode.SHARED_FAKE,
            FakeAdoptionRepository().dataMode
        )
    }

    // --- KMP-6 regression + single runtime ---

    @Test
    fun unconfigured_runtime_all_real_remote_modes() {
        val runtime = SharedRemoteRuntime.create(
            config = null,
            storage = InMemorySecureSessionStorage()
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
        assertIs<UnconfiguredUserProfileRepository>(runtime.profileRepository)
        assertIs<UnconfiguredSharedPetsRepository>(runtime.petsRepository)
        assertIs<UnconfiguredLostFoundRepository>(runtime.lostFoundRepository)
        assertIs<UnconfiguredAdoptionRepository>(runtime.adoptionRepository)
    }
}
