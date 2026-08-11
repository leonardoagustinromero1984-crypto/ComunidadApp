package com.comunidapp.app.data.remote.supabase.m14

import com.comunidapp.app.data.model.M14Visibility
import com.comunidapp.app.data.model.UpdateM14PassportInput
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M14UpdatePassportParamsTest {

    @Test
    fun visibilityOnly_omitsUnsetOptionalParams() {
        val params = updateM14PassportParams(
            id = "7911bd57-3b4f-482c-a3e5-5497c2dbd835",
            input = UpdateM14PassportInput(visibility = M14Visibility.PUBLIC_REDACTED)
        )

        assertEquals("7911bd57-3b4f-482c-a3e5-5497c2dbd835", params["p_passport_id"].toString().trim('"'))
        assertEquals("PUBLIC_REDACTED", params["p_visibility"].toString().trim('"'))
        assertFalse(params.containsKey("p_display_name"))
        assertFalse(params.containsKey("p_breed_text"))
        assertFalse(params.containsKey("p_sex"))
        assertFalse(params.containsKey("p_birth_date"))
        assertFalse(params.containsKey("p_primary_color"))
        assertFalse(params.containsKey("p_distinctive_marks"))
        assertFalse(params.containsKey("p_microchip_raw"))
    }

    @Test
    fun visibilityOnly_doesNotSendJsonNullOptionalParams() {
        val params = updateM14PassportParams(
            id = "passport-1",
            input = UpdateM14PassportInput(visibility = M14Visibility.PUBLIC_REDACTED)
        )

        params.forEach { (_, value) ->
            assertTrue(value !is JsonNull)
        }
    }
}
