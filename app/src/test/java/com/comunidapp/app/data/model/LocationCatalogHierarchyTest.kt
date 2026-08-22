package com.comunidapp.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationCatalogHierarchyTest {

    private val nodes = argentinaLocationSeed()

    @Test
    fun municipalitiesAreFilteredByProvince() {
        val ba = nodes.children("loc-ar-prov-buenos-aires", LocationLevel.MUNICIPALITY)
        val caba = nodes.children("loc-ar-prov-caba", LocationLevel.MUNICIPALITY)
        assertTrue(ba.any { it.name == "San Vicente" })
        assertTrue(ba.none { it.name == "CABA" })
        assertTrue(caba.any { it.name == "CABA" })
        assertTrue(caba.none { it.name == "San Vicente" })
    }

    @Test
    fun localitiesAreFilteredByMunicipality() {
        val korn = nodes.children("loc-ar-mun-san-vicente", LocationLevel.LOCALITY)
        assertEquals(
            listOf("Alejandro Korn", "Domselaar", "San Vicente"),
            korn.map { it.name }.sorted()
        )
        val brown = nodes.children("loc-ar-mun-almirante-brown", LocationLevel.LOCALITY)
        assertTrue(brown.any { it.name == "Adrogué" })
        assertTrue(brown.none { it.name == "Alejandro Korn" })
    }

    @Test
    fun localitiesCanBeSearchedByProvinceWithoutMunicipality() {
        val hits = nodes.searchLocalitiesInProvince("loc-ar-prov-buenos-aires", "adro")
        assertEquals(1, hits.size)
        assertEquals("Adrogué", hits.single().name)
        val selected = nodes.selectionForLocality(hits.single().id)
        assertEquals("loc-ar-prov-buenos-aires", selected.provinceId)
        assertEquals("loc-ar-mun-almirante-brown", selected.municipalityId)
        assertEquals("loc-ar-loc-adrogué", selected.localityId)
        assertNull(selected.zoneId)
        val visible = nodes.visibleLabel(selected)
        assertEquals("Adrogué, Buenos Aires", visible)
        assertFalse(visible.contains("Almirante Brown"))
    }

    @Test
    fun restoreSelectionPrefersCanonicalLocalityIdOverEmptyNames() {
        val restored = nodes.restoreSelection(
            localityId = "loc-ar-loc-adrogué",
            province = "",
            city = ""
        )
        assertEquals("loc-ar-prov-buenos-aires", restored.provinceId)
        assertEquals("loc-ar-loc-adrogué", restored.localityId)
    }

    @Test
    fun changingProvinceClearsLocalityForVisibleUx() {
        val selected = nodes.selectionForLocality("loc-ar-loc-adrogué")
        val moved = selected.withProvince("loc-ar-prov-caba")
        assertNull(moved.localityId)
        assertNull(moved.municipalityId)
        assertEquals("loc-ar-prov-caba", moved.provinceId)
    }

    @Test
    fun visibleLabelHidesMunicipalityAndZone() {
        val selection = LocationSelection(
            provinceId = "loc-ar-prov-buenos-aires",
            municipalityId = "loc-ar-mun-san-vicente",
            localityId = "loc-ar-loc-alejandro-korn",
            zoneId = "loc-ar-zone-korn-centro"
        )
        val visible = nodes.visibleLabel(selection)
        assertEquals("Alejandro Korn, Buenos Aires", visible)
        assertFalse(visible.contains("San Vicente", ignoreCase = false))
    }

    @Test
    fun changingProvinceClearsIncompatibleChildren() {
        val selection = LocationSelection(
            provinceId = "loc-ar-prov-buenos-aires",
            municipalityId = "loc-ar-mun-san-vicente",
            localityId = "loc-ar-loc-alejandro-korn",
            zoneId = "loc-ar-zone-korn-centro"
        )
        val moved = selection.withProvince("loc-ar-prov-caba")
        val cleared = nodes.clearIncompatible(moved)
        assertEquals("loc-ar-prov-caba", cleared.provinceId)
        assertNull(cleared.municipalityId)
        assertNull(cleared.localityId)
        assertNull(cleared.zoneId)
    }

    @Test
    fun incompatibleMunicipalityIsDropped() {
        val broken = LocationSelection(
            provinceId = "loc-ar-prov-caba",
            municipalityId = "loc-ar-mun-san-vicente",
            localityId = "loc-ar-loc-alejandro-korn"
        )
        val cleared = nodes.clearIncompatible(broken)
        assertEquals("loc-ar-prov-caba", cleared.provinceId)
        assertNull(cleared.municipalityId)
        assertNull(cleared.localityId)
    }

    @Test
    fun searchIsNotAFullDump() {
        val hits = nodes.search("san vic", LocationLevel.MUNICIPALITY, "loc-ar-prov-buenos-aires")
        assertEquals(1, hits.size)
        assertEquals("San Vicente", hits.single().name)
    }

    @Test
    fun resolveFromExistingStringsKeepsCompatibility() {
        val selection = nodes.resolveSelection(
            province = "Buenos Aires",
            city = "Alejandro Korn",
            label = "Alejandro Korn, San Vicente, Buenos Aires"
        )
        val display = nodes.displayOf(selection)
        assertEquals("Buenos Aires", display.provinceName)
        assertEquals("San Vicente", display.municipalityName)
        assertEquals("Alejandro Korn", display.localityName)
        assertEquals("Alejandro Korn", display.cityName)
        assertTrue(display.label.contains("Alejandro Korn"))
    }

    @Test
    fun resolveAliasCaba() {
        val selection = nodes.resolveSelection(province = "CABA", city = "Palermo")
        val display = nodes.displayOf(selection)
        assertEquals("Ciudad Autónoma de Buenos Aires", display.provinceName)
        assertEquals("Palermo", display.localityName)
    }

    @Test
    fun inactiveNodesAreExcludedFromActiveChildren() {
        val inactive = nodes.first { it.id == "loc-ar-mun-san-vicente" }.copy(active = false)
        val patched = nodes.map { if (it.id == inactive.id) inactive else it }
        val active = patched.children("loc-ar-prov-buenos-aires", LocationLevel.MUNICIPALITY, activeOnly = true)
        val all = patched.children("loc-ar-prov-buenos-aires", LocationLevel.MUNICIPALITY, activeOnly = false)
        assertTrue(active.none { it.id == "loc-ar-mun-san-vicente" })
        assertTrue(all.any { it.id == "loc-ar-mun-san-vicente" && !it.active })
    }

    @Test
    fun canonicalProvincesLoadEvenWhenParentIsCountry() {
        val canonical = listOf(
            LocationNode("loc-ar-prov-ba", "Buenos Aires", LocationLevel.PROVINCE, parentId = "loc-ar", order = 1),
            LocationNode("loc-ar-prov-caba", "CABA", LocationLevel.PROVINCE, parentId = "loc-ar", order = 2),
            LocationNode("loc-ar-loc-sv", "San Vicente", LocationLevel.LOCALITY, parentId = "loc-ar-prov-ba", order = 1),
            LocationNode("loc-ar-loc-lp", "La Plata", LocationLevel.LOCALITY, parentId = "loc-ar-prov-ba", order = 2)
        )
        val provinces = canonical.search("", LocationLevel.PROVINCE, parentId = null)
        assertEquals(2, provinces.size)
        val localities = canonical.searchLocalitiesInProvince("loc-ar-prov-ba", "")
        assertEquals(listOf("La Plata", "San Vicente"), localities.map { it.name }.sorted())
        val other = canonical.searchLocalitiesInProvince("loc-ar-prov-caba", "")
        assertTrue(other.isEmpty())
    }

    @Test
    fun changingCanonicalProvinceClearsLocality() {
        val canonical = listOf(
            LocationNode("loc-ar-prov-ba", "Buenos Aires", LocationLevel.PROVINCE, parentId = "loc-ar"),
            LocationNode("loc-ar-prov-caba", "CABA", LocationLevel.PROVINCE, parentId = "loc-ar"),
            LocationNode("loc-ar-loc-sv", "San Vicente", LocationLevel.LOCALITY, parentId = "loc-ar-prov-ba")
        )
        val selected = canonical.selectionForLocality("loc-ar-loc-sv")
        assertEquals("loc-ar-prov-ba", selected.provinceId)
        assertEquals("loc-ar-loc-sv", selected.localityId)
        val moved = canonical.clearIncompatible(selected.withProvince("loc-ar-prov-caba"))
        assertEquals("loc-ar-prov-caba", moved.provinceId)
        assertNull(moved.localityId)
    }

    @Test
    fun onboardingListsAllProvincesAfterArgentinaComplete() {
        val catalog = listOf(
            LocationNode("loc-ar-prov-ba", "Buenos Aires", LocationLevel.PROVINCE, parentId = "loc-ar"),
            LocationNode("loc-ar-prov-empty", "Catamarca", LocationLevel.PROVINCE, parentId = "loc-ar"),
            LocationNode("loc-ar-loc-sv", "San Vicente", LocationLevel.LOCALITY, parentId = "loc-ar-prov-ba")
        )
        val provinces = catalog.search("", LocationLevel.PROVINCE, parentId = null)
        assertEquals(listOf("Buenos Aires", "Catamarca"), provinces.map { it.name })
    }
}
