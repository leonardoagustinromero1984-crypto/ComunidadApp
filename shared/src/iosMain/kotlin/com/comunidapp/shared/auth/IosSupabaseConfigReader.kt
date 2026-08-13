package com.comunidapp.shared.auth

import platform.Foundation.NSBundle

/**
 * Lee URL/anon desde Info.plist (inyectadas vía xcconfig / build settings).
 * internal: no exportar a ObjC/Swift.
 */
internal object IosSupabaseConfigReader {
    fun read(): SharedSupabaseConfig? {
        val bundle = NSBundle.mainBundle
        val url = bundle.objectForInfoDictionaryKey("SUPABASE_URL") as? String
        val key = bundle.objectForInfoDictionaryKey("SUPABASE_ANON_KEY") as? String
        if (url.isNullOrBlank() || key.isNullOrBlank()) return null
        return SharedSupabaseConfig(url = url.trim(), anonKey = key.trim()).usableOrNull()
    }
}
