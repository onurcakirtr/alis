package com.alis.app.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.alis.app.R
import com.alis.app.mobile.ActionIcon
import com.alis.app.mobile.TopBar
import com.alis.core.model.Server
import com.alis.core.ui.ExpressiveCard
import com.alis.core.ui.KeyValueRow
import com.alis.core.ui.focusRing
import com.alis.core.ui.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.text.DateFormat
import java.util.Date

/** ZIP içine bakma: listele, tek dosyayı indir / aç. */
@Composable
fun ZipScreen(server: Server, path: String, name: String) {
    val g = graph()
    val ctx = LocalContext.current
    val client = g.servers.client(server)
    val load = rememberLoad(server.id, path) { client.zipList(path) }
    val queued = stringResource(R.string.download_queued)
    Column(Modifier.fillMaxSize()) {
        TopBar(name, stringResource(R.string.zip_title)) {
            ActionIcon(Icons.Rounded.Download, stringResource(R.string.download)) {
                g.transfers.download(server.id, client.rawUrl(path), name, -1, "application/zip")
                toast(ctx, queued)
            }
        }
        LoadView(load, server) { entries ->
            LazyColumn(contentPadding = PaddingValues(8.dp)) {
                items(entries) { e ->
                    Surface(
                        onClick = {
                            if (!e.dir) {
                                g.transfers.download(server.id, client.zipGetUrl(path, e.name), e.name.substringAfterLast('/'), e.size)
                                toast(ctx, queued)
                            }
                        },
                        color = Color.Transparent, shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().focusRing(MaterialTheme.shapes.medium, 1.01f),
                    ) {
                        ItemRowContent(
                            e.name, if (e.dir) "" else formatBytes(e.size), null,
                            kindIcon(if (e.dir) com.alis.core.model.Kind.FOLDER else null), false,
                        )
                    }
                }
            }
        }
    }
}

/** Belge önizleme: metin / kod / Markdown / JSON düz yazı olarak, PDF sayfa sayfa. */
@Composable
fun DocScreen(server: Server, path: String, name: String) {
    val g = graph()
    val ctx = LocalContext.current
    val client = g.servers.client(server)
    val pdf = name.endsWith(".pdf", ignoreCase = true)
    Column(Modifier.fillMaxSize()) {
        TopBar(name, stringResource(R.string.doc_title)) {
            ActionIcon(Icons.Rounded.Download, stringResource(R.string.download)) {
                g.transfers.download(server.id, client.rawUrl(path), name, -1)
                toast(ctx, ctx.getString(R.string.download_queued))
            }
        }
        if (pdf) {
            val load = rememberLoad(path) {
                withContext(Dispatchers.IO) {
                    val f = File(ctx.cacheDir, "doc.pdf")
                    g.net.client.newCall(Request.Builder().url(client.rawUrl(path)).build()).execute().use { r ->
                        if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
                        f.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
                    }
                    val pages = ArrayList<Bitmap>()
                    PdfRenderer(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)).use { pr ->
                        for (i in 0 until minOf(pr.pageCount, 60)) {
                            pr.openPage(i).use { p ->
                                val w = 1200
                                val h = (w.toFloat() * p.height / p.width).toInt()
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                pages += bmp
                            }
                        }
                    }
                    pages
                }
            }
            LoadView(load, server) { pages ->
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(pages) { b ->
                        Image(b.asImageBitmap(), null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().focusRing(MaterialTheme.shapes.small, 1f))
                    }
                }
            }
        } else {
            val load = rememberLoad(path) {
                withContext(Dispatchers.IO) {
                    g.net.client.newCall(Request.Builder().url(client.rawUrl(path)).header("Range", "bytes=0-1048575").build()).execute().use { r ->
                        if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
                        r.body!!.bytes().toString(Charsets.UTF_8)
                    }
                }
            }
            LoadView(load, server) { text ->
                SelectionContainer {
                    Text(
                        text,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(14.dp)
                            .focusRing(MaterialTheme.shapes.small, 1f),
                    )
                }
            }
        }
    }
}

/** Özellikler: EXIF, parçalar, boyut, tarih (Akış /api/info). */
@Composable
fun InfoScreen(server: Server, id: String?, path: String?, title: String) {
    val g = graph()
    val client = g.servers.client(server)
    val load = rememberLoad(id, path) { client.info(id, path) }
    val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    Column(Modifier.fillMaxSize()) {
        TopBar(title, stringResource(R.string.properties))
        LoadView(load, server) { rows ->
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                ExpressiveCard {
                    rows.forEach { (k, v) ->
                        val value = when (k) {
                            "size" -> formatBytes(v.toLongOrNull() ?: 0)
                            "m" -> df.format(Date((v.toLongOrNull() ?: 0) * 1000))
                            "d" -> com.alis.core.ui.formatDuration(v.toLongOrNull() ?: 0)
                            else -> v
                        }
                        KeyValueRow(infoLabel(k), value)
                    }
                }
            }
        }
    }
}

@Composable
private fun infoLabel(k: String): String = when (k) {
    "name" -> stringResource(R.string.info_name)
    "size" -> stringResource(R.string.info_size)
    "mime" -> stringResource(R.string.info_type)
    "m" -> stringResource(R.string.info_date)
    "path" -> stringResource(R.string.info_path)
    "w" -> stringResource(R.string.info_width)
    "h" -> stringResource(R.string.info_height)
    "d" -> stringResource(R.string.info_duration)
    "br" -> stringResource(R.string.info_bitrate)
    "count" -> stringResource(R.string.info_count)
    else -> k.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}
