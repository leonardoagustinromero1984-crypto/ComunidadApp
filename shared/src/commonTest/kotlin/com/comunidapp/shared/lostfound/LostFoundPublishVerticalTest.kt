package com.comunidapp.shared.lostfound

import com.comunidapp.shared.auth.FakeAuthSessionGateway
import com.comunidapp.shared.auth.GatewayAuthRepository
import com.comunidapp.shared.auth.InMemorySecureSessionStorage
import com.comunidapp.shared.domain.lostfound.LostFoundCaseStatus
import com.comunidapp.shared.domain.lostfound.LostFoundCaseType
import com.comunidapp.shared.location.ApproximateLocation
import com.comunidapp.shared.poc.m08.model.FileRef
import com.comunidapp.shared.remote.FakeLostFoundMediaUploadGateway
import com.comunidapp.shared.remote.FakeLostFoundRemoteGateway
import com.comunidapp.shared.remote.FakeLostFoundWriteGateway
import com.comunidapp.shared.remote.LostFoundInsertCommand
import com.comunidapp.shared.remote.SharedRemoteRuntime
import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.session.SessionUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class LostFoundPublishVerticalTest {

    private val zone = ApproximateLocation("Palermo", "CABA", "AR")

    private fun authRepo(userId: String = "user-1", name: String? = "Ana") =
        GatewayAuthRepository(
            FakeAuthSessionGateway(
                SessionState.Authenticated(SessionUser(userId, "a@leover.test", name))
            )
        )

    private fun validLostDraft() = LostFoundDraft(
        type = LostFoundCaseType.LOST,
        displayName = "Luna",
        speciesLabel = "Perro",
        description = "Se perdió cerca de la plaza",
        approximateLocation = zone
    )

    private fun validFoundDraft() = LostFoundDraft(
        type = LostFoundCaseType.FOUND,
        displayName = null,
        speciesLabel = "Gato",
        description = "Encontrado en la vereda",
        approximateLocation = zone
    )

    private fun remoteRepo(
        write: FakeLostFoundWriteGateway = FakeLostFoundWriteGateway(forcedId = "new-lf-1"),
        auth: GatewayAuthRepository = authRepo(),
        media: FakeLostFoundMediaUploadGateway = FakeLostFoundMediaUploadGateway()
    ) = RemoteLostFoundRepository(
        gateway = FakeLostFoundRemoteGateway(),
        writeGateway = write,
        sessionRepository = auth,
        mediaUploadGateway = media
    )

    @Test
    fun lost_draft_valid() {
        assertTrue(LostFoundDraftValidator.validate(validLostDraft()).isSuccess)
    }

    @Test
    fun found_draft_valid() {
        assertTrue(LostFoundDraftValidator.validate(validFoundDraft()).isSuccess)
    }

    @Test
    fun lost_without_name_invalid() {
        assertTrue(
            LostFoundDraftValidator.validate(validLostDraft().copy(displayName = null)).isFailure
        )
    }

    @Test
    fun species_blank_invalid() {
        assertTrue(
            LostFoundDraftValidator.validate(validLostDraft().copy(speciesLabel = " ")).isFailure
        )
    }

    @Test
    fun description_short_invalid() {
        assertTrue(
            LostFoundDraftValidator.validate(validLostDraft().copy(description = "corta")).isFailure
        )
    }

    @Test
    fun unauthenticated_publish() = runTest {
        val auth = GatewayAuthRepository(FakeAuthSessionGateway(SessionState.Unauthenticated))
        val repo = remoteRepo(auth = auth)
        val result = repo.publish(LostFoundPublishRequest(validLostDraft()))
        assertIs<LostFoundPublishResult.Unauthenticated>(result)
    }

    @Test
    fun permission_denied() = runTest {
        val write = FakeLostFoundWriteGateway(
            insertError = IllegalStateException("403 RLS policy denied")
        )
        val result = remoteRepo(write = write).publish(LostFoundPublishRequest(validLostDraft()))
        assertIs<LostFoundPublishResult.PermissionDenied>(result)
        assertFalse(result.message.contains("RLS"))
    }

    @Test
    fun network_error() = runTest {
        val write = FakeLostFoundWriteGateway(
            insertError = IllegalStateException("NETWORK timeout")
        )
        val result = remoteRepo(write = write).publish(LostFoundPublishRequest(validLostDraft()))
        assertIs<LostFoundPublishResult.NetworkError>(result)
        assertTrue(result.message.contains("conexión", ignoreCase = true))
    }

    @Test
    fun backend_error_sanitized() = runTest {
        val write = FakeLostFoundWriteGateway(
            insertError = IllegalStateException("JWT eyJhbGciOi weird SQL SELECT * FROM")
        )
        val result = remoteRepo(write = write).publish(LostFoundPublishRequest(validLostDraft()))
        assertIs<LostFoundPublishResult.Unauthenticated>(result)
        assertFalse(result.message.contains("eyJ"))
        assertFalse(result.message.contains("SQL"))
    }

    @Test
    fun lost_publish_success() = runTest {
        val write = FakeLostFoundWriteGateway(forcedId = "lost-1")
        val result = remoteRepo(write = write).publish(LostFoundPublishRequest(validLostDraft()))
        val ok = assertIs<LostFoundPublishResult.Success>(result)
        assertEquals("lost-1", ok.id.value)
        assertEquals(1, write.insertCalls)
        assertEquals("LOST", write.inserted.single().type)
        assertEquals("ACTIVE", write.inserted.single().status)
        assertEquals("DOG", write.inserted.single().species)
    }

    @Test
    fun found_publish_success() = runTest {
        val write = FakeLostFoundWriteGateway(forcedId = "found-1")
        val result = remoteRepo(write = write).publish(LostFoundPublishRequest(validFoundDraft()))
        val ok = assertIs<LostFoundPublishResult.Success>(result)
        assertEquals("FOUND", write.inserted.single().type)
        assertNull(write.inserted.single().petName)
        assertNotNull(ok.publicCode)
    }

    @Test
    fun status_initial_mapper() {
        assertEquals(LostFoundCaseStatus.ACTIVE, LostFoundPublishMapper.initialStatus())
        assertEquals("ACTIVE", LostFoundPublishMapper.initialStatus().name)
    }

    @Test
    fun author_from_session() = runTest {
        val write = FakeLostFoundWriteGateway(forcedId = "x1")
        remoteRepo(write = write, auth = authRepo(userId = "uid-9", name = "Ana Pub"))
            .publish(LostFoundPublishRequest(validLostDraft()))
        assertEquals("uid-9", write.inserted.single().authorId)
        assertEquals("Ana Pub", write.inserted.single().authorName)
    }

    @Test
    fun author_not_controllable_by_ui() = runTest {
        val write = FakeLostFoundWriteGateway(forcedId = "x2")
        // contactNote / draft no pueden inyectar author_id
        remoteRepo(write = write, auth = authRepo(userId = "real-user"))
            .publish(
                LostFoundPublishRequest(
                    validLostDraft().copy(contactNote = "author_id=evil; phone=+5411")
                )
            )
        assertEquals("real-user", write.inserted.single().authorId)
        assertFalse(write.inserted.single().authorId.contains("evil"))
    }

    @Test
    fun location_approximate_mapping() {
        val text = LostFoundPublishMapper.locationText(validLostDraft())
        assertEquals("Palermo, CABA, AR", text)
        assertFalse(text.contains("-34"))
    }

    @Test
    fun no_exact_coords_in_ui_model() = runTest {
        val write = FakeLostFoundWriteGateway(forcedId = "c1")
        val result = remoteRepo(write = write).publish(LostFoundPublishRequest(validLostDraft()))
        val ok = assertIs<LostFoundPublishResult.Success>(result)
        assertFalse(ok.toString().contains("latitude", ignoreCase = true))
        assertFalse(ok.toString().contains("-58"))
        // insert row also has no lat/lng fields in command path
        assertFalse(write.inserted.single().toString().contains("latitude", ignoreCase = true))
    }

    @Test
    fun publish_without_photo() = runTest {
        val write = FakeLostFoundWriteGateway(forcedId = "np1")
        val result = remoteRepo(write = write)
            .publish(LostFoundPublishRequest(validLostDraft(), media = null))
        val ok = assertIs<LostFoundPublishResult.Success>(result)
        assertFalse(ok.mediaAttached)
        assertFalse(ok.mediaDeferred)
        assertNull(write.inserted.single().photoUrl)
    }

    @Test
    fun publish_with_file_ref_media_partial() = runTest {
        val write = FakeLostFoundWriteGateway(forcedId = "m1")
        val media = FakeLostFoundMediaUploadGateway(succeedWithAssetId = null)
        val file = FileRef("foto.jpg", "image/jpeg", 1024, "file://tmp/foto.jpg")
        val result = remoteRepo(write = write, media = media)
            .publish(LostFoundPublishRequest(validLostDraft(), media = file))
        val ok = assertIs<LostFoundPublishResult.Success>(result)
        assertFalse(ok.mediaAttached)
        assertTrue(ok.mediaDeferred)
    }

    @Test
    fun media_upload_failure_does_not_block_text() = runTest {
        val write = FakeLostFoundWriteGateway(forcedId = "m2")
        val media = FakeLostFoundMediaUploadGateway(
            error = IllegalStateException("upload storage failed")
        )
        val file = FileRef("foto.jpg", "image/jpeg", 1024, "id-1")
        val result = remoteRepo(write = write, media = media)
            .publish(LostFoundPublishRequest(validLostDraft(), media = file))
        assertIs<LostFoundPublishResult.Success>(result)
        assertEquals(1, write.insertCalls)
    }

    @Test
    fun success_refresh() = runTest {
        val write = FakeLostFoundWriteGateway(forcedId = "r1")
        val readGw = FakeLostFoundRemoteGateway()
        val repo = RemoteLostFoundRepository(readGw, write, authRepo())
        repo.observeList(LostFoundListFilter.ALL).first()
        val before = readGw.listCalls
        repo.publish(LostFoundPublishRequest(validLostDraft()))
        // refreshTick increment; next collect triggers another list read
        repo.observeList(LostFoundListFilter.ALL).first()
        assertTrue(readGw.listCalls > before)
    }

    @Test
    fun double_submit_blocked() = runTest {
        val repo = FakeLostFoundRepository()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val vm = LostFoundPublishViewModelShared(repo, scope = scope)
        vm.setDisplayName("Luna")
        vm.setSpeciesLabel("Perro")
        vm.setLocality("Palermo")
        vm.setDescription("Descripción válida larga")
        vm.publish()
        vm.publish()
        assertEquals(1, repo.publishCalls)
        vm.clear()
    }

    @Test
    fun error_keeps_draft() = runTest {
        val write = FakeLostFoundWriteGateway(insertError = IllegalStateException("NETWORK boom"))
        val repo = remoteRepo(write = write)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val vm = LostFoundPublishViewModelShared(repo, scope = scope)
        vm.setDisplayName("Luna")
        vm.setSpeciesLabel("Perro")
        vm.setLocality("Palermo")
        vm.setDescription("Descripción válida larga")
        vm.publish()
        val form = vm.form.value
        assertIs<LostFoundPublishUiState.Error>(form.ui)
        assertEquals("Luna", form.displayName)
        assertEquals("Palermo", form.locality)
        vm.clear()
    }

    @Test
    fun data_remains_real_remote() {
        assertEquals(
            LostFoundDataMode.REAL_REMOTE,
            remoteRepo().dataMode
        )
        assertEquals(
            LostFoundDataMode.REAL_REMOTE,
            UnconfiguredLostFoundRepository().dataMode
        )
    }

    @Test
    fun single_runtime_client_modes() {
        val runtime = SharedRemoteRuntime.create(null, InMemorySecureSessionStorage())
        assertEquals(LostFoundDataMode.REAL_REMOTE, runtime.lostFoundRepository.dataMode)
        assertIs<UnconfiguredLostFoundRepository>(runtime.lostFoundRepository)
    }

    @Test
    fun fake_gateway_not_required_on_host_contract() {
        // Host usa SharedRemoteRuntime → Supabase* gateways; Fake* solo tests.
        assertEquals(LostFoundDataMode.SHARED_FAKE, FakeLostFoundRepository().dataMode)
    }

    @Test
    fun contact_info_derived_not_email_raw() {
        val info = LostFoundPublishMapper.resolveContactInfo(
            validLostDraft(),
            SessionUser("u1", "secret@leover.test", "Ana")
        )
        assertTrue(info.contains("LeoVer"))
        assertFalse(info.contains("secret@leover.test"))
    }

    @Test
    fun insert_command_species_and_status() {
        val cmd = LostFoundInsertCommand(
            authorId = "u",
            authorName = "A",
            type = LostFoundPublishMapper.typeWire(LostFoundCaseType.LOST),
            petName = "Luna",
            species = LostFoundPublishMapper.speciesWire("Perro"),
            location = "Palermo",
            description = "desc",
            contactInfo = "Contactar por LeoVer",
            status = LostFoundPublishMapper.initialStatus().name
        )
        assertEquals("DOG", cmd.species)
        assertEquals("ACTIVE", cmd.status)
    }
}
