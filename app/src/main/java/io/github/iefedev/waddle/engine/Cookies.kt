package io.github.iefedev.waddle.engine

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import java.io.File

/** Sites the user can sign in to. WebView ones log in inside the app; YouTube takes an imported cookies.txt. */
enum class Site(
    val label: String,
    val domains: List<String>,
    /** Cookie that only exists for a signed-in session. */
    val sessionCookie: String?,
    val loginUrl: String?,
) {
    INSTAGRAM("Instagram", listOf("instagram.com"), "sessionid", "https://www.instagram.com/accounts/login/"),
    X("X", listOf("x.com", "twitter.com"), "auth_token", "https://x.com/i/flow/login"),
    TIKTOK("TikTok", listOf("tiktok.com"), "sessionid", "https://www.tiktok.com/login"),
    // Google refuses sign-in inside a WebView, so YouTube cookies come from a browser export.
    YOUTUBE("YouTube", listOf("youtube.com", "google.com"), null, null);

    val viaWebView get() = loginUrl != null
}

/** Per-site Netscape cookie files in app-private storage; each yt-dlp run gets a copy of its site's. */
object Cookies {
    private const val HEADER = "# Netscape HTTP Cookie File"
    private const val YEAR_S = 365L * 24 * 3600

    private fun dir(c: Context) = File(c.filesDir, "cookies").apply { mkdirs() }
    private fun fileOf(c: Context, site: Site) = File(dir(c), site.name.lowercase() + ".txt")

    fun isSignedIn(c: Context, site: Site): Boolean {
        val f = fileOf(c, site)
        if (!f.exists()) return false
        val name = site.sessionCookie ?: return f.length() > 0
        return f.readLines().any { it.split('\t').getOrNull(5) == name }
    }

    /** Writes the WebView's cookies for [site] to its file. False when no signed-in session is there. */
    fun exportFromWebView(c: Context, site: Site): Boolean {
        val cm = CookieManager.getInstance().apply { flush() }
        val expiry = System.currentTimeMillis() / 1000 + YEAR_S
        val lines = site.domains.flatMap { domain ->
            val raw = cm.getCookie("https://www.$domain/").orEmpty()
            raw.split(';').mapNotNull { pair ->
                val p = pair.trim()
                val eq = p.indexOf('=')
                if (eq <= 0) null
                else listOf(".$domain", "TRUE", "/", "TRUE", expiry, p.substring(0, eq), p.substring(eq + 1)).joinToString("\t")
            }
        }
        // Anonymous visitor cookies (csrftoken…) are there before any sign-in; only a session counts.
        if (lines.none { it.split('\t')[5] == site.sessionCookie }) return false
        fileOf(c, site).writeText((listOf(HEADER) + lines).joinToString("\n", postfix = "\n"))
        return true
    }

    /** Copies the [site] lines of a browser-exported cookies.txt. False when it has none. */
    fun import(c: Context, site: Site, uri: Uri): Boolean {
        val text = runCatching { c.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            ?: return false
        val lines = text.lineSequence().map { it.trimEnd('\r') }.filter { line ->
            val f = line.removePrefix("#HttpOnly_").split('\t')
            f.size == 7 && site.domains.any { d -> f[0].trimStart('.').let { it == d || it.endsWith(".$d") } }
        }.toList()
        if (lines.isEmpty()) return false
        fileOf(c, site).writeText((listOf(HEADER) + lines).joinToString("\n", postfix = "\n"))
        return true
    }

    fun signOut(c: Context, site: Site) {
        fileOf(c, site).delete()
        if (!site.viaWebView) return
        val cm = CookieManager.getInstance()
        site.domains.forEach { domain ->
            val url = "https://www.$domain"
            cm.getCookie(url).orEmpty().split(';').map { it.substringBefore('=').trim() }.filter { it.isNotEmpty() }.forEach { name ->
                cm.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=.$domain")
                cm.setCookie(url, "$name=; Max-Age=0; Path=/")
            }
        }
        cm.flush()
    }

    fun siteOf(url: String): Site? {
        val host = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase() ?: return null
        return Site.entries.firstOrNull { s -> s.domains.any { host == it || host.endsWith(".$it") } || (s == Site.YOUTUBE && host == "youtu.be") }
    }

    fun hasCookies(c: Context, url: String): Boolean = siteOf(url)?.let { fileOf(c, it).exists() } == true

    /**
     * A throwaway copy of the cookies for [url]'s site, for one yt-dlp run; the caller deletes it.
     * yt-dlp writes its cookie jar back to the file it was given, so it never gets the kept file:
     * a download still running would otherwise undo a sign-out. YouTube's only with [withYouTube].
     */
    fun forUrl(c: Context, url: String, withYouTube: Boolean): File? {
        val site = siteOf(url) ?: return null
        if (site == Site.YOUTUBE && !withYouTube) return null
        val kept = fileOf(c, site).takeIf { it.exists() } ?: return null
        return File(c.cacheDir, "cookies-${System.nanoTime()}.txt").apply { kept.copyTo(this, overwrite = true) }
    }
}
