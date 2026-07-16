package com.comunidapp.app.data.provider

import com.comunidapp.app.data.repository.FileAccessRepository
import com.comunidapp.app.data.repository.FileAssetRepository
import com.comunidapp.app.data.repository.FileDownloadRepository
import com.comunidapp.app.data.repository.FileRetentionRepository
import com.comunidapp.app.data.repository.FileUploadRepository
import com.comunidapp.app.data.repository.MockFileAccessRepository
import com.comunidapp.app.data.repository.MockFileAssetRepository
import com.comunidapp.app.data.repository.MockFileDownloadRepository
import com.comunidapp.app.data.repository.MockFileRetentionRepository
import com.comunidapp.app.data.repository.MockFileUploadRepository
import com.comunidapp.app.data.repository.SupabaseFileAccessRepository
import com.comunidapp.app.data.repository.SupabaseFileAssetRepository
import com.comunidapp.app.data.repository.SupabaseFileDownloadRepository
import com.comunidapp.app.data.repository.SupabaseFileRetentionRepository
import com.comunidapp.app.data.repository.SupabaseFileUploadRepository
import org.junit.Assert.assertTrue
import org.junit.Test

class M05DataProviderWiringTest {

    @Test
    fun `mock repositories remain constructible and implement contracts`() {
        val assets = MockFileAssetRepository()
        assertTrue(assets is FileAssetRepository)
        assertTrue(MockFileUploadRepository(assets) is FileUploadRepository)
        assertTrue(MockFileDownloadRepository(assets) is FileDownloadRepository)
        assertTrue(MockFileAccessRepository(assets) is FileAccessRepository)
        assertTrue(MockFileRetentionRepository(assets) is FileRetentionRepository)
    }

    @Test
    fun `Supabase repository classes implement M05 contracts`() {
        assertTrue(FileAssetRepository::class.java.isAssignableFrom(SupabaseFileAssetRepository::class.java))
        assertTrue(FileUploadRepository::class.java.isAssignableFrom(SupabaseFileUploadRepository::class.java))
        assertTrue(FileDownloadRepository::class.java.isAssignableFrom(SupabaseFileDownloadRepository::class.java))
        assertTrue(FileAccessRepository::class.java.isAssignableFrom(SupabaseFileAccessRepository::class.java))
        assertTrue(FileRetentionRepository::class.java.isAssignableFrom(SupabaseFileRetentionRepository::class.java))
    }
}
