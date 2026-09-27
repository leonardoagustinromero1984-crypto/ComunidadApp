package com.comunidapp.app.domain.lostfound

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.domain.pets.LostPetSelector
import com.comunidapp.app.domain.schedule.ProviderWeeklySchedule
import com.comunidapp.app.domain.schedule.WeeklyHoursBulkApply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityCare02PhysicalRound3Test {

    @Test
    fun foundLocationNeverKeepsMapPlaceholder() {
        val label = LostFoundLocationDisplay.visibleOrNearby(
            locationLabel = null,
            localityId = null,
            note = "Zona marcada en el mapa · Encontrada"
        )
        assertEquals(LostFoundLocationDisplay.NEARBY_FALLBACK, label)
        assertEquals(
            "Encontrada",
            LostFoundLocationDisplay.noteWithoutPlaceholder("Zona marcada en el mapa · Encontrada")
        )
        assertEquals(
            "San Vicente, Buenos Aires",
            LostFoundLocationDisplay.humanLabel(locationLabel = "San Vicente, Buenos Aires")
        )
    }

    @Test
    fun lostSelectorHidesFoundCasePlaceholder() {
        val real = samplePet(name = "lala", origin = "STANDARD")
        val unnamedReal = samplePet(name = "Sin nombre", origin = "STANDARD")
        val foundCase = samplePet(name = "Sin nombre", origin = "FOUND_CASE")
        val archived = samplePet(name = "Toby", origin = "STANDARD", archived = true)
        val selected = LostPetSelector.selectable(listOf(real, unnamedReal, foundCase, archived))
        assertEquals(listOf("lala", "Sin nombre"), selected.map { it.name })
        assertFalse(selected.any { it.originKind == "FOUND_CASE" })
    }

    @Test
    fun weeklyHoursApplyWeekdaysThenSaturday() {
        var schedule = ProviderWeeklySchedule.emptyTemplate()
        schedule = WeeklyHoursBulkApply.apply(
            schedule,
            WeeklyHoursBulkApply.Range(setOf(1, 2, 3, 4, 5), "09:00", "18:00")
        )
        assertTrue(schedule.day(1)?.closed == false)
        assertEquals("09:00", schedule.day(1)?.opensAt)
        assertTrue(WeeklyHoursBulkApply.daysWithoutRule(schedule).contains(6))
        schedule = WeeklyHoursBulkApply.apply(
            schedule,
            WeeklyHoursBulkApply.Range(setOf(6), "09:00", "13:00")
        )
        assertEquals("13:00", schedule.day(6)?.closesAt)
        assertEquals("18:00", schedule.day(5)?.closesAt)
    }

    private fun samplePet(
        name: String,
        origin: String,
        archived: Boolean = false
    ) = Pet(
        id = name,
        name = name,
        species = PetSpecies.DOG,
        sex = PetSex.FEMALE,
        ageYears = 1,
        size = PetSize.MEDIUM,
        description = "",
        originKind = origin,
        archivedAt = if (archived) 1L else null
    )
}
