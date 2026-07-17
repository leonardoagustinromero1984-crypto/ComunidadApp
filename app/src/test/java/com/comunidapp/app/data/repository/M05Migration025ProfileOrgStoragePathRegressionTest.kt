package com.comunidapp.app.data.repository

import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileLogicalBucket
import com.comunidapp.app.domain.files.FileNameSanitizer
import com.comunidapp.app.domain.files.FilePathBuildRequest
import com.comunidapp.app.domain.files.FilePathBuilder
import com.comunidapp.app.domain.files.FilePurposePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regresión del defecto bloqueante de 024 corregido por 025:
 * paths M05 en profile-avatars / organization-media no coinciden con RLS legacy
 * (017: users/.../avatar/; 019: .../logo|cover/{file} de un solo segmento).
 */
class M05Migration025ProfileOrgStoragePathRegressionTest {

    private val userId = "123e4567-e89b-12d3-a456-426614174000"
    private val orgId = "223e4567-e89b-12d3-a456-426614174000"
    private val assetId = "323e4567-e89b-12d3-a456-426614174001"

    @Test
    fun `USER_AVATAR M05 path uses avatars plural and assetId segment unlike legacy avatar`() {
        val safe = FileNameSanitizer.sanitize("avatar.jpg").getOrThrow()
        val path = FilePathBuilder.build(
            FilePathBuildRequest(
                purpose = FileAssetPurpose.USER_AVATAR,
                owner = FileAssetOwner.User(userId),
                assetId = assetId,
                safeFilename = safe
            )
        ).getOrThrow()

        assertEquals(FileLogicalBucket.PROFILE_AVATARS, FilePurposePolicy.resolveLogicalBucket(FileAssetPurpose.USER_AVATAR))
        assertTrue(path.startsWith("users/$userId/avatars/$assetId/"))
        assertFalse("Legacy singular avatar must not be the M05 shape", path.contains("/avatar/"))
        assertTrue(m05ProfileAvatarsRegex.matches(path))
        assertFalse(legacyAvatarRegex.matches(path))
    }

    @Test
    fun `USER_COVER M05 path is under covers and is rejected by legacy avatar owner regex`() {
        val safe = FileNameSanitizer.sanitize("cover.webp").getOrThrow()
        val path = FilePathBuilder.build(
            FilePathBuildRequest(
                purpose = FileAssetPurpose.USER_COVER,
                owner = FileAssetOwner.User(userId),
                assetId = assetId,
                safeFilename = safe
            )
        ).getOrThrow()

        assertTrue(path.startsWith("users/$userId/covers/$assetId/"))
        assertTrue(m05ProfileAvatarsRegex.matches(path))
        assertFalse(legacyAvatarRegex.matches(path))
    }

    @Test
    fun `ORGANIZATION_LOGO M05 path has assetId segment rejected by legacy org media regex`() {
        val safe = FileNameSanitizer.sanitize("logo.png").getOrThrow()
        val path = FilePathBuilder.build(
            FilePathBuildRequest(
                purpose = FileAssetPurpose.ORGANIZATION_LOGO,
                owner = FileAssetOwner.Organization(orgId),
                assetId = assetId,
                safeFilename = safe
            )
        ).getOrThrow()

        assertEquals(
            FileLogicalBucket.ORGANIZATION_MEDIA,
            FilePurposePolicy.resolveLogicalBucket(FileAssetPurpose.ORGANIZATION_LOGO)
        )
        assertTrue(path.startsWith("organizations/$orgId/logo/$assetId/"))
        assertTrue(m05OrgMediaRegex.matches(path))
        assertFalse(
            "Legacy org regex allows only one segment after logo/",
            legacyOrgMediaRegex.matches(path)
        )
    }

    @Test
    fun `ORGANIZATION_COVER M05 path likewise requires 025 session-gated policies`() {
        val safe = FileNameSanitizer.sanitize("cover.jpg").getOrThrow()
        val path = FilePathBuilder.build(
            FilePathBuildRequest(
                purpose = FileAssetPurpose.ORGANIZATION_COVER,
                owner = FileAssetOwner.Organization(orgId),
                assetId = assetId,
                safeFilename = safe
            )
        ).getOrThrow()

        assertTrue(path.startsWith("organizations/$orgId/cover/$assetId/"))
        assertTrue(m05OrgMediaRegex.matches(path))
        assertFalse(legacyOrgMediaRegex.matches(path))
    }

    @Test
    fun `migration 025 exists and does not edit 024 or touch leover write policies`() {
        val sql = migration025Text()
        assertTrue(File(migration024Path()).isFile)
        assertTrue(sql.contains("m05_profile_avatars_insert"))
        assertTrue(sql.contains("m05_organization_media_insert"))
        assertTrue(sql.contains("m05_storage_session_allows_write"))
        assertFalse(sql.contains("drop policy if exists leover_public_read"))
        assertFalse(sql.contains("create policy leover_authenticated_upload"))
        assertTrue(sql.contains("No toca leover"))
        assertFalse(sql.contains("drop table if exists public.file_assets"))
    }

    private fun migration025Text(): String {
        val candidates = listOf(
            File("supabase/migrations/025_m05_profile_org_media_storage_rls_fix.sql"),
            File("../supabase/migrations/025_m05_profile_org_media_storage_rls_fix.sql"),
            File("../../supabase/migrations/025_m05_profile_org_media_storage_rls_fix.sql"),
            File(System.getProperty("user.dir"), "supabase/migrations/025_m05_profile_org_media_storage_rls_fix.sql"),
            File(System.getProperty("user.dir"), "../supabase/migrations/025_m05_profile_org_media_storage_rls_fix.sql")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error(
                "025 migration not found. cwd=${System.getProperty("user.dir")} tried=" +
                    candidates.joinToString { it.absolutePath }
            )
        return file.readText()
    }

    private fun migration024Path(): String {
        val candidates = listOf(
            File("supabase/migrations/024_m05_file_assets_storage_foundation.sql"),
            File("../supabase/migrations/024_m05_file_assets_storage_foundation.sql"),
            File("../../supabase/migrations/024_m05_file_assets_storage_foundation.sql")
        )
        return candidates.first { it.isFile }.absolutePath
    }

    companion object {
        // Mirrors 025 helpers / legacy 017–019 shapes (case-insensitive path checks).
        private val m05ProfileAvatarsRegex = Regex(
            "^users/[0-9a-fA-F-]{36}/(avatars|covers)/[0-9a-fA-F-]{36}/[A-Za-z0-9][A-Za-z0-9._-]{0,127}$"
        )
        private val legacyAvatarRegex = Regex(
            "^users/[0-9a-fA-F-]{36}/avatar/[^/]+$"
        )
        private val m05OrgMediaRegex = Regex(
            "^organizations/[0-9a-fA-F-]{36}/(logo|cover)/[0-9a-fA-F-]{36}/[A-Za-z0-9][A-Za-z0-9._-]{0,127}$"
        )
        private val legacyOrgMediaRegex = Regex(
            "^organizations/[0-9a-fA-F-]{36}/(logo|cover)/[^/]+$"
        )
    }
}
