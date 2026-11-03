package io.github.iefedev.waddle.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.compose.ui.res.stringResource
import io.github.iefedev.waddle.Prefs
import io.github.iefedev.waddle.R
import io.github.iefedev.waddle.Urls
import io.github.iefedev.waddle.download.DownloadWorker
import io.github.iefedev.waddle.download.Downloads
import io.github.iefedev.waddle.engine.Engine
import io.github.iefedev.waddle.storage.Storage
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import java.io.File

/** How long the penguin celebrates (or sulks) after a download finishes. */
private const val REACTION_MS = 2400L

@Composable
fun HomeScreen(pendingUrl: String?, onUrlConsumed: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    val choice = remember { DownloadChoice(Prefs.lastCategory(context), Prefs.lastQuality(context)) }
    var notice by remember { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(pendingUrl) {
        if (pendingUrl != null) { input = pendingUrl; onUrlConsumed() }
    }
    val url = Urls.extract(input)
    LaunchedEffect(url) {
        choice.reset()
        if (url != null) { delay(400); choice.probed(Engine.probe(url)) }
    }

    val wm = remember { WorkManager.getInstance(context) }
    val jobs by wm.getWorkInfosByTagFlow(DownloadWorker.TAG).collectAsState(emptyList())

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
    ) {
        item {
            LinkCard(
                input,
                onInput = { input = it; notice = null },
                onPaste = { pasteFromClipboard(context)?.let { input = it; notice = null } },
            )
        }
        item { DownloadOptions(choice) }
        item {
            WaddleButton(stringResource(R.string.action_download), modifier = Modifier.fillMaxWidth(), onClick = {
                notice = when {
                    url == null -> R.string.notice_paste_first
                    choice.probe?.channel == true -> R.string.notice_channel
                    choice.nothingSelected -> R.string.notice_select_one
                    else -> null
                }
                if (notice != null || url == null) {
                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                    return@WaddleButton
                }
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                choice.enqueue(context, url)
                input = ""; choice.name = ""
            })
        }
        val shown = notice ?: R.string.notice_channel.takeIf { choice.probe?.channel == true }
        if (shown != null) {
            item { Text(stringResource(shown), color = Waddle.Beak, style = MaterialTheme.typography.bodySmall) }
        }

        val visible = jobs.sortedWith(compareBy<WorkInfo> { it.state.isFinished }.thenByDescending { it.id })
        if (visible.isEmpty()) {
            item { EmptyDownloads() }
        } else {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.downloads_header), color = Waddle.Muted,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    )
                    if (visible.any { it.state.isFinished }) {
                        TextButton(onClick = { wm.pruneWork() }) { Text(stringResource(R.string.action_clear_finished)) }
                    }
                }
            }
        }
        items(visible, key = { it.id }) { job -> JobCard(job, wm) }
    }
}

/** The link field: same row as the file name, with an orange paste button on the right. */
@Composable
private fun LinkCard(input: String, onInput: (String) -> Unit, onPaste: () -> Unit) {
    FieldCard(input, onInput, stringResource(R.string.hint_paste_link)) {
        if (input.isNotEmpty()) {
            Icon(
                Lucide.X, stringResource(R.string.cd_clear), tint = Waddle.Muted,
                modifier = Modifier.padding(end = 10.dp).size(20.dp).clickable { onInput("") },
            )
        }
        // A plain box, not a Surface button: its 48 dp touch minimum would make this row taller than the name row.
        Icon(
            Lucide.ClipboardPaste, stringResource(R.string.cd_paste), tint = Waddle.OnBeak,
            modifier = Modifier.clip(MaterialTheme.shapes.small).background(Waddle.Beak).clickable(onClick = onPaste)
                .padding(3.dp).size(18.dp),
        )
    }
}

