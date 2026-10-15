package com.anoop.financetracker

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Environment check: can this device's WebView finish loading a tiny local page at all? */
@RunWith(AndroidJUnit4::class)
class WebViewEnvTest {
    @Test fun ENV_01_webview_can_load_a_local_page() {
        val ins = InstrumentationRegistry.getInstrumentation()
        val done = CountDownLatch(1)
        var result = ""
        ins.runOnMainSync {
            val w = WebView(ins.targetContext)
            w.settings.javaScriptEnabled = true
            w.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    view.evaluateJavascript("1+1") { r -> result = r ?: ""; done.countDown() }
                }
            }
            w.loadData("<p>hi</p>", "text/html", "utf-8")
        }
        assertTrue("WebView did not finish a local page in 60 s on this device", done.await(60, TimeUnit.SECONDS))
        assertTrue("JS result was $result", result == "2")
    }

    /** Loads the real study page in a plain WebView (optionally with the app's shim and a dummy bridge) and reports how far it gets. */
    private fun loadStudy(withShim: Boolean, attach: Int = 0): String {
        val ins = InstrumentationRegistry.getInstrumentation()
        val done = CountDownLatch(1)
        var result = "not finished"
        var progress = 0
        ins.runOnMainSync {
            val w = WebView(ins.targetContext)
            w.settings.javaScriptEnabled = true
            w.settings.domStorageEnabled = true
            if (withShim) {
                w.addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface fun isAndroid() = true
                    @android.webkit.JavascriptInterface fun saveFile(a: String, b: String, c: String) {}
                    @android.webkit.JavascriptInterface fun shareFile(a: String, b: String, c: String, d: String) {}
                    @android.webkit.JavascriptInterface fun shareText(a: String, b: String) {}
                    @android.webkit.JavascriptInterface fun print() {}
                    @android.webkit.JavascriptInterface fun speak(a: String, b: String, c: String, d: Double, e: Double, f: Double) {}
                    @android.webkit.JavascriptInterface fun stopSpeaking() {}
                }, "FTAndroid")
                androidx.webkit.WebViewCompat.addDocumentStartJavaScript(w, com.anoop.financetracker.study.StudyBridge.SHIM, setOf("https://anoopkarunakaranpillai-a11y.github.io"))
            }
            w.webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onProgressChanged(view: WebView, p: Int) { progress = p }
                override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                    android.util.Log.i("FTStudyEnv", "console ${m.messageLevel()}: ${m.message().take(200)} @${m.lineNumber()}"); return true
                }
            }
            w.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    view.evaluateJavascript("document.documentElement.className+'|'+document.body.className+'|'+(!!window.FTAndroid)") { r -> result = r ?: "null"; done.countDown() }
                }
            }
            w.loadUrl(com.anoop.financetracker.study.studyUrl("navw"))
            if (attach > 0) {
                if (attach == 2) w.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                scenario!!.onActivity { a -> a.addContentView(w, android.view.ViewGroup.LayoutParams(-1, -1)) }
            }
        }
        val ok = done.await(90, TimeUnit.SECONDS)
        android.util.Log.i("FTStudyEnv", "attach=$attach shim=$withShim finished=$ok progress=$progress result=$result")
        return "finished=$ok progress=$progress result=$result"
    }

    @Test fun ENV_02_study_page_loads_in_plain_webview() {
        val r = loadStudy(false)
        assertTrue(r, r.startsWith("finished=true") && r.contains("studyonly"))
    }

    @Test fun ENV_03_study_page_loads_with_app_shim() {
        val r = loadStudy(true)
        assertTrue(r, r.startsWith("finished=true") && r.contains("studyonly"))
    }

    private var scenario: androidx.test.core.app.ActivityScenario<MainActivity>? = null

    @Test fun ENV_04_study_page_loads_when_attached_to_a_window() {
        scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        val r = try { loadStudy(true, attach = 1) } finally { scenario?.close() }
        assertTrue(r, r.startsWith("finished=true"))
    }

    @Test fun ENV_05_study_page_loads_attached_with_software_rendering() {
        scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        val r = try { loadStudy(true, attach = 2) } finally { scenario?.close() }
        assertTrue(r, r.startsWith("finished=true"))
    }

    @Test fun ENV_06_app_study_webview_loads_when_attached_outside_compose() {
        val sc = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        val done = CountDownLatch(1)
        var result = "not finished"
        var progress = 0
        try {
            sc.onActivity { a ->
                val host = android.net.Uri.parse(BuildConfig.STUDY_BASE_URL).host!!
                lateinit var w: WebView
                w = com.anoop.financetracker.study.createStudyWebView(a, "navw", host, onProgress = { progress = it }, onFinished = {
                    w.evaluateJavascript("document.documentElement.className+'|'+document.body.className") { r -> result = r ?: "null"; done.countDown() }
                })
                a.addContentView(w, android.view.ViewGroup.LayoutParams(-1, -1))
            }
            val ok = done.await(90, TimeUnit.SECONDS)
            android.util.Log.i("FTStudyEnv", "ENV_06 finished=$ok progress=$progress result=$result")
            assertTrue("finished=$ok progress=$progress result=$result", ok && result.contains("studyonly"))
        } finally { sc.close() }
    }
}
