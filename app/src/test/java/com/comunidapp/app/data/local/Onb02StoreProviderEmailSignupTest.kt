package com.comunidapp.app.data.local

import com.comunidapp.app.domain.onboarding.onb02.Onb02FlowKind
import org.junit.Assert.assertEquals
import org.junit.Test
class Onb02StoreProviderEmailSignupTest {

    @Test
    fun completePersonWithoutRemoteTutorial_startsFullOnboarding() {
        val store = InMemoryOnb02Store()
        Onb02StoreProvider.override = store
        try {
            val userId = "existing-complete-user"
            val kind = Onb02StoreProvider.decideEntry(
                userId = userId,
                justCompletedProfileSetup = false,
                personOnboardingComplete = true,
                remoteTutorialFlowCompleted = false
            )
            assertEquals(Onb02FlowKind.FULL_ONBOARDING, kind)
            assertEquals(Onb02Completion.FULL_PENDING, store.completion(userId))
        } finally {
            Onb02StoreProvider.override = null
        }
    }

    @Test
    fun remoteTutorialCompleted_skipsSelectorAfterReinstall() {
        val store = InMemoryOnb02Store()
        Onb02StoreProvider.override = store
        try {
            val userId = "existing-complete-user"
            val kind = Onb02StoreProvider.decideEntry(
                userId = userId,
                justCompletedProfileSetup = false,
                personOnboardingComplete = true,
                remoteTutorialFlowCompleted = true
            )
            assertEquals(null, kind)
            assertEquals(Onb02Completion.COMPLETED, store.completion(userId))
        } finally {
            Onb02StoreProvider.override = null
        }
    }

    @Test
    fun justCompletedProfileSetup_startsFullOnboarding() {
        val store = InMemoryOnb02Store()
        Onb02StoreProvider.override = store
        try {
            val kind = Onb02StoreProvider.decideEntry(
                userId = "new-after-profile",
                justCompletedProfileSetup = true,
                personOnboardingComplete = true
            )
            assertEquals(Onb02FlowKind.FULL_ONBOARDING, kind)
            assertEquals(Onb02Completion.FULL_PENDING, store.completion("new-after-profile"))
        } finally {
            Onb02StoreProvider.override = null
        }
    }
}
