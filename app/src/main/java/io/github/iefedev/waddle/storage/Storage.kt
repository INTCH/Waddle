package io.github.iefedev.waddle.storage

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import io.github.iefedev.waddle.Prefs
import io.github.iefedev.waddle.R
import java.io.File

/**
 * Layout: <downloads>/<category>/<name>.<ext>, partials in Waddle/.tmp. <downloads> is
 * <shared storage>/Waddle/Downloads unless the user picked another folder in Settings.
 */
object Storage {
    const val DEFAULT_CATEGORY = "General"

    /** User-chosen downloads folder; set from Prefs at app start and when changed in Settings. */
    @Volatile private var customDownloads: File? = null

    fun init(context: Context) {
        customDownloads = Prefs.downloadsPath(context)?.let(::File)
    }

    private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "mov", "m4v", "3gp")
    private val AUDIO_EXT = setOf("m4a", "mp3", "opus", "ogg", "aac", "wav", "flac")
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif")
    private val PARTIAL = Regex("""(\.part|\.ytdl|\.part-Frag\d+)$|\.temp\.\w+$|\.f\d+\.\w+$""")
    private val ILLEGAL = Regex("""[\\/:*?"<>|\u0000-\u001F]""")

    fun root(): File = File(Environment.getExternalStorageDirectory(), "Waddle")
    fun downloads(): File = customDownloads ?: defaultDownloads()

    private fun defaultDownloads() = File(root(), "Downloads")

    /** Absolute path for a folder-picker tree URI on internal storage; null for SD cards / other providers. */
    fun pathOfTree(uri: Uri): String? {
        if (uri.authority != "com.android.externalstorage.documents") return null
        val id = DocumentsContract.getTreeDocumentId(uri)
        if (!id.startsWith("primary:")) return null
        return File(Environment.getExternalStorageDirectory(), id.removePrefix("primary:")).absolutePath
    }

    /**
     * Makes [target] the downloads folder and moves every category (and its files, names made unique)
     * from the current one into it. Refuses a target inside the current folder, which would move
     * folders into themselves. Returns false if anything could not be moved; what moved stays moved.
     */
    fun changeDownloads(context: Context, target: File): Boolean {
        val old = downloads()
        if (target == old) return true
        if (target.absolutePath.startsWith(old.absolutePath + "/")) return false
        if (!target.mkdirs() && !target.isDirectory) return false
        var ok = true
        old.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }.orEmpty().forEach { cat ->
            val dst = File(target, cat.name).apply { mkdirs() }
            cat.listFiles()?.filter { it.isFile }.orEmpty().forEach { f ->
                if (relocate(context, f, File(dst, uniqueBase(dst, f.nameWithoutExtension) + ext(f))) == null) ok = false
            }
            cat.delete() // only succeeds once empty
        }
        old.delete()
        Prefs.setDownloadsPath(context, if (target == defaultDownloads()) null else target.absolutePath)
        customDownloads = if (target == defaultDownloads()) null else target
        return ok
    }

    fun tempDir(): File = File(root(), ".tmp").apply {
        mkdirs()
        File(this, ".nomedia").let { if (!it.exists()) it.createNewFile() }
    }

    fun hasAccess(): Boolean = Environment.isExternalStorageManager()

    fun categoryDir(category: String): File = File(downloads(), sanitize(category).ifEmpty { DEFAULT_CATEGORY })

    fun categories(): List<String> {
        downloads().mkdirs()
        val existing = downloads().listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            ?.map { it.name }.orEmpty()
        return (existing + DEFAULT_CATEGORY).distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    fun createCategory(name: String): String? {
        val clean = sanitize(name)
        if (clean.isEmpty()) return null
        categoryDir(clean).mkdirs()
        return clean
    }

    /** Media files in a category, newest first. Ignores yt-dlp partials and hidden files. */
    fun files(category: String): List<File> =
        categoryDir(category).listFiles { f -> f.isFile && isMedia(f) }
            ?.sortedByDescending { it.lastModified() }.orEmpty()

    /** Each folder's media files: name to (bytes, count), biggest first. Blocking. */
    fun folderSizes(): List<Triple<String, Long, Int>> =
        categories().map { c -> files(c).let { fs -> Triple(c, fs.sumOf { it.length() }, fs.size) } }
            .sortedByDescending { it.second }

    fun isMedia(f: File): Boolean {
        if (f.name.startsWith(".") || PARTIAL.containsMatchIn(f.name)) return false
        val ext = f.extension.lowercase()
        return ext in VIDEO_EXT || ext in AUDIO_EXT || ext in IMAGE_EXT
    }

    fun mimeOf(f: File): String = when (f.extension.lowercase()) {
        in VIDEO_EXT -> "video/*"
        in AUDIO_EXT -> "audio/*"
        in IMAGE_EXT -> "image/*"
        else -> "*/*"
    }

    /** File-system safe name: no path/reserved chars, no leading dot, at most ~200 UTF-8 bytes. */
    fun sanitize(raw: String): String {
        var s = raw.replace(ILLEGAL, " ").replace(Regex("""\s+"""), " ").trim().trimStart('.')
        s = s.trimEnd('.', ' ')
        while (s.toByteArray(Charsets.UTF_8).size > 200) s = s.dropLast(1)
        return s.trimEnd('.', ' ')
    }

    /**
     * [base] if no file with that stem exists in [dir], else "base (2)", "base (3)"... With [multi] the
     * files will be "base 1", "base 2"…, so a base counts as taken when any "base N" exists too.
     */
    fun uniqueBase(dir: File, base: String, multi: Boolean = false): String {
        val taken = dir.listFiles()?.map { it.nameWithoutExtension }?.toSet().orEmpty()
        fun free(b: String) = b !in taken && (!multi || taken.none { it.startsWith("$b ") && it.substring(b.length + 1).all(Char::isDigit) })
        if (free(base)) return base
        var i = 2
        while (!free("$base ($i)")) i++
        return "$base ($i)"
    }

    /** yt-dlp treats % in -o as a template field. */
    fun escapeTemplate(name: String): String = name.replace("%", "%%")

    fun scan(context: Context, file: File) = scanPaths(context, file.absolutePath)

    private fun uri(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    fun openIntent(context: Context, file: File): Intent =
        Intent(Intent.ACTION_VIEW).setDataAndType(uri(context, file), mimeOf(file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    /** One file as SEND, several as SEND_MULTIPLE; a mix of video and audio goes out as any-file type. */
    fun shareIntent(context: Context, files: List<File>): Intent {
        if (files.size == 1) return shareIntent(context, files[0])
        val types = files.map { mimeOf(it) }.distinct()
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).setType(types.singleOrNull() ?: "*/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files.map { uri(context, it) }))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val title = context.resources.getQuantityString(R.plurals.share_files, files.size, files.size)
        return Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun shareIntent(context: Context, file: File): Intent {
        val send = Intent(Intent.ACTION_SEND).setType(mimeOf(file))
            .putExtra(Intent.EXTRA_STREAM, uri(context, file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, file.nameWithoutExtension).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Renames keeping the extension; returns the new file, or null if the name is empty/failed. */
    fun rename(context: Context, file: File, newName: String): File? {
        val clean = sanitize(newName)
        if (clean.isEmpty()) return null
        if (clean == file.nameWithoutExtension) return file
        val dir = file.parentFile ?: return null
        return relocate(context, file, File(dir, uniqueBase(dir, clean) + ext(file)))
    }

    fun move(context: Context, file: File, category: String): File? {
        val dir = categoryDir(category).apply { mkdirs() }
        if (dir == file.parentFile) return file
        return relocate(context, file, File(dir, uniqueBase(dir, file.nameWithoutExtension) + ext(file)))
    }

    fun delete(context: Context, file: File): Boolean =
        file.delete().also { if (it) scanPaths(context, file.absolutePath) }

    /** Renames a folder. Fails (null) when the name is empty or another folder already has it. */
    fun renameCategory(context: Context, from: String, to: String): String? {
        val clean = sanitize(to)
        if (clean.isEmpty()) return null
        if (clean == from) return from
        val src = categoryDir(from)
        val dst = categoryDir(clean)
        if (dst.exists()) return null
        val before = src.listFiles()?.map { it.absolutePath }.orEmpty()
        if (!src.renameTo(dst)) return null
        scanPaths(context, *(before + before.map { File(dst, File(it).name).absolutePath }).toTypedArray())
        return clean
    }

    /** Moves every file of [from] into [to] (names made unique), then removes [from]. */
    fun mergeCategory(context: Context, from: String, to: String): Boolean {
        val files = categoryDir(from).listFiles()?.filter { it.isFile }.orEmpty()
        val allMoved = files.all { move(context, it, to) != null }
        if (allMoved) categoryDir(from).delete()
        return allMoved
    }

    /** Deletes a folder and everything in it. */
    fun deleteCategory(context: Context, name: String): Boolean {
        val dir = categoryDir(name)
        val paths = dir.walkBottomUp().filter { it.isFile }.map { it.absolutePath }.toList()
        val ok = dir.deleteRecursively()
        scanPaths(context, *paths.toTypedArray())
        return ok
    }

    private fun ext(file: File) = if (file.extension.isEmpty()) "" else ".${file.extension}"

    private fun relocate(context: Context, from: File, to: File): File? {
        if (!from.renameTo(to)) return null
        // Rescanning the old path drops its stale MediaStore row; the new one gets indexed.
        scanPaths(context, from.absolutePath, to.absolutePath)
        return to
    }

    private fun scanPaths(context: Context, vararg paths: String) =
        MediaScannerConnection.scanFile(context, arrayOf(*paths), null, null)
}
