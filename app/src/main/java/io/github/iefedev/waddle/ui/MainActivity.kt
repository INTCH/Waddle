package io.github.iefedev.waddle.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.compose.ui.res.stringResource
import io.github.iefedev.waddle.R
import io.github.iefedev.waddle.Urls
import io.github.iefedev.waddle.download.DownloadWorker

class MainActivity : ComponentActivity() {

    /** A link handed over from the share panel or another app, consumed by the download tab. */
    private var pendingUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark-only app: light status/nav icons even when the system is in light mode.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        pendingUrl = intent.getStringExtra(EXTRA_URL)
        setContent { WaddleScreen { Root() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_URL)?.let { pendingUrl = Urls.extract(it) ?: it }
    }

    @Composable
    private fun Root() {
        var ready by rememberSaveable { mutableStateOf(Onboarding.isComplete(this)) }
        if (!ready) {
            OnboardingScreen(onDone = { ready = true })
            return
        }
        DownloadHaptics()
        var tab by rememberSaveable { mutableIntStateOf(0) }
        LaunchedEffect(pendingUrl) { if (pendingUrl != null) tab = 0 }
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Tab(0, tab, stringResource(R.string.tab_download), Lucide.Download) { tab = it }
                    Tab(1, tab, stringResource(R.string.tab_gallery), Lucide.Film) { tab = it }
                    Tab(2, tab, stringResource(R.string.tab_settings), Lucide.Settings) { tab = it }
                }
            },
        ) { padding ->
            val m = Modifier.padding(padding)
            when (tab) {
                0 -> HomeScreen(pendingUrl, onUrlConsumed = { pendingUrl = null }, modifier = m)
                1 -> GalleryScreen(m)
                else -> SettingsScreen(m)
            }
        }
    }

    @Composable
    private fun androidx.compose.foundation.layout.RowScope.Tab(
        index: Int, current: Int, label: String, icon: ImageVector, onSelect: (Int) -> Unit,
    ) = NavigationBarItem(
        selected = index == current,
        onClick = { onSelect(index) },
        icon = { Icon(icon, null) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(selectedTextColor = Waddle.Text),
    )

    /**
     * A short buzz when a download ends while the app is on screen (success and failure feel different).
     * The first listing only records what had already ended, so opening the app never buzzes for old jobs.
     */
    @Composable
    private fun DownloadHaptics() {
        val haptic = LocalHapticFeedback.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(Unit) {
            var ended: Set<java.util.UUID>? = null
            WorkManager.getInstance(this@MainActivity).getWorkInfosByTagFlow(DownloadWorker.TAG).collect { jobs ->
                val now = jobs.filter { it.state.isFinished }
                val fresh = ended?.let { seen -> now.filter { it.id !in seen } }.orEmpty()
                ended = now.map { it.id }.toSet()
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@collect
                when {
                    fresh.any { it.state == WorkInfo.State.FAILED } -> haptic.performHapticFeedback(HapticFeedbackType.Reject)
                    fresh.any { it.state == WorkInfo.State.SUCCEEDED } -> haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                }
            }
        }
    }

    companion object {
        const val EXTRA_URL = "url"
    }
}
