package com.comunidapp.app.domain.onboarding.onb02

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Onb02SelectorVisualOrderTest {

    @Test
    fun firstLevel_fosterIsBetweenRescuerAndRefuge() {
        assertEquals(
            listOf(
                ProfileActorKind.PERSON,
                ProfileActorKind.INDEPENDENT_RESCUER,
                ProfileActorKind.FOSTER,
                ProfileActorKind.REFUGE,
                ProfileActorKind.INDEPENDENT_PROFESSIONAL,
                ProfileActorKind.BUSINESS
            ),
            ProfileActorTaxonomy.firstLevel
        )
        val labels = ProfileActorTaxonomy.firstLevelLabels()
        val foster = labels.indexOf("Hogar de tránsito")
        val rescuer = labels.indexOf("Rescatista independiente")
        val refuge = labels.indexOf("Refugio / Organización de rescate")
        assertTrue(foster >= 0 && rescuer >= 0 && refuge >= 0)
        assertTrue(rescuer < foster)
        assertTrue(foster < refuge)
        assertEquals(ProfileActorKind.PERSON, ProfileActorTaxonomy.firstLevel.first())
    }
}
