package com.comunidapp.app.ui.map

import android.util.Log

/**
 * One-shot map tile diagnosis. Never logs the API key.
 */
object MapsTileDiagnostics {
    private const val TAG = "LeoVerMaps"

    @Volatile
    private var configLogged = false

    @Volatile
    private var loadLogged = false

    fun logConfigOnce(configured: Boolean, keyLength: Int) {
        if (configLogged) return
        configLogged = true
        Log.w(
            TAG,
            "tiles_config configured=$configured key_len=$keyLength sdk=MapsSDK_Android"
        )
    }

    fun logLoadOnce(loaded: Boolean) {
        if (loadLogged) return
        loadLogged = true
        Log.w(TAG, if (loaded) "tiles_callback=loaded" else "tiles_callback=timeout")
    }
}
