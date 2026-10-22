package com.focustask.data

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException

/**
 * A place that holds the JSON files: a folder inside the app, or a folder the user picked.
 * Paths are plain names ("lists.json") or one level down ("sounds/bell.mp3").
 */
interface DataFolder {
    /** The file's bytes, or null when it is not there. Never creates anything. */
    fun read(path: String): ByteArray?

    /** Writes the file, replacing it. Creates what is missing. */
    fun write(path: String, bytes: ByteArray)

    /** A short name for the person ("FocusTask data"). */
    val label: String
}

fun DataFolder.readText(path: String): String? = read(path)?.toString(Charsets.UTF_8)
fun DataFolder.writeText(path: String, text: String) = write(path, text.toByteArray(Charsets.UTF_8))

/** A folder in the app's own storage. A file is written beside its target and then renamed, so it is never half written. */
class FileDataFolder(private val root: File, override val label: String = "App storage") : DataFolder {
    override fun read(path: String): ByteArray? = File(root, path).takeIf { it.isFile }?.readBytes()

    override fun write(path: String, bytes: ByteArray) {
        val target = File(root, path)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw IOException("Could not write ${target.name}")
        }
    }
}

/**
 * A folder the person picked with Android's folder picker. The copy lives in a folder called
 * [subfolder] inside it. Reading looks there first and then in the picked folder itself, so either
 * folder works when restoring.
 */
class TreeDataFolder(
    private val context: Context,
    private val tree: Uri,
    private val subfolder: String? = SUBFOLDER,
) : DataFolder {
    private val resolver get() = context.contentResolver
    private val cache = HashMap<String, Uri>()

    override val label: String get() = folderName(context, tree)

    private val rootDoc: Uri
        get() = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private fun children(parent: Uri): Map<String, Pair<Uri, Boolean>> {
        val out = LinkedHashMap<String, Pair<Uri, Boolean>>()
        val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        resolver.query(
            kids,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
            Bundle.EMPTY, null,
        )?.use { c ->
            // look the columns up by name: a provider need not return them in the order asked for
            val iId = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val iName = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val iMime = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (c.moveToNext()) {
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(iId))
                out[c.getString(iName)] = uri to (c.getString(iMime) == DocumentsContract.Document.MIME_TYPE_DIR)
            }
        }
        return out
    }

    private fun find(parent: Uri, name: String): Uri? = children(parent)[name]?.first

    /** The folder that holds the copy; null if it does not exist and [create] is false. */
    private fun base(create: Boolean): Uri? {
        val sub = subfolder ?: return rootDoc
        cache["/"]?.let { return it }
        val found = children(rootDoc)[sub]?.takeIf { it.second }?.first
            ?: if (create) DocumentsContract.createDocument(resolver, rootDoc, DocumentsContract.Document.MIME_TYPE_DIR, sub)
            else null
        found?.let { cache["/"] = it }
        return found
    }

    private fun dirOf(path: String, create: Boolean): Uri? {
        val b = base(create) ?: return null
        val dir = path.substringBeforeLast('/', "")
        if (dir.isEmpty()) return b
        cache["d:$dir"]?.let { return it }
        val d = children(b)[dir]?.takeIf { it.second }?.first
            ?: if (create) DocumentsContract.createDocument(resolver, b, DocumentsContract.Document.MIME_TYPE_DIR, dir) else null
        d?.let { cache["d:$dir"] = it }
        return d
    }

    override fun read(path: String): ByteArray? {
        val name = path.substringAfterLast('/')
        val dir = dirOf(path, create = false) ?: return null
        val file = find(dir, name) ?: return null
        return resolver.openInputStream(file)?.use { it.readBytes() }
    }

    override fun write(path: String, bytes: ByteArray) {
        try {
            writeOnce(path, bytes)
        } catch (e: Exception) {
            // a document id can go stale when the person moves or deletes things: look everything up again
            cache.clear()
            writeOnce(path, bytes)
        }
    }

    private fun writeOnce(path: String, bytes: ByteArray) {
        val name = path.substringAfterLast('/')
        val dir = dirOf(path, create = true) ?: throw IOException("Could not open the folder")
        val file = cache["f:$path"] ?: find(dir, name)
            ?: DocumentsContract.createDocument(resolver, dir, mimeOf(name), name)
            ?: throw IOException("Could not create $name")
        resolver.openOutputStream(file, "wt")?.use { it.write(bytes) } ?: throw IOException("Could not write $name")
        cache["f:$path"] = file
    }

    companion object {
        const val SUBFOLDER = "FocusTask data"

        private fun mimeOf(name: String): String {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "json" -> "application/json"
                "" -> "application/octet-stream"
                else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
            }
        }

        /** The picked folder's name, for showing. */
        fun folderName(context: Context, tree: Uri): String = runCatching {
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), Bundle.EMPTY, null)?.use {
                if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) else null
            }
        }.getOrNull() ?: tree.lastPathSegment?.substringAfterLast(':') ?: "Chosen folder"
    }
}
