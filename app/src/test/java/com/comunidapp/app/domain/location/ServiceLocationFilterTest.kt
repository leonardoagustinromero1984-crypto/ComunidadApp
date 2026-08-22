package com.comunidapp.app.domain.location

import com.comunidapp.app.data.model.LocationLevel
import com.comunidapp.app.data.model.LocationNode
import com.comunidapp.app.data.model.ServiceCategory
import com.comunidapp.app.data.model.ServiceProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceLocationFilterTest {

    private val catalog = listOf(
        LocationNode(
            id = "loc-ar-prov-caba",
            name = "Ciudad Autónoma de Buenos Aires",
            level = LocationLevel.PROVINCE,
            aliases = listOf("CABA", "Capital Federal")
        ),
        LocationNode(
            id = "loc-ar-loc-palermo",
            name = "Palermo",
            level = LocationLevel.LOCALITY,
            parentId = "loc-ar-prov-caba"
        ),
        LocationNode(
            id = "loc-ar-prov-buenos-aires",
            name = "Buenos Aires",
            level = LocationLevel.PROVINCE,
            aliases = listOf("PBA")
        ),
        LocationNode(
            id = "loc-ar-loc-san-vicente",
            name = "San Vicente",
            level = LocationLevel.LOCALITY,
            parentId = "loc-ar-prov-buenos-aires"
        )
    )

    private fun profile(
        name: String,
        location: String = "",
        provinceId: String? = null,
        localityId: String? = null
    ) = ServiceProfile(
        id = name,
        ownerId = "o",
        category = ServiceCategory.VET,
        name = name,
        location = location,
        provinceId = provinceId,
        localityId = localityId,
        localityIds = listOfNotNull(localityId)
    )

    @Test
    fun caba_catalog_name_matches_caba_text_and_ids() {
        val mockCaba = profile("QA • Veterinaria mock", "Almagro, CABA")
        val canonicalCaba = profile(
            "QA • Veterinaria Palermo",
            provinceId = "loc-ar-prov-caba",
            localityId = "loc-ar-loc-palermo"
        )
        val ba = profile(
            "QA • Veterinaria San Vicente",
            provinceId = "loc-ar-prov-buenos-aires",
            localityId = "loc-ar-loc-san-vicente"
        )
        assertTrue(ServiceLocationFilter.matches(mockCaba, "Ciudad Autónoma de Buenos Aires", catalog))
        assertTrue(ServiceLocationFilter.matches(canonicalCaba, "CABA", catalog))
        assertFalse(ServiceLocationFilter.matches(ba, "Ciudad Autónoma de Buenos Aires", catalog))
        assertTrue(ServiceLocationFilter.matches(ba, "Buenos Aires", catalog))
        assertFalse(ServiceLocationFilter.matches(canonicalCaba, "Buenos Aires", catalog))
    }

    @Test
    fun empty_query_keeps_all() {
        val any = profile("x", "Córdoba")
        assertTrue(ServiceLocationFilter.matches(any, "", catalog))
    }
}
