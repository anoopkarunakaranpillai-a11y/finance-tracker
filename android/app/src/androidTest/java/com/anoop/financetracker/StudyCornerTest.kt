package com.anoop.financetracker

import android.content.Intent
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anoop.financetracker.study.StudyBridge
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Opens the Study corner screen on a device (through the app's own navigation) and checks, inside the page,
 * that the website loaded in study-only mode for the right child, with the Android bridge in place.
 */
@RunWith(AndroidJUnit4::class)
class StudyCornerTest {
    private val ins = InstrumentationRegistry.getInstrumentation()

    private fun js(w: WebView, script: String): String {
        var out = ""
        val latch = CountDownLatch(1)
        ins.runOnMainSync { w.evaluateJavascript(script) { r -> out = r ?: ""; latch.countDown() } }
        latch.await(10, TimeUnit.SECONDS)
        return out
    }

    private fun check(who: String, expectTab: String) {
        val app = ins.targetContext.applicationContext as FinanceApp
        runBlocking { app.container()!!.settings.update { it.copy(welcomeDone = true, lockEnabled = false) }; app.container()!!.repo.createDefaultAccounts("KWD") }
        StudyBridge.current = null
        val intent = Intent(ins.targetContext, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "study/$who")
        ActivityScenario.launch<MainActivity>(intent).use {
            val probe = "(function(){try{var t=[].slice.call(document.querySelectorAll('.tab')).filter(function(b){return !b.hidden&&getComputedStyle(b).display!=='none'}).map(function(b){return b.dataset.tab});" +
                "return [document.documentElement.classList.contains('studyonly'),t.indexOf('$expectTab')>=0,t.indexOf('pover')<0&&t.indexOf('dash')<0,!!window.FTAndroid,typeof window.speechSynthesis.speak].join(',')}catch(e){return 'err:'+e}})()"
            var r = "no WebView yet"
            val end = System.currentTimeMillis() + 60_000
            while (System.currentTimeMillis() < end) {
                val w = StudyBridge.current?.get()
                if (w != null) { r = js(w, probe); if (r.contains("true,true,true,true,function")) break }
                Thread.sleep(1_500)
            }
            assertTrue("Study page state: $r", r.contains("true,true,true,true,function"))
        }
    }

    @Test fun TC_EDU_02_navamika_pages_load_in_study_only_mode() = check("navw", "nhome")
    @Test fun TC_EDU_03_arya_pages_load_in_study_only_mode() = check("arya", "arya")
}
