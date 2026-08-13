package com.comunidapp.shared.media

import com.comunidapp.shared.auth.InMemorySecureSessionStorage
import com.comunidapp.shared.remote.RemoteLostFoundMapper
import com.comunidapp.shared.remote.RemoteLostFoundRow
import com.comunidapp.shared.remote.SharedRemoteRuntime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class M05MediaReadVerticalTest {

    private val sampleAssetId = "11111111-1111-1111-1111-111111111111"
    private val sampleBytes = ByteArray(16) { 1 }

    private fun successResource(key: String = sampleAssetId, expiresAt: Long? = 9_999_999_999L) =
        MediaResolveResult.Success(
            MediaResource(bytes = sampleBytes, cacheKey = key, expiresAtEpochMs = expiresAt)
        )

    @Test
    fun asset_ref_valid() {
        val ref = MediaRef.Asset(sampleAssetId)
        assertEquals(sampleAssetId, ref.assetId)
    }

    @Test
    fun asset_ref_blank_invalid() {
        assertTrue(runCatching { MediaRef.Asset("  ") }.isFailure)
    }

    @Test
    fun remote_url_valid() {
        val ref = MediaRef.RemoteUrl("https://cdn.example/a.jpg")
        assertTrue(MediaRefParser.isHttpUrl(ref.url))
    }

    @Test
    fun parse_photo_field_asset_uuid() {
        val ref = MediaRefParser.fromPhotoField(sampleAssetId)
        assertIs<MediaRef.Asset>(ref)
        assertEquals(sampleAssetId, ref.assetId)
    }

    @Test
    fun parse_photo_field_https() {
        val ref = MediaRefParser.fromPhotoField("https://cdn.example/x.jpg")
        assertIs<MediaRef.RemoteUrl>(ref)
    }

    @Test
    fun parse_photo_field_empty_null() {
        assertNull(MediaRefParser.fromPhotoField(null))
        assertNull(MediaRefParser.fromPhotoField("  "))
    }

    @Test
    fun parse_photo_field_storage_path_null() {
        assertNull(MediaRefParser.fromPhotoField("lost_found/case/a.jpg"))
    }

    @Test
    fun parse_forbidden_content_uri() {
        assertNull(MediaRefParser.fromPhotoField("content://media/1"))
    }

    @Test
    fun fake_resolver_not_found() = runTest {
        val resolver = FakeMediaResolver(defaultResult = MediaResolveResult.NotFound)
        assertEquals(MediaResolveResult.NotFound, resolver.resolve(MediaRef.Asset(sampleAssetId)))
    }

    @Test
    fun fake_resolver_forbidden() = runTest {
        val resolver = FakeMediaResolver()
        resolver.put(sampleAssetId, MediaResolveResult.Forbidden)
        assertEquals(MediaResolveResult.Forbidden, resolver.resolve(MediaRef.Asset(sampleAssetId)))
    }

    @Test
    fun fake_resolver_unauthenticated() = runTest {
        val resolver = FakeMediaResolver(defaultResult = MediaResolveResult.Unauthenticated)
        assertEquals(
            MediaResolveResult.Unauthenticated,
            resolver.resolve(MediaRef.Asset(sampleAssetId))
        )
    }

    @Test
    fun fake_resolver_network_error() = runTest {
        val resolver = FakeMediaResolver(defaultResult = MediaResolveResult.NetworkError)
        assertEquals(MediaResolveResult.NetworkError, resolver.resolve(MediaRef.Asset(sampleAssetId)))
    }

    @Test
    fun incomplete_asset_not_resolvable() = runTest {
        val gw = FakeM05MediaReadGateway(defaultAsset = MediaResolveResult.IncompleteAsset)
        val resolver = CachingMediaResolver(
            gateway = gw,
            clock = { 1_000L },
            checkAuthenticated = { true }
        )
        assertEquals(
            MediaResolveResult.IncompleteAsset,
            resolver.resolve(MediaRef.Asset(sampleAssetId))
        )
    }

    @Test
    fun signed_url_success_via_gateway_bytes() = runTest {
        val gw = FakeM05MediaReadGateway()
        gw.assetResults[sampleAssetId] = successResource()
        val resolver = CachingMediaResolver(
            gateway = gw,
            clock = { 1_000L },
            checkAuthenticated = { true }
        )
        val result = resolver.resolve(MediaRef.Asset(sampleAssetId))
        assertIs<MediaResolveResult.Success>(result)
        assertTrue(result.resource.bytes.contentEquals(sampleBytes))
    }

    @Test
    fun cache_hit_skips_second_gateway_call() = runTest {
        val gw = FakeM05MediaReadGateway()
        gw.assetResults[sampleAssetId] = successResource(expiresAt = 50_000L)
        val resolver = CachingMediaResolver(
            gateway = gw,
            clock = { 1_000L },
            checkAuthenticated = { true }
        )
        resolver.resolve(MediaRef.Asset(sampleAssetId))
        resolver.resolve(MediaRef.Asset(sampleAssetId))
        assertEquals(1, gw.assetCalls)
        assertEquals(1, resolver.cachedCountForTests())
    }

    @Test
    fun cache_expired_refreshes() = runTest {
        val gw = FakeM05MediaReadGateway()
        gw.assetResults[sampleAssetId] = successResource(expiresAt = 2_000L)
        var now = 1_000L
        val resolver = CachingMediaResolver(
            gateway = gw,
            clock = { now },
            checkAuthenticated = { true },
            skewMs = 0L
        )
        resolver.resolve(MediaRef.Asset(sampleAssetId))
        now = 3_000L
        resolver.resolve(MediaRef.Asset(sampleAssetId))
        assertEquals(2, gw.assetCalls)
    }

    @Test
    fun logout_clears_private_cache() = runTest {
        val gw = FakeM05MediaReadGateway()
        gw.assetResults[sampleAssetId] = successResource()
        var authed = true
        val resolver = CachingMediaResolver(
            gateway = gw,
            clock = { 1_000L },
            checkAuthenticated = { authed }
        )
        resolver.resolve(MediaRef.Asset(sampleAssetId))
        assertEquals(1, resolver.cachedCountForTests())
        authed = false
        val result = resolver.resolve(MediaRef.Asset(sampleAssetId))
        assertEquals(MediaResolveResult.Unauthenticated, result)
        assertEquals(0, resolver.cachedCountForTests())
    }

    @Test
    fun unconfigured_runtime_media_unavailable() = runTest {
        val runtime = SharedRemoteRuntime.create(config = null, storage = InMemorySecureSessionStorage())
        val result = runtime.mediaResolver.resolve(MediaRef.Asset(sampleAssetId))
        assertEquals(MediaResolveResult.Unavailable, result)
    }

    @Test
    fun lost_summary_maps_asset_id() {
        val row = lfRow(photoUrl = sampleAssetId)
        val summary = assertNotNull(RemoteLostFoundMapper.toSummary(row))
        assertTrue(summary.hasPhoto)
        assertIs<MediaRef.Asset>(summary.mediaRef)
        assertEquals(sampleAssetId, (summary.mediaRef as MediaRef.Asset).assetId)
    }

    @Test
    fun lost_detail_maps_asset_id() {
        val detail = assertNotNull(RemoteLostFoundMapper.toDetail(lfRow(photoUrl = sampleAssetId)))
        assertIs<MediaRef.Asset>(detail.mediaRef)
    }

    @Test
    fun found_summary_maps_asset_id() {
        val summary = assertNotNull(
            RemoteLostFoundMapper.toSummary(lfRow(type = "FOUND", photoUrl = sampleAssetId))
        )
        assertIs<MediaRef.Asset>(summary.mediaRef)
    }

    @Test
    fun found_detail_maps_asset_id() {
        val detail = assertNotNull(
            RemoteLostFoundMapper.toDetail(lfRow(type = "FOUND", photoUrl = sampleAssetId))
        )
        assertIs<MediaRef.Asset>(detail.mediaRef)
    }

    @Test
    fun no_photo_null_media_ref() {
        val summary = assertNotNull(RemoteLostFoundMapper.toSummary(lfRow(photoUrl = null)))
        assertFalse(summary.hasPhoto)
        assertNull(summary.mediaRef)
    }

    @Test
    fun has_photo_consistency_with_blank() {
        val summary = assertNotNull(RemoteLostFoundMapper.toSummary(lfRow(photoUrl = "  ")))
        assertFalse(summary.hasPhoto)
        assertNull(summary.mediaRef)
    }

    @Test
    fun pet_media_prefers_avatar_asset_id() {
        val ref = MediaRefParser.fromPetFields(sampleAssetId, "https://cdn.example/legacy.jpg")
        assertIs<MediaRef.Asset>(ref)
    }

    @Test
    fun adoption_photo_field_maps_asset() {
        val ref = MediaRefParser.fromPhotoField(sampleAssetId)
        assertIs<MediaRef.Asset>(ref)
    }

    @Test
    fun profile_https_maps_remote_url() {
        val ref = MediaRefParser.fromProfileFields(
            avatarPath = "profile-avatars/u1.png",
            profileImageUrl = "https://cdn.example/avatar.jpg"
        )
        assertIs<MediaRef.RemoteUrl>(ref)
    }

    @Test
    fun profile_path_only_legacy_storage_maps_avatar_ref() {
        val ref = MediaRefParser.fromProfileFields(
            avatarPath = "users/u1/avatar/photo.jpg",
            profileImageUrl = null
        )
        assertIs<MediaRef.ProfileAvatarPath>(ref)
    }

    @Test
    fun profile_bucket_name_prefix_still_null() {
        assertNull(
            MediaRefParser.fromProfileFields(
                avatarPath = "profile-avatars/u1.png",
                profileImageUrl = null
            )
        )
    }

    @Test
    fun media_read_message_no_url_leak() {
        val msg = mediaReadUserMessage(MediaResolveResult.Forbidden)
        assertFalse(msg.contains("http", ignoreCase = true))
        assertFalse(msg.contains("eyJ"))
        assertFalse(msg.contains("storage"))
    }

    @Test
    fun map_read_throwable_forbidden() {
        assertEquals(
            MediaResolveResult.Forbidden,
            mapReadThrowable(IllegalStateException("403 RLS denied"))
        )
    }

    @Test
    fun map_read_throwable_network() {
        assertEquals(
            MediaResolveResult.NetworkError,
            mapReadThrowable(IllegalStateException("network timeout"))
        )
    }

    @Test
    fun clear_cache_api() {
        val resolver = FakeMediaResolver()
        resolver.clearCache()
        assertEquals(1, resolver.clearCount)
    }

    @Test
    fun remote_url_resolve_via_gateway() = runTest {
        val url = "https://cdn.example/p.jpg"
        val gw = FakeM05MediaReadGateway()
        gw.urlResults[url] = successResource(key = "url")
        val resolver = CachingMediaResolver(
            gateway = gw,
            clock = { 1L },
            checkAuthenticated = { true }
        )
        assertIs<MediaResolveResult.Success>(resolver.resolve(MediaRef.RemoteUrl(url)))
        assertEquals(1, gw.urlCalls)
    }

    @Test
    fun public_ttl_constants_match_android_fallback() {
        assertEquals(300, SupabaseM05MediaReadGateway.PUBLIC_TTL_SECONDS)
        assertEquals(600, SupabaseM05MediaReadGateway.PRIVATE_TTL_SECONDS)
    }

    private fun lfRow(
        type: String = "LOST",
        photoUrl: String? = sampleAssetId
    ) = RemoteLostFoundRow(
        id = "case-1",
        type = type,
        status = "ACTIVE",
        petName = "Luna",
        species = "DOG",
        description = "Se perdió cerca de la plaza grande.",
        location = "Palermo",
        createdAt = "2026-08-01T12:00:00Z",
        publicCode = "LV-L-1",
        authorName = "Demo",
        photoUrl = photoUrl
    )
}
