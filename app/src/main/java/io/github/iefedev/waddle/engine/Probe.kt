package io.github.iefedev.waddle.engine

import org.json.JSONObject

/** One video of a multi-video link. [available] false = private / deleted: starts unticked. */
data class Entry(val title: String?, val durationSec: Long?, val thumb: String?, val available: Boolean = true)

/**
 * What a link points at. [entries] is set only when it holds more than one video; a playlist with a
 * single video reads as that video. [channel]: a channel / profile, whose entries are whole playlists
 * (its Videos, Shorts… tabs); not downloadable as such.
 */
data class Probe(val title: String?, val durationSec: Long?, val entries: List<Entry>?, val channel: Boolean = false) {
    val multi get() = entries != null

    companion object {
        fun parse(json: String): Probe {
            val o = JSONObject(json)
            val raw = o.optJSONArray("entries")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }
            if (raw != null && raw.any { it.isNested() }) return Probe(o.str("title"), null, null, channel = true)
            val list = raw?.let(::entriesOf)
            if (list != null && list.size == 1) return Probe(list[0].title ?: o.str("title"), list[0].durationSec, null)
            return Probe(o.str("title"), o.duration(), list?.takeIf { it.size > 1 })
        }

        // Unavailable entries come back as null and were dropped already.
        private fun entriesOf(all: List<JSONObject>): List<Entry> {
            return all.map { e ->
                val title = e.str("title")
                val gone = title in setOf("[Private video]", "[Deleted video]") ||
                    e.str("availability") in setOf("private", "needs_auth", "subscriber_only", "premium_only")
                Entry(title, e.duration(), e.thumb(), available = !gone)
            }
        }

        private fun JSONObject.isNested(): Boolean {
            if (str("_type") == "playlist") return true
            val ie = str("ie_key").orEmpty()
            return ie.endsWith("Tab") || ie.contains("Playlist") || ie.endsWith("User")
        }

        private fun JSONObject.str(key: String) = optString(key).takeIf { has(key) && !isNull(key) && it.isNotBlank() }
        private fun JSONObject.duration() = optDouble("duration").takeIf { !it.isNaN() && it > 0 }?.toLong()

        /** "thumbnail", else the first (smallest; only a list row shows it) of "thumbnails". */
        private fun JSONObject.thumb(): String? = str("thumbnail") ?: optJSONArray("thumbnails")?.let { t ->
            (0 until t.length()).firstNotNullOfOrNull { t.optJSONObject(it)?.str("url") }
        }
    }
}

/** A time range to keep, in whole seconds. */
data class Section(val start: Long, val end: Long) {
    /** "1:20-2:05", for the screen. */
    val label get() = "${clock(start)}-${clock(end)}"

    /** "1.20-2.05": file names can't hold ':'. */
    val fileLabel get() = label.replace(':', '.')

    companion object {
        fun clock(s: Long) = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)

        /** "1:20", "80", "1:02:03" → seconds; null when it doesn't parse. */
        fun parseClock(text: String): Long? {
            val parts = text.trim().split(':')
            if (parts.isEmpty() || parts.size > 3) return null
            return parts.fold(0L) { acc, p -> acc * 60 + (p.toLongOrNull()?.takeIf { it >= 0 } ?: return null) }
        }
    }
}
