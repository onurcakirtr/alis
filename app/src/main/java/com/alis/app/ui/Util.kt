package com.alis.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import com.alis.app.R
import com.alis.core.data.graph
import com.alis.core.model.Server

/** Paylaşılan / seçilen bir dosyanın adı, boyutu ve türü. */
fun uriInfo(ctx: Context, uri: Uri): Triple<String, Long, String?> {
    var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    var size = -1L
    runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
    }
    if (size < 0) runCatching { ctx.contentResolver.openFileDescriptor(uri, "r")?.use { size = it.statSize } }
    return Triple(name, size, ctx.contentResolver.getType(uri))
}

/** Seçilen dosyaları Akış'a yükleme sırasına koyar. [dir] boşsa Akış'ın yükleme klasörü. */
fun uploadUris(ctx: Context, server: Server, uris: List<Uri>, dir: String?) {
    if (uris.isEmpty()) return
    val tr = ctx.graph.transfers
    for (u in uris) {
        runCatching { ctx.contentResolver.takePersistableUriPermission(u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val (name, size, mime) = uriInfo(ctx, u)
        tr.upload(server.id, u, name, size, mime, dir)
    }
    Toast.makeText(ctx, ctx.resources.getQuantityString(R.plurals.upload_queued, uris.size, uris.size), Toast.LENGTH_SHORT).show()
}

fun toast(ctx: Context, text: String) = Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
