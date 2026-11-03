package io.github.iefedev.waddle.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.iefedev.waddle.Prefs
import io.github.iefedev.waddle.engine.Quality
import io.github.iefedev.waddle.engine.Section

/**
 * What to download beyond the link. [multi] null = not probed yet (silent share): the worker finds out.
 * [items] are 1-based entry numbers; null = all of them.
 */
data class Extras(val section: Section? = null, val multi: Boolean? = null, val items: List<Int>? = null)

object Downloads {
    private const val QUEUE = "waddle-queue"
    // WorkInfo doesn't expose input data, so the request is mirrored in tags for the
    // download list (queued rows' names) and for retrying a failed item.
    private const val TAG_LABEL = "label:"
    private const val TAG_URL = "url:"
    private const val TAG_NAME = "name:"
    private const val TAG_CATEGORY = "cat:"
    private const val TAG_QUALITY = "q:"
    private const val TAG_SECTION = "sec:"
    private const val TAG_MULTI = "multi:"
    private const val TAG_ITEMS = "items:"

    /** Queues a download; downloads run one after another. [name] blank = use the video title. */
    fun enqueue(context: Context, url: String, name: String, category: String, quality: Quality, extras: Extras = Extras()) {
        Prefs.setLastCategory(context, category)
        Prefs.setLastQuality(context, quality)
        val cleanName = name.trim()
        val label = cleanName.ifEmpty { url }
        val section = extras.section?.let { "${it.start}-${it.end}" }
        val items = extras.items?.let(::itemSpec)
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(
                DownloadWorker.KEY_URL to url,
                DownloadWorker.KEY_NAME to cleanName,
                DownloadWorker.KEY_CATEGORY to category,
                DownloadWorker.KEY_QUALITY to quality.name,
                DownloadWorker.KEY_LABEL to label,
                DownloadWorker.KEY_SECTION to section,
                DownloadWorker.KEY_MULTI to extras.multi?.toString(),
                DownloadWorker.KEY_ITEMS to items,
            ))
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(DownloadWorker.TAG)
            .addTag(TAG_LABEL + label)
            .addTag(TAG_URL + url)
            .addTag(TAG_NAME + cleanName)
            .addTag(TAG_CATEGORY + category)
            .addTag(TAG_QUALITY + quality.name)
            .apply {
                section?.let { addTag(TAG_SECTION + it) }
                extras.multi?.let { addTag(TAG_MULTI + it) }
                items?.let { addTag(TAG_ITEMS + it) }
            }
            .build()
        // APPEND_OR_REPLACE: a failed/cancelled earlier item doesn't block the rest of the queue.
        WorkManager.getInstance(context).enqueueUniqueWork(QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun label(job: WorkInfo): String? = job.tag(TAG_LABEL)

    fun category(job: WorkInfo): String? = job.tag(TAG_CATEGORY)

    /** Re-queues a finished (failed/cancelled) job with the same options. */
    fun retry(context: Context, job: WorkInfo): Boolean {
        val url = job.tag(TAG_URL) ?: return false
        enqueue(
            context, url,
            job.tag(TAG_NAME).orEmpty(),
            job.tag(TAG_CATEGORY) ?: Prefs.lastCategory(context),
            Quality.of(job.tag(TAG_QUALITY)),
            Extras(
                parseSection(job.tag(TAG_SECTION)),
                job.tag(TAG_MULTI)?.toBooleanStrictOrNull(),
                parseItems(job.tag(TAG_ITEMS)),
            ),
        )
        WorkManager.getInstance(context).pruneWork()
        return true
    }

    fun parseSection(s: String?): Section? {
        val (a, b) = s?.split('-')?.takeIf { it.size == 2 } ?: return null
        return Section(a.toLongOrNull() ?: return null, b.toLongOrNull() ?: return null)
    }

    /** yt-dlp's -I form, with runs folded so a long playlist stays a short string: "1-5,7,9-12". */
    fun itemSpec(items: List<Int>): String {
        val out = mutableListOf<String>()
        var start = -1
        var prev = -1
        for (i in items.sorted().distinct() + Int.MIN_VALUE) {
            if (start >= 0 && i == prev + 1) { prev = i; continue }
            if (start >= 0) out += if (start == prev) "$start" else "$start-$prev"
            start = i; prev = i
        }
        return out.joinToString(",")
    }

    fun parseItems(s: String?): List<Int>? = s?.split(',')?.flatMap { part ->
        val (a, b) = part.split('-').let { p -> p[0] to p.getOrElse(1) { p[0] } }
        val from = a.toIntOrNull() ?: return@flatMap emptyList()
        (from..(b.toIntOrNull() ?: from)).toList()
    }?.takeIf { it.isNotEmpty() }

    private fun WorkInfo.tag(prefix: String) = tags.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)
}
