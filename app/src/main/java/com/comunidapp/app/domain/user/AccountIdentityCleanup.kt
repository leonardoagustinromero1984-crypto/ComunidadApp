package com.comunidapp.app.domain.user

import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.domain.context.OperationalContextProvider
import com.comunidapp.app.domain.organization.OrganizationContextProvider

/**
 * Identity-scoped in-memory caches that survive A → B if logout only
 * clears SessionViewModel fields. Must run on logout and on account switch.
 */
object AccountIdentityCleanup {
    fun clear() {
        runCatching { com.comunidapp.app.domain.social.ReelPublishController.get().onSessionEnded() }
        runCatching { com.comunidapp.app.viewmodel.files.FileSessionCleanup.clear() }
        SessionResolvedPerson.clear()
        ProfileHydrationStore.clear()
        OperationalContextProvider.clear()
        OrganizationContextProvider.clear()
        DataProvider.petRepository.clearAccountCache()
        DataProvider.feedRepository.clearAccountCache()
    }
}
