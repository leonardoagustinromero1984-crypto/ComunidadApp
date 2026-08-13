package com.comunidapp.shared.profile

import com.comunidapp.shared.auth.FakeAuthSessionGateway
import com.comunidapp.shared.auth.GatewayAuthRepository
import com.comunidapp.shared.media.CachingMediaResolver
import com.comunidapp.shared.media.FakeM05MediaReadGateway
import com.comunidapp.shared.media.FakeMediaResolver
import com.comunidapp.shared.media.MediaRef
import com.comunidapp.shared.media.MediaRefParser
import com.comunidapp.shared.media.MediaResolveResult
import com.comunidapp.shared.media.MediaResource
import com.comunidapp.shared.poc.m08.model.FileRef
import com.comunidapp.shared.remote.FakeProfileRemoteGateway
import com.comunidapp.shared.remote.FakeProfileWriteRemoteGateway
import com.comunidapp.shared.remote.RemoteUserProfileRow
import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.session.SessionUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class ProfileUpdateVerticalTest {

    private fun auth(userId: String = "u1") =
        GatewayAuthRepository(
            FakeAuthSessionGateway(
                SessionState.Authenticated(SessionUser(userId, "a@leover.test", "Ana"))
            )
        )

    private fun row(
        displayName: String = "Ana",
        city: String? = "CABA",
        avatarPath: String? = null
    ) = RemoteUserProfileRow(
        id = "u1",
        displayName = displayName,
        city = city,
        province = null,
        avatarPath = avatarPath,
        profileImageUrl = null,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z"
    )

    private fun sampleFile() = FileRef(
        name = "a.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 1024L,
        platformIdentifier = "file://tmp/a.jpg"
    )

    private fun remote(
        read: FakeProfileRemoteGateway = FakeProfileRemoteGateway(row = row()),
        write: FakeProfileWriteRemoteGateway = FakeProfileWriteRemoteGateway(),
        avatar: FakeProfileAvatarUploadGateway = FakeProfileAvatarUploadGateway(),
        authRepo: GatewayAuthRepository = auth(),
        media: FakeMediaResolver? = FakeMediaResolver()
    ) = RemoteUserProfileRepository(
        gateway = read,
        writeGateway = write,
        avatarUpload = avatar,
        sessionRepository = authRepo,
        mediaResolver = media
    )

    @Test
    fun load_existing() = runTest {
        val state = remote().observeMyProfile("u1").first { it !is ProfileLoadState.Loading }
        assertIs<ProfileLoadState.Content>(state)
        assertEquals("Ana", state.profile.displayName)
    }

    @Test
    fun update_name() = runTest {
        val write = FakeProfileWriteRemoteGateway()
        val read = FakeProfileRemoteGateway(row = row(displayName = "Ana"))
        val repo = remote(read = read, write = write)
        read.row = row(displayName = "Ana López")
        val result = repo.updateProfile(ProfileUpdateDraft(displayName = "Ana López"))
        assertIs<ProfileUpdateResult.Success>(result)
        assertEquals("Ana López", result.profile.displayName)
        assertEquals(1, write.calls)
        assertEquals("Ana López", write.last?.displayName)
    }

    @Test
    fun update_approximate_locality() = runTest {
        val write = FakeProfileWriteRemoteGateway()
        val read = FakeProfileRemoteGateway(row = row())
        read.row = row(city = "Palermo")
        val result = remote(read = read, write = write)
            .updateProfile(ProfileUpdateDraft(city = "Palermo", province = "CABA"))
        assertIs<ProfileUpdateResult.Success>(result)
        assertEquals("Palermo", write.last?.city)
        assertEquals("CABA", write.last?.province)
    }

    @Test
    fun invalid_empty_name() {
        assertTrue(
            ProfileUpdateDraftValidator.validate(ProfileUpdateDraft(displayName = "A")).isFailure
        )
    }

    @Test
    fun auth_required() = runTest {
        val authRepo = GatewayAuthRepository(FakeAuthSessionGateway(SessionState.Unauthenticated))
        val result = remote(authRepo = authRepo).updateProfile(ProfileUpdateDraft(displayName = "Ana"))
        assertIs<ProfileUpdateResult.Unauthenticated>(result)
    }

    @Test
    fun rls_forbidden_sanitized() = runTest {
        val write = FakeProfileWriteRemoteGateway(
            error = IllegalStateException("403 forbidden RLS")
        )
        val result = remote(write = write).updateProfile(ProfileUpdateDraft(displayName = "Ana"))
        assertIs<ProfileUpdateResult.Forbidden>(result)
        assertFalse(result.message.contains("RLS", ignoreCase = true))
    }

    @Test
    fun network_sanitized() = runTest {
        val write = FakeProfileWriteRemoteGateway(
            error = IllegalStateException("network timeout")
        )
        val result = remote(write = write).updateProfile(ProfileUpdateDraft(displayName = "Ana"))
        assertIs<ProfileUpdateResult.BackendError>(result)
        assertTrue(result.message.contains("conexión", ignoreCase = true))
    }

    @Test
    fun avatar_legacy_path_resolves() {
        val ref = MediaRefParser.fromProfileFields(
            avatarPath = "users/u1/avatar/photo.jpg",
            profileImageUrl = null
        )
        assertIs<MediaRef.ProfileAvatarPath>(ref)
    }

    @Test
    fun avatar_legacy_bucket_prefix_still_null() {
        assertNull(
            MediaRefParser.fromProfileFields(
                avatarPath = "profile-avatars/u1.png",
                profileImageUrl = null
            )
        )
    }

    @Test
    fun avatar_resolve_via_gateway() = runTest {
        val path = "users/u1/avatar/a.jpg"
        val gw = FakeM05MediaReadGateway()
        gw.avatarResults[path] = MediaResolveResult.Success(
            MediaResource(
                bytes = byteArrayOf(1, 2, 3),
                cacheKey = "avatar:$path",
                expiresAtEpochMs = 9_999
            )
        )
        val resolver = CachingMediaResolver(
            gateway = gw,
            clock = { 1L },
            checkAuthenticated = { true }
        )
        val result = resolver.resolve(MediaRef.ProfileAvatarPath(path))
        assertIs<MediaResolveResult.Success>(result)
        assertEquals(1, gw.avatarCalls)
    }

    @Test
    fun avatar_upload_success_then_profile_ref() = runTest {
        val write = FakeProfileWriteRemoteGateway()
        val read = FakeProfileRemoteGateway(row = row(avatarPath = "users/u1/avatar/a.jpg"))
        val avatar = FakeProfileAvatarUploadGateway(
            result = ProfileAvatarUploadResult.Success("users/u1/avatar/a.jpg")
        )
        val media = FakeMediaResolver()
        val result = remote(read = read, write = write, avatar = avatar, media = media)
            .uploadAvatar(sampleFile())
        assertIs<ProfileUpdateResult.Success>(result)
        assertEquals(1, avatar.calls)
        assertEquals("users/u1/avatar/a.jpg", write.last?.avatarPath)
        assertEquals(1, media.clearCount)
    }

    @Test
    fun avatar_upload_fail() = runTest {
        val avatar = FakeProfileAvatarUploadGateway(
            result = ProfileAvatarUploadResult.BackendError("No se pudo subir la foto.")
        )
        val result = remote(avatar = avatar).uploadAvatar(sampleFile())
        assertIs<ProfileUpdateResult.BackendError>(result)
    }

    @Test
    fun profile_ref_update_fail_after_upload() = runTest {
        val write = FakeProfileWriteRemoteGateway(
            error = IllegalStateException("AVATAR_PATH_INVALID")
        )
        val avatar = FakeProfileAvatarUploadGateway(
            result = ProfileAvatarUploadResult.Success("users/u1/avatar/a.jpg")
        )
        val result = remote(write = write, avatar = avatar).uploadAvatar(sampleFile())
        assertIs<ProfileUpdateResult.BackendError>(result)
        assertTrue(result.message.contains("avatar", ignoreCase = true))
    }

    @Test
    fun unconfigured_no_fake_success() = runTest {
        val result = UnconfiguredUserProfileRepository()
            .updateProfile(ProfileUpdateDraft(displayName = "Ana"))
        assertIs<ProfileUpdateResult.BackendError>(result)
    }

    @Test
    fun no_signed_url_in_profile_model() {
        val path = "users/u1/avatar/a.jpg"
        val summary = FakeUserProfileRepository.defaultProfile(
            "u1",
            com.comunidapp.shared.platform.PlatformClock.SYSTEM
        ).copy(
            avatarRef = path,
            mediaRef = MediaRef.ProfileAvatarPath(path)
        )
        assertFalse(summary.avatarRef.orEmpty().startsWith("http"))
        assertIs<MediaRef.ProfileAvatarPath>(assertNotNull(summary.mediaRef))
    }
}
