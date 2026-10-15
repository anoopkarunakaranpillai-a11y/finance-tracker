package com.anoop.financetracker

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Screenshots of the Study corner pages, taken without a Compose test rule (its test clock stops WebView pages finishing). */
@RunWith(AndroidJUnit4::class)
class StudyScreenshots {
    private fun app() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FinanceApp

    /** The study pages are captured outside the Compose test clock, which stops WebView pages from finishing. */
    @Test fun tourStudy() {
        runBlocking { app().container()!!.settings.update { it.copy(welcomeDone = true, lockEnabled = false) } }
        val ins = InstrumentationRegistry.getInstrumentation()
        for ((who, name) in listOf("navw" to "11-study-navamika", "arya" to "12-study-arya")) {
            val intent = android.content.Intent(ins.targetContext, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "study/$who")
            androidx.test.core.app.ActivityScenario.launch<MainActivity>(intent).use {
                Thread.sleep(15_000)
                val bmp = ins.uiAutomation.takeScreenshot()
                val dir = File(app().filesDir, "shots").apply { mkdirs() }
                File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
