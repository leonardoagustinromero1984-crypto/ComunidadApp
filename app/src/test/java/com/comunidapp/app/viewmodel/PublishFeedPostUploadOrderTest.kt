package com.comunidapp.app.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Test

class PublishFeedPostUploadOrderTest {
    @Test
    fun uploadRunsBeforeCreateOnCanonicalPath() {
        val source = com.comunidapp.app.ui.UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/viewmodel/PublishViewModel.kt"
        ).readText()
        val uploadIdx = source.indexOf("FileAssetPurpose.POST_MEDIA")
        val addIdx = source.indexOf("feedRepository.addFeedPost(post)")
        assertFalse(uploadIdx < 0 || addIdx < 0)
        assertFalse("post was created before upload", addIdx < uploadIdx)
    }
}
