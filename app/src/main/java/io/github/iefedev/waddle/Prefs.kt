package io.github.iefedev.waddle

import android.content.Context
import io.github.iefedev.waddle.engine.Quality
import io.github.iefedev.waddle.storage.Storage

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("waddle", Context.MODE_PRIVATE)

    fun lastCategory(c: Context): String = sp(c).getString("lastCategory", null) ?: Storage.DEFAULT_CATEGORY
    fun setLastCategory(c: Context, v: String) = sp(c).edit().putString("lastCategory", v).apply()

    fun lastQuality(c: Context): Quality = Quality.of(sp(c).getString("lastQuality", null))
    fun setLastQuality(c: Context, v: Quality) = sp(c).edit().putString("lastQuality", v.name).apply()

    /** Share without the panel: download straight away with the title and last category. */
    fun silentShare(c: Context): Boolean = sp(c).getBoolean("silentShare", false)
    fun setSilentShare(c: Context, v: Boolean) = sp(c).edit().putBoolean("silentShare", v).apply()

    /** Custom downloads folder (absolute path); null = the default Waddle/Downloads. */
    fun downloadsPath(c: Context): String? = sp(c).getString("downloadsPath", null)
    fun setDownloadsPath(c: Context, v: String?) = sp(c).edit().putString("downloadsPath", v).apply()

    /** Daily yt-dlp self-update at app start; on by default, switchable in Settings. */
    fun autoUpdateEngine(c: Context): Boolean = sp(c).getBoolean("autoUpdateEngine", true)
    fun setAutoUpdateEngine(c: Context, v: Boolean) = sp(c).edit().putBoolean("autoUpdateEngine", v).apply()

    fun lastUpdateCheck(c: Context): Long = sp(c).getLong("lastUpdateCheck", 0L)
    fun setLastUpdateCheck(c: Context, v: Long) = sp(c).edit().putLong("lastUpdateCheck", v).apply()
}
