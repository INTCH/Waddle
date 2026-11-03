package io.github.iefedev.waddle.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.iefedev.waddle.DebugTools
import io.github.iefedev.waddle.Prefs
import io.github.iefedev.waddle.R
import io.github.iefedev.waddle.engine.Cookies
import io.github.iefedev.waddle.engine.Engine
import io.github.iefedev.waddle.engine.Site
import io.github.iefedev.waddle.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Bumped on resume (back from the login page, a file manager) and after changes, to re-read state.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    var silent by remember { mutableStateOf(Prefs.silentShare(context)) }
    var autoUpdate by remember { mutableStateOf(Prefs.autoUpdateEngine(context)) }
    var status by remember { mutableStateOf<String?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    val where = remember(refresh) { Storage.downloads().absolutePath }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val path = Storage.pathOfTree(uri)
        if (path == null) {
            Toast.makeText(context, R.string.toast_internal_only, Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        moving = true
        scope.launch {
            val target = File(path)
            val inside = target.absolutePath.startsWith(Storage.downloads().absolutePath + "/")
            val ok = withContext(Dispatchers.IO) { Storage.changeDownloads(context, target) }
            moving = false
            refresh++
            val msg = when {
                inside -> R.string.toast_inside_current
                ok -> R.string.toast_location_changed
                else -> R.string.toast_some_not_moved
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
    val importCookies = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = Cookies.import(context, Site.YOUTUBE, uri)
        Toast.makeText(
            context, if (ok) R.string.toast_youtube_cookies_loaded else R.string.toast_youtube_cookies_missing,
            Toast.LENGTH_LONG,
        ).show()
        refresh++
    }
    val version by produceState<String?>(null, status) {
        value = withContext(Dispatchers.IO) { runCatching { Engine.awaitReady(); Engine.version(context) }.getOrNull() }
    }
    val sizes by produceState<List<Triple<String, Long, Int>>?>(null, refresh, moving) {
        value = withContext(Dispatchers.IO) { runCatching { Storage.folderSizes() }.getOrNull() }
    }
    val signedIn = remember(refresh) { Site.entries.associateWith { Cookies.isSignedIn(context, it) } }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)

        Section(stringResource(R.string.section_sharing))
        RowCard(onClick = { silent = !silent; Prefs.setSilentShare(context, silent) }) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.silent_share))
                Hint(stringResource(R.string.silent_share_hint))
            }
            Switch(silent, { silent = it; Prefs.setSilentShare(context, it) })
        }

        Section(stringResource(R.string.section_accounts))
        Site.entries.forEach { site ->
            val on = signedIn[site] == true
            RowCard {
                Column(Modifier.weight(1f)) {
                    Text(site.label)
                    Hint(
                        stringResource(
                            when {
                                on && site.viaWebView -> R.string.account_signed_in
                                on -> R.string.account_cookies_loaded
                                site.viaWebView -> R.string.account_signed_out
                                else -> R.string.account_cookies_txt
                            },
                        ),
                    )
                }
                when {
                    on -> Pill(stringResource(R.string.action_sign_out), false, { Cookies.signOut(context, site); refresh++ }, Lucide.LogOut)
                    site.viaWebView -> Pill(stringResource(R.string.action_sign_in), false, {
                        context.startActivity(Intent(context, LoginActivity::class.java).putExtra(LoginActivity.EXTRA_SITE, site.name))
                    }, Lucide.LogIn)
                    else -> Pill(stringResource(R.string.action_choose_file), false, {
                        importCookies.launch(arrayOf("text/*", "application/octet-stream"))
                    }, Lucide.FileUp)
                }
            }
        }
        Hint(stringResource(R.string.accounts_hint), Modifier.padding(horizontal = 4.dp))

        Section(stringResource(R.string.section_engine))
        val updating = stringResource(R.string.action_updating)
        RowCard {
            Column(Modifier.weight(1f)) {
                // versionName already reads like "yt-dlp 2026.10.26".
                Text(version ?: stringResource(R.string.engine_loading))
                Hint(
                    status ?: updateError?.let { stringResource(R.string.engine_error, it) }
                        ?: stringResource(R.string.engine_hint),
                )
            }
            Pill(if (busy) updating else stringResource(R.string.action_update), false, {
                if (busy) return@Pill
                busy = true; status = updating; updateError = null
                scope.launch {
                    runCatching { Engine.update(context) }
                        .onSuccess { status = it }
                        .onFailure { status = null; updateError = it.message.orEmpty() }
                    busy = false
                }
            }, Lucide.RefreshCw)
        }
        RowCard(onClick = { autoUpdate = !autoUpdate; Prefs.setAutoUpdateEngine(context, autoUpdate) }) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.auto_update))
                Hint(stringResource(R.string.auto_update_hint))
            }
            Switch(autoUpdate, { autoUpdate = it; Prefs.setAutoUpdateEngine(context, it) })
        }

        Section(stringResource(R.string.section_storage))
        RowCard(onClick = { if (!moving) pickFolder.launch(null) }) {
            Icon(Lucide.HardDrive, null, tint = Waddle.Ice, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(where, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Hint(stringResource(if (moving) R.string.storage_moving else R.string.storage_hint))
            }
            Icon(Lucide.ChevronRight, null, tint = Waddle.Muted)
        }
        sizes?.let { FolderSizes(it) }

        if (DebugTools.AVAILABLE) {
            Section(DebugTools.SECTION_TITLE)
            RowCard(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { DebugTools.seedDemoLibrary(context) }
                    refresh++
                    Toast.makeText(context, DebugTools.SEEDED_MESSAGE, Toast.LENGTH_SHORT).show()
                }
            }) {
                Column(Modifier.weight(1f)) {
                    Text(DebugTools.SEED_TITLE)
                    Hint(DebugTools.SEED_HINT)
                }
            }
        }
    }
}

/** One card: every folder with its file count and size, then the total. */
@Composable
private fun FolderSizes(rows: List<Triple<String, Long, Int>>) {
    Surface(color = Waddle.Card, contentColor = Waddle.Text, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            rows.forEach { (name, bytes, count) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.Folder, null, tint = Waddle.Muted, modifier = Modifier.size(16.dp))
                    Text(name, Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pluralStringResource(R.plurals.folder_files, count, count), color = Waddle.Muted, style = MaterialTheme.typography.bodySmall)
                    Text(formatSize(bytes), Modifier.width(76.dp), style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
            HorizontalDivider(color = Waddle.Line)
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text(stringResource(R.string.total), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(formatSize(rows.sumOf { it.second }), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Section(title: String) =
    Text(title, Modifier.padding(top = 14.dp, start = 4.dp), color = Waddle.Muted, style = MaterialTheme.typography.labelLarge)

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, color = Waddle.Muted, style = MaterialTheme.typography.bodySmall)

/** Binary units, number formatted for the device locale. */
fun formatSize(bytes: Long, locale: Locale = Locale.getDefault()): String = when {
    bytes >= 1L shl 30 -> String.format(locale, "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(locale, "%.0f MB", bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> String.format(locale, "%.0f KB", bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}
