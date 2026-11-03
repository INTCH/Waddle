package io.github.iefedev.waddle.download

import android.Manifest
import android.app.NotificationChannel
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.iefedev.waddle.R
import io.github.iefedev.waddle.storage.Storage
import io.github.iefedev.waddle.ui.MainActivity
import java.io.File

object Notifications {
    const val CH_PROGRESS = "progress"
    const val CH_DONE = "done"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, context.getString(R.string.channel_progress), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, context.getString(R.string.channel_done), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun progress(context: Context, title: String, percent: Float, cancel: PendingIntent) =
        NotificationCompat.Builder(context, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(
                if (percent < 0) context.getString(R.string.notif_preparing)
                else context.getString(R.string.notif_percent, percent.toInt())
            )
            .setProgress(100, percent.coerceAtLeast(0f).toInt(), percent < 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context))
            .addAction(0, context.getString(R.string.action_cancel), cancel)
            .build()

    /** One file opens on tap; several open the app (the gallery has them all). */
    fun done(context: Context, id: Int, files: List<File>, category: String) {
        val file = files.first()
        val open = if (files.size > 1) openApp(context) else PendingIntent.getActivity(
            context, id, Storage.openIntent(context, file), PendingIntent.FLAG_IMMUTABLE
        )
        notify(context, id, NotificationCompat.Builder(context, CH_DONE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(
                if (files.size > 1) context.resources.getQuantityString(R.plurals.notif_files_downloaded, files.size, files.size)
                else file.nameWithoutExtension
            )
            .setContentText(context.getString(R.string.notif_saved_to, category))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
    }

    fun failed(context: Context, id: Int, title: String, reason: String) {
        notify(context, id, NotificationCompat.Builder(context, CH_DONE)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.notif_failed, title))
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build())
    }

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
    )

    /** Posts [n] when the user allowed notifications; otherwise does nothing. */
    private fun notify(context: Context, id: Int, n: android.app.Notification) {
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }
}
