package io.github.iefedev.waddle.engine

import android.content.Context
import android.util.Log
import io.github.iefedev.waddle.Prefs
import io.github.iefedev.waddle.R
import io.github.iefedev.waddle.storage.Storage
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runInterruptible
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Single owner of the embedded yt-dlp + ffmpeg. */
object Engine {
    private const val TAG = "WaddleEngine"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ready = CompletableDeferred<Result<Unit>>()
    @Volatile private var started = false
    @Volatile private var app: Context? = null

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        this.app = app
        scope.launch {
            val result = runCatching {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
            }
            result.exceptionOrNull()?.let { Log.e(TAG, "init failed", it) }
            ready.complete(result)
            // The bundled yt-dlp is a snapshot and sites change often: refresh at most once a day,
            // unless the user turned automatic updates off.
            if (result.isSuccess && Prefs.autoUpdateEngine(app) &&
                System.currentTimeMillis() - Prefs.lastUpdateCheck(app) > 24 * 3600_000L
            ) {
                runCatching { update(app) }.onFailure { Log.w(TAG, "auto update failed", it) }
            }
        }
    }

    suspend fun awaitReady() {
        ready.await().getOrThrow()
    }

    /**
     * Downloads the latest stable yt-dlp release (youtubedl-android fetches it from yt-dlp's GitHub
     * releases) and returns a human readable status line.
     */
    suspend fun update(context: Context): String = withContext(Dispatchers.IO) {
        awaitReady()
        val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
        Prefs.setLastUpdateCheck(context, System.currentTimeMillis())
        val version = "yt-dlp " + (YoutubeDL.getInstance().versionName(context) ?: "?")
        if (status == YoutubeDL.UpdateStatus.DONE) context.getString(R.string.engine_updated, version)
        else context.getString(R.string.engine_up_to_date, version)
    }

    fun version(context: Context): String? =
        runCatching { YoutubeDL.getInstance().versionName(context) }.getOrNull()

    /**
     * What [url] points at, from one `yt-dlp -J`: the title and duration for the panel, and the
     * entries when it holds several videos (carousel, multi-video post, playlist). Null on failure.
     */
    suspend fun probe(url: String): Probe? {
        // An expired or rejected session makes even public posts fail: then look again signed out.
        val signedIn = app?.let { Cookies.siteOf(url) != Site.YOUTUBE && Cookies.hasCookies(it, url) } == true
        return probeOnce(url, if (signedIn) CookieUse.SITE else CookieUse.NONE)
            ?: if (signedIn) probeOnce(url, CookieUse.NONE) else null
    }

    private suspend fun probeOnce(url: String, use: CookieUse): Probe? = withContext(Dispatchers.IO) {
        awaitReady()
        // A changed link or a closed panel cancels the caller; the Python process has to go with it.
        val id = "probe-" + UUID.randomUUID()
        val cookies = cookieCopy(url, use)
        try {
            runInterruptible {
                val request = YoutubeDLRequest(url).apply {
                    addOption("-J")
                    // Playlist entries stay unresolved: one request instead of one per video.
                    addOption("--flat-playlist")
                    addOption("--no-playlist")
                    // A carousel's photos have no video formats; don't let them sink the whole probe.
                    addOption("--ignore-no-formats-error")
                    cookies?.let { addOption("--cookies", it.absolutePath) }
                }
                Probe.parse(YoutubeDL.getInstance().execute(request, id).out)
            }
        } catch (e: CancellationException) {
            cancel(id)
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "probe failed", e)
            null
        } finally {
            cookies?.delete()
        }
    }

    /** Which signed-in cookies a run gets: the URL's site except YouTube, YouTube too, or none. */
    enum class CookieUse { SITE, WITH_YOUTUBE, NONE }

    private fun cookieCopy(url: String, use: CookieUse): File? =
        if (use == CookieUse.NONE) null else app?.let { Cookies.forUrl(it, url, withYouTube = use == CookieUse.WITH_YOUTUBE) }

    /** Files that made it, and the error when some entries (or all, then [files] is empty) failed. */
    class Outcome(val files: List<File>, val error: Throwable?)

    /**
     * Downloads [url] into [targetDir] as [baseName] (already sanitized and unique, without
     * extension). With [multi], every entry (or only [items], a 1-based -I spec) lands as "<baseName> N".
     * [section] keeps only that time range. YouTube cookies go along only with [CookieUse.WITH_YOUTUBE]: with
     * them yt-dlp drops the clients that work best for public videos. Throws only when nothing landed.
     */
    suspend fun download(
        url: String,
        targetDir: File,
        baseName: String,
        quality: Quality,
        processId: String,
        section: Section? = null,
        multi: Boolean = false,
        items: String? = null,
        cookieUse: CookieUse = CookieUse.SITE,
        onProgress: (fraction: Float, item: Int, of: Int) -> Unit,
    ): Outcome = withContext(Dispatchers.IO) {
        awaitReady()
        targetDir.mkdirs()
        val tmp = Storage.tempDir()
        // Paths go to a file, not stdout: when one entry of several fails yt-dlp exits non-zero and
        // youtubedl-android throws away stdout, but the entries before it are still on disk.
        val paths = File(tmp, "$processId.paths").apply { delete() }
        val cookies = cookieCopy(url, cookieUse)
        val request = YoutubeDLRequest(url).apply {
            // Partial files go to Waddle/.tmp (same filesystem → final move is a rename).
            addOption("-P", "home:${targetDir.absolutePath}")
            addOption("-P", "temp:${tmp.absolutePath}")
            val stem = Storage.escapeTemplate(baseName)
            addOption("-o", if (multi) "$stem %(playlist_autonumber)d.%(ext)s" else "$stem.%(ext)s")
            if (multi && items != null) addOption("-I", items)
            if (section != null) {
                addOption("--download-sections", "*${section.start}-${section.end}")
                // Cuts on the exact second instead of the nearest keyframe; re-encodes the clip (libx264
                // in the bundled ffmpeg), so trade a little size for a lot of speed on the phone.
                addOption("--force-keyframes-at-cuts")
                addOption("--downloader-args", "ffmpeg_o:-preset veryfast -crf 20")
            }
            addOption("--ignore-no-formats-error")
            cookies?.let { addOption("--cookies", it.absolutePath) }
            if (quality.audioOnly) {
                addOption("-f", "ba/b")
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", "0")
                addOption("--embed-thumbnail")
            } else {
                // Prefer mp4/m4a so the result plays everywhere; merge into mp4 when separate.
                val sort = quality.maxHeight?.let { "res:$it,ext:mp4:m4a" } ?: "ext:mp4:m4a"
                addOption("-S", sort)
                addOption("--merge-output-format", "mp4")
            }
            addOption("--embed-metadata")
            addOption("--no-mtime")
            addOption("--no-playlist")
            // addOption takes one argument; options keep insertion order, so the path follows as its own word.
            addOption("--print-to-file", "after_move:filepath")
            addOption(paths.absolutePath)
            addOption("--newline")
        }
        var item = 1
        var of = 1
        val error = try {
            runInterruptible {
                YoutubeDL.getInstance().execute(request, processId) { progress, _, line ->
                    ITEM.find(line)?.let { m -> item = m.groupValues[1].toInt(); of = m.groupValues[2].toInt() }
                    onProgress(if (progress < 0) -1f else ((item - 1) + progress / 100f) / of, item, of)
                }
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            e
        } finally {
            cookies?.delete()
        }
        val files = runCatching { paths.readLines() }.getOrDefault(emptyList()).map { File(it.trim()) }
            .filter { it.path.startsWith("/") && it.exists() }.distinct()
        paths.delete()
        if (files.isEmpty()) throw error ?: IllegalStateException("yt-dlp did not report the output file")
        Outcome(files, error)
    }

    private val ITEM = Regex("""Downloading item (\d+) of (\d+)""")

    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }
}
