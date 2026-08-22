package com.comunidapp.app.domain.onboarding.onb02

/**
 * Session-only flag. Not persisted. Used to start the full ONB-02 flow
 * after profile fields are completed, without restarting existing users.
 */
object Onb02SessionFlags {
    @Volatile
    var justCompletedProfileSetup: Boolean = false

    fun consumeJustCompletedProfileSetup(): Boolean {
        val value = justCompletedProfileSetup
        justCompletedProfileSetup = false
        return value
    }
}