@Composable
private fun EmptyDownloads() {
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Penguin(Mood.Fish, 96.dp)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.empty_body), color = Waddle.Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun JobCard(job: WorkInfo, wm: WorkManager) {
    val context = LocalContext.current
    val label = job.progress.getString(DownloadWorker.KEY_LABEL)
        ?: job.outputData.getString(DownloadWorker.KEY_LABEL)
        ?: Downloads.label(job)
        ?: stringResource(R.string.job_fallback)
    val percent = job.progress.getFloat(DownloadWorker.KEY_PERCENT, -1f)
    val path = job.outputData.getString(DownloadWorker.KEY_PATH)
    val state = job.state

    // Progress data disappears when the job ends; keep the last position for the finish pose.
    var lastFraction by remember(job.id) { mutableFloatStateOf(0f) }
    SideEffect { if (percent >= 0) lastFraction = percent / 100f }
    var before by remember(job.id) { mutableStateOf(state) }
    var reaction by remember(job.id) { mutableStateOf<Mood?>(null) }
    LaunchedEffect(state) {
        val justEnded = !before.isFinished && state.isFinished
        before = state
        val mood = when (state) {
            WorkInfo.State.SUCCEEDED -> Mood.Happy
            WorkInfo.State.CANCELLED -> Mood.Shrug
            else -> null
        }
        if (justEnded && mood != null) { reaction = mood; delay(REACTION_MS); reaction = null }
    }

    Surface(
        color = Waddle.Card,
        contentColor = Waddle.Text,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().clickable(enabled = path != null) {
            context.startActivity(Storage.openIntent(context, File(path!!)))
        },
    ) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state == WorkInfo.State.RUNNING && percent >= 0) {
                    Text("${percent.toInt()}%", color = Waddle.Beak, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                }
                if (!state.isFinished) {
                    IconButton(onClick = { wm.cancelWorkById(job.id) }) {
                        Icon(Lucide.X, stringResource(R.string.action_cancel), tint = Waddle.Muted)
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }
            Column(Modifier.padding(end = 10.dp)) {
                when {
                    state == WorkInfo.State.RUNNING -> Track(Mood.Walk, if (percent < 0) null else percent / 100f)
                    state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.BLOCKED -> Track(Mood.Wait, 0f)
                    reaction == Mood.Happy -> Track(Mood.Happy, 1f)
                    reaction == Mood.Shrug -> Track(Mood.Shrug, lastFraction, Waddle.Muted)
                    state == WorkInfo.State.FAILED -> Track(Mood.Sad, lastFraction, Waddle.Error)
                }
                Spacer(Modifier.height(6.dp))
                StatusLine(job, context)
            }
        }
    }
}

/** The penguin walking along the progress bar. [fraction] null = progress not known yet. */
@Composable
private fun Track(mood: Mood, fraction: Float?, color: Color = Waddle.Beak) {
    val shown by animateFloatAsState(fraction ?: 0f, label = "progress")
    val penguin = 26.dp
    BoxWithConstraints(Modifier.fillMaxWidth().height(32.dp)) {
        Penguin(mood, penguin, Modifier.align(Alignment.BottomStart).offset(x = (maxWidth - penguin) * shown))
    }
    val bar = Modifier.fillMaxWidth().height(5.dp)
    if (fraction == null) {
        LinearProgressIndicator(bar, color = color, trackColor = Waddle.Raised, strokeCap = StrokeCap.Round, gapSize = 0.dp)
    } else {
        LinearProgressIndicator(
            progress = { shown }, modifier = bar, color = color, trackColor = Waddle.Raised,
            strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
        )
    }
}

@Composable
private fun StatusLine(job: WorkInfo, context: Context) {
    val small = MaterialTheme.typography.bodySmall
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (job.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                Text(stringResource(R.string.status_queued), color = Waddle.Muted, style = small)
            WorkInfo.State.RUNNING -> Text(stringResource(R.string.status_downloading), color = Waddle.Muted, style = small)
            WorkInfo.State.SUCCEEDED -> {
                val count = job.outputData.getInt(DownloadWorker.KEY_COUNT, 1)
                val partial = job.outputData.getString(DownloadWorker.KEY_ERROR)
                val folder = Downloads.category(job) ?: Storage.DEFAULT_CATEGORY
                Icon(Lucide.Check, null, tint = Waddle.Ice, modifier = Modifier.size(14.dp))
                Text(
                    " " + if (count > 1) stringResource(R.string.status_done_files, folder, count)
                    else stringResource(R.string.status_done, folder),
                    color = Waddle.Ice, style = small,
                )
                if (partial != null) Text("  $partial", color = Waddle.ErrorText, style = small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                else Text("  " + stringResource(R.string.status_tap_to_open), color = Waddle.Muted, style = small)
            }
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                val msg = if (job.state == WorkInfo.State.CANCELLED) stringResource(R.string.status_cancelled)
                else job.outputData.getString(DownloadWorker.KEY_ERROR) ?: stringResource(R.string.status_failed)
                Text(
                    msg, Modifier.weight(1f, fill = false), maxLines = 2, overflow = TextOverflow.Ellipsis, style = small,
                    color = if (job.state == WorkInfo.State.FAILED) Waddle.ErrorText else Waddle.Muted,
                )
                Text(
                    stringResource(R.string.action_retry), color = Waddle.Beak, style = small,
                    modifier = Modifier.padding(start = 8.dp).clickable { Downloads.retry(context, job) },
                )
            }
        }
    }
}

private fun pasteFromClipboard(context: Context): String? {
    val cm = context.getSystemService(ClipboardManager::class.java)
    val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
    return Urls.extract(text) ?: text
}
