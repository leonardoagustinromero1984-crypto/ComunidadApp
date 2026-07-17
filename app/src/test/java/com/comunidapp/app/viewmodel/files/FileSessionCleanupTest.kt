package com.comunidapp.app.viewmodel.files

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.files.FileBytesReader
import com.comunidapp.app.data.files.FileLocalMetadataReader
import com.comunidapp.app.data.files.FileUploadCoordinator
import com.comunidapp.app.data.files.MockFileObjectUploader
import com.comunidapp.app.data.repository.MockFileAssetRepository
import com.comunidapp.app.data.repository.MockFileUploadRepository
import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileAssetVisibility
import com.comunidapp.app.domain.files.FileLocalMetadata
import com.comunidapp.app.domain.files.FileUploadPhase
import com.comunidapp.app.viewmodel.moderation.AdministrativeSessionCleanup
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileSessionCleanupTest {

    @After
    fun tearDown() {
        FileSessionCleanup.resetForTests()
    }

    @Test
    fun `administrative logout cleanup clears file preview and lock state`() = runTest {
        val assets = MockFileAssetRepository()
        val coordinator = FileUploadCoordinator(
            MockFileUploadRepository(assets),
            assets,
            MockFileObjectUploader(),
            object : FileLocalMetadataReader {
                override suspend fun read(uriString: String) = AppResult.Success(
                    FileLocalMetadata("photo.jpg", "image/jpeg", 100L, uriString)
                )
            },
            object : FileBytesReader {
                override suspend fun readBytes(uriString: String) =
                    AppResult.Success(byteArrayOf(1))
            }
        )
        coordinator.selectAndValidate(
            uri = "content://sensitive-preview",
            purpose = FileAssetPurpose.USER_AVATAR,
            owner = FileAssetOwner.User("user-1"),
            visibility = FileAssetVisibility.PUBLIC,
            actorUserId = "user-1"
        )
        FileSessionCleanup.register(coordinator = coordinator)

        AdministrativeSessionCleanup.clear()

        assertEquals(FileUploadPhase.Idle, coordinator.uiState.value.phase)
        assertNull(coordinator.uiState.value.previewUri)
        assertNull(coordinator.uiState.value.temporaryDisplayUrl)
    }
}
