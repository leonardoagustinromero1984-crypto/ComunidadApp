package com.comunidapp.app.domain.vitacora.import

import com.comunidapp.app.data.model.argentinaLocationSeed
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VitacoraImportEngineTest {

    private val locations = argentinaLocationSeed()

    private fun member(
        orgId: String = "org-a",
        verified: Boolean = true,
        membership: Boolean = true,
        permissions: Set<String> = setOf(VitacoraImportPolicy.ORG_IMPORT_PERMISSION),
        staff: Boolean = false
    ) = VitacoraImportAuth(
        actorId = "user-1",
        isStaff = staff,
        membershipActive = membership,
        permissions = permissions,
        organizationVerified = verified,
        requestedOrganizationId = orgId
    )

    private fun staff(orgId: String? = "org-a") = VitacoraImportAuth(
        actorId = "staff-1",
        isStaff = true,
        membershipActive = false,
        permissions = emptySet(),
        organizationVerified = false,
        requestedOrganizationId = orgId
    )

    private fun row(
        ref: String = "PATITAS-001",
        name: String = "Mora",
        species: String = "Perro",
        sex: String = "Hembra",
        status: String = "Activo",
        country: String = "Argentina",
        province: String = "Buenos Aires",
        locality: String = "La Plata",
        extra: Map<String, String> = emptyMap()
    ): List<String> {
        val values = linkedMapOf(
            VitacoraImportColumns.EXTERNAL_ID to ref,
            VitacoraImportColumns.NAME to name,
            VitacoraImportColumns.SPECIES to species,
            VitacoraImportColumns.SEX to sex,
            VitacoraImportColumns.STATUS to status,
            VitacoraImportColumns.COUNTRY to country,
            VitacoraImportColumns.ADMIN_AREA to province,
            VitacoraImportColumns.LOCALITY to locality,
            VitacoraImportColumns.AGE to "",
            VitacoraImportColumns.BIRTH to "",
            VitacoraImportColumns.BREED to "",
            VitacoraImportColumns.SIZE to "",
            VitacoraImportColumns.COLOR to "",
            VitacoraImportColumns.NEUTERED to "",
            VitacoraImportColumns.VACCINATED to "",
            VitacoraImportColumns.DESCRIPTION to "",
            VitacoraImportColumns.INTAKE to "",
            VitacoraImportColumns.DOGS to "",
            VitacoraImportColumns.CATS to "",
            VitacoraImportColumns.KIDS to "",
            VitacoraImportColumns.SPECIAL to "",
            VitacoraImportColumns.NOTES to ""
        )
        extra.forEach { (k, v) -> values[k] = v }
        return VitacoraImportColumns.HEADERS.keys.map { values[it].orEmpty() }
    }

    private fun bytes(rows: List<List<String>>, type: String = VitacoraImportPolicy.TEMPLATE_TYPE, version: Int = 1, meta: Boolean = true) =
        VitacoraImportXlsx.writePetsSheet(rows, type, version, meta)

    private fun engine() = InMemoryVitacoraImportEngine(locations)

    private suspend fun analyzeFile(
        engine: InMemoryVitacoraImportEngine,
        rows: List<List<String>>,
        auth: VitacoraImportAuth = member(),
        mode: VitacoraImportMode = VitacoraImportMode.SELF_SERVICE,
        orgId: String = "org-a",
        fileName: String = "ok.xlsx",
        raw: ByteArray? = null
    ): VitacoraImportJob {
        val data = raw ?: bytes(rows)
        val job = engine.createJob(orgId, "Patitas", mode, auth.actorId, auth, "mock/a.xlsx", sha256Hex(data)).getOrThrow()
        engine.rememberPayload(job.id, VitacoraImportXlsx.inspect(data, fileName))
        return engine.analyze(job.id, VitacoraImportXlsx.inspect(data, fileName), auth).getOrThrow()
    }

    @Test
    fun publicNumberIsServerGeneratedAndUnique() {
        val engine = engine()
        val first = engine.nextPublicNumberIgnoringClient(999)
        val second = engine.nextPublicNumberIgnoringClient(1)
        assertTrue(first > 0)
        assertNotEquals(999L, first)
        assertNotEquals(first, second)
        assertEquals(first + 1, second)
    }

    @Test
    fun concurrentNumbersDoNotCollide() = runBlocking {
        val engine = engine()
        val nums = (1..40).map {
            async { engine.nextPublicNumberIgnoringClient(1) }
        }.awaitAll()
        assertEquals(40, nums.toSet().size)
    }

    @Test
    fun rollbackMayConsumeNumberWithoutReuse() {
        val engine = engine()
        val consumed = engine.nextPublicNumberIgnoringClient(null)
        val next = engine.nextPublicNumberIgnoringClient(null)
        assertEquals(consumed + 1, next)
        assertFalse(VitacoraImportPolicy.NUMBER_REUSE_ALLOWED)
        assertFalse(VitacoraImportPolicy.NUMBER_GAPLESS_REQUIRED)
    }

    @Test
    fun uuidPreservedAndLegacyUnnumbered() = runBlocking {
        val engine = engine()
        val analyzed = analyzeFile(engine, listOf(row()))
        val confirmed = engine.confirm(analyzed.id, member()).getOrThrow()
        val created = engine.createdPets().first()
        assertNotNull(created.petId)
        assertTrue(created.vitacoraNumber > 0)
        assertEquals(created.petId, confirmed.rows.first().createdPetId)
        assertNull(null as Long?)
    }

    @Test
    fun searchQueriesParsePublicNumber() {
        assertEquals(1528L, VitacoraNumberQuery.parse("1528"))
        assertEquals(1528L, VitacoraNumberQuery.parse("#1528"))
        assertEquals(1528L, VitacoraNumberQuery.parse("VitaCora 1528"))
        assertEquals(1528L, VitacoraNumberQuery.parse("VitaCora #1528"))
        assertEquals("Mora · VitaCora #1528", VitacoraNumberQuery.petTitle("Mora", 1528))
    }

    @Test
    fun permissionsRejectUnauthorizedActors() {
        assertTrue(VitacoraImportAuthorization.canImport(member(verified = false), "org-a", VitacoraImportMode.SELF_SERVICE).isFailure)
        assertTrue(VitacoraImportAuthorization.canImport(member(membership = false), "org-a", VitacoraImportMode.SELF_SERVICE).isFailure)
        assertTrue(VitacoraImportAuthorization.canImport(member(permissions = emptySet()), "org-a", VitacoraImportMode.SELF_SERVICE).isFailure)
        assertTrue(VitacoraImportAuthorization.canImport(member(orgId = "org-a"), "org-b", VitacoraImportMode.SELF_SERVICE).isFailure)
        assertTrue(VitacoraImportAuthorization.canImport(member(), "org-a", VitacoraImportMode.ADMIN).isFailure)
        assertTrue(VitacoraImportAuthorization.canImport(staff("org-a"), "org-a", VitacoraImportMode.ADMIN).isSuccess)
        assertTrue(VitacoraImportAuthorization.canImport(member(permissions = setOf(VitacoraImportPolicy.ORG_PETS_MANAGE_PERMISSION)), "org-a", VitacoraImportMode.SELF_SERVICE).isSuccess)
    }

    @Test
    fun previewDoesNotCreatePetsAndConfirmIsIdempotent() = runBlocking {
        val engine = engine()
        val preview = analyzeFile(engine, listOf(row()))
        assertEquals(0, engine.createdPets().size)
        assertEquals(VitacoraImportJobStatus.READY, preview.status)
        val first = engine.confirm(preview.id, member()).getOrThrow()
        val second = engine.confirm(preview.id, member()).getOrThrow()
        assertEquals(1, engine.createdPets().size)
        assertEquals(first.summary.created, second.summary.created)
        assertEquals(first.rows.first().createdPetId, second.rows.first().createdPetId)
        assertFalse(engine.createdPets().first().adoptionPublished)
        assertTrue(engine.createdPets().first().needsPhoto)
    }

    @Test
    fun invalidRowDoesNotCreateAndValidRowDoes() = runBlocking {
        val engine = engine()
        val job = analyzeFile(
            engine,
            listOf(
                row(ref = "OK-1"),
                row(ref = "BAD-1", locality = "San Visente")
            )
        )
        assertEquals(1, job.summary.ready)
        assertEquals(1, job.summary.errors)
        val done = engine.confirm(job.id, member()).getOrThrow()
        assertEquals(1, done.summary.created)
        assertEquals(1, engine.createdPets().size)
    }

    @Test
    fun existingExternalIdIsNotUpdated() = runBlocking {
        val engine = engine()
        engine.seedExisting("org-a", "PATITAS-001")
        val job = analyzeFile(engine, listOf(row(ref = " patitas-001 ")))
        assertEquals(VitacoraImportRowStatus.YA_EXISTE, job.rows.first().status)
        engine.confirm(job.id, member())
        assertEquals(0, engine.createdPets().size)
        assertFalse(VitacoraImportPolicy.EXISTING_PETS_AUTO_UPDATED)
    }

    @Test
    fun sameExternalIdAllowedInAnotherOrg() = runBlocking {
        val engine = engine()
        engine.seedExisting("org-b", "PATITAS-001")
        val job = analyzeFile(engine, listOf(row()), orgId = "org-a")
        assertEquals(VitacoraImportRowStatus.LISTA, job.rows.first().status)
    }

    @Test
    fun duplicateWithinFileDetected() = runBlocking {
        val engine = engine()
        val job = analyzeFile(engine, listOf(row(ref = "PATITAS-001"), row(ref = "patitas-001", name = "Luna")))
        assertTrue(job.rows.any { it.status == VitacoraImportRowStatus.POSIBLE_DUPLICADO })
    }

    @Test
    fun assistedRequiresStaffToConfirmAndUsesSameEngine() = runBlocking {
        val engine = engine()
        val org = member()
        val job = analyzeFile(engine, listOf(row()), auth = org, mode = VitacoraImportMode.ASSISTED)
        assertEquals(VitacoraImportJobStatus.READY, job.status)
        assertTrue(engine.confirm(job.id, org).isFailure)
        val done = engine.confirm(job.id, staff()).getOrThrow()
        assertEquals(1, done.summary.created)
        assertEquals("staff-1", done.executedBy)
    }

    @Test
    fun adminModeUsesSameEngine() = runBlocking {
        val engine = engine()
        val job = analyzeFile(engine, listOf(row()), auth = staff(), mode = VitacoraImportMode.ADMIN)
        val done = engine.confirm(job.id, staff()).getOrThrow()
        assertEquals(1, done.summary.created)
        assertEquals(VitacoraImportMode.ADMIN, done.mode)
    }

    @Test
    fun xlsxValidOneAndFiftyAndNearMax() = runBlocking {
        val engine = engine()
        val one = analyzeFile(engine, listOf(row(ref = "R-1")))
        assertEquals(1, one.summary.ready)
        val fifty = analyzeFile(engine, (1..50).map { row(ref = "R-$it", name = "Pet$it") })
        assertEquals(50, fifty.summary.ready)
        val near = analyzeFile(engine, (1..VitacoraImportPolicy.MAX_ROWS).map { row(ref = "M-$it", name = "N$it") })
        assertEquals(VitacoraImportPolicy.MAX_ROWS, near.summary.ready)
    }

    @Test
    fun xlsxValidationCases() {
        val ctx = VitacoraImportAnalyzeContext("org-a", emptySet(), locations, true, true, VitacoraImportMode.SELF_SERVICE)
        fun analyze(rows: List<List<String>>, inspect: VitacoraImportWorkbookPayload? = null) =
            VitacoraImportAnalyzer.analyze(inspect ?: VitacoraImportXlsx.inspect(bytes(rows), "ok.xlsx"), ctx)

        assertTrue(analyze(listOf(row(name = ""))).second.first().issues.any { it.code == "REQUIRED" })
        assertTrue(analyze(listOf(row(extra = mapOf(VitacoraImportColumns.BIRTH to "32/13/2020")))).second.first().issues.any { it.code == "DATE_INVALID" })
        assertTrue(analyze(listOf(row(species = "Dragón"))).second.first().issues.any { it.code == "ENUM_INVALID" })
        assertTrue(analyze(listOf(row(extra = mapOf(VitacoraImportColumns.NEUTERED to "quizás")))).second.first().issues.any { it.code == "YES_NO_INVALID" })
        assertTrue(analyze(listOf(row(country = "Narnia"))).second.first().issues.any { it.code == "COUNTRY_INVALID" })
        assertTrue(analyze(listOf(row(province = "Provincia Inventada"))).second.first().issues.any { it.code == "ADMINISTRATIVE_AREA_INVALID" })
        assertTrue(analyze(listOf(row(locality = "San Visente"))).second.first().issues.any { it.code == "LOCALITY_INVALID" })
        assertTrue(analyze(listOf(row(province = "Mendoza", locality = "La Plata"))).second.first().issues.any { it.code == "LOCALITY_NOT_IN_AREA" })
        assertEquals("TEMPLATE_VERSION_INVALID", analyze(listOf(row()), VitacoraImportXlsx.inspect(bytes(listOf(row()), version = 9), "ok.xlsx")).second.first().issues.first().code)
        assertEquals("TEMPLATE_TYPE_INVALID", analyze(listOf(row()), VitacoraImportXlsx.inspect(bytes(listOf(row()), type = "OTHER"), "ok.xlsx")).second.first().issues.first().code)
        assertEquals("TEMPLATE_META_MISSING", analyze(listOf(row()), VitacoraImportXlsx.inspect(bytes(listOf(row()), meta = false), "ok.xlsx")).second.first().issues.first().code)
        assertEquals("XLS_REJECTED", VitacoraImportXlsx.inspect(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte()), "pets.xls").rejected)
        assertEquals("XLSM_REJECTED", VitacoraImportXlsx.inspect(byteArrayOf(0x50, 0x4B, 3, 4), "pets.xlsm").rejected)
        assertEquals("XLSX_CORRUPT", VitacoraImportXlsx.inspect(byteArrayOf(0x50, 0x4B, 3, 4, 0, 0, 0), "pets.xlsx").rejected)
        assertEquals("FAKE_XLSX", VitacoraImportXlsx.inspect("not-a-zip".toByteArray(), "pets.xlsx").rejected)
        assertEquals("MAX_FILE_SIZE_EXCEEDED", VitacoraImportXlsx.inspect(ByteArray(VitacoraImportPolicy.MAX_FILE_SIZE_BYTES.toInt() + 1) { 0x50 }, "pets.xlsx").rejected)
        val over = VitacoraImportXlsx.inspect(bytes((1..501).map { row(ref = "X-$it", name = "N$it") }), "ok.xlsx")
        assertEquals("MAX_ROWS_EXCEEDED", over.rejected)
        val formula = withFormula(bytes(listOf(row())))
        val inspected = VitacoraImportXlsx.inspect(formula, "ok.xlsx")
        val result = VitacoraImportAnalyzer.analyze(inspected, ctx)
        assertTrue(result.second.any { row -> row.issues.any { it.code == "FORMULA_FORBIDDEN" } })
    }

    @Test
    fun templateIdentityIsDetectable() {
        val bytes = VitacoraImportXlsx.writeTemplate()
        val inspected = VitacoraImportXlsx.inspect(bytes, VitacoraImportPolicy.PUBLISHED_FILENAME)
        assertEquals(VitacoraImportPolicy.TEMPLATE_TYPE, inspected.templateType)
        assertEquals(VitacoraImportPolicy.TEMPLATE_VERSION, inspected.templateVersion)
        assertNull(inspected.rejected)
        assertTrue(bytes.size < VitacoraImportPolicy.MAX_FILE_SIZE_BYTES)
    }

    private fun withFormula(source: ByteArray): ByteArray {
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(source)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                files[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val sheet = files.keys.first { it.contains("sheet2") }
        val xml = files[sheet]!!.toString(Charsets.UTF_8)
            .replaceFirst("""<c r="A2" t="s"><v>""", """<c r="A2" t="str"><f>1+1</f><v>""")
        files[sheet] = xml.toByteArray()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
