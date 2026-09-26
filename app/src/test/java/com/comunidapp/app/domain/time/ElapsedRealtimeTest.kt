package com.comunidapp.app.domain.time

import com.comunidapp.app.core.logging.AppLog
import com.comunidapp.app.domain.perf.ScreenPerfProbe
import org.junit.Assert.assertTrue
import org.junit.Test

class ElapsedRealtimeTest {
    @Test
    fun nowMs_isMonotonicOnJvmWithoutAndroidRuntime() {
        val first = ElapsedRealtime.nowMs()
        val second = ElapsedRealtime.nowMs()
        assertTrue(second >= first)
    }

    @Test
    fun appLogAndScreenProbe_doNotThrowWhenAndroidLogIsAStub() {
        AppLog.info("JVM", "cloud baseline probe")
        val session = ScreenPerfProbe.begin("pet_profile")
        session.markFirstContent()
        session.finish()
    }
}
