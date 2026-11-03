package io.github.iefedev.waddle.download

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.iefedev.waddle.R
import io.github.iefedev.waddle.engine.Cookies
import io.github.iefedev.waddle.engine.Engine
import io.github.iefedev.waddle.engine.Quality
import io.github.iefedev.waddle.engine.Site
import io.github.iefedev.waddle.storage.Storage
import kotlinx.coroutines.CancellationException
import kotlin.math.abs

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val notifId = abs(id.hashCode())
    private val processId = id.toString()

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val category = inputData.getString(KEY_CATEGORY) ?: Storage.DEFAULT_CATEGORY
        val requestedName = inputData.getString(KEY_NAME).orEmpty()
        val quality = Quality.of(inputData.getString(KEY_QUALITY))
        var label = requestedName.ifBlank { url }

        // Foreground first: a plain worker would be stopped after ~10 minutes.
        setForeground(foreground(label, -1f))
        report(label, -1f)

        return try {
            val dir = Storage.categoryDir(category).apply { mkdirs() }
            val section = Downloads.parseSection(inputData.getString(KEY_SECTION))
            val items = inputData.getString(KEY_ITEMS)
            // The panel probed already; a silent share hasn't, and needs to know about several videos.
            val known = inputData.getString(KEY_MULTI)?.toBooleanStrictOrNull()
            val probe = if (requestedName.isBlank() || known == null) Engine.probe(url) else null
            if (probe?.channel == true) throw Refused(str(R.string.error_channel_link))
            val count = probe?.entries?.size ?: 0
            // A silent share never asked which videos: a long playlist needs the panel.
            if (known == null && count > SILENT_MAX) throw Refused(str(R.string.error_too_many, count))
            val multi = known ?: probe?.multi ?: false
            val title = requestedName.ifBlank { probe?.title.orEmpty() }
            var base = Storage.sanitize(title).ifEmpty { "video_${System.currentTimeMillis()}" }
            if (section != null && !multi) base = Storage.sanitize("$base (${section.fileLabel})")
            // Downloads run one at a time, so checking here (not at enqueue) avoids name clashes.
            val unique = Storage.uniqueBase(dir, base, multi)
            label = unique

            var lastShown = -10f
            var lastItem = 0
            suspend fun run(use: Engine.CookieUse) =
                Engine.download(url, dir, unique, quality, processId, section?.takeIf { !multi }, multi, items, use) { fraction, item, of ->
                    val percent = if (fraction < 0) -1f else fraction * 100f
                    val shown = if (of > 1) "$unique ($item/$of)" else unique
                    if (item != lastItem || percent - lastShown >= 1f || percent >= 100f) {
                        lastShown = percent
                        lastItem = item
                        setForegroundAsync(foreground(shown, percent))
                        setProgressAsync(workDataOf(KEY_LABEL to shown, KEY_PERCENT to percent))
                    }
                }
            val site = Cookies.siteOf(url)
            val signedIn = Cookies.hasCookies(applicationContext, url)
            var staleSession = false
            val outcome = try {
                run(Engine.CookieUse.SITE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                when {
                    // YouTube cookies make yt-dlp skip its best clients for public videos, so they only go
                    // along on a second try, when YouTube asked for a sign-in (age limit, "not a bot", members).
                    site == Site.YOUTUBE && signedIn && wantsLogin(e.message.orEmpty()) -> run(Engine.CookieUse.WITH_YOUTUBE)
                    // An expired or rejected session breaks even public posts: try once signed out. If that
                    // fails too, the signed-in error is the one that says what is wrong.
                    site != Site.YOUTUBE && signedIn ->
                        runCatching { run(Engine.CookieUse.NONE) }.getOrElse { throw e }.also { staleSession = true }
                    else -> throw e
                }
            }
            val files = outcome.files
            files.forEach { Storage.scan(applicationContext, it) }
            Notifications.done(applicationContext, notifId, files, category)
            val note = when {
                staleSession -> str(R.string.note_stale_session, site?.label.orEmpty())
                // Some entries of several failed (private, removed, photos): the rest still counts as done.
                outcome.error != null ->
                    applicationContext.resources.getQuantityString(R.plurals.note_partial, files.size, files.size)
                else -> null
            }
            Result.success(workDataOf(KEY_PATH to files.first().absolutePath, KEY_COUNT to files.size, KEY_LABEL to label, KEY_ERROR to note))
        } catch (e: CancellationException) {
            Engine.cancel(processId)
            throw e
        } catch (e: Throwable) {
            val reason = friendlyError(e)
            Notifications.failed(applicationContext, notifId, label, reason)
            Result.failure(workDataOf(KEY_ERROR to reason, KEY_LABEL to label))
        }
    }

    // Expedited work on Android 11 runs as a foreground service and asks for this up front.
    override suspend fun getForegroundInfo(): ForegroundInfo =
        foreground(inputData.getString(KEY_LABEL).orEmpty(), -1f)

    private suspend fun report(label: String, percent: Float) =
        setProgress(workDataOf(KEY_LABEL to label, KEY_PERCENT to percent))

    private fun foreground(label: String, percent: Float): ForegroundInfo {
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        return ForegroundInfo(
            notifId + 1,
            Notifications.progress(applicationContext, label, percent, cancel),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    /** A reason this link won't be downloaded, already in words for the user. */
    private class Refused(message: String) : Exception(message)

    private fun wantsLogin(msg: String) =
        msg.contains("login", true) || msg.contains("cookies", true) || msg.contains("sign in", true)

    private fun friendlyError(e: Throwable): String {
        if (e is Refused) return e.message.orEmpty()
        val msg = e.message.orEmpty()
        val line = msg.lineSequence().lastOrNull { it.contains("ERROR") } ?: msg.lineSequence().firstOrNull().orEmpty()
        return when {
            wantsLogin(msg) -> str(R.string.error_needs_login, line)
            msg.contains("There is no video", true) -> str(R.string.error_no_video)
            msg.contains("Unsupported URL", true) -> str(R.string.error_unsupported)
            msg.contains("Unable to resolve host", true) || msg.contains("Network", true) -> str(R.string.error_no_internet)
            else -> line.take(300).ifEmpty { e.javaClass.simpleName }
        }
    }

    private fun str(id: Int, vararg args: Any): String = applicationContext.getString(id, *args)

    companion object {
        const val TAG = "download"
        /** Most videos a silent share takes from one link without asking. */
        const val SILENT_MAX = 20
        const val KEY_URL = "url"
        const val KEY_NAME = "name"
        const val KEY_CATEGORY = "category"
        const val KEY_QUALITY = "quality"
        const val KEY_LABEL = "label"
        const val KEY_PERCENT = "percent"
        const val KEY_PATH = "path"
        const val KEY_COUNT = "count"
        const val KEY_SECTION = "section"
        const val KEY_MULTI = "multi"
        const val KEY_ITEMS = "items"
        const val KEY_ERROR = "error"
    }
}
