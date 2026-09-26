package com.comunidapp.app.domain.social

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SocialAvatarAclMigrationTest {

    @Test
    fun migration1082IsAvatarOnlyAndDoesNotAutoDelete() {
        val sql = File(
            "../infra/supabase-canonical/supabase/migrations/20260913140000_1082_social_avatar_acl_and_private_bucket.sql"
        ).readText()
        assertTrue(sql.contains("_acl_social_avatar_readable"))
        assertTrue(sql.contains("_acl_accepted_connection"))
        assertTrue(sql.contains("per.avatar_asset_id = p_asset.id"))
        assertTrue(sql.contains("pet.avatar_asset_id = p_asset.id"))
        assertTrue(sql.contains("role in ('OWNER', 'PRINCIPAL')"))
        assertTrue(sql.contains("AVATAR_MIGRATE_COPY_MISSING"))
        assertFalse(sql.contains("grant execute on function public._acl_social_avatar_readable"))
        assertFalse(sql.contains("for r in"))
        assertTrue(sql.contains("_acl_media_readable"))
        assertTrue(sql.contains("Does not widen general private media"))
    }
}
