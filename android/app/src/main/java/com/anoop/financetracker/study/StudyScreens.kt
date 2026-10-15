package com.anoop.financetracker.study

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.anoop.financetracker.BuildConfig
import com.anoop.financetracker.ui.components.SectionCard
import com.anoop.financetracker.ui.components.SubPage
import com.anoop.financetracker.ui.theme.Ft

/** Separate area for the children's learning pages. No finance data is shared with it. */
@Composable
fun StudyHubScreen(nav: NavHostController) {
    SubPage("Study corner", onBack = { nav.popBackStack() }) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StudyCard("🎒", "Navamika · KG 1", "Daily worksheets, learning games, Story Time and printable PDFs.", "study-navw") { nav.navigate("study/navw") }
            StudyCard("📝", "Arya · Kerala PSC & mock tests", "Daily mock tests (new questions every morning), results, certificates and the PDF centre.", "study-arya") { nav.navigate("study/arya") }
            Text("These pages load from the Finance Tracker website and keep their own progress on this phone. They can't see your money data. " +
                "New daily content is published by the website's 9 AM schedule, so the app doesn't need to be open for it.",
                style = MaterialTheme.typography.bodySmall, color = Ft.c.muted)
        }
    }
}

@Composable
private fun StudyCard(emoji: String, title: String, body: String, tag: String, onClick: () -> Unit) {
    SectionCard(modifier = Modifier.clickable(onClick = onClick).testTag(tag)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = Ft.c.muted)
            }
        }
    }
}

fun studyUrl(who: String): String = BuildConfig.STUDY_BASE_URL + "?study=" + (if (who == "arya") "arya" else "navw") + "&app=android"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun StudyWebScreen(who: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val online = rememberOnline(ctx)
    var progress by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var fileCb by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        fileCb?.onReceiveValue(uris.toTypedArray()); fileCb = null
    }
    val host = Uri.parse(BuildConfig.STUDY_BASE_URL).host ?: ""
    val title = if (who == "arya") "Arya's Study Window" else "Navamika's Workbook"

    BackHandler { val w = web; if (w != null && w.canGoBack()) w.goBack() else onBack() }

    SubPage(title, onBack = onBack) {
        if (!online) Text("You're offline. Pages you opened before still work; new daily content arrives when you're back online.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().background(Ft.c.warn.copy(alpha = 0.18f)).padding(10.dp).testTag("offline-banner"))
        if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        Box(Modifier.fillMaxSize()) {
            AndroidView(modifier = Modifier.fillMaxSize(), onRelease = { w ->
                // Free the page (and its text-to-speech engine) when the screen goes away.
                android.util.Log.i("FTStudy", "WebView released")
                (w.tag as? StudyBridge)?.release(); w.stopLoading(); w.destroy()
            }, factory = { c ->
                android.util.Log.i("FTStudy", "creating WebView for $who")
                WebView(c).apply {
                    val bridge = StudyBridge(c, this, host)
                    tag = bridge
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    // Sounds start after a tap (the browser default); the games always begin with one.
                    settings.mediaPlaybackRequiresUserGesture = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.userAgentString = settings.userAgentString + " FTAndroid/" + BuildConfig.VERSION_NAME
                    addJavascriptInterface(bridge, "FTAndroid")
                    val early = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
                    if (early) WebViewCompat.addDocumentStartJavaScript(this, StudyBridge.SHIM, setOf("https://$host"))
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                            val u = req.url
                            if (u.scheme == "https" && u.host.equals(host, true)) return false
                            // Anything else opens outside the app.
                            runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            return true
                        }
                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            bridge.currentHost = Uri.parse(url ?: "").host ?: ""
                            failed = false
                            if (!early) view.evaluateJavascript(StudyBridge.SHIM, null)
                        }
                        override fun onPageFinished(view: WebView, url: String?) {
                            android.util.Log.i("FTStudy", "page finished")
                            bridge.currentHost = Uri.parse(url ?: "").host ?: ""
                            if (!early) view.evaluateJavascript(StudyBridge.SHIM, null)
                        }
                        override fun onReceivedError(view: WebView, req: WebResourceRequest, err: WebResourceError) {
                            android.util.Log.w("FTStudy", "load error ${err.errorCode} main=${req.isForMainFrame}")
                            if (req.isForMainFrame) failed = true
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, p: Int) { progress = p; android.util.Log.i("FTStudy", "progress $p") }
                        override fun onShowFileChooser(view: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                            fileCb?.onReceiveValue(emptyArray())
                            fileCb = cb
                            val types = params.acceptTypes.filter { it.isNotBlank() }.flatMap { it.split(',') }.map { it.trim() }
                                .map { if (it.startsWith(".")) "*/*" else it }.ifEmpty { listOf("*/*") }
                            runCatching { pickFiles.launch(types.toTypedArray()) }.onFailure { cb.onReceiveValue(emptyArray()); fileCb = null }
                            return true
                        }
                    }
                    loadUrl(studyUrl(who))
                    web = this
                    StudyBridge.current = java.lang.ref.WeakReference(this)
                    android.util.Log.i("FTStudy", "WebView ready, loading")
                }
            })
            if (failed) Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("📡", style = MaterialTheme.typography.displaySmall)
                Text("Couldn't open the study pages", style = MaterialTheme.typography.titleMedium)
                Text(if (online) "The website didn't respond. Try again in a moment." else "Connect to the internet once so the pages can be saved for offline use.",
                    color = Ft.c.muted, modifier = Modifier.padding(vertical = 8.dp))
                Button(onClick = { failed = false; web?.reload() }) { Text("Try again") }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { fileCb?.onReceiveValue(emptyArray()) }
    }
}

/** Live network status for the offline banner. */
@Composable
fun rememberOnline(ctx: Context): Boolean {
    val cm = remember { ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    fun now() = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    var online by remember { mutableStateOf(now()) }
    DisposableEffect(cm) {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = true }
            override fun onLost(network: Network) { online = now() }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }
        onDispose { runCatching { cm.unregisterNetworkCallback(cb) } }
    }
    return online
}
