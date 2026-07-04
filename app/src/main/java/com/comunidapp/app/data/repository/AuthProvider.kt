package com.comunidapp.app.data.repository

import com.comunidapp.app.BuildConfig

object AuthProvider {

    val repository: AuthRepository by lazy {
        if (BuildConfig.SUPABASE_ENABLED) {
            SupabaseAuthRepository()
        } else {
            MockAuthRepository()
        }
    }

    /** True when the app uses Supabase instead of local mock auth/data. */
    val isRemoteBackendEnabled: Boolean get() = BuildConfig.SUPABASE_ENABLED

    @Deprecated("Use isRemoteBackendEnabled", ReplaceWith("isRemoteBackendEnabled"))
    val isFirebaseEnabled: Boolean get() = isRemoteBackendEnabled
}
