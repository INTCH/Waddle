package io.github.iefedev.waddle.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.res.stringResource
import io.github.iefedev.waddle.R
import io.github.iefedev.waddle.storage.Storage

object Onboarding {
    /** All-files access is the only hard requirement; the others degrade gracefully. */
    fun isComplete(context: Context) = Storage.hasAccess()

    fun hasNotifications(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ignoresBattery(context: Context) =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun storageIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))

    // Sideloaded downloader: long downloads must survive aggressive OEM background killers.
    @SuppressLint("BatteryLife")
    fun batteryIntent(context: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
}

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) } // re-read permission state on resume
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    val storage = remember(tick) { Storage.hasAccess() }
    val notif = remember(tick) { Onboarding.hasNotifications(context) }
    val battery = remember(tick) { Onboarding.ignoresBattery(context) }
    LaunchedEffect(storage) { if (storage) Storage.downloads().mkdirs() }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.onboarding_body))

        Step(
            title = stringResource(R.string.step_storage_title),
            body = stringResource(R.string.step_storage_body),
            done = storage,
        ) { context.startActivity(Onboarding.storageIntent(context)) }

        Step(
            title = stringResource(R.string.step_notif_title),
            body = stringResource(R.string.step_notif_body),
            done = notif,
        ) { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }

        Step(
            title = stringResource(R.string.step_battery_title),
            body = stringResource(R.string.step_battery_body),
            done = battery,
        ) { context.startActivity(Onboarding.batteryIntent(context)) }

        Button(onClick = onDone, enabled = storage, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_start)) }
    }
}

@Composable
private fun Step(title: String, body: String, done: Boolean, onGrant: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (done) Text(stringResource(R.string.step_granted), color = MaterialTheme.colorScheme.primary)
            else OutlinedButton(onClick = onGrant) { Text(stringResource(R.string.action_grant)) }
        }
    }
}
