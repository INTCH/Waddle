package io.github.iefedev.waddle.engine

import androidx.annotation.StringRes
import io.github.iefedev.waddle.R

/** What to download. Heights are upper bounds: yt-dlp picks the closest at or below. */
enum class Quality(@param:StringRes val label: Int, val maxHeight: Int?, val audioOnly: Boolean = false) {
    BEST(R.string.quality_best, null),
    P1080(R.string.quality_1080, 1080),
    P720(R.string.quality_720, 720),
    P480(R.string.quality_480, 480),
    AUDIO(R.string.quality_mp3, null, audioOnly = true);

    companion object {
        fun of(name: String?): Quality = entries.firstOrNull { it.name == name } ?: BEST
    }
}
