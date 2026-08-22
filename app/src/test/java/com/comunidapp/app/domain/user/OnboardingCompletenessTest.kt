package com.comunidapp.app.domain.user

import com.comunidapp.app.data.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingCompletenessTest {

    @Test
    fun missingLocalityAsksOnlyGeographyWhenUsernameExists() {
        val user = User(
            id = "u1",
            name = "Leo",
            email = "leo@email.com",
            username = "leonardo",
            displayName = "Leonardo",
            birthDate = "1990-01-15",
            homeLocalityId = null
        )
        assertEquals(setOf("home_locality_id"), OnboardingCompleteness.missingFields(user))
        assertFalse(OnboardingCompleteness.isComplete(user))
    }

    @Test
    fun unchangedSelfUsernameIsAccepted() {
        assertTrue(OnboardingCompleteness.isUnchangedSelfUsername("Leonardo", "leonardo"))
        assertFalse(OnboardingCompleteness.isUnchangedSelfUsername("otro", "leonardo"))
    }

    @Test
    fun completeWhenUsernameAndLocalityPresent() {
        val user = User(
            id = "u1",
            name = "Leo",
            email = "leo@email.com",
            username = "leonardo",
            displayName = "Leonardo",
            birthDate = "1990-01-15",
            homeLocalityId = "loc-ar-loc-caba"
        )
        assertTrue(OnboardingCompleteness.missingFields(user).isEmpty())
        assertTrue(OnboardingCompleteness.isComplete(user))
    }
}
