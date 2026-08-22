package com.comunidapp.app.domain.vitacora.import

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object VitacoraImportXlsx {
    private val XLS_MAGIC = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte())
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B)

    fun inspect(bytes: ByteArray, fileName: String): VitacoraImportWorkbookPayload {
        val name = fileName.lowercase()
        if (name.endsWith(".xls") && !name.endsWith(".xlsx")) {
            return rejected("XLS_REJECTED", bytes.size)
        }
        if (name.endsWith(".xlsm") || name.endsWith(".xlsb")) {
            return rejected("XLSM_REJECTED", bytes.size)
        }
        if (bytes.size > VitacoraImportPolicy.MAX_FILE_SIZE_BYTES) {
            return rejected("MAX_FILE_SIZE_EXCEEDED", bytes.size.toLong())
        }
        if (startsWith(bytes, XLS_MAGIC)) return rejected("XLS_REJECTED", bytes.size)
        if (!startsWith(bytes, ZIP_MAGIC)) return rejected("FAKE_XLSX", bytes.size)
        return try {
            parseZip(bytes)
        } catch (_: Exception) {
            rejected("XLSX_CORRUPT", bytes.size)
        }
    }

    fun writeTemplate(): ByteArray {
        val headers = VitacoraImportColumns.DOWNLOAD_HEADERS.values.toList()
        val example = listOf(
            "PATITAS-001", "Mora", "Perro", "Hembra", "Activo",
            "Buenos Aires", "La Plata", "2 años", "", "Mestizo", "Mediano",
            "Dorado", "Sí", "Sí", "Cariñosa y sociable", "2024-03-01",
            "Sí", "No", "Sí", "", "Ingresó con 3 meses"
        )
        val species = listOf("Perro", "Gato", "Otro")
        val sex = listOf("Macho", "Hembra", "Desconocido")
        val status = listOf("Activo", "Archivado")
        val yn = listOf("Sí", "No")
        return writeWorkbook(
            instructions = listOf(
                listOf("Plantilla oficial LeoVer — Importación VitaCora v1"),
                listOf("Una mascota por fila. No cambies los encabezados."),
                listOf("Completá los obligatorios: referencia, nombre, especie, sexo, estado, provincia y localidad."),
                listOf("El país se resuelve automáticamente (${com.comunidapp.app.domain.i18n.MarketUxPolicy.defaultCountryDisplayName()}). No lo completes."),
                listOf("Provincia y Localidad deben coincidir con el catálogo LeoVer."),
                listOf("La referencia interna es de tu organización (ej. PATITAS-001)."),
                listOf("No completes UUID, organization_id ni el número público de VitaCora."),
                listOf("Vacunado/Castrado no crean eventos clínicos: son declaraciones."),
                listOf("Importar no publica en adopción ni agrega fotos.")
            ),
            headers = headers,
            example = example,
            catalogs = mapOf(
                "Especie" to species,
                "Sexo" to sex,
                "Estado" to status,
                "SiNo" to yn
            )
        )
    }

    fun writePetsSheet(
        rows: List<List<String>>,
        templateType: String = VitacoraImportPolicy.TEMPLATE_TYPE,
        templateVersion: Int = VitacoraImportPolicy.TEMPLATE_VERSION,
        includeMeta: Boolean = true
    ): ByteArray {
        val headers = VitacoraImportColumns.HEADERS.values.toList()
        return writeWorkbook(
            instructions = listOf(listOf("Test")),
            headers = headers,
            example = null,
            extraRows = rows,
            catalogs = emptyMap(),
            templateType = templateType,
            templateVersion = templateVersion,
            includeMeta = includeMeta
        )
    }

    private fun parseZip(bytes: ByteArray): VitacoraImportWorkbookPayload {
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            var count = 0
            while (entry != null) {
                count++
                if (count > 80) return rejected("TOO_MANY_SHEETS", bytes.size)
                if (entry.size > 8L * 1024L * 1024L) return rejected("XLSX_CORRUPT", bytes.size)
                files[entry.name.replace('\\', '/')] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val hasVba = files.keys.any {
            it.contains("vbaProject", ignoreCase = true) || it.endsWith(".bin")
        }
        if (hasVba) return rejected("XLSM_REJECTED", bytes.size).copy(hasVba = true)
        val contentTypes = files["[Content_Types].xml"]?.toString(StandardCharsets.UTF_8).orEmpty()
        if (contentTypes.contains("macroEnabled", ignoreCase = true)) {
            return rejected("XLSM_REJECTED", bytes.size).copy(hasVba = true)
        }
        val workbookXml = files["xl/workbook.xml"]?.toString(StandardCharsets.UTF_8)
            ?: return rejected("XLSX_CORRUPT", bytes.size)
        val sheetNames = Regex("<sheet[^>]*name=\"([^\"]+)\"").findAll(workbookXml)
            .map { it.groupValues[1] }
            .toList()
        if (sheetNames.size > VitacoraImportPolicy.MAX_SHEETS) {
            return rejected("TOO_MANY_SHEETS", bytes.size)
        }
        val rels = files["xl/_rels/workbook.xml.rels"]?.toString(StandardCharsets.UTF_8).orEmpty()
        val relMap = Regex("Id=\"(rId[^\"]+)\"[^>]*Target=\"([^\"]+)\"").findAll(rels)
            .associate { it.groupValues[1] to it.groupValues[2].removePrefix("/") }
        val sheetRid = Regex("<sheet[^>]*name=\"MASCOTAS\"[^>]*r:id=\"([^\"]+)\"").find(workbookXml)
            ?.groupValues?.get(1)
        val metaRid = Regex("<sheet[^>]*name=\"_LEOVER_META\"[^>]*r:id=\"([^\"]+)\"").find(workbookXml)
            ?.groupValues?.get(1)
        val strings = parseSharedStrings(files["xl/sharedStrings.xml"]?.toString(StandardCharsets.UTF_8))
        val mascotasPath = sheetRid?.let { relMap[it] }?.let { normalizeXl(it) }
            ?: files.keys.firstOrNull { it.contains("sheet1", true) }
        val metaPath = metaRid?.let { relMap[it] }?.let { normalizeXl(it) }
        val mascotasXml = mascotasPath?.let { files[it] }?.toString(StandardCharsets.UTF_8)
            ?: return rejected("XLSX_CORRUPT", bytes.size)
        val grid = parseSheet(mascotasXml, strings)
        if (grid.isEmpty()) return rejected("XLSX_CORRUPT", bytes.size)
        val header = grid.first().map { headerKey(it.value) }
        val dataRows = mutableListOf<VitacoraImportRawRow>()
        grid.drop(1).forEachIndexed { index, row ->
            if (row.all { it.value.isBlank() && !it.formula }) return@forEachIndexed
            val values = linkedMapOf<String, String>()
            val formulas = mutableSetOf<String>()
            header.forEachIndexed { col, key ->
                if (key == null) return@forEachIndexed
                val cell = row.getOrNull(col)
                values[key] = cell?.value.orEmpty()
                if (cell?.formula == true) formulas += key
                if ((cell?.value?.length ?: 0) > VitacoraImportPolicy.MAX_STRING_CHARS) {
                    return rejected("STRING_TOO_LONG", bytes.size)
                }
            }
            dataRows += VitacoraImportRawRow(
                rowNumber = index + 2,
                values = values,
                formulaFields = formulas
            )
        }
        if (dataRows.size > VitacoraImportPolicy.MAX_ROWS) {
            return rejected("MAX_ROWS_EXCEEDED", bytes.size)
        }
        var type: String? = null
        var version: Int? = null
        metaPath?.let { files[it] }?.toString(StandardCharsets.UTF_8)?.let { xml ->
            val metaGrid = parseSheet(xml, strings)
            metaGrid.forEach { row ->
                val k = row.getOrNull(0)?.value?.trim().orEmpty()
                val v = row.getOrNull(1)?.value?.trim().orEmpty()
                if (k.equals("template_type", true)) type = v
                if (k.equals("template_version", true)) version = v.toIntOrNull()
            }
        }
        return VitacoraImportWorkbookPayload(
            templateType = type,
            templateVersion = version,
            rows = dataRows,
            hasVba = false,
            sheetCount = sheetNames.size,
            fileSizeBytes = bytes.size.toLong()
        )
    }

    private data class XlsxCell(val value: String, val formula: Boolean)

    private fun parseSharedStrings(xml: String?): List<String> {
        if (xml.isNullOrBlank()) return emptyList()
        return Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { si ->
            Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).findAll(si.groupValues[1])
                .joinToString("") { unescape(it.groupValues[1]) }
        }.toList()
    }

    private fun parseSheet(xml: String, strings: List<String>): List<List<XlsxCell>> {
        val rows = mutableListOf<List<XlsxCell>>()
        Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { rowMatch ->
            val cells = sortedMapOf<Int, XlsxCell>()
            Regex("<c ([^>]*)>(.*?)</c>|<c ([^>]*)/>", RegexOption.DOT_MATCHES_ALL)
                .findAll(rowMatch.groupValues[1]).forEach { c ->
                    val attrs = c.groupValues[1].ifBlank { c.groupValues[3] }
                    val inner = c.groupValues[2]
                    val ref = Regex("r=\"([A-Z]+)(\\d+)\"").find(attrs)?.groupValues?.get(1) ?: return@forEach
                    val col = colIndex(ref)
                    val formula = inner.contains("<f")
                    val t = Regex("t=\"([^\"]+)\"").find(attrs)?.groupValues?.get(1)
                    val v = Regex("<v>(.*?)</v>").find(inner)?.groupValues?.get(1).orEmpty()
                    val inline = Regex("<t[^>]*>(.*?)</t>").find(inner)?.groupValues?.get(1)
                    val value = when {
                        inline != null -> unescape(inline)
                        t == "s" -> strings.getOrNull(v.toIntOrNull() ?: -1).orEmpty()
                        else -> unescape(v)
                    }
                    cells[col] = XlsxCell(value, formula)
                }
            if (cells.isNotEmpty()) {
                val max = cells.lastKey()
                rows += (0..max).map { cells[it] ?: XlsxCell("", false) }
            }
        }
        return rows
    }

    private fun colIndex(letters: String): Int {
        var n = 0
        letters.forEach { n = n * 26 + (it - 'A' + 1) }
        return n - 1
    }

    private fun headerKey(label: String): String? {
        val n = label.trim()
        return VitacoraImportColumns.HEADERS.entries.firstOrNull { it.value.equals(n, true) }?.key
    }

    private fun writeWorkbook(
        instructions: List<List<String>>,
        headers: List<String>,
        example: List<String>?,
        extraRows: List<List<String>> = emptyList(),
        catalogs: Map<String, List<String>>,
        templateType: String = VitacoraImportPolicy.TEMPLATE_TYPE,
        templateVersion: Int = VitacoraImportPolicy.TEMPLATE_VERSION,
        includeMeta: Boolean = true
    ): ByteArray {
        val mascotas = mutableListOf(headers)
        if (example != null) mascotas += example
        mascotas += extraRows
        val catalogRows = mutableListOf<List<String>>()
        val catalogHeaders = catalogs.keys.toList()
        if (catalogHeaders.isNotEmpty()) {
            catalogRows += catalogHeaders
            val max = catalogs.values.maxOf { it.size }
            repeat(max) { i ->
                catalogRows += catalogHeaders.map { catalogs[it]?.getOrNull(i).orEmpty() }
            }
        }
        val meta = listOf(
            listOf("template_type", templateType),
            listOf("template_version", templateVersion.toString())
        )
        val sheets = buildList {
            add("INSTRUCCIONES" to instructions)
            add("MASCOTAS" to mascotas)
            if (catalogRows.isNotEmpty()) add("CATALOGOS" to catalogRows)
            if (includeMeta) add("_LEOVER_META" to meta)
        }
        val strings = linkedSetOf<String>()
        sheets.forEach { (_, rows) -> rows.forEach { row -> row.forEach { strings += it } } }
        val shared = strings.toList()
        val indexOf = shared.withIndex().associate { it.value to it.index }

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, xml: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(xml.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypes(sheets.size))
            put("_rels/.rels", relsRoot())
            put("xl/workbook.xml", workbookXml(sheets.map { it.first }))
            put("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            put("xl/sharedStrings.xml", sharedStringsXml(shared))
            put("xl/styles.xml", stylesXml())
            sheets.forEachIndexed { i, (_, rows) ->
                put("xl/worksheets/sheet${i + 1}.xml", sheetXml(rows, indexOf, freeze = i == 1))
            }
        }
        return out.toByteArray()
    }

    private fun contentTypes(sheetCount: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        repeat(sheetCount) { i ->
            append("""<Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun relsRoot() =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbookXml(names: List<String>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("<sheets>")
        names.forEachIndexed { i, name ->
            val hidden = if (name.startsWith("_") || name == "CATALOGOS") """ state="hidden"""" else ""
            append("""<sheet name="$name" sheetId="${i + 1}" r:id="rId${i + 1}"$hidden/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(count: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        repeat(count) { i ->
            append("""<Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>""")
        }
        append("""<Relationship Id="rId${count + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>""")
        append("""<Relationship Id="rId${count + 2}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private fun sharedStringsXml(values: List<String>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${values.size}" uniqueCount="${values.size}">""")
        values.forEach {
            append("<si><t xml:space=\"preserve\">${escape(it)}</t></si>")
        }
        append("</sst>")
    }

    private fun stylesXml() =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
<fills count="1"><fill><patternFill patternType="none"/></fill></fills>
<borders count="1"><border/></borders>
<cellStyleXfs count="1"><xf/></cellStyleXfs>
<cellXfs count="1"><xf/></cellXfs>
</styleSheet>"""

    private fun sheetXml(rows: List<List<String>>, indexOf: Map<String, Int>, freeze: Boolean) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        if (freeze) append("""<sheetViews><sheetView tabSelected="1" workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        append("""<sheetFormatPr defaultColWidth="18"/>""")
        append("<sheetData>")
        rows.forEachIndexed { r, row ->
            append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, value ->
                val ref = colName(c) + (r + 1)
                val idx = indexOf[value] ?: 0
                append("""<c r="$ref" t="s"><v>$idx</v></c>""")
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun colName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            n--
            sb.insert(0, ('A'.code + n % 26).toChar())
            n /= 26
        }
        return sb.toString()
    }

    private fun normalizeXl(target: String): String {
        val t = target.removePrefix("/").removePrefix("xl/")
        return if (target.startsWith("xl/")) target else "xl/$t"
    }

    private fun rejected(code: String, size: Int) = rejected(code, size.toLong())
    private fun rejected(code: String, size: Long) = VitacoraImportWorkbookPayload(
        templateType = null,
        templateVersion = null,
        rows = emptyList(),
        fileSizeBytes = size,
        rejected = code
    )

    private fun startsWith(bytes: ByteArray, magic: ByteArray): Boolean {
        if (bytes.size < magic.size) return false
        return magic.indices.all { bytes[it] == magic[it] }
    }

    private fun escape(value: String) = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun unescape(value: String) = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
}
