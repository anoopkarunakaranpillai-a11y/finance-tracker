package com.anoop.financetracker.io

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Writes a file into the app's private cache and opens Android's share sheet for it. */
object ShareFiles {
    fun share(ctx: Context, name: String, mime: String, write: (java.io.OutputStream) -> Unit) {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        // Old exports are removed so private data does not pile up in the cache.
        dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 60 * 60 * 1000) it.delete() }
        val f = File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        f.outputStream().use(write)
        val uri: Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Share $name").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun writeTo(ctx: Context, uri: Uri, write: (java.io.OutputStream) -> Unit): Boolean = try {
        ctx.contentResolver.openOutputStream(uri, "wt")?.use(write) != null
    } catch (e: Exception) { false }
}
