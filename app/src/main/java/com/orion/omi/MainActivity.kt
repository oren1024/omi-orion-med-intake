package com.orion.omi

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.util.Calendar

private class Item(val ms: Long, val text: String, val kind: String, val rec: JSONObject)

class MainActivity : Activity() {

    companion object {
        const val REQ_DATA = 1
        const val REQ_BACKUP = 2
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var titleView: TextView
    private lateinit var body: FrameLayout

    private var folder: Folder? = null
    private var snap: Snapshot? = null
    private val pendingReload = ArrayList<() -> Unit>()
    private var rescan = false
    private var saving = false
    private var lastScan = 0L
    private var scanError: String? = null
    private var loading = false
    private var onHome = true

    private val phone: String get() = prefs.getString("phone", "") ?: ""
    private val he: Boolean get() = prefs.getString("lang", "en") == "he"
    private fun flag(key: String, def: Boolean): Boolean = prefs.getBoolean(key, def)

    private val version: String
        get() = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }

    // ---------- small UI helpers ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun text(s: String, size: Float = 16f, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        t.setPadding(0, dp(4), 0, dp(4))
        return t
    }

    private fun button(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.setAllCaps(false)
        b.setOnClickListener { onClick() }
        return b
    }

    private fun edit(hint: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText {
        val e = EditText(this)
        e.hint = hint
        e.inputType = type
        return e
    }

    private fun column(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.setPadding(dp(16), dp(8), dp(16), dp(24))
        return l
    }

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    }

    private fun row(s: String, onClick: () -> Unit): TextView {
        val t = text(s, 15f)
        t.setPadding(0, dp(10), 0, dp(10))
        t.setOnClickListener { onClick() }
        return t
    }

    private fun divider(): View {
        val v = View(this)
        v.setBackgroundColor(0xFFDDDDDD.toInt())
        v.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        return v
    }

    private fun spinner(labels: List<String>): Spinner {
        val s = Spinner(this)
        s.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        return s
    }

    private fun screen(title: String, home: Boolean, view: View) {
        onHome = home
        titleView.text = title
        body.removeAllViews()
        val sv = ScrollView(this)
        sv.addView(view)
        body.addView(sv)
    }

    /** Runs [work] off the main thread. [done] runs only if it succeeded, [fail] only if it threw. */
    private fun bg(work: () -> Unit, done: () -> Unit, fail: () -> Unit = {}) {
        Thread {
            try {
                work()
                runOnUiThread { done() }
            } catch (e: Exception) {
                runOnUiThread {
                    toast("Error: " + (e.message ?: e.javaClass.simpleName))
                    fail()
                }
            }
        }.start()
    }

    private fun copyText(s: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("OMI", s))
        toast("Copied")
    }

    private fun shareText(s: String) {
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(Intent.EXTRA_TEXT, s)
        startActivity(Intent.createChooser(i, "Send to"))
    }

    private fun pickDate(initial: Long, cb: (Long) -> Unit) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = initial
        DatePickerDialog(this, { _, y, m, d ->
            val c = Calendar.getInstance()
            c.set(y, m, d, 12, 0, 0)
            cb(c.timeInMillis)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun pickDateTime(initial: Long, cb: (Long) -> Unit) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = initial
        DatePickerDialog(this, { _, y, m, d ->
            TimePickerDialog(this, { _, h, mi ->
                val c = Calendar.getInstance()
                c.set(y, m, d, h, mi, 0)
                c.set(Calendar.MILLISECOND, 0)
                cb(c.timeInMillis)
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun askNumber(title: String, cb: (Double) -> Unit) {
        val e = edit("Number", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        AlertDialog.Builder(this).setTitle(title).setView(e)
            .setPositiveButton("OK") { _, _ ->
                val v = e.text.toString().num()
                if (v == null || v < 0.0) toast("Enter a number, zero or more") else cb(v)
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun confirm(message: String, ok: String, action: () -> Unit) {
        AlertDialog.Builder(this).setMessage(message)
            .setPositiveButton(ok) { _, _ -> action() }
            .setNegativeButton("Cancel", null).show()
    }

    // ---------- lifecycle ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("omi", MODE_PRIVATE)

        val rootLayout = LinearLayout(this)
        rootLayout.orientation = LinearLayout.VERTICAL
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(16), dp(12), dp(8), dp(4))
        titleView = text("OMI", 20f, true)
        bar.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(button("Home") { showHome() })
        rootLayout.addView(bar)
        body = FrameLayout(this)
        rootLayout.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(rootLayout)

        if (phone.isEmpty()) askPhone()
        openFolder()
        showHome()
    }

    override fun onResume() {
        super.onResume()
        if (folder != null && onHome) reload { if (onHome) showHome() }
    }

    override fun onBackPressed() {
        if (!onHome) showHome() else super.onBackPressed()
    }

    private fun askPhone() {
        AlertDialog.Builder(this)
            .setTitle("Which phone is this?")
            .setMessage("Pick a different letter on each phone. It is used in file names so the two phones never clash.")
            .setCancelable(false)
            .setItems(arrayOf("Phone A", "Phone B")) { _, which ->
                prefs.edit().putString("phone", if (which == 0) "A" else "B").apply()
            }.show()
    }

    private fun openFolder() {
        val s = prefs.getString("data_tree", null)
        folder = null
        if (s != null) {
            val uri = Uri.parse(s)
            val ok = contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
            if (ok) folder = Folder(contentResolver, uri)
        }
    }

    private fun treeLabel(key: String): String {
        val s = prefs.getString(key, null) ?: return "not set"
        return try {
            DocumentsContract.getTreeDocumentId(Uri.parse(s)).substringAfter(':')
        } catch (e: Exception) {
            s
        }
    }

    private fun pickFolder(req: Int) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        i.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )
        startActivityForResult(i, req)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        if (requestCode == REQ_DATA) {
            prefs.edit().putString("data_tree", uri.toString()).apply()
            snap = null
            openFolder()
            val f = folder
            if (f != null) {
                bg({
                    try {
                        f.ensureMarker()
                    } catch (e: Exception) {
                        // The marker file is optional; reading the folder still works without it.
                    }
                }, { reload { showHome() } })
            }
        } else if (requestCode == REQ_BACKUP) {
            prefs.edit().putString("backup_tree", uri.toString()).apply()
            showSettings()
        }
    }

    private fun reload(done: () -> Unit) {
        val f = folder
        if (f == null) {
            snap = null
            done()
            return
        }
        if (loading) {
            // A scan is already running and may have started before the latest write.
            // Queue the callback and scan once more when it finishes.
            pendingReload.add(done)
            rescan = true
            return
        }
        loading = true
        Thread {
            var err: String? = null
            try {
                snap = f.scan()
                lastScan = System.currentTimeMillis()
            } catch (e: Exception) {
                err = e.message ?: e.javaClass.simpleName
            }
            runOnUiThread {
                scanError = err
                loading = false
                val callbacks = ArrayList<() -> Unit>()
                callbacks.add(done)
                callbacks.addAll(pendingReload)
                pendingReload.clear()
                if (rescan) {
                    rescan = false
                    reload { for (cb in callbacks) cb() }
                } else {
                    for (cb in callbacks) cb()
                }
            }
        }.start()
    }

    /** Writes one record file, rescans, then calls [next]. */
    private fun saveRecord(path: List<String>, stampMs: Long, rec: JSONObject, next: () -> Unit) {
        val f = folder
        if (f == null) {
            toast("Choose the data folder first")
            return
        }
        if (saving) return
        saving = true
        val name = T.stamp(stampMs) + "_" + phone + "_" + rec.optString("id") + ".json"
        bg(
            { f.writeText(path, name, rec.toString(2)) },
            {
                saving = false
                reload { next() }
            },
            { saving = false }
        )
    }

    private fun offerShare(title: String, message: String) {
        if (flag("autoshare", false)) {
            shareText(message)
            showHome()
            return
        }
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setCancelable(false)
            .setPositiveButton("Share") { _, _ -> shareText(message); showHome() }
            .setNeutralButton("Copy") { _, _ -> copyText(message); showHome() }
            .setNegativeButton("Done") { _, _ -> showHome() }
            .show()
    }

    // ---------- Home ----------

    private fun showHome() {
        val c = column()
        val f = folder
        if (f == null) {
            c.addView(text("Step 1: choose the data folder", 18f, true))
            c.addView(text("Pick the folder Documents/OMI on this phone (create it in the picker if it does not exist). FolderSync will sync this same folder to OneDrive."))
            c.addView(button("Choose data folder") { pickFolder(REQ_DATA) })
            screen("OMI v$version", true, c)
            return
        }
        val s = snap
        c.addView(button("Log dose") { showLogDose() })
        c.addView(button("Log blood pressure") { showLogBp() })
        c.addView(button("Medications") { showMeds() })
        c.addView(button("History") { showHistory() })
        c.addView(button("Export and backup") { showExport() })
        c.addView(button("Settings") { showSettings() })

        if (s == null) {
            c.addView(text(if (loading) "Reading the folder..." else "Not loaded yet."))
            if (scanError != null) c.addView(text("Problem: $scanError"))
            c.addView(button("Refresh") { reload { showHome() } })
            screen("OMI v$version", true, c)
            return
        }

        val low = s.meds.values.filter { it.optBoolean("active", true) }.mapNotNull { m ->
            val left = s.left(m.optString("id"))
            if (left != null && left <= 7.0) "⚠ ${m.optString("name")}: ${fmtQty(left)} left" else null
        }
        for (w in low) c.addView(text(w, 15f, true))

        c.addView(text("Today", 18f, true))
        val today = T.day(System.currentTimeMillis())
        val todays = items(s).filter { T.day(it.ms) == today }
        if (todays.isEmpty()) c.addView(text("Nothing logged yet today."))
        for (it2 in todays) {
            c.addView(row(T.hm(it2.ms) + " · " + it2.text.substringAfter(" · ")) { itemActions(it2) })
            c.addView(divider())
        }

        c.addView(text("Status", 18f, true))
        var st = "Phone $phone · ${s.meds.size} medications · ${s.doses.size} doses · ${s.bps.size} BP readings · ${s.files} files"
        if (s.skipped > 0) st += "\n⚠ ${s.skipped} files could not be read (may still be syncing)"
        if (lastScan > 0) st += "\nLast read: ${T.hm(lastScan)}"
        c.addView(text(st, 13f))
        if (scanError != null) c.addView(text("Problem: $scanError", 13f))
        c.addView(button("Refresh") { reload { showHome() } })
        screen("OMI v$version", true, c)
    }

    // ---------- Log dose ----------

    private fun showLogDose() {
        val s = snap
        if (s == null) {
            toast("Folder not loaded yet")
            return
        }
        val active = s.meds.values.filter { it.optBoolean("active", true) }
        if (active.isEmpty()) {
            toast("Add a medication first")
            showMeds()
            return
        }
        val last = HashMap<String, Long>()
        for (d in s.doses) {
            val m = d.optString("med_id")
            val t = T.parse(d.optString("taken_at"))
            if (t > (last[m] ?: 0L)) last[m] = t
        }
        val meds = active.sortedWith(
            compareByDescending<JSONObject> { last[it.optString("id")] ?: 0L }
                .thenBy { it.optString("name").lowercase() }
        )

        val c = column()
        c.addView(text("Medication"))
        val medSpin = spinner(meds.map { Msg.medLabel(it) })
        c.addView(medSpin)

        c.addView(text("Quantity"))
        val qty = edit("Quantity", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        c.addView(qty)
        medSpin.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                qty.setText(fmtQty(meds[pos].optDouble("default_qty", 1.0)))
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        c.addView(text("When"))
        var whenMs = System.currentTimeMillis()
        val whenView = text(T.nice(whenMs), 18f, true)
        c.addView(whenView)
        val tr = LinearLayout(this)
        tr.orientation = LinearLayout.HORIZONTAL
        tr.addView(button("Now") {
            whenMs = System.currentTimeMillis()
            whenView.text = T.nice(whenMs)
        })
        tr.addView(button("Took it earlier") {
            pickDateTime(whenMs) { ms ->
                whenMs = ms
                whenView.text = T.nice(ms)
            }
        })
        c.addView(tr)

        c.addView(text("Why"))
        val reasonSpin = spinner(REASONS.map { reasonLabel(it, false) })
        c.addView(reasonSpin)
        val note = edit("Note (optional)")
        c.addView(note)

        c.addView(button("Save") {
            val q = qty.text.toString().num()
            if (q == null || q <= 0.0) {
                toast("Enter a quantity")
                return@button
            }
            val med = meds[medSpin.selectedItemPosition]
            val medId = med.optString("id")
            val rec = JSONObject()
                .put("schema", 1).put("type", "dose").put("id", newId())
                .put("med_id", medId)
                .put("taken_at", T.iso(whenMs))
                .put("logged_at", T.iso(System.currentTimeMillis()))
                .put("qty", q)
                .put("reason", REASONS[reasonSpin.selectedItemPosition])
                .put("note", note.text.toString().trim())
                .put("device", phone)
            saveRecord(listOf("doses", T.year(whenMs), T.month(whenMs)), whenMs, rec) {
                val s2 = snap
                offerShare(
                    "Saved",
                    Msg.dose(s2?.meds?.get(medId), rec, s2?.left(medId), he, flag("reason", true), flag("left", true))
                )
            }
        })
        screen("Log dose", false, c)
    }

    // ---------- Log blood pressure ----------

    private fun showLogBp() {
        val c = column()
        val num = InputType.TYPE_CLASS_NUMBER
        val sys = edit("Systolic (top)", num)
        val dia = edit("Diastolic (bottom)", num)
        val pulse = edit("Pulse (optional)", num)
        c.addView(sys)
        c.addView(dia)
        c.addView(pulse)

        c.addView(text("When"))
        var whenMs = System.currentTimeMillis()
        val whenView = text(T.nice(whenMs), 18f, true)
        c.addView(whenView)
        val tr = LinearLayout(this)
        tr.orientation = LinearLayout.HORIZONTAL
        tr.addView(button("Now") {
            whenMs = System.currentTimeMillis()
            whenView.text = T.nice(whenMs)
        })
        tr.addView(button("Measured earlier") {
            pickDateTime(whenMs) { ms ->
                whenMs = ms
                whenView.text = T.nice(ms)
            }
        })
        c.addView(tr)

        c.addView(text("Context"))
        val ctx = spinner(BP_CONTEXTS.map { contextLabel(it) })
        c.addView(ctx)
        val note = edit("Note (optional)")
        c.addView(note)

        c.addView(button("Save") {
            val sy = sys.text.toString().toIntOrNull()
            val di = dia.text.toString().toIntOrNull()
            val pu = pulse.text.toString().toIntOrNull()
            if (sy == null || di == null) {
                toast("Enter systolic and diastolic")
                return@button
            }
            val rec = JSONObject()
                .put("schema", 1).put("type", "bp").put("id", newId())
                .put("measured_at", T.iso(whenMs))
                .put("systolic", sy).put("diastolic", di)
            if (pu != null) rec.put("pulse", pu)
            rec.put("context", BP_CONTEXTS[ctx.selectedItemPosition])
                .put("note", note.text.toString().trim())
                .put("device", phone)

            val odd = sy !in 50..260 || di !in 30..150 || (pu != null && pu !in 30..220) || sy <= di
            val go = {
                saveRecord(listOf("bp", T.year(whenMs), T.month(whenMs)), whenMs, rec) {
                    offerShare("Saved", Msg.bp(rec, he))
                }
            }
            if (odd) confirm("These values look unusual: $sy/$di. Save anyway?", "Save") { go() } else go()
        })
        screen("Log blood pressure", false, c)
    }

    // ---------- Medications ----------

    private fun showMeds() {
        val s = snap
        if (s == null) {
            toast("Folder not loaded yet")
            return
        }
        val c = column()
        c.addView(button("Add medication") { showMedForm(null) })
        val sorted = s.meds.values.sortedWith(
            compareByDescending<JSONObject> { it.optBoolean("active", true) }
                .thenBy { it.optString("name").lowercase() }
        )
        if (sorted.isEmpty()) c.addView(text("No medications yet."))
        for (m in sorted) {
            val left = s.left(m.optString("id"))
            var t = Msg.medLabel(m)
            if (!m.optBoolean("active", true)) t += " (inactive)"
            if (left != null) t += " — ${fmtQty(left)} left"
            c.addView(row(t) { showMedDetail(m.optString("id")) })
            c.addView(divider())
        }
        screen("Medications", false, c)
    }

    private fun showMedForm(existing: JSONObject?) {
        val c = column()
        val name = edit("Name")
        val nameHe = edit("Name in Hebrew (optional)")
        val strength = edit("Strength, e.g. 500 mg")
        val form = edit("Form, e.g. tablet")
        val shape = edit("Shape, e.g. round")
        val color = edit("Colour")
        val defQty = edit("Usual quantity per dose", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val notes = edit("Notes, e.g. take with food")
        if (existing != null) {
            name.setText(existing.optString("name"))
            nameHe.setText(existing.optString("name_he"))
            strength.setText(existing.optString("strength"))
            form.setText(existing.optString("form"))
            val ap = existing.optJSONObject("appearance")
            shape.setText(ap?.optString("shape") ?: "")
            color.setText(ap?.optString("color") ?: "")
            defQty.setText(fmtQty(existing.optDouble("default_qty", 1.0)))
            notes.setText(existing.optString("notes"))
        } else {
            defQty.setText("1")
        }
        for (v in listOf(name, nameHe, strength, form, shape, color, defQty, notes)) c.addView(v)

        c.addView(button("Save") {
            val n = name.text.toString().trim()
            if (n.isEmpty()) {
                toast("Name is required")
                return@button
            }
            val now = System.currentTimeMillis()
            val id = existing?.optString("id") ?: ("med_" + newId())
            val rec = JSONObject()
                .put("schema", 1).put("type", "med").put("id", id)
                .put("name", n)
                .put("name_he", nameHe.text.toString().trim())
                .put("strength", strength.text.toString().trim())
                .put("form", form.text.toString().trim())
                .put("appearance", JSONObject()
                    .put("shape", shape.text.toString().trim())
                    .put("color", color.text.toString().trim())
                    .put("marking", "").put("photo", ""))
                .put("default_qty", defQty.text.toString().num() ?: 1.0)
                .put("notes", notes.text.toString().trim())
                .put("active", existing?.optBoolean("active", true) ?: true)
                .put("updated", T.iso(now))
                .put("device", phone)
            saveRecord(listOf("meds", id), now, rec) { showMedDetail(id) }
        })
        screen(if (existing == null) "Add medication" else "Edit medication", false, c)
    }

    private fun showMedDetail(medId: String) {
        val s = snap
        val med = s?.meds?.get(medId)
        if (s == null || med == null) {
            showMeds()
            return
        }
        val doses = s.dosesOf(medId).sortedBy { T.parse(it.optString("taken_at")) }
        val c = column()
        c.addView(text(Msg.medLabel(med), 20f, true))
        val details = listOf(
            med.optString("form"),
            med.optJSONObject("appearance")?.let { (it.optString("shape") + " " + it.optString("color")).trim() } ?: "",
            med.optString("notes")
        ).filter { it.isNotEmpty() }
        if (details.isNotEmpty()) c.addView(text(details.joinToString(" · "), 14f))

        val left = s.left(medId)
        val total = doses.sumOf { it.optDouble("qty", 0.0) }
        var info = "First logged: " + (if (doses.isEmpty()) "never" else T.nice(T.parse(doses.first().optString("taken_at")))) +
                "\nLast logged: " + (if (doses.isEmpty()) "never" else T.nice(T.parse(doses.last().optString("taken_at")))) +
                "\nTotal doses: ${doses.size} (quantity ${fmtQty(total)})" +
                "\nPills left: " + (if (left == null) "not tracked yet, add a refill or count" else fmtQty(left))
        c.addView(text(info, 16f))

        c.addView(button("Edit") { showMedForm(med) })
        c.addView(button("Refill (add pills)") {
            askNumber("How many pills did you add?") { q ->
                if (q <= 0.0) toast("Enter more than zero") else saveStock(medId, "refill", q)
            }
        })
        c.addView(button("Recount (set pills left)") {
            askNumber("How many pills do you count now?") { counted ->
                saveStock(medId, "adjust", counted - (left ?: 0.0))
            }
        })
        val active = med.optBoolean("active", true)
        c.addView(button(if (active) "Mark inactive" else "Mark active") {
            val now = System.currentTimeMillis()
            val rec = JSONObject(med.toString())
                .put("active", !active).put("updated", T.iso(now)).put("device", phone)
            saveRecord(listOf("meds", medId), now, rec) { showMedDetail(medId) }
        })

        c.addView(text("Doses (newest first)", 18f, true))
        if (doses.isEmpty()) c.addView(text("No doses logged."))
        for (d in doses.reversed()) {
            val ms = T.parse(d.optString("taken_at"))
            val item = Item(ms, "${T.nice(ms)} · ${Msg.medLabel(med)} × ${fmtQty(d.optDouble("qty", 1.0))}", "dose", d)
            c.addView(row(T.nice(ms) + " · × " + fmtQty(d.optDouble("qty", 1.0)) + " · " + reasonLabel(d.optString("reason"), false)) { itemActions(item) })
            c.addView(divider())
        }
        screen("Medication", false, c)
    }

    private fun saveStock(medId: String, kind: String, qty: Double) {
        val now = System.currentTimeMillis()
        val rec = JSONObject()
            .put("schema", 1).put("type", "stock").put("id", newId())
            .put("med_id", medId).put("kind", kind).put("qty", qty)
            .put("at", T.iso(now)).put("device", phone)
        saveRecord(listOf("stock", T.year(now), T.month(now)), now, rec) { showMedDetail(medId) }
    }

    // ---------- History ----------

    private fun items(s: Snapshot): List<Item> {
        val out = ArrayList<Item>()
        for (d in s.doses) {
            val ms = T.parse(d.optString("taken_at"))
            val medId = d.optString("med_id")
            var t = "${T.nice(ms)} · ${Msg.medLabel(s.meds[medId], medId)} × ${fmtQty(d.optDouble("qty", 1.0))} · ${reasonLabel(d.optString("reason"), false)}"
            val n = d.optString("note")
            if (n.isNotEmpty()) t += " · $n"
            out.add(Item(ms, t, "dose", d))
        }
        for (b in s.bps) {
            val ms = T.parse(b.optString("measured_at"))
            var t = "${T.nice(ms)} · BP ${b.optInt("systolic")}/${b.optInt("diastolic")}"
            if (b.has("pulse")) t += " · pulse ${b.optInt("pulse")}"
            val n = b.optString("note")
            if (n.isNotEmpty()) t += " · $n"
            out.add(Item(ms, t, "bp", b))
        }
        out.sortByDescending { it.ms }
        return out
    }

    private fun showHistory() {
        val s = snap
        if (s == null) {
            toast("Folder not loaded yet")
            return
        }
        val all = items(s)
        val c = column()
        val filter = spinner(listOf("All", "Doses", "Blood pressure"))
        val search = edit("Search name or note")
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        c.addView(filter)
        c.addView(search)
        c.addView(list)

        val render = {
            list.removeAllViews()
            val kind = when (filter.selectedItemPosition) {
                1 -> "dose"
                2 -> "bp"
                else -> ""
            }
            val term = search.text.toString().trim().lowercase()
            val shown = all.filter { (kind.isEmpty() || it.kind == kind) && (term.isEmpty() || it.text.lowercase().contains(term)) }
            if (shown.isEmpty()) list.addView(text("Nothing found."))
            for (it2 in shown.take(300)) {
                list.addView(row(it2.text) { itemActions(it2) })
                list.addView(divider())
            }
            if (shown.size > 300) list.addView(text("Showing the newest 300 of ${shown.size}. Narrow the search to see older ones.", 13f))
        }
        filter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                render()
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                render()
            }
        })
        screen("History", false, c)
    }

    private fun messageFor(item: Item): String {
        val s = snap
        return if (item.kind == "dose") {
            val medId = item.rec.optString("med_id")
            Msg.dose(s?.meds?.get(medId), item.rec, s?.left(medId), he, flag("reason", true), flag("left", true))
        } else {
            Msg.bp(item.rec, he)
        }
    }

    private fun itemActions(item: Item) {
        AlertDialog.Builder(this).setTitle(item.text.substringBefore(" · "))
            .setItems(arrayOf("Share message", "Delete")) { _, which ->
                if (which == 0) {
                    offerShare("Message", messageFor(item))
                } else {
                    confirm("Delete this entry? It disappears from the lists and from the other phone.", "Delete") { deleteItem(item) }
                }
            }.show()
    }

    private fun deleteItem(item: Item) {
        val s = snap
        val now = System.currentTimeMillis()
        val removal = if (item.kind == "dose")
            Msg.removedDose(s?.meds?.get(item.rec.optString("med_id")), item.rec, he)
        else Msg.removedBp(item.rec, he)
        val rec = JSONObject()
            .put("schema", 1).put("type", "deleted").put("id", newId())
            .put("target_type", item.kind).put("target", item.rec.optString("id"))
            .put("at", T.iso(now)).put("device", phone)
        saveRecord(listOf("deleted", T.year(now), T.month(now)), now, rec) {
            offerShare("Deleted. Tell the group?", removal)
        }
    }

    // ---------- Export and backup ----------

    private fun dateButton(get: () -> Long, set: (Long) -> Unit): Button {
        val b = Button(this)
        b.setAllCaps(false)
        b.text = T.day(get())
        b.setOnClickListener {
            pickDate(get()) { ms ->
                set(ms)
                b.text = T.day(get())
            }
        }
        return b
    }

    private fun showExport() {
        val s = snap
        val f = folder
        if (s == null || f == null) {
            toast("Folder not loaded yet")
            return
        }
        var from = T.startOfDay(System.currentTimeMillis() - 30L * 86400000L)
        var to = T.endOfDay(System.currentTimeMillis())
        val c = column()
        c.addView(text("Report period", 18f, true))
        c.addView(text("From"))
        c.addView(dateButton({ from }, { from = T.startOfDay(it) }))
        c.addView(text("To"))
        c.addView(dateButton({ to }, { to = T.endOfDay(it) }))
        val status = text("")
        c.addView(status)
        c.addView(text("Files are saved in the exports folder inside your OMI folder.", 13f))

        c.addView(button("Export CSV (doses and blood pressure)") {
            val range = T.day(from) + "_to_" + T.day(to)
            val uris = ArrayList<Uri>()
            bg({
                val u1 = f.create(listOf("exports"), "OMI-doses_$range.csv", "text/csv")
                f.openOut(u1).use { it.write(Export.dosesCsv(s, from, to).toByteArray(Charsets.UTF_8)) }
                val u2 = f.create(listOf("exports"), "OMI-bp_$range.csv", "text/csv")
                f.openOut(u2).use { it.write(Export.bpCsv(s, from, to).toByteArray(Charsets.UTF_8)) }
                uris.add(u1)
                uris.add(u2)
            }, {
                if (uris.isNotEmpty()) {
                    status.text = "Saved exports/OMI-doses_$range.csv and exports/OMI-bp_$range.csv"
                    shareFiles(uris, "text/csv")
                }
            })
        })
        c.addView(button("Export PDF (doctor summary)") {
            val range = T.day(from) + "_to_" + T.day(to)
            val uris = ArrayList<Uri>()
            bg({
                val u = f.create(listOf("exports"), "OMI-report_$range.pdf", "application/pdf")
                f.openOut(u).use { Export.pdf(s, from, to, it) }
                uris.add(u)
            }, {
                if (uris.isNotEmpty()) {
                    status.text = "Saved exports/OMI-report_$range.pdf"
                    shareFiles(uris, "application/pdf")
                }
            })
        })

        c.addView(text("Backup", 18f, true))
        c.addView(text("Backup folder: " + treeLabel("backup_tree"), 14f))
        c.addView(button("Back up now") { backupNow(status) })
        screen("Export and backup", false, c)
    }

    private fun shareFiles(uris: List<Uri>, mime: String) {
        val i = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        i.type = mime
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(i, "Share export"))
    }

    private fun backupNow(status: TextView) {
        val f = folder
        val bt = prefs.getString("backup_tree", null)
        if (f == null) {
            toast("Choose the data folder first")
            return
        }
        if (bt == null) {
            toast("Choose a backup folder in Settings first")
            return
        }
        if (bt == prefs.getString("data_tree", null)) {
            toast("The backup folder must be a different folder from the data folder")
            return
        }
        val uri = Uri.parse(bt)
        val ok = contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
        if (!ok) {
            toast("Backup folder access was lost. Choose it again in Settings.")
            return
        }
        val name = "omi-backup-" + T.backupStamp(System.currentTimeMillis()) + ".zip"
        var done = false
        bg({
            val b = Folder(contentResolver, uri)
            val u = b.create(emptyList(), name, "application/zip")
            b.openOut(u).use { f.zipTo(it) }
            done = true
        }, {
            if (done) {
                status.text = "Backup saved: $name"
                toast("Backup saved")
            }
        })
    }

    // ---------- Settings ----------

    private fun showSettings() {
        val c = column()
        c.addView(text("This phone", 18f, true))
        val ph = spinner(listOf("Phone A", "Phone B"))
        ph.setSelection(if (phone == "B") 1 else 0)
        ph.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                prefs.edit().putString("phone", if (pos == 0) "A" else "B").apply()
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        c.addView(ph)

        c.addView(text("WhatsApp message language", 18f, true))
        val lang = spinner(listOf("English", "Hebrew"))
        lang.setSelection(if (he) 1 else 0)
        lang.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                prefs.edit().putString("lang", if (pos == 1) "he" else "en").apply()
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        c.addView(lang)

        fun check(label: String, key: String, def: Boolean) {
            val cb = CheckBox(this)
            cb.text = label
            cb.isChecked = flag(key, def)
            cb.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(key, on).apply() }
            c.addView(cb)
        }
        check("Show the reason in the message", "reason", true)
        check("Show pills left in the message", "left", true)
        check("Open the share sheet straight after saving", "autoshare", false)

        c.addView(text("Folders", 18f, true))
        c.addView(text("Data folder: " + treeLabel("data_tree"), 14f))
        c.addView(button("Choose data folder") { pickFolder(REQ_DATA) })
        c.addView(text("Backup folder: " + treeLabel("backup_tree"), 14f))
        c.addView(button("Choose backup folder") { pickFolder(REQ_BACKUP) })
        c.addView(text("Use a different folder for backups, for example Documents/OMI-Backups.", 13f))

        c.addView(text("About", 18f, true))
        c.addView(text("OMI v$version. Test version: do not store real medical records yet.", 13f))
        screen("Settings", false, c)
    }
}
