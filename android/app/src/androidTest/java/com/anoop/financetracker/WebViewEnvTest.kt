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
}
