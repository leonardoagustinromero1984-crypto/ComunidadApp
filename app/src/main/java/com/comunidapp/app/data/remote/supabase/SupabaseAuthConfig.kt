package com.comunidapp.app.data.remote.supabase

object SupabaseAuthConfig {
    const val SCHEME = "com.comunidapp.app"
    const val HOST = "login-callback"
    const val REDIRECT_URL = "$SCHEME://$HOST"
}
