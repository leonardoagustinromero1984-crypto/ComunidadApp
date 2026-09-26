package com.comunidapp.app.data.local

import android.content.Context
import com.comunidapp.app.LeoverApplication
import com.comunidapp.app.domain.location.LocationConsentContracts

interface LocationConsentStore {
    fun rationaleAccepted(userId: String): Boolean
    fun markRationaleAccepted(userId: String)
    fun permanentlyDeniedHint(userId: String): Boolean
    fun markPermanentlyDeniedHint(userId: String, value: Boolean)
}

class SharedPreferencesLocationConsentStore(
    private val contextProvider: () -> Context? = {
        runCatching { LeoverApplication.instance }.getOrNull()
    }
) : LocationConsentStore {
    private val prefs get() = contextProvider()?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun rationaleAccepted(userId: String): Boolean =
        prefs?.getBoolean(rationaleKey(userId), false) == true

    override fun markRationaleAccepted(userId: String) {
        prefs?.edit()?.putBoolean(rationaleKey(userId), true)?.apply()
    }

    override fun permanentlyDeniedHint(userId: String): Boolean =
        prefs?.getBoolean(deniedKey(userId), false) == true

    override fun markPermanentlyDeniedHint(userId: String, value: Boolean) {
        prefs?.edit()?.putBoolean(deniedKey(userId), value)?.apply()
    }

    private fun rationaleKey(userId: String) =
        "loc_rationale_${userId}_${LocationConsentContracts.CONSENT_VERSION}"
    private fun deniedKey(userId: String) = "loc_denied_$userId"

    companion object {
        private const val PREFS = "leover_location_consent"
    }
}

object LocationConsentStoreProvider {
    @Volatile
    var override: LocationConsentStore? = null
    val instance: LocationConsentStore
        get() = override ?: SharedPreferencesLocationConsentStore()
}
