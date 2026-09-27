package com.comunidapp.app.domain.security

import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.core.result.AppErrorMapper
import com.comunidapp.app.data.remote.supabase.m19.M19SocialErrorMapper
import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Sec03AbuseControlsContractTest {

    @Test
    fun migrationDefinesCanonicalRateLimitsAndKillSwitches() {
        val sql = source(
            "infra/supabase-canonical/supabase/migrations/20260902200000_1063_sec03_rate_limits_quotas.sql"
        )
        assertTrue(sql.contains("security_rate_limit_policies"))
        assertTrue(sql.contains("security_usage_windows"))
        assertTrue(sql.contains("security_feature_flags"))
        assertTrue(sql.contains("media.video.upload.enabled"))
        assertTrue(sql.contains("reels.create.enabled"))
        assertTrue(sql.contains("imports.enabled"))
        assertTrue(sql.contains("on conflict (operation_key, scope_kind, scope_id, window_started_at)"))
        assertTrue(sql.contains("canon_authorize_media_signed_url"))
        assertTrue(sql.contains("FEATURE_TEMPORARILY_DISABLED"))
        assertFalse(sql.contains("p_rows jsonb"))
        assertFalse(sql.contains("emergency_bypass"))
    }

    @Test
    fun androidMapsLimitCodesWithoutFunctionalUiRewrite() {
        assertEquals(AppErrorKind.RATE_LIMITED, AppErrorMapper.rateLimited().kind)
        assertEquals(AppErrorKind.QUOTA_EXCEEDED, AppErrorMapper.quotaExceeded().kind)
        assertEquals(
            AppErrorKind.FEATURE_TEMPORARILY_DISABLED,
            AppErrorMapper.featureTemporarilyDisabled().kind
        )
        assertEquals(
            "Estás publicando demasiado rápido. Intentá nuevamente más tarde.",
            M19SocialErrorMapper.userMessage("RATE_LIMITED")
        )
        val media = source("app/src/main/java/com/comunidapp/app/data/repository/CanonicalMediaRepositories.kt")
        assertTrue(media.contains("functions/v1/"))
        assertTrue(media.contains("EDGE_MEDIA_SIGNED_URL"))
        assertTrue(media.contains("invokeCanonicalMediaSignedUrl"))
        assertFalse(media.contains("createSignedUrl"))
        assertFalse(media.contains("SUPABASE_SERVICE_ROLE"))
        val edge = source("infra/supabase-canonical/supabase/functions/media-signed-url/index.ts")
        assertTrue(edge.contains("canon_authorize_media_signed_url"))
        assertTrue(edge.contains("createSignedUrl"))
        assertTrue(edge.contains("SUPABASE_SERVICE_ROLE_KEY"))
        assertFalse(edge.contains("console.log(signed"))
    }

    @Test
    fun migration1066ClosesSignedUrlFeedMessagesAndMime() {
        val sql = source(
            "infra/supabase-canonical/supabase/migrations/20260904180000_1066_sec_p1_closure.sql"
        )
        assertTrue(sql.contains("canon_storage_select_public_media"))
        assertTrue(sql.contains("drop policy if exists canon_storage_select"))
        assertTrue(sql.contains("p_cursor_created_at"))
        assertTrue(sql.contains("p_cursor_id"))
        assertTrue(sql.contains("allowed_mime_types"))
        assertTrue(sql.contains("video/mp4"))
        assertFalse(sql.contains("where id in ('moderation-evidence'"))
    }

    @Test
    fun undeployedEdgeFunctionsStayInRepoOnly() {
        val push = source("supabase/functions/push/index.ts")
        val del = source("supabase/functions/delete-account/index.ts")
        val klipy = source("infra/supabase-canonical/supabase/functions/klipy-proxy/index.ts")
        assertTrue(push.isNotBlank())
        assertTrue(del.isNotBlank())
        assertTrue(klipy.isNotBlank())
        val vitacora = source(
            "infra/supabase-canonical/supabase/functions/vitacora-import-analyze/index.ts"
        )
        assertTrue(vitacora.contains("imports.enabled"))
        assertTrue(vitacora.contains("FEATURE_TEMPORARILY_DISABLED"))
        assertFalse(vitacora.contains("SERVICE_ROLE"))
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()
}
