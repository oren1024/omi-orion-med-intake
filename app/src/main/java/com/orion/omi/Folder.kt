package com.orion.omi

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A folder the user picked with Android's folder picker (Documents/OMI).
 * All reads and writes go through the Storage Access Framework, so no
 * special storage permission is needed.
 */
class Folder(private val cr: ContentResolver, val tree: Uri) {

    class Entry(val id: String, val name: String, val isDir: Boolean)

    private val rootId: String = DocumentsContract.getTreeDocumentId(tree)
    private val dirs = HashMap<String, String>()

    fun docUri(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    fun list(id: String): List<Entry> {
        val out = ArrayList<Entry>()
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        // A null cursor means the provider could not list the folder. That must be an
        // error, not an empty folder, or all records would appear to vanish.
        val cursor = cr.query(uri, cols, null, null, null) ?: throw IOException("Cannot list the folder")
        cursor.use { c ->
            while (c.moveToNext()) {
                out.add(
                    Entry(
                        c.getString(0),
                        c.getString(1) ?: "",
                        c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                    )
                )
            }
        }
        return out
    }

    @Synchronized
    fun ensureDir(path: List<String>): String {
        var id = rootId
        var key = ""
        for (seg in path) {
            key += "/$seg"
            val hit = dirs[key]
            if (hit != null) {
                id = hit
                continue
            }
            val existing = list(id).firstOrNull { it.isDir && it.name == seg }
            val next: String = if (existing != null) {
                existing.id
            } else {
                val made = DocumentsContract.createDocument(
                    cr, docUri(id), DocumentsContract.Document.MIME_TYPE_DIR, seg
                ) ?: throw IOException("Cannot create folder $seg")
                DocumentsContract.getDocumentId(made)
            }
            dirs[key] = next
            id = next
        }
        return id
    }

    /** Creates an empty file and returns its address. */
    fun create(path: List<String>, name: String, mime: String): Uri {
        val dir = ensureDir(path)
        return DocumentsContract.createDocument(cr, docUri(dir), mime, name)
            ?: throw IOException("Cannot create $name")
    }

    fun writeText(path: List<String>, name: String, text: String) {
        val uri = create(path, name, "application/json")
        val os = cr.openOutputStream(uri, "wt") ?: throw IOException("Cannot write $name")
        os.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    fun openOut(uri: Uri): OutputStream =
        cr.openOutputStream(uri, "wt") ?: throw IOException("Cannot open for writing")

    private fun readText(id: String): String {
        val ins = cr.openInputStream(docUri(id)) ?: throw IOException("Cannot read")
        return ins.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun walk(id: String, prefix: String, depth: Int, visit: (Entry, String) -> Unit) {
        if (depth > 6) return
        for (e in list(id)) {
            if (e.isDir) walk(e.id, prefix + e.name + "/", depth + 1, visit) else visit(e, prefix)
        }
    }

    /** Writes omi.json once, so the folder is recognisable. */
    fun ensureMarker() {
        if (list(rootId).none { it.name == "omi.json" }) {
            val j = JSONObject().put("schema", 1).put("app", "OMI").put("created", T.iso(System.currentTimeMillis()))
            writeText(emptyList(), "omi.json", j.toString(2))
        }
    }

    private val cache = HashMap<String, JSONObject>()

    /**
     * Reads every .json file in the folder, whatever subfolder it is in.
     * Files already read are kept in memory, so only new files cost time.
     */
    @Synchronized
    fun scan(): Snapshot {
        val records = ArrayList<JSONObject>()
        var files = 0
        var skipped = 0
        walk(rootId, "", 0) { e, _ ->
            if (e.name.endsWith(".json") && e.name != "omi.json") {
                files++
                val cached = cache[e.id]
                if (cached != null) {
                    records.add(cached)
                } else {
                    val rec: JSONObject? = try {
                        JSONObject(readText(e.id))
                    } catch (ex: Exception) {
                        null
                    }
                    if (rec == null) {
                        skipped++
                    } else {
                        cache[e.id] = rec
                        records.add(rec)
                    }
                }
            }
        }
        return Snapshot.build(records, files, skipped)
    }

    /** Zips the whole folder into [out]. */
    fun zipTo(out: OutputStream) {
        val used = HashSet<String>()
        ZipOutputStream(out).use { z ->
            walk(rootId, "", 0) { e, prefix ->
                val name = prefix + e.name
                if (used.add(name)) {
                    z.putNextEntry(ZipEntry(name))
                    cr.openInputStream(docUri(e.id))?.use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
    }
}
