package com.comunidapp.app.domain.user

import com.comunidapp.app.data.files.SignedUrlMintCoordinator
import com.comunidapp.app.data.mock.InMemoryDataStore
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.domain.capability.StartupSessionLatchStore
import com.comunidapp.app.domain.context.OperationalContextProvider
import com.comunidapp.app.domain.organization.OrganizationContextProvider

/**
 * Identity-scoped in-memory caches that survive A → B if logout only
 * clears SessionViewModel fields. Must run on logout and on account switch.
 *
 * [SessionGeneration.invalidate] runs first so in-flight responses die.
 * Pet cache clear runs last: it rotates the epoch again and restarts polling
 * only after every other user-scoped write has been invalidated.
 */
object AccountIdentityCleanup {
    fun clear() {
        SessionGeneration.invalidate()
        StartupSessionLatchStore.clear()
        com.comunidapp.app.data.local.AdoptionApplicantProfileStore.clear()
        com.comunidapp.app.domain.onboarding.onb02.InitialOnboardingGate.markResolving()
        runCatching { com.comunidapp.app.domain.social.ReelPublishController.get().onSessionEnded() }
        runCatching { com.comunidapp.app.viewmodel.files.FileSessionCleanup.clear() }
        SessionResolvedPerson.clear()
        ProfileHydrationStore.clear()
        OperationalContextProvider.clear()
        OrganizationContextProvider.clear()
        DataProvider.feedRepository.clearAccountCache()
        DataProvider.lostFoundRepository.clearAccountCache()
        DataProvider.adoptionRepository.clearAccountCache()
        DataProvider.clearUserScopedMockStores()
        SignedUrlMintCoordinator.clear()
        InMemoryDataStore.clearUserScopedSession()
        DataProvider.petRepository.clearAccountCache()
    }
}
