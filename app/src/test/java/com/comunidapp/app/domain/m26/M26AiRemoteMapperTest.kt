package com.comunidapp.app.domain.m26

import com.comunidapp.app.data.model.M26AssistanceTopic
import com.comunidapp.app.data.model.M26ConfidenceBand
import com.comunidapp.app.data.model.M26DuplicateStatus
import com.comunidapp.app.data.model.M26RecommendationKind
import com.comunidapp.app.data.model.M26VisualMatchStatus
import com.comunidapp.app.data.remote.supabase.m26.toM26PublicAssistance
import com.comunidapp.app.data.remote.supabase.m26.toM26PublicDuplicate
import com.comunidapp.app.data.remote.supabase.m26.toM26PublicRecommendation
import com.comunidapp.app.data.remote.supabase.m26.toM26PublicVisualMatch
import com.comunidapp.app.data.remote.supabase.m26.toM26VisualMatchSuggestion
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M26AiRemoteMapperTest {
    @Test
    fun publicVisualMatchMapsScoreWithoutInternalIds() {
        val match = buildJsonObject {
            put("source_label", "Foto A")
            put("target_label", "Foto B")
            put("score", 0.91)
            put("confidence_band", "HIGH")
            put("status", "PENDING")
        }.toM26PublicVisualMatch()

        assertEquals(M26ConfidenceBand.HIGH, match.confidenceBand)
        assertEquals(0.91, match.score, 0.001)
        assertFalse(match.toString().contains("requester_user_id"))
    }

    @Test
    fun publicDuplicateMapsSimilarityScore() {
        val duplicate = buildJsonObject {
            put("primary_label", "Perfil A")
            put("duplicate_label", "Perfil B")
            put("similarity_score", 0.88)
            put("status", "OPEN")
        }.toM26PublicDuplicate()

        assertEquals(M26DuplicateStatus.OPEN, duplicate.status)
        assertEquals(0.88, duplicate.similarityScore, 0.001)
    }

    @Test
    fun publicAssistanceMapsTopicAndSummary() {
        val session = buildJsonObject {
            put("topic", "LOST_PET")
            put("status", "ACTIVE")
            put("summary", "Sesión stub de orientación")
        }.toM26PublicAssistance()

        assertEquals(M26AssistanceTopic.LOST_PET, session.topic)
        assertTrue(session.summary.contains("stub"))
    }

    @Test
    fun publicRecommendationMapsHumanReviewFlag() {
        val rec = buildJsonObject {
            put("title", "Prestador cercano")
            put("rationale", "Coincide con búsquedas recientes")
            put("kind", "PROVIDER")
            put("human_reviewed", true)
            put("approved_for_display", true)
        }.toM26PublicRecommendation()

        assertEquals(M26RecommendationKind.PROVIDER, rec.kind)
        assertTrue(rec.humanReviewed)
        assertTrue(rec.approvedForDisplay)
    }

    @Test
    fun internalMatchMapsTimestamps() {
        val match = buildJsonObject {
            put("id", "match-1")
            put("requester_user_id", "user-1")
            put("source_label", "A")
            put("target_label", "B")
            put("score", 0.75)
            put("confidence_band", "MEDIUM")
            put("status", "PENDING")
            put("created_at", "2026-01-01T00:00:00Z")
            put("updated_at", "2026-01-02T00:00:00Z")
        }.toM26VisualMatchSuggestion()

        assertEquals("user-1", match.requesterUserId)
        assertEquals(M26VisualMatchStatus.PENDING, match.status)
    }
}
