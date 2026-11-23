package com.orion.omi

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Time helpers. Records store ISO 8601 with the UTC offset, e.g. 2026-11-06T21:46:00+02:00. */
object T {
    private const val ISO = "yyyy-MM-dd'T'HH:mm:ssXXX"

    fun iso(ms: Long): String = SimpleDateFormat(ISO, Locale.US).format(Date(ms))

    fun parse(s: String): Long = try {
        SimpleDateFormat(ISO, Locale.US).parse(s)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }

    fun stamp(ms: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss", Locale.US).format(Date(ms))
    fun year(ms: Long): String = SimpleDateFormat("yyyy", Locale.US).format(Date(ms))
    fun month(ms: Long): String = SimpleDateFormat("MM", Locale.US).format(Date(ms))
    fun day(ms: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
    fun hm(ms: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(ms))
    fun nice(ms: Long): String = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.ENGLISH).format(Date(ms))
    fun backupStamp(ms: Long): String = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date(ms))

    fun startOfDay(ms: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun endOfDay(ms: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = startOfDay(ms)
        c.add(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis - 1L
    }
}

/** Eight hex characters. Long enough that two records never share an id by accident. */
fun newId(): String = UUID.randomUUID().toString().replace("-", "").substring(0, 8)

fun fmtQty(d: Double): String =
    if (d == Math.floor(d)) d.toLong().toString()
    else String.format(Locale.US, "%.2f", d).trimEnd('0').trimEnd('.')

/** Reads a typed number, accepting a comma as the decimal separator. */
fun String.num(): Double? = this.trim().replace(',', '.').toDoubleOrNull()

val REASONS = listOf("scheduled", "as_needed", "symptom", "catch_up", "other")

fun reasonLabel(key: String, he: Boolean): String = when (key) {
    "scheduled" -> if (he) "לפי לוח זמנים" else "Scheduled"
    "as_needed" -> if (he) "לפי צורך" else "As needed"
    "symptom" -> if (he) "סימפטום" else "Symptom"
    "catch_up" -> if (he) "השלמה" else "Catch-up"
    else -> if (he) "אחר" else "Other"
}

val BP_CONTEXTS = listOf("morning", "evening", "post_workout", "stress", "other")

fun contextLabel(key: String): String = when (key) {
    "morning" -> "Morning"
    "evening" -> "Evening"
    "post_workout" -> "After workout"
    "stress" -> "Stress"
    else -> "Other"
}

/**
 * Everything OMI knows, built from the record files.
 * Records are never edited: a deletion is a separate "deleted" record, a newer
 * medication definition replaces an older one with the same id.
 */
class Snapshot(
    val meds: Map<String, JSONObject>,
    val doses: List<JSONObject>,
    val bps: List<JSONObject>,
    val stocks: List<JSONObject>,
    val files: Int,
    val skipped: Int
) {
    /**
     * Pills left, or null when no refill or count has been recorded yet.
     * Doses taken before the first refill or count are not subtracted: they
     * came out of a supply that was never recorded.
     */
    fun left(medId: String): Double? {
        val st = stocks.filter { it.optString("med_id") == medId }
        if (st.isEmpty()) return null
        val first = st.minOf { T.parse(it.optString("at")) }
        val given = doses.filter {
            it.optString("med_id") == medId && T.parse(it.optString("taken_at")) >= first
        }.sumOf { it.optDouble("qty", 0.0) }
        return st.sumOf { it.optDouble("qty", 0.0) } - given
    }

    fun dosesOf(medId: String): List<JSONObject> =
        doses.filter { it.optString("med_id") == medId }

    companion object {
        fun build(records: List<JSONObject>, files: Int, skipped: Int): Snapshot {
            val gone = HashSet<String>()
            for (r in records) {
                val type = r.optString("type")
                if (type == "deleted") {
                    gone.add(r.optString("target_type") + ":" + r.optString("target"))
                }
                val sup = r.optString("supersedes")
                if (sup.isNotEmpty()) gone.add("$type:$sup")
            }
            val meds = HashMap<String, JSONObject>()
            val doses = ArrayList<JSONObject>()
            val bps = ArrayList<JSONObject>()
            val stocks = ArrayList<JSONObject>()
            val seen = HashSet<String>()
            for (r in records) {
                val type = r.optString("type")
                val id = r.optString("id")
                if (id.isEmpty()) continue
                val key = "$type:$id"
                when (type) {
                    "med" -> {
                        val cur = meds[id]
                        if (cur == null || T.parse(r.optString("updated")) >= T.parse(cur.optString("updated"))) {
                            meds[id] = r
                        }
                    }
                    "dose" -> if (key !in gone && seen.add(key)) doses.add(r)
                    "bp" -> if (key !in gone && seen.add(key)) bps.add(r)
                    "stock" -> if (key !in gone && seen.add(key)) stocks.add(r)
                }
            }
            doses.sortByDescending { T.parse(it.optString("taken_at")) }
            bps.sortByDescending { T.parse(it.optString("measured_at")) }
            return Snapshot(meds, doses, bps, stocks, files, skipped)
        }
    }
}
