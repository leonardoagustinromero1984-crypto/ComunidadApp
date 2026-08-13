package com.comunidapp.shared.vertical

import com.comunidapp.shared.adoption.AdoptionDraft
import com.comunidapp.shared.adoption.AdoptionDraftValidator
import com.comunidapp.shared.adoption.AdoptionId
import com.comunidapp.shared.adoption.FakeAdoptionRepository
import com.comunidapp.shared.domain.lostfound.LostFoundCaseType
import com.comunidapp.shared.location.ApproximateLocation
import com.comunidapp.shared.lostfound.FakeLostFoundRepository
import com.comunidapp.shared.lostfound.FakeLostFoundSeed
import com.comunidapp.shared.lostfound.LostFoundDraft
import com.comunidapp.shared.lostfound.LostFoundDraftValidator
import com.comunidapp.shared.lostfound.LostFoundId
import com.comunidapp.shared.lostfound.LostFoundListFilter
import com.comunidapp.shared.ui.ErrorSanitizer
import com.comunidapp.shared.ui.VerticalLoadState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class LostFoundAdoptionVerticalTest {

    private val zone = ApproximateLocation("Palermo", "CABA", "AR")

    @Test
    fun lost_list_loading_then_content() = runTest {
        val repo = FakeLostFoundRepository()
        val firstNonLoading = repo.observeList(LostFoundListFilter.LOST)
            .filterNot { it is VerticalLoadState.Loading }
            .first()
        val content = assertIs<VerticalLoadState.Content<List<*>>>(firstNonLoading)
        assertTrue(content.data.isNotEmpty())
    }

    @Test
    fun lost_list_empty() = runTest {
        val repo = FakeLostFoundRepository(seeds = emptyList())
        val empty = assertIs<VerticalLoadState.Empty>(
            repo.observeList(LostFoundListFilter.LOST)
                .filterNot { it is VerticalLoadState.Loading }
                .first()
        )
        assertEquals(VerticalLoadState.Empty, empty)
    }

    @Test
    fun found_list_content() = runTest {
        val repo = FakeLostFoundRepository()
        val content = assertIs<VerticalLoadState.Content<*>>(
            repo.observeList(LostFoundListFilter.FOUND)
                .filterNot { it is VerticalLoadState.Loading }
                .first()
        )
        @Suppress("UNCHECKED_CAST")
        val items = content.data as List<com.comunidapp.shared.lostfound.LostFoundSummary>
        assertTrue(items.all { it.type == LostFoundCaseType.FOUND })
        assertTrue(items.any { it.displayName == null })
    }

    @Test
    fun filter_lost_only() = runTest {
        val repo = FakeLostFoundRepository()
        val content = assertIs<VerticalLoadState.Content<*>>(
            repo.observeList(LostFoundListFilter.LOST)
                .filterNot { it is VerticalLoadState.Loading }
                .first()
        )
        @Suppress("UNCHECKED_CAST")
        val items = content.data as List<com.comunidapp.shared.lostfound.LostFoundSummary>
        assertTrue(items.all { it.type == LostFoundCaseType.LOST })
    }

    @Test
    fun filter_found_only() = runTest {
        val repo = FakeLostFoundRepository()
        val content = assertIs<VerticalLoadState.Content<*>>(
            repo.observeList(LostFoundListFilter.FOUND)
                .filterNot { it is VerticalLoadState.Loading }
                .first()
        )
        @Suppress("UNCHECKED_CAST")
        val items = content.data as List<com.comunidapp.shared.lostfound.LostFoundSummary>
        assertEquals(1, items.size)
        assertEquals(LostFoundCaseType.FOUND, items.first().type)
    }

    @Test
    fun lost_detail_content() = runTest {
        val repo = FakeLostFoundRepository()
        val detail = assertIs<VerticalLoadState.Content<*>>(
            repo.observeDetail(LostFoundId("demo-lost-luna"))
                .filterNot { it is VerticalLoadState.Loading }
                .first()
        )
        val body = detail.data as com.comunidapp.shared.lostfound.LostFoundDetail
        assertEquals(LostFoundCaseType.LOST, body.type)
        assertEquals("Luna", body.displayName)
    }

    @Test
    fun found_detail_content() = runTest {
        val repo = FakeLostFoundRepository()
        val detail = assertIs<VerticalLoadState.Content<*>>(
            repo.observeDetail(LostFoundId("demo-found-gato"))
                .filterNot { it is VerticalLoadState.Loading }
                .first()
        )
        val body = detail.data as com.comunidapp.shared.lostfound.LostFoundDetail
        assertEquals(LostFoundCaseType.FOUND, body.type)
        assertNull(body.displayName)
    }

    @Test
    fun location_sanitized_no_coords() {
        val label = zone.displayLabel()
        assertFalse(label.contains("lat", ignoreCase = true))
        assertFalse(label.contains("-34"))
        assertTrue(label.contains("Palermo"))
    }

    @Test
    fun lost_found_public_model_no_pii_fields() = runTest {
        val repo = FakeLostFoundRepository()
        val detail = assertIs<VerticalLoadState.Content<*>>(
            repo.observeDetail(LostFoundId("demo-lost-luna"))
                .filterNot { it is VerticalLoadState.Loading }
                .first()
        )
        val body = detail.data as com.comunidapp.shared.lostfound.LostFoundDetail
        assertFalse(body.description.contains("+54"))
        assertFalse(body.description.contains("@"))
        assertFalse(body.approximateLocation.displayLabel().contains("lat="))
        // Public model: only safe fields (no ownerId/userId/coords properties by design).
        assertTrue(body.publisherDisplayName != null)
        assertTrue(body.publicCode != null)
    }

    @Test
    fun lost_found_error_sanitized() = runTest {
        val repo = FakeLostFoundRepository(fail = true)
        val err = assertIs<VerticalLoadState.Error>(
            repo.observeList(LostFoundListFilter.ALL)
                .filterIsInstance<VerticalLoadState.Error>()
                .first()
        )
        assertTrue(err.message.isNotBlank())
        assertFalse(err.message.contains("LOST_FOUND_UNAVAILABLE"))
    }

    @Test
    fun adoption_list_loading_then_content() = runTest {
        val repo = FakeAdoptionRepository()
        val content = assertIs<VerticalLoadState.Content<*>>(
            repo.observeList()
                .filterNot { it is VerticalLoadState.Loading }
                .first()
        )
        assertTrue((content.data as List<*>).isNotEmpty())
    }

    @Test
    fun adoption_list_empty() = runTest {
        val repo = FakeAdoptionRepository(seeds = emptyList())
        assertIs<VerticalLoadState.Empty>(
            repo.observeList()
                .filterNot { it is VerticalLoadState.Loading }
                .first()
        )
    }

    @Test
    fun adoption_detail_content() = runTest {
        val repo = FakeAdoptionRepository()
        val detail = assertIs<VerticalLoadState.Content<*>>(
            repo.observeDetail(AdoptionId("demo-adopt-nube"))
                .filterNot { it is VerticalLoadState.Loading }
                .first()
        )
        val body = detail.data as com.comunidapp.shared.adoption.AdoptionDetail
        assertEquals("Nube", body.displayName)
        assertEquals("Gato", body.speciesLabel)
    }

    @Test
    fun adoption_privacy_no_phone_email_coords() = runTest {
        val repo = FakeAdoptionRepository()
        val detail = assertIs<VerticalLoadState.Content<*>>(
            repo.observeDetail(AdoptionId("demo-adopt-nube"))
                .filterNot { it is VerticalLoadState.Loading }
                .first()
        )
        val body = detail.data as com.comunidapp.shared.adoption.AdoptionDetail
        assertFalse(body.description.contains("+54"))
        assertFalse(body.description.contains("@"))
        assertFalse(body.approximateLocation.displayLabel().contains("lat="))
        assertTrue(body.publisherDisplayName != null)
    }

    @Test
    fun fake_repositories_deterministic() = runTest {
        val a = FakeLostFoundRepository()
        val b = FakeLostFoundRepository()
        val ca = assertIs<VerticalLoadState.Content<*>>(
            a.observeList(LostFoundListFilter.ALL).filterNot { it is VerticalLoadState.Loading }.first()
        )
        val cb = assertIs<VerticalLoadState.Content<*>>(
            b.observeList(LostFoundListFilter.ALL).filterNot { it is VerticalLoadState.Loading }.first()
        )
        assertEquals((ca.data as List<*>).size, (cb.data as List<*>).size)
        assertEquals(
            FakeAdoptionRepository().observeDetail(AdoptionId("demo-adopt-teo"))
                .filterNot { it is VerticalLoadState.Loading }.first().let {
                    (it as VerticalLoadState.Content<*>).data
                }.let { (it as com.comunidapp.shared.adoption.AdoptionDetail).displayName },
            "Teo"
        )
    }

    @Test
    fun refresh_keeps_deterministic_content() = runTest {
        val repo = FakeLostFoundRepository()
        val before = assertIs<VerticalLoadState.Content<*>>(
            repo.observeList(LostFoundListFilter.ALL).filterNot { it is VerticalLoadState.Loading }.first()
        )
        repo.refresh()
        val after = assertIs<VerticalLoadState.Content<*>>(
            repo.observeList(LostFoundListFilter.ALL).filterNot { it is VerticalLoadState.Loading }.first()
        )
        assertEquals((before.data as List<*>).size, (after.data as List<*>).size)
    }

    @Test
    fun navigation_ids_lost_found_adoption_stable() {
        assertEquals("demo-lost-luna", LostFoundId("demo-lost-luna").value)
        assertEquals("demo-found-gato", LostFoundId("demo-found-gato").value)
        assertEquals("demo-adopt-nube", AdoptionId("demo-adopt-nube").value)
        val seeds: List<FakeLostFoundSeed> = FakeLostFoundRepository.defaultSeeds()
        assertTrue(seeds.any { it.summary.type == LostFoundCaseType.LOST })
        assertTrue(seeds.any { it.summary.type == LostFoundCaseType.FOUND })
    }

    @Test
    fun draft_lost_validation() {
        val bad = LostFoundDraft(
            type = LostFoundCaseType.LOST,
            displayName = null,
            speciesLabel = "Perro",
            description = "corta",
            approximateLocation = zone
        )
        assertTrue(LostFoundDraftValidator.validate(bad).isFailure)
        val good = bad.copy(displayName = "Luna", description = "Descripción válida de aviso")
        assertTrue(LostFoundDraftValidator.validate(good).isSuccess)
    }

    @Test
    fun draft_found_validation() {
        val good = LostFoundDraft(
            type = LostFoundCaseType.FOUND,
            displayName = null,
            speciesLabel = "Gato",
            description = "Encontrado en la esquina",
            approximateLocation = zone
        )
        assertTrue(LostFoundDraftValidator.validate(good).isSuccess)
        val bad = good.copy(speciesLabel = " ")
        assertTrue(LostFoundDraftValidator.validate(bad).isFailure)
    }

    @Test
    fun draft_adoption_validation() {
        val bad = AdoptionDraft(
            displayName = "",
            speciesLabel = "Perro",
            description = "corto",
            approximateLocation = zone
        )
        assertTrue(AdoptionDraftValidator.validate(bad).isFailure)
        val good = bad.copy(displayName = "Teo", description = "Busca hogar responsable")
        assertTrue(AdoptionDraftValidator.validate(good).isSuccess)
    }

    @Test
    fun error_sanitizer_codes() {
        val msg = ErrorSanitizer.sanitize(IllegalStateException("LOST_FOUND_NOT_FOUND"))
        assertEquals("No encontramos ese contenido.", msg)
        val adoption = ErrorSanitizer.sanitize(IllegalStateException("ADOPTION_DRAFT_NAME_BLANK"))
        assertEquals("No pudimos completar la operación. Intentá nuevamente.", adoption)
    }

    @Test
    fun shared_contracts_consistent_modes() {
        assertEquals("SHARED_FAKE", FakeLostFoundRepository().dataMode.name)
        assertEquals("SHARED_FAKE", FakeAdoptionRepository().dataMode.name)
    }
}
