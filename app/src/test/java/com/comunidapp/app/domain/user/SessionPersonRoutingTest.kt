package com.comunidapp.app.domain.user

import com.comunidapp.app.data.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPersonRoutingTest {

    @Test
    fun freshStateDoesNotRouteJwtStubToCompleteProfile() {
        val stub = User(
            id = "google-uid",
            name = "Leo",
            email = "leo@gmail.com",
            onboardingStatus = "IN_PROGRESS"
        )
        assertTrue(SessionPersonRouting.isJwtStub(stub))
        assertEquals(
            SessionPersonRouting.Decision.LOADING,
            SessionPersonRouting.decide(stub, personResolved = false)
        )
        assertEquals(
            SessionPersonRouting.Decision.LOADING,
            SessionPersonRouting.decide(person = null, personResolved = false)
        )
    }

    @Test
    fun resolvedIncompletePersonGoesToCompleteProfile() {
        val stubShapedPerson = User(
            id = "google-uid",
            name = "Leo",
            email = "leo@gmail.com",
            onboardingStatus = "IN_PROGRESS"
        )
        assertEquals(
            SessionPersonRouting.Decision.COMPLETE_PROFILE,
            SessionPersonRouting.decide(stubShapedPerson, personResolved = true)
        )
    }

    @Test
    fun existingCompletePersonFirstDecisionIsHome() {
        val person = User(
            id = "google-uid",
            name = "Leo",
            email = "leo@gmail.com",
            username = "leo.ver",
            displayName = "Leo",
            birthDate = "1990-01-01",
            homeLocalityId = "loc-ar-caba",
            onboardingStatus = "COMPLETED",
            accountStatus = "ACTIVE"
        )
        assertFalse(SessionPersonRouting.isJwtStub(person))
        assertEquals(
            SessionPersonRouting.Decision.HOME,
            SessionPersonRouting.decide(person, personResolved = true)
        )
    }

    @Test
    fun missingPersonAfterResolvedFetchGoesToCompleteProfile() {
        assertEquals(
            SessionPersonRouting.Decision.COMPLETE_PROFILE,
            SessionPersonRouting.decide(person = null, personResolved = true)
        )
    }
}
