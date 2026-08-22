package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.LocationLevel
import com.comunidapp.app.data.model.argentinaLocationSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationCatalogRepositoryTest {

    @Test
    fun deactivateDoesNotDelete() {
        val repo = InMemoryLocationCatalogRepository(argentinaLocationSeed())
        val id = "loc-ar-mun-san-vicente"
        repo.setActive(id, false)
        val node = repo.get(id)
        assertFalse(node!!.active)
        assertTrue(repo.snapshot().any { it.id == id })
    }

    @Test
    fun createRequiresParentForMunicipality() {
        val repo = InMemoryLocationCatalogRepository(argentinaLocationSeed())
        val created = repo.create(
            name = "Pilar",
            level = LocationLevel.MUNICIPALITY,
            parentId = "loc-ar-prov-buenos-aires"
        )
        assertEquals(LocationLevel.MUNICIPALITY, created.level)
        assertEquals("loc-ar-prov-buenos-aires", created.parentId)
        assertTrue(created.active)
    }
}
