package io.github.iefedev.waddle.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import io.github.iefedev.waddle.Prefs
import io.github.iefedev.waddle.R
import io.github.iefedev.waddle.Urls
import io.github.iefedev.waddle.download.Downloads
import io.github.iefedev.waddle.engine.Engine
import io.github.iefedev.waddle.storage.Storage
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.iefedev.waddle.ui.DownloadChoice
import io.github.iefedev.waddle.ui.DownloadOptions
import io.github.iefedev.waddle.ui.MainActivity
import io.github.iefedev.waddle.ui.Waddle
import io.github.iefedev.waddle.ui.WaddleButton
import io.github.iefedev.waddle.ui.WaddleTheme

/** Target of Android's share sheet: a small panel over the source app, or a silent download. */
class ShareActivity : ComponentActivity() {

    private var url by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!handle(intent)) return
        setContent { WaddleTheme { Panel() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    /** Returns false when the activity already finished. */
    private fun handle(intent: Intent): Boolean {
        val shared = Urls.extract(intent.getStringExtra(Intent.EXTRA_TEXT))
            ?: Urls.extract(intent.getStringExtra(Intent.EXTRA_SUBJECT))
        if (shared == null) {
            toast(getString(R.string.toast_no_link))
            finish(); return false
        }
        if (!Storage.hasAccess()) {
            toast(getString(R.string.toast_grant_access_first))
            startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_URL, shared))
            finish(); return false
        }
        if (Prefs.silentShare(this)) {
            Downloads.enqueue(this, shared, "", Prefs.lastCategory(this), Prefs.lastQuality(this))
            toast(getString(R.string.toast_downloading_to, Prefs.lastCategory(this)))
            finish(); return false
        }
        url = shared
        return true
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    @OptIn(ExperimentalMaterial3Api::class)
    @androidx.compose.runtime.Composable
    private fun Panel() {
        val link = url ?: return
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val choice = remember(link) { DownloadChoice(Prefs.lastCategory(this), Prefs.lastQuality(this)) }
        var probing by remember(link) { mutableStateOf(true) }
        LaunchedEffect(link) { choice.probed(Engine.probe(link)); probing = false }
        val title = choice.probe?.title
        val haptic = LocalHapticFeedback.current
        var notice by remember(link) { mutableStateOf<Int?>(null) }

        val host = remember(link) { Uri.parse(link).host?.removePrefix("www.") ?: link }

        ModalBottomSheet(onDismissRequest = { finish() }, sheetState = sheet, containerColor = Waddle.Night) {
            Column(
                Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding().imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    title ?: stringResource(if (probing) R.string.title_loading else R.string.title_unavailable),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (title == null) Waddle.Muted else Waddle.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(host, style = MaterialTheme.typography.bodySmall, color = Waddle.Muted, modifier = Modifier.padding(bottom = 4.dp))
                DownloadOptions(choice)
                val shown = notice ?: R.string.notice_channel.takeIf { choice.probe?.channel == true }
                if (shown != null) Text(stringResource(shown), color = Waddle.Beak, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WaddleButton(stringResource(R.string.action_cancel), { finish() }, Modifier.weight(1f), primary = false)
                    WaddleButton(stringResource(R.string.action_download), {
                        notice = when {
                            choice.probe?.channel == true -> R.string.notice_channel
                            choice.nothingSelected -> R.string.notice_select_one
                            else -> null
                        }
                        if (notice != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.Reject)
                            return@WaddleButton
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        choice.enqueue(this@ShareActivity, link)
                        toast(getString(R.string.toast_downloading_to, choice.category))
                        finish()
                    }, Modifier.weight(2f))
                }
            }
        }
    }
}
