package com.orion.omi

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import org.json.JSONObject
import java.io.OutputStream
import java.util.Locale

/** CSV and PDF exports for a date range. */
object Export {
    private fun q(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    private fun medName(s: Snapshot, id: String): String = s.meds[id]?.optString("name") ?: id

    fun dosesInRange(s: Snapshot, from: Long, to: Long): List<JSONObject> =
        s.doses.filter { T.parse(it.optString("taken_at")) in from..to }
            .sortedBy { T.parse(it.optString("taken_at")) }

    fun bpsInRange(s: Snapshot, from: Long, to: Long): List<JSONObject> =
        s.bps.filter { T.parse(it.optString("measured_at")) in from..to }
            .sortedBy { T.parse(it.optString("measured_at")) }

    fun dosesCsv(s: Snapshot, from: Long, to: Long): String {
        val sb = StringBuilder("﻿")
        sb.append("date,time,medication,strength,quantity,reason,note\r\n")
        for (d in dosesInRange(s, from, to)) {
            val ms = T.parse(d.optString("taken_at"))
            val med = s.meds[d.optString("med_id")]
            sb.append(T.day(ms)).append(',').append(T.hm(ms)).append(',')
                .append(q(medName(s, d.optString("med_id")))).append(',')
                .append(q(med?.optString("strength") ?: "")).append(',')
                .append(fmtQty(d.optDouble("qty", 1.0))).append(',')
                .append(q(reasonLabel(d.optString("reason"), false))).append(',')
                .append(q(d.optString("note"))).append("\r\n")
        }
        return sb.toString()
    }

    fun bpCsv(s: Snapshot, from: Long, to: Long): String {
        val sb = StringBuilder("﻿")
        sb.append("date,time,systolic,diastolic,pulse,context,note\r\n")
        for (b in bpsInRange(s, from, to)) {
            val ms = T.parse(b.optString("measured_at"))
            sb.append(T.day(ms)).append(',').append(T.hm(ms)).append(',')
                .append(b.optInt("systolic")).append(',').append(b.optInt("diastolic")).append(',')
                .append(if (b.has("pulse")) b.optInt("pulse").toString() else "").append(',')
                .append(q(contextLabel(b.optString("context")))).append(',')
                .append(q(b.optString("note"))).append("\r\n")
        }
        return sb.toString()
    }

    private class PdfOut {
        val doc = PdfDocument()
        private var page: PdfDocument.Page? = null
        private var y = 0f
        private var n = 0
        private val body = Paint().apply { textSize = 11f; isAntiAlias = true }
        private val head = Paint().apply { textSize = 14f; isFakeBoldText = true; isAntiAlias = true }

        private fun newPage() {
            page?.let { doc.finishPage(it) }
            n++
            page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, n).create())
            y = 50f
        }

        /** Long lines wrap onto the next line instead of being cut off. */
        fun line(s: String, bold: Boolean = false, gap: Float = 15f) {
            val width = if (bold) 70 else 90
            var rest = s
            do {
                if (page == null || y > 800f) newPage()
                val p = page!!
                p.canvas.drawText(rest.take(width), 40f, y, if (bold) head else body)
                y += gap
                rest = rest.drop(width)
            } while (rest.isNotEmpty())
        }

        fun space() {
            y += 8f
        }

        fun finish(out: OutputStream) {
            page?.let { doc.finishPage(it) }
            doc.writeTo(out)
            doc.close()
        }
    }

    fun pdf(s: Snapshot, from: Long, to: Long, out: OutputStream) {
        val w = PdfOut()
        val doses = dosesInRange(s, from, to)
        val bps = bpsInRange(s, from, to)
        w.line("OMI report", true, 20f)
        w.line("Period: ${T.day(from)} to ${T.day(to)}   Created: ${T.nice(System.currentTimeMillis())}")
        w.space()

        w.line("Blood pressure", true, 20f)
        if (bps.isEmpty()) {
            w.line("No readings in this period.")
        } else {
            val sys = bps.map { it.optInt("systolic") }
            val dia = bps.map { it.optInt("diastolic") }
            val pul = bps.filter { it.has("pulse") }.map { it.optInt("pulse") }
            w.line("Readings: ${bps.size}")
            w.line(String.format(Locale.US, "Average: %.0f/%.0f", sys.average(), dia.average()) +
                    (if (pul.isNotEmpty()) String.format(Locale.US, "   pulse %.0f", pul.average()) else ""))
            w.line("Lowest: ${sys.min()}/${dia.min()}   Highest: ${sys.max()}/${dia.max()}")
            w.space()
            for (b in bps) {
                val ms = T.parse(b.optString("measured_at"))
                var t = "${T.nice(ms)}   ${b.optInt("systolic")}/${b.optInt("diastolic")}"
                if (b.has("pulse")) t += "   pulse ${b.optInt("pulse")}"
                val note = b.optString("note")
                if (note.isNotEmpty()) t += "   $note"
                w.line(t)
            }
        }
        w.space()

        w.line("Medication", true, 20f)
        if (doses.isEmpty()) {
            w.line("No doses in this period.")
        } else {
            val byMed = doses.groupBy { it.optString("med_id") }
            for ((id, list) in byMed) {
                val med = s.meds[id]
                val first = list.minOf { T.parse(it.optString("taken_at")) }
                val last = list.maxOf { T.parse(it.optString("taken_at")) }
                val total = list.sumOf { it.optDouble("qty", 0.0) }
                w.line(Msg.medLabel(med, id), true)
                w.line("Doses: ${list.size}   Total quantity: ${fmtQty(total)}   First: ${T.day(first)}   Last: ${T.day(last)}")
                w.space()
            }
            w.line("All doses", true, 20f)
            for (d in doses) {
                val ms = T.parse(d.optString("taken_at"))
                var t = "${T.nice(ms)}   ${Msg.medLabel(s.meds[d.optString("med_id")], d.optString("med_id"))} x ${fmtQty(d.optDouble("qty", 1.0))}   ${reasonLabel(d.optString("reason"), false)}"
                val note = d.optString("note")
                if (note.isNotEmpty()) t += "   $note"
                w.line(t)
            }
        }
        w.finish(out)
    }
}
