package com.comunidapp.shared.platform

import platform.Foundation.NSUserDefaults

/**
 * Preferencias no sensibles (NSUserDefaults).
 * No almacenar tokens aquí — Keychain es el siguiente paso.
 */
class IosPlatformPreferences(
    private val suite: NSUserDefaults = NSUserDefaults.standardUserDefaults
) : PlatformPreferences {
    override fun getString(key: String): String? = suite.stringForKey(key)

    override fun putString(key: String, value: String) {
        suite.setObject(value, forKey = key)
    }

    override fun remove(key: String) {
        suite.removeObjectForKey(key)
    }
}
