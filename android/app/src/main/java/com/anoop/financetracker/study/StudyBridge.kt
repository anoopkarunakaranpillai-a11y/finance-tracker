package com.anoop.financetracker.study

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import com.anoop.financetracker.io.ShareFiles
import java.util.Locale

/**
 * Lets the study pages do things a plain WebView can't: save downloaded PDFs, open the share sheet,
 * print, and read text aloud with Android's text-to-speech (Android WebView has no working Web Speech voice).
 * Methods only act while the WebView shows the study website.
 */
class StudyBridge(private val ctx: Context, private val web: WebView, private val allowedHost: String) {
    @Volatile var currentHost: String = ""
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    private val pending = ArrayList<Array<Any>>()

    private fun allowed() = currentHost.equals(allowedHost, ignoreCase = true)

    @JavascriptInterface fun isAndroid(): Boolean = true

    @JavascriptInterface
    fun saveFile(name: String, mime: String, base64: String) {
        if (!allowed()) return
        val bytes = try { Base64.decode(base64, Base64.DEFAULT) } catch (e: Exception) { return }
        val safe = name.replace(Regex("[^A-Za-z0-9._ ()-]"), "_").ifBlank { "download" }
        main.post {
            val ok = try { saveToDownloads(safe, mime.ifBlank { guessMime(safe) }, bytes) } catch (e: Exception) { false }
            if (ok) Toast.makeText(ctx, "Saved to Downloads: $safe", Toast.LENGTH_LONG).show()
            else ShareFiles.share(ctx, safe, mime.ifBlank { guessMime(safe) }) { it.write(bytes) }
        }
    }

    @JavascriptInterface
    fun shareFile(name: String, mime: String, base64: String, title: String) {
        if (!allowed()) return
        val bytes = try { Base64.decode(base64, Base64.DEFAULT) } catch (e: Exception) { return }
        main.post { runCatching { ShareFiles.share(ctx, name.ifBlank { "file" }, mime.ifBlank { guessMime(name) }) { it.write(bytes) } } }
    }

    @JavascriptInterface
    fun shareText(title: String, text: String) {
        if (!allowed()) return
        main.post {
            val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, title).putExtra(Intent.EXTRA_TEXT, text.trim())
            ctx.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    @JavascriptInterface
    fun print() {
        if (!allowed()) return
        main.post {
            val pm = ctx.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return@post
            pm.print("Study worksheet", web.createPrintDocumentAdapter("Study worksheet"), PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
        }
    }

    @JavascriptInterface
    fun speak(id: String, text: String, lang: String, pitch: Double, rate: Double, volume: Double) {
        if (!allowed()) { done(id, true); return }
        main.post {
            ensureTts()
            if (!ttsReady) { pending += arrayOf(id, text, lang, pitch, rate, volume); return@post }
            doSpeak(id, text, lang, pitch, rate, volume)
        }
    }

    @JavascriptInterface
    fun stopSpeaking() { main.post { pending.clear(); tts?.stop() } }

    private fun ensureTts() {
        if (tts != null) return
        tts = TextToSpeech(ctx.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { utteranceId?.let { done(it, false) } }
                @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { utteranceId?.let { done(it, true) } }
                override fun onError(utteranceId: String?, errorCode: Int) { utteranceId?.let { done(it, true) } }
            })
            val q = ArrayList(pending); pending.clear()
            q.forEach { a -> if (ttsReady) doSpeak(a[0] as String, a[1] as String, a[2] as String, a[3] as Double, a[4] as Double, a[5] as Double) else done(a[0] as String, true) }
        }
    }

