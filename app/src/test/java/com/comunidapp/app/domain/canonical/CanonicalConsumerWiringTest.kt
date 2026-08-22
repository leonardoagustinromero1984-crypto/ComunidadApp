package com.comunidapp.app.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CanonicalConsumerWiringTest {

    @Test
    fun healthWritesAreTheSevenDeclaredRpcs() {
        assertEquals(7, CanonicalHealthWrites.rpcNames.size)
        assertEquals("DECLARED", CanonicalHealthWrites.PROVENANCE)
        assertTrue(CanonicalHealthWrites.rpcNames.contains("canon_record_pet_allergy"))
        assertTrue(CanonicalHealthWrites.rpcNames.contains("canon_set_pet_care_instructions"))
    }

    private fun sourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath"),
            File(System.getProperty("user.dir"), relativePath),
            File(System.getProperty("user.dir"), "../$relativePath")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("SOURCE_NOT_FOUND:$relativePath")
    }

    @Test
    fun usernameSignupUsesCanonicalRpc() {
        val source = sourceFile("app/src/main/java/com/comunidapp/app/data/remote/supabase/UserSupabaseDataSource.kt")
            .readText()
        assertTrue(source.contains("RPC_IS_USERNAME_AVAILABLE"))
        assertFalse(source.contains("is_username_available\""))
    }

    @Test
    fun petUpdateAndAvatarUseCanonicalRpcs() {
        val source = sourceFile(
            "app/src/main/java/com/comunidapp/app/data/remote/supabase/m08/SupabasePetM08RemoteDataSource.kt"
        ).readText()
        assertTrue(source.contains("RPC_UPDATE_PET"))
        assertTrue(source.contains("RPC_SET_PET_AVATAR"))
        assertTrue(source.contains("p_media_asset_id"))
        assertTrue(source.contains("RPC_RECORD_PET_ALLERGY"))
        assertTrue(source.contains("RPC_RECORD_PET_MEDICATION"))
        assertTrue(source.contains("RPC_RECORD_PET_CONDITION"))
        assertTrue(source.contains("RPC_LIST_PET_HOLDERS"))
        assertFalse(source.contains("m08_set_pet_avatar_asset"))
        assertFalse(source.contains("m08_update_pet_profile"))
        assertFalse(source.contains("hydrateDeclaredHealth"))
        assertTrue(source.contains("RPC_GET_PET_HEALTH"))
        assertTrue(source.contains("RPC_END_PET_RESPONSIBILITY"))
    }

    @Test
    fun permissionFacadesDoNotUseAppMode() {
        val role = sourceFile("app/src/main/java/com/comunidapp/app/domain/RolePermissions.kt").readText()
        val modules = sourceFile("app/src/main/java/com/comunidapp/app/domain/ModulePermissions.kt").readText()
        assertFalse(role.contains("enum class AppMode"))
        assertFalse(role.contains("toAppMode"))
        assertFalse(role.contains("AppMode."))
        assertFalse(modules.contains("AppMode"))
        assertFalse(modules.contains("toAppMode"))
    }

    @Test
    fun canonicalSchemaHasHealthReadRpcIn1024() {
        val migrations = listOf(
            "infra/supabase-canonical/supabase/migrations/20260815170900_1009_health_declared.sql",
            "infra/supabase-canonical/supabase/migrations/20260815172000_1020_rls_rpc.sql",
            "infra/supabase-canonical/supabase/migrations/20260815212500_1023_consumer_enablement.sql",
            "infra/supabase-canonical/supabase/migrations/20260816023000_1024_consumer_read_write.sql"
        ).joinToString("\n") { sourceFile(it).readText() }
        assertTrue(migrations.contains("canon_get_pet_health"))
        assertFalse(migrations.contains("canon_list_pet_allergies"))
        assertFalse(migrations.contains("canon_list_pet_medications"))
        assertFalse(migrations.contains("canon_list_pet_vaccinations"))
        assertFalse(migrations.contains("canon_list_pet_weights"))
        assertFalse(Regex("""grant\s+select\s+on\s+table\s+public\.pet_allergies""", RegexOption.IGNORE_CASE).containsMatchIn(migrations))
        assertFalse(Regex("""grant\s+select\s+on\s+table\s+public\.pet_medications""", RegexOption.IGNORE_CASE).containsMatchIn(migrations))
        assertFalse(Regex("""grant\s+select\s+on\s+table\s+public\.pet_weights""", RegexOption.IGNORE_CASE).containsMatchIn(migrations))
        assertFalse(Regex("""grant\s+select\s+on\s+table\s+public\.pet_declared_vaccinations""", RegexOption.IGNORE_CASE).containsMatchIn(migrations))
        assertTrue(migrations.contains("canon_record_pet_allergy"))
        assertTrue(migrations.contains("canon_record_pet_medication"))
        assertTrue(migrations.contains("canon_record_pet_vaccination"))
        assertTrue(migrations.contains("canon_record_pet_weight"))
    }

    @Test
    fun dataProviderWiresCanonicalMediaAndHolders() {
        val source = sourceFile("app/src/main/java/com/comunidapp/app/data/provider/DataProvider.kt").readText()
        assertTrue(source.contains("CanonicalFileUploadRepository"))
        assertTrue(source.contains("CanonicalFileAssetRepository"))
        assertTrue(source.contains("CanonicalFileDownloadRepository"))
        assertTrue(source.contains("CanonicalFileObjectUploader"))
        assertTrue(source.contains("CanonicalVitaCoraProjectionRepository"))
        assertTrue(source.contains("CanonicalLostFoundRepository"))
        assertTrue(source.contains("CanonicalAdoptionRepository"))
        assertTrue(source.contains("CanonicalFeedRepository"))
        assertTrue(source.contains("CanonicalChatRepository"))
        assertTrue(source.contains("if (useSupabase) SupabasePetResponsibilityRepository()"))
    }

    @Test
    fun petFormDoesNotDeletePreviousAsset() {
        val source = sourceFile("app/src/main/java/com/comunidapp/app/viewmodel/PetFormViewModel.kt").readText()
        assertTrue(source.contains("canon_set_pet_avatar"))
        assertFalse(source.contains("requestDelete"))
        assertFalse(source.contains("m08_set_pet_avatar_asset"))
    }

    @Test
    fun publicAdoptionUsesCanonicalRpc() {
        val source = sourceFile("web/lib/public/api.ts").readText()
        assertTrue(source.contains("canon_public_adoption"))
    }
}
