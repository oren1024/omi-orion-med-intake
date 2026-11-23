package com.orion.omi

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The WhatsApp messages, in English and Hebrew. */
object Msg {
    private val heDays = arrayOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת")

    fun medLabel(med: JSONObject?, fallbackId: String = ""): String {
        if (med == null) return fallbackId
        val s = med.optString("strength")
        return if (s.isEmpty()) med.optString("name") else med.optString("name") + " " + s
    }

    private fun medText(med: JSONObject?, fallbackId: String, he: Boolean): String {
        if (med == null) return fallbackId
        val nameHe = med.optString("name_he")
        val name = if (he && nameHe.isNotEmpty()) nameHe else med.optString("name")
        val s = med.optString("strength")
        return if (s.isEmpty()) name else "$name $s"
    }

    private fun dateTime(ms: Long, he: Boolean, withYear: Boolean): String {
        if (he) {
            val c = Calendar.getInstance()
            c.timeInMillis = ms
            val day = heDays[c.get(Calendar.DAY_OF_WEEK) - 1]
            val pattern = if (withYear) "dd.MM.yyyy, HH:mm" else "dd.MM, HH:mm"
            return day + " " + SimpleDateFormat(pattern, Locale.US).format(Date(ms))
        }
        val pattern = if (withYear) "EEE dd MMM yyyy, HH:mm" else "EEE dd MMM, HH:mm"
        return SimpleDateFormat(pattern, Locale.ENGLISH).format(Date(ms))
    }

    fun dose(
        med: JSONObject?, dose: JSONObject, left: Double?, he: Boolean,
        showReason: Boolean, showLeft: Boolean
    ): String {
        val ms = T.parse(dose.optString("taken_at"))
        val sb = StringBuilder()
        sb.append("💊 ").append(medText(med, dose.optString("med_id"), he))
        sb.append(" × ").append(fmtQty(dose.optDouble("qty", 1.0)))
        sb.append("\n").append(dateTime(ms, he, true))
        if (showReason) sb.append(" · ").append(reasonLabel(dose.optString("reason"), he))
        if (showLeft && left != null) {
            sb.append(" · ").append(if (he) "נותרו " + fmtQty(left) else fmtQty(left) + " left")
        }
        val note = dose.optString("note")
        if (note.isNotEmpty()) sb.append("\n📝 ").append(note)
        return sb.toString()
    }

    fun bp(b: JSONObject, he: Boolean): String {
        val ms = T.parse(b.optString("measured_at"))
        val sb = StringBuilder()
        sb.append("🩺 ").append(if (he) "לחץ דם " else "BP ")
        sb.append(b.optInt("systolic")).append("/").append(b.optInt("diastolic"))
        if (b.has("pulse")) {
            sb.append(" · ").append(if (he) "דופק " else "pulse ").append(b.optInt("pulse"))
        }
        sb.append(" · ").append(dateTime(ms, he, false))
        val note = b.optString("note")
        if (note.isNotEmpty()) sb.append("\n📝 ").append(note)
        return sb.toString()
    }

    fun removedDose(med: JSONObject?, dose: JSONObject, he: Boolean): String {
        val ms = T.parse(dose.optString("taken_at"))
        val label = medText(med, dose.optString("med_id"), he)
        val qty = fmtQty(dose.optDouble("qty", 1.0))
        return if (he) "❌ בוטל: $label × $qty, ${dateTime(ms, true, false)}"
        else "❌ Removed: $label × $qty, ${dateTime(ms, false, false)}"
    }

    fun removedBp(b: JSONObject, he: Boolean): String {
        val ms = T.parse(b.optString("measured_at"))
        val v = b.optInt("systolic").toString() + "/" + b.optInt("diastolic")
        return if (he) "❌ בוטל: לחץ דם $v, ${dateTime(ms, true, false)}"
        else "❌ Removed: BP $v, ${dateTime(ms, false, false)}"
    }
}
