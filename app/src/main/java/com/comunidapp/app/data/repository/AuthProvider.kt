package com.comunidapp.app.data.repository

import com.comunidapp.app.BuildConfig

object AuthProvider {

    val repository: AuthRepository by lazy {
        if (BuildConfig.FIREBASE_ENABLED) {
            FirebaseAuthRepository()
        } else {
            MockAuthRepository()
        }
    }

    val isFirebaseEnabled: Boolean get() = BuildConfig.FIREBASE_ENABLED
}
