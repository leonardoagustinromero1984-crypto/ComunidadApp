package com.comunidapp.app.domain.alerts

import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.LostFoundStatus
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.data.model.PetSpecies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertMapFilterTest {

    private fun post(
        id: String,
        type: LostFoundType,
        status: LostFoundStatus = LostFoundStatus.ACTIVE,
        lat: Double? = -34.58,
        lng: Double? = -58.43,
        location: String = "Palermo"
    ) = LostFoundPost(
        id = id,
        authorId = "u1",
        authorName = "Demo",
        type = type,
        petName = "Luna",
        species = PetSpecies.DOG,
        location = location,
        description = "desc",
        contactInfo = "x",
        status = status,
        latitude = lat,
        longitude = lng,
        date = "01/01/2026",
        createdAt = System.currentTimeMillis()
    )

    @Test
    fun mapEligible_requiresActiveAndValidCoords() {
        assertTrue(AlertLocationPrivacy.isMapEligible(post("1", LostFoundType.LOST)))
        assertFalse(
            AlertLocationPrivacy.isMapEligible(
                post("2", LostFoundType.LOST, status = LostFoundStatus.RESOLVED)
            )
        )
        assertFalse(
            AlertLocationPrivacy.isMapEligible(
                post("3", LostFoundType.FOUND, lat = null, lng = null)
            )
        )
        assertFalse(
            AlertLocationPrivacy.isMapEligible(
                post("4", LostFoundType.FOUND, lat = 0.0, lng = 0.0)
            )
        )
    }

    @Test
    fun publicLocation_neverExposesExactPrecision() {
        val pub = AlertLocationPrivacy.publicLocation(
            post("1", LostFoundType.LOST, lat = -34.58891234, lng = -58.43005678)
        )
        assertEquals("Palermo", pub.zoneLabel)
        assertTrue(pub.hasValidCoordinates)
        assertEquals(-34.589, pub.displayLatitude!!, 0.0001)
        assertEquals(-58.430, pub.displayLongitude!!, 0.0001)
    }

    @Test
    fun typeFilters_areDeterministic() {
        assertEquals(AlertMapTypeFilter.ALL, AlertMapTypeFilter.valueOf("ALL"))
        assertEquals(AlertMapTypeFilter.LOST, AlertMapTypeFilter.valueOf("LOST"))
        assertEquals(AlertMapTypeFilter.FOUND, AlertMapTypeFilter.valueOf("FOUND"))
    }
}