    private fun doSpeak(id: String, text: String, lang: String, pitch: Double, rate: Double, volume: Double) {
        val t = tts ?: return done(id, true)
        val loc = Locale.forLanguageTag(lang.ifBlank { "en-IN" })
        val r = t.setLanguage(loc)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) t.setLanguage(Locale("en", "IN"))
        t.setPitch(pitch.toFloat().coerceIn(0.5f, 2f))
        t.setSpeechRate(rate.toFloat().coerceIn(0.5f, 2f))
        val params = android.os.Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.toFloat().coerceIn(0f, 1f)) }
        if (t.speak(text, TextToSpeech.QUEUE_ADD, params, id) != TextToSpeech.SUCCESS) done(id, true)
    }

    private fun done(id: String, error: Boolean) {
        val safeId = id.replace(Regex("[^A-Za-z0-9_-]"), "")
        main.post { web.evaluateJavascript("window.__ftTtsDone&&window.__ftTtsDone('$safeId',${if (error) 1 else 0})", null) }
    }

    fun release() { main.post { tts?.shutdown(); tts = null; ttsReady = false } }

    private fun saveToDownloads(name: String, mime: String, bytes: ByteArray): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return true
        }
        // Android 8–9: no permission-free Downloads access, so the caller falls back to the share sheet.
        return false
    }

    private fun guessMime(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"; "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "json" -> "application/json"
        "csv" -> "text/csv"; "txt" -> "text/plain"; "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"; else -> "application/octet-stream"
    }


    companion object {
        /** Injected at document start so the page sees Android-backed download, share, print and speech. */
        const val SHIM = """(function(){
if(window.__ftShim)return;window.__ftShim=1;var B=window.FTAndroid;if(!B)return;
function b64(blob,cb){var r=new FileReader();r.onload=function(){var s=String(r.result);cb(s.slice(s.indexOf(',')+1))};r.readAsDataURL(blob)}
var oc=HTMLAnchorElement.prototype.click;
HTMLAnchorElement.prototype.click=function(){var h=this.href||'';if(this.hasAttribute('download')&&(h.indexOf('blob:')===0||h.indexOf('data:')===0)){var name=this.getAttribute('download')||'download';fetch(h).then(function(r){return r.blob()}).then(function(b){b64(b,function(d){B.saveFile(name,b.type||'',d)})});return}return oc.apply(this,arguments)};
navigator.canShare=function(d){return !!(d&&((d.files&&d.files.length)||d.url||d.text))};
navigator.share=function(d){return new Promise(function(res){if(d&&d.files&&d.files.length){var f=d.files[0];b64(f,function(x){B.shareFile(f.name||'file',f.type||'',x,d.title||'');res()})}else{B.shareText((d&&d.title)||'',((d&&d.text)||'')+' '+((d&&d.url)||''));res()}})};
window.print=function(){B.print()};
var cb={},n=0;
window.__ftTtsDone=function(id,err){var u=cb[id];delete cb[id];if(!u)return;try{if(err){u.onerror&&u.onerror({error:'synthesis-failed'})}else{u.onend&&u.onend({})}}catch(e){}};
window.SpeechSynthesisUtterance=function(t){this.text=t||'';this.lang='';this.pitch=1;this.rate=1;this.volume=1;this.voice=null;this.onend=null;this.onerror=null};
var voices=[{name:'Android voice English (India) female',lang:'en-IN',voiceURI:'android-en-IN',localService:true,'default':true},{name:'Android voice English (US) female',lang:'en-US',voiceURI:'android-en-US',localService:true,'default':false},{name:'Android voice Malayalam',lang:'ml-IN',voiceURI:'android-ml-IN',localService:true,'default':false}];
var ss={speaking:false,pending:false,paused:false,onvoiceschanged:null,getVoices:function(){return voices},
speak:function(u){var id='u'+(++n);cb[id]=u;var lang=(u.voice&&u.voice.lang)||u.lang||'en-IN';B.speak(id,String(u.text||''),lang,+u.pitch||1,+u.rate||1,u.volume==null?1:+u.volume)},
cancel:function(){var k=Object.keys(cb),old=cb;cb={};B.stopSpeaking();k.forEach(function(i){try{old[i].onerror&&old[i].onerror({error:'canceled'})}catch(e){}})},
pause:function(){},resume:function(){},addEventListener:function(){},removeEventListener:function(){}};
try{Object.defineProperty(window,'speechSynthesis',{value:ss,configurable:true,writable:true})}catch(e){window.speechSynthesis=ss}
})();"""
    }
}
