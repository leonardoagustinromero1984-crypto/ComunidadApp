package com.comunidapp.app.domain.m28

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.comunidapp.app.data.model.M28ExportSnapshot
import java.io.ByteArrayOutputStream

/** Generates a legible PDF from M28 export snapshot (Pilot Minimum — Android fallback without web). */
object M28ExportPdfGenerator {
    fun generate(snapshot: M28ExportSnapshot): ByteArray {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val titlePaint = Paint().apply {
            textSize = 16f
            isFakeBoldText = true
        }
        val bodyPaint = Paint().apply { textSize = 11f }
        var y = 40f
        fun line(text: String, paint: Paint = bodyPaint) {
            canvas.drawText(text.take(90), 40f, y, paint)
            y += 18f
        }
        line("LeoVer — Exportación profesional", titlePaint)
        line("Establecimiento: ${snapshot.clinicName}")
        line("Generado: ${snapshot.generatedAtEpochMs}")
        y += 8f
        line(snapshot.disclaimer)
        y += 12f
        line("Mascota: ${snapshot.pet["name"] ?: snapshot.pet["pet_id"] ?: "—"}", titlePaint)
        line("Atenciones: ${snapshot.cares.size}")
        snapshot.cares.take(20).forEach { c ->
            line("- ${c["care_type_label"] ?: c["id"]} · ${c["status"]} · ${c["finalized_at"] ?: ""}")
        }
        line("Vacunas: ${snapshot.vaccinations.size}")
        snapshot.vaccinations.take(10).forEach { v ->
            line("- ${v["vaccine_label"] ?: v["vaccine_code"]} · ${v["administered_at"]}")
        }
        document.finishPage(page)
        val out = ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }
}
