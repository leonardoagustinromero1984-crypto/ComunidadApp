package com.comunidapp.app.domain.social

import com.comunidapp.app.data.local.InMemoryOnb02Store
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.PostType
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FilePurposePolicy
import com.comunidapp.app.domain.onboarding.onb02.Onb02Planner
import com.comunidapp.app.domain.onboarding.onb02.TutorialId
import com.comunidapp.app.domain.organization.OrgInvitePolicy
import com.comunidapp.app.domain.organization.authorization.OrganizationRoleCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeoVerOrgSocialQaContractTest {

    @Test
    fun orgPersonSearchDoesNotUseEmailTokenInPolicy() {
        assertTrue(OrgInvitePolicy.MEMBER_PERMISSION_OPTIONS.any { it.catalogCode == "org.publish" })
        assertTrue(OrgInvitePolicy.MEMBER_PERMISSION_OPTIONS.none { it.catalogCode.contains("email") })
        assertFalse(OrgInvitePolicy.PENDING_HAS_ACCESS)
        assertTrue(OrgInvitePolicy.canInviteRole(OrganizationRoleCode.MEMBER))
        assertEquals(
            setOf("org.view", "org.publish"),
            OrgInvitePolicy.normalizeMemberPermissions(setOf("org.publish", "secret")).toSet()
        )
    }

    @Test
    fun lastAdminProtectionStillDocumentedInTeamUi() {
        val src = com.comunidapp.app.ui.UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/screens/organization/OrganizationTeamScreen.kt"
        ).readText()
        assertTrue(src.contains("ownerCount <= 1") || src.contains("LAST_ADMIN") || src.contains("ownerCount"))
        assertFalse(src.contains("member.userId, fontWeight"))
    }

    @Test
    fun tutorialServerProgressVersionAwareNoAutoReplay() {
        val store = InMemoryOnb02Store()
        store.applyRemoteProgress("u", TutorialId.T00_MULTI_FUNCTION_INTRO, viewed = true, skipped = false, completed = true)
        assertTrue(Onb02Planner.tutorialFinished(store.progress("u", TutorialId.T00_MULTI_FUNCTION_INTRO)))
        store.applyRemoteProgress("u", TutorialId.T00_MULTI_FUNCTION_INTRO, viewed = true, skipped = false, completed = false)
        assertTrue(store.progress("u", TutorialId.T00_MULTI_FUNCTION_INTRO).completed)
        store.markCompleted("u", TutorialId.T01_PROFILE_PERSONAL)
        assertTrue(Onb02Planner.reopenFromHelpAllowed())
    }

    @Test
    fun storyIsDistinctFromPostAndExpires24h() {
        val created = 1_700_000_000_000L
        val story = FeedPost(
            id = "s1",
            authorId = "a",
            authorName = "Ana",
            type = PostType.STORY,
            title = "Historia",
            content = "",
            createdAt = created,
            expiresAt = StoryExpiration.expiresAtFrom(created)
        )
        val post = story.copy(id = "p1", type = PostType.GENERAL, expiresAt = null)
        assertTrue(story.isActiveStory(created + 1_000))
        assertFalse(story.isActiveStory(created + StoryExpiration.DURATION_MS + 1))
        assertFalse(post.isActiveStory())
        assertEquals(PostType.STORY, story.type)
        assertEquals(PostType.GENERAL, post.type)
    }

    @Test
    fun storyAndReelMediaAllowVideoUnlikePostMedia() {
        assertFalse(FilePurposePolicy.spec(FileAssetPurpose.POST_MEDIA).allowedMimeTypes.any { it.startsWith("video/") })
        assertEquals(VideoExportPolicy.OLD_POST_MEDIA_LIMIT_BYTES, FilePurposePolicy.spec(FileAssetPurpose.POST_MEDIA).maxSizeBytes)
        assertTrue(FilePurposePolicy.spec(FileAssetPurpose.STORY_MEDIA).allowedMimeTypes.contains("video/mp4"))
        assertTrue(FilePurposePolicy.spec(FileAssetPurpose.REEL_MEDIA).allowedMimeTypes.contains("video/mp4"))
        assertTrue(FilePurposePolicy.spec(FileAssetPurpose.STORY_MEDIA).maxSizeBytes > VideoExportPolicy.OLD_POST_MEDIA_LIMIT_BYTES)
        assertTrue(VideoExportPolicy.ROOT_CAUSE.contains("8 MiB"))
    }

    @Test
    fun ownStoryViewerVsCreatorContractInHome() {
        val home = com.comunidapp.app.ui.UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/screens/home/HomeScreen.kt"
        ).readText()
        assertTrue(home.contains("onOwnStoryClick"))
        assertTrue(home.contains("ownHasActive"))
        assertTrue(home.contains("onOpenStoryViewer"))
        assertFalse(home.contains("Compartir próximamente"))
    }

    @Test
    fun petIdManualFieldRemovedFromStoryAndReelComposers() {
        val story = com.comunidapp.app.ui.UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/screens/social/SocialComposerScreens.kt"
        ).readText()
        assertFalse(story.contains("ID de mascota"))
        assertFalse(story.contains("ID mascota"))
        assertTrue(story.contains("Agregar mascota"))
        assertTrue(story.contains("BitacoraCopy.SAVE_BUTTON"))
        assertTrue(story.contains("AHORA NO"))
        assertEquals("GUARDAR EN VITACORA", BitacoraCopy.SAVE_BUTTON)
        assertFalse(story.contains("V2LocationStringPicker"))
    }

    @Test
    fun bitacoraPromptIsExplicitNotAutomatic() {
        val prompt = BitacoraCopy.savePrompt(SocialContentKind.STORY, "Mora")
        assertTrue(prompt.contains("VitaCora de Mora"))
        val vm = com.comunidapp.app.ui.UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/viewmodel/PublishViewModel.kt"
        ).readText()
        assertTrue(vm.contains("bitacoraPrompt"))
        assertTrue(vm.contains("skipBitacoraSave"))
        assertTrue(vm.contains("confirmBitacoraSave"))
    }

    @Test
    fun commentsPersistAndCountInMock() {
        val repo = com.comunidapp.app.ui.UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/data/repository/CanonicalConsumerRepositories.kt"
        ).readText()
        val mock = com.comunidapp.app.ui.UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/data/mock/InMemoryDataStore.kt"
        ).readText()
        assertTrue(repo.contains("RPC_LIST_SOCIAL_COMMENTS"))
        assertTrue(repo.contains("RPC_DELETE_OWN_COMMENT"))
        assertTrue(repo.contains("refreshComments"))
        assertTrue(mock.contains("fun addComment"))
        assertTrue(mock.contains("fun deleteOwnComment"))
        assertTrue(mock.contains("commentCount = post.commentCount + 1"))
    }

    @Test
    fun shareDeepLinkContract() {
        assertEquals("https://leover.app/p/abc", SocialShare.deepLink(SocialContentKind.POST, "abc"))
        assertEquals("https://leover.app/r/abc", SocialShare.deepLink(SocialContentKind.REEL, "abc"))
        assertEquals("https://leover.app/s/abc", SocialShare.deepLink(SocialContentKind.STORY, "abc"))
        assertTrue(SocialShare.shareText(SocialContentKind.REEL, "Ana", "abc").contains("LeoVer"))
    }

    @Test
    fun storyCreateDoesNotUseGenericPostRpcInCanonicalRepo() {
        val src = com.comunidapp.app.ui.UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/data/repository/CanonicalConsumerRepositories.kt"
        ).readText()
        assertTrue(src.contains("RPC_CREATE_STORY"))
        assertTrue(src.contains("content_kind"))
        assertTrue(src.contains("RPC_LIST_SOCIAL_COMMENTS"))
    }
}
