package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.repository.CanonicalCommunityNearbyRepository
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.data.repository.MockPlatformRepository
import com.comunidapp.app.data.repository.MockServiceRepository
import com.comunidapp.app.data.repository.MockUserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityFormDirtyStateTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun communityFiltersSurviveOpeningMap() {
        val vm = ComunidadViewModel(
            serviceRepository = MockServiceRepository(),
            authRepository = MockAuthRepository(),
            userRepository = MockUserRepository(),
            nearbyRepository = CanonicalCommunityNearbyRepository()
        )
        vm.applyFilters("Burzaco", true)
        vm.setResultsView(CommunityResultsView.MAP)
        assertEquals("Burzaco", vm.uiState.value.locationQuery)
        assertEquals(true, vm.uiState.value.activeOnly)
        assertEquals(CommunityResultsView.MAP, vm.uiState.value.resultsView)
    }

    @Test
    fun businessFormSurvivesMapPin() {
        val vm = MiNegocioViewModel(
            serviceRepository = MockServiceRepository(),
            platformRepository = MockPlatformRepository(),
            authRepository = MockAuthRepository()
        )
        vm.updateName("Clínica Norte")
        vm.updateDescription("Guardia")
        vm.updateContact("11 5555 0101")
        vm.updateMapPin(-34.6, -58.4, true)
        assertEquals("Clínica Norte", vm.uiState.value.name)
        assertEquals("Guardia", vm.uiState.value.description)
        assertEquals("11 5555 0101", vm.uiState.value.contactInfo)
        assertEquals(-34.6, vm.uiState.value.pinLat!!, 0.0001)
        assertEquals(-58.4, vm.uiState.value.pinLng!!, 0.0001)
    }
}
