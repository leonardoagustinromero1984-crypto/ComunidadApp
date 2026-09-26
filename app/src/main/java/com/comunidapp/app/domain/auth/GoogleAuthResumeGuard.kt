package com.comunidapp.app.domain.auth

import com.comunidapp.app.domain.time.ElapsedRealtime

/**
 * First-tap Custom Tabs / chooser overlay can bounce ON_RESUME before Google
 * has a session. Cancelling on that bounce aborted the same path the second
 * tap then completed.
 */
object GoogleAuthResumeGuard {
    const val IGNORE_RESUME_AFTER_LAUNCH_MS = 1_800L

    fun shouldIgnoreResume(
        launchAtElapsedMs: Long,
        hostPausedSinceLaunch: Boolean,
        nowElapsedMs: Long = ElapsedRealtime.nowMs()
    ): Boolean {
        if (launchAtElapsedMs <= 0L) return true
        if (!hostPausedSinceLaunch) return true
        return nowElapsedMs - launchAtElapsedMs < IGNORE_RESUME_AFTER_LAUNCH_MS
    }
}
