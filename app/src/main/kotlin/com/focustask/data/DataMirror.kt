package com.focustask.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the settings screen shows about the copy outside the app. */
data class MirrorStatus(
    /** Name of the folder the person picked, or null when no copy is kept outside the app. */
    val folderName: String? = null,
    /** When the copy outside the app was last written (epoch millis), or 0. */
    val savedAt: Long = 0L,
    /** Why the last write failed, or null. */
    val error: String? = null,
)

/** A copy that was read and checked, ready to be put back. */
class LoadedCopy(val snapshot: DataSnapshot, val info: CopyInfo, val folder: DataFolder)

/**
 * Keeps the JSON files up to date and brings them back.
 *
 * Every change to the data writes the files into the app's own storage. When the person has picked
 * a folder, the same files (and their custom sounds) are written there too: that copy survives
 * uninstalling the app, which nothing inside the app's storage can.
 */
class DataMirror(
    private val context: Context,
    private val repo: Repository,
    private val scope: CoroutineScope,
    private val appVersion: String,
    private val inside: DataFolder = FileDataFolder(File(context.filesDir, "data")),
) {
    private val prefs = context.getSharedPreferences("data_copy", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private var lastInside: Map<String, String> = emptyMap()
    private var lastOutside: Map<String, String> = emptyMap()

    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<MirrorStatus> = _status

    private fun readStatus() = MirrorStatus(
        folderName = prefs.getString(KEY_NAME, null).takeIf { prefs.getString(KEY_TREE, null) != null },
        savedAt = prefs.getLong(KEY_SAVED, 0L),
        error = prefs.getString(KEY_ERROR, null),
    )

    private fun tree(): Uri? = prefs.getString(KEY_TREE, null)?.let(Uri::parse)

    /** Starts saving after every change. */
    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            repo.data.filterNotNull().debounce(700).collect { runCatching { saveNow() } }
        }
    }

    /** Writes the copy now (also when nothing changed since the last write, if [force]). */
    suspend fun saveNow(force: Boolean = false) = lock.withLock {
        withContext(Dispatchers.IO) {
            val snapshot = repo.snapshot()
            val files = DataFiles.encode(snapshot, appVersion)
            // the "saved at" line changes every time: compare the rest
            val same = { old: Map<String, String> -> !force && old.filterKeys { it != FILE_MANIFEST } == files.filterKeys { it != FILE_MANIFEST } }

            if (!same(lastInside)) {
                writeAll(inside, files)
                lastInside = files
            }
            val t = tree() ?: return@withContext
            if (same(lastOutside) && prefs.getString(KEY_ERROR, null) == null) return@withContext
            try {
                val folder = TreeDataFolder(context, t)
                DataFiles.soundFiles(snapshot.settings).forEach { name ->
                    val f = File(context.filesDir, "$DIR_SOUNDS/$name")
                    if (f.isFile) folder.write("$DIR_SOUNDS/$name", f.readBytes())
                }
                writeAll(folder, files)
                lastOutside = files
                prefs.edit().putLong(KEY_SAVED, System.currentTimeMillis()).remove(KEY_ERROR).apply()
            } catch (e: Exception) {
                lastOutside = emptyMap()
                prefs.edit().putString(KEY_ERROR, "Could not save to that folder. Is it still there?").apply()
            }
            _status.value = readStatus()
        }
    }

    private fun writeAll(folder: DataFolder, files: Map<String, String>) {
        // the manifest is last (the map is ordered that way): until it is there, the copy counts as unfinished
        files.forEach { (name, text) -> folder.writeText(name, text) }
    }

    /** From now on, also keep the copy in [uri]. The caller has already asked Android to remember the permission. */
    suspend fun useFolder(uri: Uri) {
        prefs.edit().putString(KEY_TREE, uri.toString()).putString(KEY_NAME, TreeDataFolder.folderName(context, uri))
            .putLong(KEY_SAVED, 0L).remove(KEY_ERROR).apply()
        lastOutside = emptyMap()
        _status.value = readStatus()
        saveNow(force = true)
    }

    /** Stop keeping a copy outside the app. What is already in the folder stays there. */
    fun stopUsingFolder() {
        tree()?.let { runCatching { context.contentResolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } }
        prefs.edit().remove(KEY_TREE).remove(KEY_NAME).remove(KEY_SAVED).remove(KEY_ERROR).apply()
        lastOutside = emptyMap()
        _status.value = readStatus()
    }

    // ------------------------------------------------------------------ bringing a copy back

    /** Reads and checks the copy in [folder]. Throws [DataFormatException] with a message for the person. */
    suspend fun load(folder: DataFolder): LoadedCopy = withContext(Dispatchers.IO) {
        val info = DataFiles.info(folder.readText(FILE_MANIFEST))
        val snapshot = DataFiles.decode { folder.readText(it) }
        LoadedCopy(snapshot, info, folder)
    }

    /** The copy inside a folder the person picked: in "FocusTask data" or in the folder itself. */
    suspend fun loadFromTree(uri: Uri): LoadedCopy = withContext(Dispatchers.IO) {
        val sub = TreeDataFolder(context, uri)
        val folder = if (sub.read(FILE_MANIFEST) != null) sub else TreeDataFolder(context, uri, subfolder = null)
        load(folder)
    }

    /** Puts a loaded copy in place of everything in the app, custom sounds included. */
    suspend fun restore(copy: LoadedCopy) = lock.withLock {
        withContext(Dispatchers.IO) {
            val settings = copy.snapshot.settings
            val sounds = settings.sounds.mapValues { (slot, c) ->
                if (!c.source.startsWith("file:")) return@mapValues c
                val name = File(c.source.removePrefix("file:")).name
                val target = File(context.filesDir, "$DIR_SOUNDS/$name")
                if (!target.isFile) {
                    val bytes = runCatching { copy.folder.read("$DIR_SOUNDS/$name") }.getOrNull()
                    if (bytes != null) { target.parentFile?.mkdirs(); target.writeBytes(bytes) }
                }
                if (target.isFile) c.copy(source = "file:${target.absolutePath}")
                else defaultSounds().getValue(slot)       // the sound file did not come with the copy
            }
            repo.restore(copy.snapshot.copy(settings = settings.copy(sounds = sounds)))
        }
        lastInside = emptyMap(); lastOutside = emptyMap()
    }

    /**
     * Deletes everything: lists, tasks, time blocks, one-day changes, settings and the sounds the person added.
     * The copy inside the app, and the one in the picked folder, are emptied straight away, so nothing
     * can bring the data back later by itself.
     */
    suspend fun deleteEverything() {
        withContext(Dispatchers.IO) {
            lock.withLock {
                repo.restore(DataSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), Settings(seeded = true)))
                File(context.filesDir, DIR_SOUNDS).listFiles()?.forEach { it.delete() }
            }
        }
        saveNow(force = true)
    }

    /**
     * If the app has no data at all but its own copy does (the database was lost), bring it back.
     * Returns true when it did.
     */
    suspend fun restoreIfEmpty(): Boolean {
        if (!repo.hasNoUserData()) return false
        val copy = runCatching { load(inside) }.getOrNull() ?: return false
        if (copy.snapshot.isEmpty) return false
        restore(copy)
        return true
    }

    /** Reads the copy that sits inside the app (for tests and for a "last copy" line). */

    companion object {
        private const val KEY_TREE = "tree"
        private const val KEY_NAME = "name"
        private const val KEY_SAVED = "saved_at"
        private const val KEY_ERROR = "error"
    }
}
