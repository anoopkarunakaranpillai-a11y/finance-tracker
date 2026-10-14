package com.anoop.financetracker

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opens the Study corner on a device and checks, inside the page, that the website loaded in study-only mode with the Android bridge. */
@RunWith(AndroidJUnit4::class)
class StudyCornerTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun findWebView(v: View): WebView? {
        if (v is WebView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findWebView(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun tree(v: View, d: Int): String = v.javaClass.simpleName + (if (v is ViewGroup && d < 8) (0 until v.childCount).joinToString(",", "[", "]") { tree(v.getChildAt(it), d + 1) } else "")

    private fun js(script: String): String {
        var out = ""
        val latch = CountDownLatch(1)
        rule.runOnUiThread {
            val w = findWebView(rule.activity.window.decorView)
            if (w == null) { out = "no WebView; views=" + tree(rule.activity.window.decorView, 0); latch.countDown() } else w.evaluateJavascript(script) { r -> out = r ?: ""; latch.countDown() }
        }
        latch.await(10, TimeUnit.SECONDS)
        return out
    }

    private fun check(who: String, expectTab: String) {
        val app = rule.activity.application as FinanceApp
        runBlocking { app.container()!!.settings.update { it.copy(welcomeDone = true) }; app.container()!!.repo.createDefaultAccounts("KWD") }
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("open-study").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("open-study").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("study-$who").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("study-$who").performClick()
        val probe = "(function(){try{var t=[].slice.call(document.querySelectorAll('.tab')).filter(function(b){return !b.hidden&&getComputedStyle(b).display!=='none'}).map(function(b){return b.dataset.tab});" +
            "return [document.documentElement.classList.contains('studyonly'),t.indexOf('$expectTab')>=0,t.indexOf('pover')<0&&t.indexOf('dash')<0,!!window.FTAndroid,typeof window.speechSynthesis.speak].join(',')}catch(e){return 'err:'+e}})()"
        var r = ""
        val end = System.currentTimeMillis() + 45_000
        while (System.currentTimeMillis() < end) {
            r = js(probe)
            if (r.contains("true,true,true,true,function")) break
            Thread.sleep(1_500)
        }
        val ui = runCatching { rule.onRoot(useUnmergedTree = true).printToString(maxDepth = 12) }.getOrElse { "semantics: $it" }
        assertTrue("Study page state: $r\n$ui", r.contains("true,true,true,true,function"))
    }

    @Test fun TC_EDU_02_navamika_pages_load_in_study_only_mode() = check("navw", "nhome")
    @Test fun TC_EDU_03_arya_pages_load_in_study_only_mode() = check("arya", "arya")
}
