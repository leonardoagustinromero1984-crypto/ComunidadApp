package com.comunidapp.app.core.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class AppErrorMapperTest {

    @Test
    fun fromThrowable_mapsNetwork() {
        val error = AppErrorMapper.fromThrowable(SocketTimeoutException("timeout"))
        assertEquals(AppErrorKind.NETWORK, error.kind)
        assertEquals("NETWORK", error.code)
        assertTrue(error.userMessage.isNotBlank())
        assertTrue(error.technicalMessage.contains("timeout") || error.technicalMessage.isNotBlank())
    }

    @Test
    fun fromThrowable_mapsValidation() {
        val error = AppErrorMapper.fromThrowable(IllegalArgumentException("bad"))
        assertEquals(AppErrorKind.VALIDATION, error.kind)
    }

    @Test
    fun configuration_kind() {
        val error = AppErrorMapper.configuration("missing SUPABASE_URL")
        assertEquals(AppErrorKind.CONFIGURATION, error.kind)
        assertEquals("CONFIGURATION", error.code)
    }

    @Test
    fun result_toAppResult_successAndFailure() {
        val ok = Result.success(42).toAppResult()
        assertTrue(ok is AppResult.Success)
        assertEquals(42, ok.getOrNull())

        val fail = Result.failure<Int>(IllegalStateException("x")).toAppResult()
        assertTrue(fail is AppResult.Failure)
    }

    @Test
    fun specializedFactories() {
        assertEquals(AppErrorKind.UNAUTHORIZED, AppErrorMapper.unauthorized().kind)
        assertEquals(AppErrorKind.NOT_FOUND, AppErrorMapper.notFound().kind)
        assertEquals(AppErrorKind.SERVER, AppErrorMapper.server("500").kind)
        assertEquals(AppErrorKind.RATE_LIMITED, AppErrorMapper.rateLimited().kind)
        assertEquals(AppErrorKind.CONFLICT, AppErrorMapper.conflict().kind)
    }
}
