package com.comunidapp.app.data.repository



import org.junit.Assert.assertEquals

import org.junit.Assert.assertTrue

import org.junit.Test



class M14PublicQrPayloadServiceTest {



    @Test

    fun canonicalHexCodeBuildsHttpsPayload() {

        val result = M14PublicQrPayloadService.buildPayload("A1B2C3D4E5F6")

        assertTrue(result.isSuccess)

        assertEquals("https://leover.com.ar/mascota/A1B2C3D4E5F6", result.getOrNull())

    }



    @Test

    fun deepLinkPayloadKeepsPassportScheme() {

        val result = M14PublicQrPayloadService.buildDeepLinkPayload("A1B2C3D4E5F6")

        assertTrue(result.isSuccess)

        assertEquals("leover://passport/PUB-A1B2C3D4E5F6", result.getOrNull())

    }



    @Test

    fun extractPublicCodeFromHttpsUrl() {

        val result = M14PublicQrPayloadService.extractPublicCode(

            "https://leover.com.ar/mascota/A1B2C3D4E5F6"

        )

        assertTrue(result.isSuccess)

        assertEquals("A1B2C3D4E5F6", result.getOrNull())

    }



    @Test

    fun extractPublicCodeStripsDisplayPrefixFromDeepLink() {

        val result = M14PublicQrPayloadService.extractPublicCode("leover://passport/PUB-A1B2C3D4E5F6")

        assertTrue(result.isSuccess)

        assertEquals("A1B2C3D4E5F6", result.getOrNull())

    }

}

