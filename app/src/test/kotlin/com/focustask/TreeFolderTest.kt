package com.focustask

import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import androidx.test.core.app.ApplicationProvider
import com.focustask.data.DataFiles
import com.focustask.data.DataMirror
import com.focustask.data.DataSnapshot
import com.focustask.data.FILE_LISTS
import com.focustask.data.FILE_MANIFEST
import com.focustask.data.FileDataFolder
import com.focustask.data.Settings
import com.focustask.data.TreeDataFolder
import com.focustask.data.readText
import com.focustask.data.writeText
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A folder on "shared storage": a tiny document provider over a temp directory, like the one the folder picker talks to. */
class FakeDocuments : DocumentsProvider() {
    override fun onCreate() = true
    private fun file(id: String) = File(root, id.removePrefix("root").removePrefix("/")).let { if (id == "root") root else it }
    private fun id(f: File) = if (f == root) "root" else "root/" + f.relativeTo(root).path

    private fun row(c: MatrixCursor, f: File) {
        c.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, id(f))
            .add(Document.COLUMN_DISPLAY_NAME, if (f == root) "Documents" else f.name)
            .add(Document.COLUMN_MIME_TYPE, if (f.isDirectory) Document.MIME_TYPE_DIR else "application/octet-stream")
            .add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_WRITE or Document.FLAG_DIR_SUPPORTS_CREATE)
            .add(Document.COLUMN_SIZE, f.length())
    }

    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID))
    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(COLS).also { row(it, file(documentId)) }
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(COLS).also { c -> file(parentDocumentId).listFiles()?.sortedBy { it.name }?.forEach { row(c, it) } }
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode))
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val f = File(file(parentDocumentId), displayName)
        if (mimeType == Document.MIME_TYPE_DIR) f.mkdirs() else f.createNewFile()
        return id(f)
    }
    override fun isChildDocument(parentDocumentId: String, documentId: String) = documentId.startsWith(parentDocumentId)

    companion object {
        const val AUTHORITY = "com.focustask.test.documents"
        lateinit var root: File
        private val COLS = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_SIZE)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestFocusApp::class)
class TreeFolderTest {
    private val app get() = ApplicationProvider.getApplicationContext<FocusApp>()

    private fun tree(): Uri {
        FakeDocuments.root = File(app.cacheDir, "shared-${System.nanoTime()}").apply { mkdirs() }
        val info = ProviderInfo().apply {
            authority = FakeDocuments.AUTHORITY; exported = true; grantUriPermissions = true
            readPermission = "android.permission.MANAGE_DOCUMENTS"; writePermission = "android.permission.MANAGE_DOCUMENTS"
            name = FakeDocuments::class.java.name; packageName = app.packageName
        }
        Robolectric.buildContentProvider(FakeDocuments::class.java).create(info)
        return DocumentsContract.buildTreeDocumentUri(FakeDocuments.AUTHORITY, "root")
    }

    @Test fun filesAreWrittenReplacedAndReadBackInAPickedFolder() {
        val uri = tree()
        val folder = TreeDataFolder(app, uri)
        assertNull("nothing is created by looking", folder.read("lists.json"))
        assertTrue(FakeDocuments.root.listFiles().isNullOrEmpty())
        folder.writeText("lists.json", "first")
        folder.writeText("lists.json", "second, and longer than the first")
        folder.writeText("lists.json", "third")
        folder.write("sounds/bell.mp3", byteArrayOf(9, 8, 7))
        assertEquals("third", folder.readText("lists.json"))
        assertEquals(listOf<Byte>(9, 8, 7), folder.read("sounds/bell.mp3")!!.toList())
        // on disk: one file, in the subfolder, not a pile of copies
        val sub = File(FakeDocuments.root, "FocusTask data")
        assertEquals(listOf("lists.json", "sounds"), sub.list()!!.sorted())
        assertEquals("third", File(sub, "lists.json").readText())
        // a new object (as after restarting the app) finds the same files
        assertEquals("third", TreeDataFolder(app, uri).readText("lists.json"))
    }

    @Test fun aCopyIsFoundInTheSubfolderOrInThePickedFolderItself() {
        val uri = tree()
        val files = DataFiles.encode(DataSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), Settings()), "1")
        // written by the app: inside "FocusTask data"
        val written = TreeDataFolder(app, uri)
        files.forEach { (n, t) -> written.writeText(n, t) }
        assertNotNull(TreeDataFolder(app, uri).readText(FILE_MANIFEST))
        assertNull("the picked folder itself has no copy", TreeDataFolder(app, uri, subfolder = null).readText(FILE_MANIFEST))
        // the person picked the "FocusTask data" folder itself
        File(FakeDocuments.root, "FocusTask data").listFiles()!!.forEach { it.copyTo(File(FakeDocuments.root, it.name)) }
        assertNotNull(TreeDataFolder(app, uri, subfolder = null).readText(FILE_LISTS))
    }

    @Test fun theAppKeepsAPickedFolderUpToDateAndCanRestoreFromIt() = runBlocking {
        val uri = tree()
        val repo = app.repo
        val list = repo.addList("Outside ${System.nanoTime()}", 1)
        repo.addTask(list, "Survives an uninstall", null)
        val m = DataMirror(app, repo, app.scope, "9.9", FileDataFolder(File(app.cacheDir, "in-${System.nanoTime()}")))
        assertNull(m.status.value.folderName)
        m.useFolder(uri)
        assertEquals("Documents", m.status.value.folderName)
        assertTrue(m.status.value.savedAt > 0)
        assertNull(m.status.value.error)
        val sub = File(FakeDocuments.root, "FocusTask data")
        assertEquals(setOf("manifest.json", "settings.json", "lists.json", "timeblocks.json", "daychanges.json"), sub.list()!!.toSet())

        // a change is written again, an unchanged state is not rewritten
        val newer = repo.addList("Added later", 3)
        m.saveNow()
        assertTrue(File(sub, "lists.json").readText().contains("Added later"))

        // the copy can be read back through the picker's folder, as after reinstalling
        val copy = m.loadFromTree(uri)
        assertTrue(copy.snapshot.lists.any { it.id == newer && it.title == "Added later" })
        assertTrue(copy.snapshot.tasks.any { it.title == "Survives an uninstall" })
        assertEquals("9.9", copy.info.appVersion)

        // when the folder is gone the app says so instead of failing silently
        FakeDocuments.root.deleteRecursively()
        repo.addList("After the folder vanished", 0)
        m.saveNow(force = true)
        m.stopUsingFolder()
        assertNull(m.status.value.folderName)
    }
}
