package com.comunidapp.app.data.remote.supabase.m14

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M14ErrorMapperUxTest {

    @Test
    fun unauthorized_usesHumanPermissionLanguage_withoutM08() {
        val msg = M14ErrorMapper.userMessage("UNAUTHORIZED")
        assertTrue(msg.contains("permiso", ignoreCase = true))
        assertTrue(msg.contains("pasaporte", ignoreCase = true))
        assertFalse(msg.contains("M08"))
        assertFalse(msg.contains("M14"))
        assertFalse(msg.contains("UNAUTHORIZED"))
    }

    @Test
    fun createFailed_recoverableCopy() {
        val msg = M14ErrorMapper.userMessage("PASSPORT_CREATE_FAILED")
        assertTrue(msg.contains("No pudimos crear el pasaporte"))
        assertTrue(msg.contains("conexión") || msg.contains("conexion") || msg.contains("intentá") || msg.contains("intenta"))
        assertFalse(msg.contains("M14"))
    }

    @Test
    fun unknownCode_mapsToRecoverableCreateCopy() {
        val msg = M14ErrorMapper.userMessage("M14_UNKNOWN")
        assertTrue(msg.contains("No pudimos crear el pasaporte"))
    }

    @Test
    fun serializationBlob_mapsToCreateFailedViaCodeOf() {
        val code = M14ErrorMapper.codeOf(
            RuntimeException("Expected start of the array [ but was { at path $")
        )
        assertEquals("M14_UNKNOWN", code)
        val msg = M14ErrorMapper.userMessage(code)
        assertTrue(msg.contains("No pudimos crear el pasaporte"))
    }

    @Test
    fun genericFailure_hasNoModuleCode() {
        val msg = M14ErrorMapper.userMessage("M14_REPOSITORY_FAILURE")
        assertFalse(msg.contains("M14"))
        assertTrue(msg.contains("pasaporte"))
    }
}
