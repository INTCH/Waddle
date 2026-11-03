package io.github.iefedev.waddle

import android.util.Patterns

object Urls {
    /** First http(s) URL in shared text like "Check this out https://x.com/... via @app". */
    fun extract(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val m = Patterns.WEB_URL.matcher(text)
        while (m.find()) {
            val candidate = m.group().trimEnd('.', ',', ')', ']', '!', '?', '"', '\'')
            val url = if (candidate.startsWith("http://", true) || candidate.startsWith("https://", true)) {
                candidate
            } else {
                "https://$candidate"
            }
            // Bare words like "video.mp4" match WEB_URL too; require a dot in the host and a known scheme position.
            if (url.substringAfter("://").substringBefore('/').contains('.')) return url
        }
        return null
    }
}
