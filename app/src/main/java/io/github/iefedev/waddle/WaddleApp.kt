package io.github.iefedev.waddle

import android.app.Application
import io.github.iefedev.waddle.download.Notifications
import io.github.iefedev.waddle.engine.Engine
import io.github.iefedev.waddle.storage.Library
import io.github.iefedev.waddle.storage.Storage
import kotlin.concurrent.thread

class WaddleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        Storage.init(this)
        // Unpacks the bundled Python on first run; everything that needs yt-dlp awaits this.
        Engine.start(this)
        // Folder listings, cover thumbnails and metadata into memory, so the gallery opens drawn.
        thread(name = "gallery-warm", priority = Thread.MIN_PRIORITY) { Library.warm(this) }
    }
}
