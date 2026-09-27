package com.comunidapp.app.domain.files

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumableUploadPolicyVideoTest {

    @Test
    fun reasonableMobileVideoUsesTusInsteadOfByteArray() {
        val size = 3L * 1024L * 1024L
        assertFalse(ResumableUploadPolicy.shouldUseTus(size))
        assertTrue(ResumableUploadPolicy.shouldUseTus(size, "video/mp4"))
    }
}
