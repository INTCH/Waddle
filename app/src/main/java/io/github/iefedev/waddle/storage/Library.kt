package io.github.iefedev.waddle.storage

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The last folder and file listings the gallery read, kept for the process so opening the tab or
 * backing out of a folder draws at once instead of blank; the screens still re-read the disk and
 * replace them. Listing functions block; call them off the main thread.
 */
object Library {
    /** Folder name to its files, newest first; null until listed once. */
    @Volatile var folders: List<Pair<String, List<File>>>? = null
        private set
    val files = ConcurrentHashMap<String, List<File>>()

    fun listFolders(): List<Pair<String, List<File>>> =
        Storage.categories().map { it to Storage.files(it) }.also { list ->
            folders = list
            files.clear()
            list.forEach { (name, fs) -> files[name] = fs }
        }

    /** Quarter cells of the folder collage (2×2, and the stacked pair beside a big one) get small thumbs. */
    fun coverCellIsSmall(count: Int, index: Int) = count >= 4 || (count == 3 && index > 0)

    fun listFiles(category: String): List<File> = Storage.files(category).also { files[category] = it }

    /**
     * Lists everything and puts the folder covers' thumbnails in memory, then reads any metadata
     * not cached yet, so the first gallery visit after launch is already warm. Run once at app start.
     */
    fun warm(context: Context) {
        if (!Storage.hasAccess()) return
        Media.load(context)
        val list = runCatching { listFolders() }.getOrNull() ?: return
        list.forEach { (_, fs) ->
            val cover = fs.takeLast(4).reversed()
            cover.forEachIndexed { i, f -> runCatching { Media.thumb(context, f, coverCellIsSmall(cover.size, i)) } }
        }
        list.forEach { (_, fs) -> fs.forEach { runCatching { Media.info(context, it) } } }
        Media.save(context)
    }
}
