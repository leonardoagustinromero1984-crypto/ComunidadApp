package com.comunidapp.app.domain.moderation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModerationLegacyCompatibilityTest {

    @Test
    fun maps_legacy_user_post_comment() {
        assertEquals(
            ModerationTargetType.USER_PROFILE,
            ModerationLegacyTargets.fromLegacyType("USER")
        )
        assertEquals(ModerationTargetType.POST, ModerationLegacyTargets.fromLegacyType("POST"))
        assertEquals(ModerationTargetType.COMMENT, ModerationLegacyTargets.fromLegacyType("COMMENT"))
    }

    @Test
    fun round_trip_legacy_compatible_types() {
        assertEquals("USER", ModerationLegacyTargets.toLegacyTypeOrNull(ModerationTargetType.USER_PROFILE))
        assertEquals("POST", ModerationLegacyTargets.toLegacyTypeOrNull(ModerationTargetType.POST))
        assertEquals("COMMENT", ModerationLegacyTargets.toLegacyTypeOrNull(ModerationTargetType.COMMENT))
        assertNull(ModerationLegacyTargets.toLegacyTypeOrNull(ModerationTargetType.ORGANIZATION))
        assertTrue(ModerationLegacyTargets.isLegacyCompatible(ModerationTargetType.POST))
        assertFalse(ModerationLegacyTargets.isLegacyCompatible(ModerationTargetType.PET_PROFILE))
    }

    @Test
    fun legacy_status_mapping_does_not_imply_real_measure() {
        assertEquals(ModerationReportStatus.OPEN, ModerationReportRules.mapLegacyStatus("OPEN"))
        assertEquals(ModerationReportStatus.IN_REVIEW, ModerationReportRules.mapLegacyStatus("REVIEWED"))
        assertEquals(ModerationReportStatus.DISMISSED, ModerationReportRules.mapLegacyStatus("DISMISSED"))
        assertEquals(
            ModerationReportStatus.ACTION_REQUIRED,
            ModerationReportRules.mapLegacyStatus("ACTIONED")
        )
        assertFalse(ModerationReportRules.legacyActionedMeansRealMeasure())
    }

    @Test
    fun unknown_legacy_type_falls_back_to_other() {
        assertEquals(ModerationTargetType.OTHER, ModerationLegacyTargets.fromLegacyType("WEIRD"))
    }
}
