package com.comunidapp.app.domain.pets

import com.comunidapp.app.data.remote.supabase.m08.M08RpcDecoding
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CareNetworkRpcDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    private data class Row(
        @SerialName("link_id") val linkId: String,
        @SerialName("pet_id") val petId: String,
        @SerialName("pet_name") val petName: String? = null,
        @SerialName("owner_name") val ownerName: String? = null,
        @SerialName("care_role") val careRole: String? = null,
        val status: String? = null
    )

    @Test
    fun stagingListMyCareInvitesJsonBecomesPendingInvite() {
        val resultData = """[{"link_id":"a4b6d0ad-1111-2222-3333-444444444444","pet_id":"78068b30-03f1-4d41-82bc-81318a143471","pet_name":"Samu","owner_name":"Veronica Obregon","care_role":"FAMILY","status":"PENDING"}]"""
        val rows = M08RpcDecoding.decodeRows<Row>(json.parseToJsonElement(resultData))
        assertEquals(1, rows.size)
        assertEquals("Samu", rows.first().petName)
        assertEquals("Veronica Obregon", rows.first().ownerName)
        assertEquals("FAMILY", rows.first().careRole)
        assertEquals("PENDING", rows.first().status)
        assertEquals(
            CareNetworkRole.FAMILY,
            CareNetworkRole.fromRaw(rows.first().careRole)
        )
    }

    @Test
    fun stagingListMyCarePetsJsonBecomesActivePet() {
        val resultData = """[{"link_id":"1f1dafd6-aaaa-bbbb-cccc-dddddddddddd","pet_id":"78068b30-03f1-4d41-82bc-81318a143471","pet_name":"Samu","owner_name":"Veronica Obregon","care_role":"FAMILY","status":"ACTIVE"}]"""
        val rows = M08RpcDecoding.decodeRows<Row>(json.parseToJsonElement(resultData))
        assertEquals("78068b30-03f1-4d41-82bc-81318a143471", rows.first().petId)
        assertEquals("ACTIVE", rows.first().status)
        assertTrue(CareNetworkRules.canViewVitacora(rows.first().status.orEmpty()))
    }

    @Test
    fun inviteUuidFromQuotedResultData() {
        val resultData = "\"a4b6d0ad-1111-2222-3333-444444444444\""
        val uuid = com.comunidapp.app.data.remote.supabase.SupabaseRowDecoding.decodeUuidFromRaw(resultData)
        assertEquals("a4b6d0ad-1111-2222-3333-444444444444", uuid)
    }
}
