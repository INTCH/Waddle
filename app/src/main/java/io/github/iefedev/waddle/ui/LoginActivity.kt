package io.github.iefedev.waddle.ui

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import io.github.iefedev.waddle.R
import io.github.iefedev.waddle.engine.Cookies
import io.github.iefedev.waddle.engine.Site

/** A site's own login page in a WebView; "Done" saves its cookies for yt-dlp. */
class LoginActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val site = Site.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_SITE) }
        val url = site?.loginUrl ?: return finish()
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) WebView.setWebContentsDebuggingEnabled(true)
        setContent {
            WaddleScreen {
                val web = remember {
                    WebView(this).apply {
                        // Without this AndroidView leaves WRAP_CONTENT, the page's height:100% chain resolves
                        // to 0 and Instagram's login form is clipped away (blank page).
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // Some sites refuse sign-in from embedded browsers, which they detect by the "; wv"
                        // token and "Version/4.0" in the user agent; dropping both makes it read as
                        // the system's Chrome. Everything else in the user agent stays as is.
                        settings.userAgentString = settings.userAgentString.replace("; wv", "").replace(Regex("""Version/\S+ """), "")
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = WebViewClient()
                        loadUrl(url)
                    }
                }
                BackHandler { if (web.canGoBack()) web.goBack() else finish() }
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { finish() }) { Icon(Lucide.X, stringResource(R.string.action_cancel)) }
                        Text(stringResource(R.string.login_title, site.label), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Pill(stringResource(R.string.action_done), selected = true, onClick = { done(site) }, icon = Lucide.Check)
                        Spacer(Modifier.width(8.dp))
                    }
                    AndroidView({ web }, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }

    private fun done(site: Site) {
        val ok = Cookies.exportFromWebView(this, site)
        Toast.makeText(
            this,
            if (ok) getString(R.string.toast_account_linked, site.label) else getString(R.string.toast_no_login),
            Toast.LENGTH_SHORT,
        ).show()
        if (ok) finish()
    }

    companion object {
        const val EXTRA_SITE = "site"
    }
}
