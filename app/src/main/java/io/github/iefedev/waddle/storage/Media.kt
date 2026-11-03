package io.github.iefedev.waddle.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.util.LruCache
import android.util.Size
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** What the gallery shows about a file; [aspect] is width/height as displayed (rotation applied). */
data class MediaInfo(val aspect: Float, val durationMs: Long, val title: String?)

/**
 * Per-file metadata and thumbnails, read from the file itself and cached by path+mtime+size, so a
 * file dropped in by a file manager behaves like a download. Both caches are disposable.
 * Everything here blocks; call it off the main thread.
 */
object Media {
    private val infos = ConcurrentHashMap<String, MediaInfo>()
    @Volatile private var loaded = false
    @Volatile private var dirty = false
    private val thumbs = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun key(f: File) = "${f.absolutePath}|${f.lastModified()}|${f.length()}"

    fun info(context: Context, f: File): MediaInfo {
        load(context)
        val k = key(f)
        return infos[k] ?: read(f).also { infos[k] = it; dirty = true }
    }

    /** Writes the metadata cache, dropping rows whose file is gone. */
    fun save(context: Context): Unit = synchronized(this) {
        if (!dirty) return
        dirty = false
        val rows = infos.entries.filter { File(it.key.substringBeforeLast('|').substringBeforeLast('|')).exists() }
        runCatching {
            infoFile(context).writeText(rows.joinToString("\n") { (k, i) ->
                listOf(k, i.aspect, i.durationMs, i.title.orEmpty().replace(Regex("""\s"""), " ")).joinToString("\t")
            })
        }
    }

    /** Metadata if it is already in memory; never touches the disk, so the UI can draw with it at once. */
    fun cachedInfo(f: File): MediaInfo? = if (loaded) infos[key(f)] else null

    /** [small] is half the size, for folder-cover cells; a full one in memory serves it too. */
    fun cachedThumb(f: File, small: Boolean = false): Bitmap? {
        val k = key(f)
        return if (small) thumbs.get("$k|s") ?: thumbs.get(k) else thumbs.get(k)
    }

    fun thumb(context: Context, f: File, small: Boolean = false): Bitmap? {
        cachedThumb(f, small)?.let { return it }
        val k = key(f)
        val disk = File(File(context.cacheDir, "thumbs").apply { mkdirs() }, sha1(k) + ".jpg")
        val sample = BitmapFactory.Options().apply { inSampleSize = if (small) 2 else 1 }
        val bmp = if (disk.exists()) BitmapFactory.decodeFile(disk.path, sample)
        else makeThumb(f)?.also { b -> runCatching { disk.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 85, it) } } }
            ?.let { if (small) Bitmap.createScaledBitmap(it, (it.width / 2).coerceAtLeast(1), (it.height / 2).coerceAtLeast(1), true) else it }
            ?: if (Storage.mimeOf(f) == "video/*" && ffmpegFrame(context, f, disk)) BitmapFactory.decodeFile(disk.path, sample) else null
        return bmp?.also { thumbs.put(if (small) "$k|s" else k, it) }
    }

    /**
     * Frame via the bundled ffmpeg, for codecs some devices' own decoders refuse (for example
     * Instagram's VP9-in-mp4). Needs youtubedl-android's extracted libs; false until they exist.
     */
    private fun ffmpegFrame(context: Context, f: File, out: File): Boolean {
        val packages = File(context.noBackupFilesDir, "youtubedl-android/packages")
        val bin = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        // 1 s in skips black intro frames; clips shorter than that fall back to the first frame.
        for (at in listOf("1", "0")) {
            val ok = runCatching {
                val p = ProcessBuilder(
                    bin.path, "-v", "error", "-y", "-ss", at, "-i", f.path, "-frames:v", "1",
                    "-vf", "scale=512:512:force_original_aspect_ratio=decrease", "-q:v", "4", out.path,
                ).redirectErrorStream(true).apply {
                    environment()["LD_LIBRARY_PATH"] = "$packages/python/usr/lib:$packages/ffmpeg/usr/lib"
                }.start()
                p.inputStream.readBytes()
                p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0
            }.getOrDefault(false)
            if (ok && out.length() > 0) return true
        }
        out.delete()
        return false
    }

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                infoFile(context).takeIf { it.exists() }?.forEachLine { line ->
                    val p = line.split('\t')
                    if (p.size == 4) {
                        val aspect = p[1].toFloatOrNull() ?: return@forEachLine
                        infos[p[0]] = MediaInfo(aspect, p[2].toLongOrNull() ?: 0, p[3].ifBlank { null })
                    }
                }
            }
            loaded = true
        }
    }

    private fun infoFile(context: Context) = File(context.cacheDir, "media-info.tsv")

    private fun read(f: File): MediaInfo {
        val mime = Storage.mimeOf(f)
        return runCatching {
            if (mime == "image/*") {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, o)
                MediaInfo(o.outWidth.toFloat() / o.outHeight, 0, null)
            } else MediaMetadataRetriever().use { r ->
                r.setDataSource(f.path)
                fun num(key: Int) = r.extractMetadata(key)?.toIntOrNull() ?: 0
                val w = num(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val h = num(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val rotated = num(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) % 180 != 0
                val aspect = when {
                    mime != "video/*" || w <= 0 || h <= 0 -> 1f
                    rotated -> h.toFloat() / w
                    else -> w.toFloat() / h
                }
                MediaInfo(
                    aspect,
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() },
                )
            }
        }.getOrNull()?.takeIf { it.aspect.isFinite() && it.aspect > 0 }
            ?: MediaInfo(if (mime == "video/*") 16f / 9f else 1f, 0, null)
    }

    private fun makeThumb(f: File): Bitmap? = runCatching {
        val size = Size(512, 512)
        when (Storage.mimeOf(f)) {
            "video/*" -> ThumbnailUtils.createVideoThumbnail(f, size, null)
            "image/*" -> ThumbnailUtils.createImageThumbnail(f, size, null)
            "audio/*" -> ThumbnailUtils.createAudioThumbnail(f, size, null)
            else -> null
        }
    }.getOrNull()

    private fun sha1(s: String) =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
