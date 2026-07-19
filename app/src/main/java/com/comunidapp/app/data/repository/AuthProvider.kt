package com.comunidapp.app.data.repository

import com.comunidapp.app.core.config.AppConfigProvider

object AuthProvider {

    val repository: AuthRepository by lazy {
        if (AppConfigProvider.featureFlags().useSupabase) {
            SupabaseAuthRepository()
        } else {
            MockAuthRepository()
        }
    }

    /** True when the app uses Supabase instead of local mock auth/data. */
    val isRemoteBackendEnabled: Boolean get() = AppConfigProvider.featureFlags().useSupabase

    @Deprecated("Use isRemoteBackendEnabled", ReplaceWith("isRemoteBackendEnabled"))
    val isFirebaseEnabled: Boolean get() = isRemoteBackendEnabled
}
