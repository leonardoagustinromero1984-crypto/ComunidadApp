package com.comunidapp.shared.poc.m08

import com.comunidapp.shared.poc.m08.data.FakePetPocRepository
import com.comunidapp.shared.poc.m08.domain.FileRefRules
import com.comunidapp.shared.poc.m08.model.FileRef
import com.comunidapp.shared.poc.m08.model.ImagePickResult
import com.comunidapp.shared.poc.m08.navigation.M08PocNavContract
import com.comunidapp.shared.poc.m08.navigation.PetDetailRoute
import com.comunidapp.shared.poc.m08.navigation.PetListRoute
import com.comunidapp.shared.poc.m08.navigation.PetMediaRoute
import com.comunidapp.shared.poc.m08.platform.ImagePicker
import com.comunidapp.shared.poc.m08.viewmodel.PetMediaViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class M08PocNavigationTests {
    @Test
    fun listInitial() {
        val nav = M08PocNavContract()
        assertIs<PetListRoute>(nav.current)
        assertEquals(1, nav.depth)
        assertTrue(nav.isAtList())
    }

    @Test
    fun detailRouteCarriesId() {
        val nav = M08PocNavContract()
        nav.navigateToDetail("pet-luna")
        val detail = assertIs<PetDetailRoute>(nav.current)
        assertEquals("pet-luna", detail.petId)
    }

    @Test
    fun mediaRouteAndBackRestoresDetailThenList() {
        val nav = M08PocNavContract()
        nav.navigateToDetail("pet-michi")
        nav.navigateToMedia("pet-michi")
        assertIs<PetMediaRoute>(nav.current)
        assertEquals("pet-michi", (nav.current as PetMediaRoute).petId)

        assertTrue(nav.back())
        assertIs<PetDetailRoute>(nav.current)
        assertEquals("pet-michi", (nav.current as PetDetailRoute).petId)

        assertTrue(nav.back())
        assertIs<PetListRoute>(nav.current)
        assertFalse(nav.back())
    }
}

class M08PocFileRefTests {
    @Test
    fun validFileRef() {
        val ref = FileRef("photo.jpg", "image/jpeg", 1024, "content://poc/1")
        assertTrue(ref.isImage)
        assertTrue(FileRefRules.validateForPetAvatar(ref).isSuccess)
    }

    @Test
    fun invalidBlankName() {
        assertFailsWith<IllegalArgumentException> {
            FileRef("  ", "image/png", 10, "id")
        }
    }

    @Test
    fun invalidSize() {
        assertFailsWith<IllegalArgumentException> {
            FileRef("a.png", "image/png", 0, "id")
        }
    }

    @Test
    fun rejectOversized() {
        val big = FileRef("big.jpg", "image/jpeg", 9L * 1024 * 1024, "id")
        assertTrue(FileRefRules.validateForPetAvatar(big).isFailure)
    }

    @Test
    fun rejectNonImageMime() {
        val pdf = FileRef("doc.pdf", "application/pdf", 100, "id")
        assertTrue(FileRefRules.validateForPetAvatar(pdf).isFailure)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class M08PocViewModelPickerTests {
    private class FakePicker(private val result: ImagePickResult) : ImagePicker {
        override suspend fun pickImage(): ImagePickResult = result
    }

    @Test
    fun pickerSuccessUpdatesSelectedFile() = runTest {
        val repo = FakePetPocRepository()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val vm = PetMediaViewModel("pet-luna", repo, scope)
        val file = FileRef("luna.jpg", "image/jpeg", 2048, "opaque-android-uri")
        vm.pickVia(FakePicker(ImagePickResult.Success(file)))
        advanceUntilIdle()
        assertEquals(file, vm.uiState.value.selectedFile)
        assertNotNull(repo.getPet("pet-luna")?.pendingMedia)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun pickerCancelledKeepsPreviousSelection() = runTest {
        val repo = FakePetPocRepository()
        val existing = FileRef("old.jpg", "image/jpeg", 100, "id-1")
        repo.attachLocalMedia("pet-luna", existing)
        val vm = PetMediaViewModel("pet-luna", repo, backgroundScope)
        vm.applyPickResult(ImagePickResult.Cancelled)
        assertEquals(existing, vm.uiState.value.selectedFile)
        assertEquals("Selección cancelada", vm.uiState.value.infoMessage)
    }

    @Test
    fun pickerFailureSetsError() = runTest {
        val repo = FakePetPocRepository()
        val vm = PetMediaViewModel("pet-luna", repo, backgroundScope)
        vm.applyPickResult(ImagePickResult.Failure("PICKER_FAILURE"))
        assertEquals("PICKER_FAILURE", vm.uiState.value.errorMessage)
        assertNull(vm.uiState.value.selectedFile)
    }
}
