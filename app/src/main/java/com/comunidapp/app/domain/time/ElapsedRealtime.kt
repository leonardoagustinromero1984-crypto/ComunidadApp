package com.comunidapp.app.domain.time

import android.os.SystemClock

/**
 * Monotonic elapsed time for JVM tests and devices.
 * On a device, [SystemClock.elapsedRealtime] is the source.
 * android.jar throws RuntimeException("Stub!") from unit tests; that case
 * falls back to a process-local nanoTime delta so durations stay monotonic
 * and are not stuck at zero.
 */
internal fun isUnitFrameworkStub(error: RuntimeException): Boolean {
    val message = error.message.orEmpty()
    return message == "Stub!" || message.contains("not mocked")
}

object ElapsedRealtime {
    private val jvmOriginNs = System.nanoTime()

    fun nowMs(): Long {
        return try {
            SystemClock.elapsedRealtime()
        } catch (error: RuntimeException) {
            if (!isUnitFrameworkStub(error)) throw error
            (System.nanoTime() - jvmOriginNs) / 1_000_000L
        }
    }
}
