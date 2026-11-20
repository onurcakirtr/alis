package com.alis.app.ui.tools

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import com.alis.app.R
import com.alis.app.mobile.ActionIcon
import com.alis.app.mobile.TopBar
import com.alis.app.ui.ItemRowContent
import com.alis.app.ui.LoadView
import com.alis.app.ui.Open
import com.alis.app.ui.graph
import com.alis.app.ui.rememberLoad
import com.alis.app.ui.toast
import com.alis.core.model.Item
import com.alis.core.model.Kind
import com.alis.core.model.Server
import com.alis.core.player.Players
import com.alis.core.ui.ExpressiveCard
import com.alis.core.ui.LiveDot
import com.alis.core.ui.MessageBox
import com.alis.core.ui.SectionTitle
import com.alis.core.ui.StatPill
import com.alis.core.ui.focusRing
import com.alis.core.ui.formatBytes
import com.alis.core.ui.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

// ---------------- Telefon durumu ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusScreen(server: Server) {
    val g = graph()
    val client = g.servers.client(server)
    val load = rememberLoad(server.id) { client.status() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            load.reload()
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.tool_status), server.name) { ActionIcon(Icons.Rounded.Refresh, stringResource(R.string.refresh)) { load.reload() } }
        LoadView(load, server) { st ->
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(st.device + " · Android " + st.android, style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (st.battery >= 0) StatPill(if (st.charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd, "${st.battery}%", stringResource(if (st.charging) R.string.st_charging else R.string.st_battery), Modifier.width(150.dp))
                    st.temp?.let { StatPill(Icons.Rounded.Thermostat, "%.1f °C".format(it), stringResource(R.string.st_temp), Modifier.width(150.dp)) }
                    if (st.ramTotal > 0) StatPill(Icons.Rounded.Memory, formatBytes(st.ramTotal - st.ramFree), stringResource(R.string.st_ram, formatBytes(st.ramTotal)), Modifier.width(150.dp))
                    st.rssi?.let { StatPill(Icons.Rounded.Wifi, "$it dBm", stringResource(R.string.st_wifi, st.linkSpeed ?: 0), Modifier.width(150.dp)) }
                    if (st.uptimeMs > 0) StatPill(Icons.Rounded.Timer, formatDuration(st.uptimeMs), stringResource(R.string.st_uptime), Modifier.width(150.dp))
                }
                SectionTitle(stringResource(R.string.st_storage))
                st.storage.forEach { (name, total, free) ->
                    ExpressiveCard(padding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.SdStorage, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.st_free, formatBytes(free), formatBytes(total)), style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { if (total > 0) (total - free).toFloat() / total else 0f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

// ---------------- Uygulamalar ----------------

@Composable
fun AppsScreen(server: Server) {
    val g = graph()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = g.servers.client(server)
    val load = rememberLoad(server.id) { client.apps() }
    var q by remember { mutableStateOf("") }
    var system by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf<String?>(null) }
    val queued = stringResource(R.string.download_queued)
    val splitMsg = stringResource(R.string.apps_split)
    val failMsg = stringResource(R.string.apps_failed)

    fun install(pkg: String) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        installing = pkg
        scope.launch {
            val f = runCatching {
                withContext(Dispatchers.IO) {
                    val dir = File(ctx.cacheDir, "apk").apply { mkdirs() }
                    val out = File(dir, "$pkg.apk")
                    g.net.client.newBuilder().readTimeout(120, TimeUnit.SECONDS).build()
                        .newCall(Request.Builder().url(client.apkUrl(pkg)).build()).execute().use { r ->
                            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
                            out.outputStream().use { o -> r.body!!.byteStream().copyTo(o) }
                        }
                    out
                }
            }.getOrNull()
            installing = null
            if (f == null) return@launch toast(ctx, failMsg)
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.tool_apps), server.name)
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(q, { q = it }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            FilterChip(system, { system = !system }, label = { Text(stringResource(R.string.apps_system)) }, modifier = Modifier.focusRing(MaterialTheme.shapes.small))
        }
        LoadView(load, server) { apps ->
            val list = apps.filter { (system || !it.system) && (q.isBlank() || it.name.contains(q, true) || it.pkg.contains(q, true)) }
                .sortedBy { it.name.lowercase() }
            LazyColumn(contentPadding = PaddingValues(8.dp)) {
                items(list, key = { it.pkg }) { a ->
                    Surface(color = Color.Transparent, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                        ItemRowContent(a.name, "${a.version} · ${formatBytes(a.size)}" + if (a.split) " · split" else "", client.appIconUrl(a.pkg), Icons.Rounded.Android, false) {
                            if (installing == a.pkg) CircularProgressIndicator(Modifier.size(24.dp))
                            else {
                                ActionIcon(Icons.Rounded.Download, stringResource(R.string.download)) {
                                    g.transfers.download(server.id, client.apkUrl(a.pkg), a.name + (if (a.split) ".apks" else ".apk"), a.size)
                                    toast(ctx, queued)
                                }
                                ActionIcon(Icons.Rounded.InstallMobile, stringResource(R.string.apps_install)) {
                                    if (a.split) toast(ctx, splitMsg) else install(a.pkg)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Bildirimler ----------------

@Composable
fun NotifsScreen(server: Server) {
    val g = graph()
    val client = g.servers.client(server)
    val load = rememberLoad(server.id) { client.notifs().second }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            load.reload()
        }
    }
    val df = DateFormat.getTimeInstance(DateFormat.SHORT)
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.tool_notifs), server.name)
        LoadView(load, server) { list ->
            if (list.isEmpty()) return@LoadView MessageBox(Icons.Rounded.Notifications, stringResource(R.string.notifs_empty))
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.key + it.time }) { n ->
                    ExpressiveCard(padding = 14.dp, color = if (n.gone) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).clip(MaterialTheme.shapes.small)) {
                                com.alis.app.ui.Thumb(client.notifIconUrl(n.pkg), Icons.Rounded.Notifications, Modifier.fillMaxSize(), 20.dp, crop = false)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(n.app, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(n.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                n.text?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                            Text(df.format(Date(n.time)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Gelenler ----------------

@Composable
fun InboxScreen(server: Server) {
    val g = graph()
    val ctx = LocalContext.current
    val client = g.servers.client(server)
    val load = rememberLoad(server.id) { client.inbox() }
    val queued = stringResource(R.string.download_queued)
    val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.tool_inbox), server.name) { ActionIcon(Icons.Rounded.Refresh, stringResource(R.string.refresh)) { load.reload() } }
        LoadView(load, server) { list ->
            if (list.isEmpty()) return@LoadView MessageBox(Icons.Rounded.Inbox, stringResource(R.string.inbox_empty))
            LazyColumn(contentPadding = PaddingValues(8.dp)) {
                items(list, key = { it.id }) { x ->
                    Surface(
                        onClick = { Open.external(ctx, client.inboxUrl(x.id), x.mime) },
                        color = Color.Transparent, shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().focusRing(MaterialTheme.shapes.medium, 1.01f),
                    ) {
                        ItemRowContent(x.name, "${formatBytes(x.size)} · ${x.from} · ${df.format(Date(x.time))}", null, Icons.Rounded.Inbox, false) {
                            ActionIcon(Icons.Rounded.Download, stringResource(R.string.download)) {
                                g.transfers.download(server.id, client.inboxUrl(x.id), x.name, x.size, x.mime)
                                toast(ctx, queued)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Canlı ----------------

/** Canlı kamera / ekran (MJPEG), mikrofon (WAV) ve HD yayınlar (MPEG-TS → oynatıcı). */
@Composable
fun LiveScreen(server: Server) {
    val g = graph()
    val ctx = LocalContext.current
    val client = g.servers.client(server)
    val caps = g.servers.cachedCaps(server.id)
    var mode by remember { mutableStateOf(if (caps?.camera == true) "cam" else if (caps?.screen == true) "screen" else "mic") }
    var front by remember { mutableStateOf(caps?.camFront ?: false) }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.tool_live), server.name)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (caps?.camera == true) FilterChip(mode == "cam", { mode = "cam" }, label = { Text(stringResource(R.string.live_camera)) }, leadingIcon = { Icon(Icons.Rounded.CameraAlt, null) }, modifier = Modifier.focusRing(MaterialTheme.shapes.small))
            if (caps?.screen == true) FilterChip(mode == "screen", { mode = "screen" }, label = { Text(stringResource(R.string.live_screen)) }, leadingIcon = { Icon(Icons.Rounded.ScreenShare, null) }, modifier = Modifier.focusRing(MaterialTheme.shapes.small))
            if (caps?.mic == true) FilterChip(mode == "mic", { mode = "mic" }, label = { Text(stringResource(R.string.live_mic)) }, leadingIcon = { Icon(Icons.Rounded.Mic, null) }, modifier = Modifier.focusRing(MaterialTheme.shapes.small))
        }
        Spacer(Modifier.height(10.dp))
        when (mode) {
            "cam", "screen" -> {
                Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp).clip(MaterialTheme.shapes.large).background(Color.Black), contentAlignment = Alignment.Center) {
                    val url = if (mode == "cam") client.cameraUrl(front, caps?.camHeight ?: 720) else client.screenUrl()
                    MjpegView(url, Modifier.fillMaxSize())
                    Row(Modifier.align(Alignment.TopStart).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        LiveDot(true, 10.dp, Color(0xFFD71921))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.live_now), color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (mode == "cam") FilledTonalButton(onClick = { front = !front }, modifier = Modifier.focusRing(CircleShape)) {
                        Text(stringResource(if (front) R.string.live_back else R.string.live_front))
                    }
                    FilledTonalButton(onClick = {
                        // HD: H.264 + AAC MPEG-TS, kendi oynatıcımızda
                        val id = if (mode == "cam") "live:camhd" else "live:screenhd"
                        val item = Item(id, ctx.getString(if (mode == "cam") R.string.live_camera else R.string.live_screen), Kind.VIDEO, server.id, url = client.mediaUrl(id, "ts"), mime = "video/mp2t", ext = "ts")
                        Open.video(ctx, server, item, listOf(item), null)
                    }, modifier = Modifier.focusRing(CircleShape)) { Text(stringResource(R.string.live_hd)) }
                }
            }
            else -> MicPlayer(client.micUrl())
        }
    }
}

/** Mikrofon: 16 kHz WAV akışını ExoPlayer ile dinler. */
@Composable
private fun MicPlayer(url: String) {
    val ctx = LocalContext.current
    val g = graph()
    val player = remember { Players.exo(ctx, g.net.client, audioOnly = true) }
    var playing by remember { mutableStateOf(false) }
    DisposableEffect(url) {
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
        playing = true
        onDispose { player.release() }
    }
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        com.alis.core.ui.IconBadge(Icons.Rounded.Mic, size = 120.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(playing, 12.dp, Color(0xFFD71921))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.live_listening), style = MaterialTheme.typography.titleMedium)
        }
        FilledTonalButton(onClick = { if (playing) player.pause() else player.play(); playing = !playing }, modifier = Modifier.focusRing(CircleShape)) {
            Text(stringResource(if (playing) R.string.pause else R.string.play))
        }
    }
}

/** multipart/x-mixed-replace MJPEG: JPEG başlangıç / bitiş işaretlerinden kareleri ayırır. */
@Composable
fun MjpegView(url: String, modifier: Modifier) {
    val g = graph()
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(url) {
        error = false
        withContext(Dispatchers.IO) {
            val client = g.net.client.newBuilder().readTimeout(15, TimeUnit.SECONDS).build()
            while (isActive) {
                try {
                    client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                        if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
                        val input = r.body!!.byteStream().buffered(64 * 1024)
                        val buf = ByteArrayOutputStream(256 * 1024)
                        var prev = -1
                        var inJpeg = false
                        while (isActive) {
                            val b = input.read()
                            if (b < 0) break
                            if (!inJpeg) {
                                if (prev == 0xFF && b == 0xD8) {
                                    inJpeg = true
                                    buf.reset()
                                    buf.write(0xFF)
                                    buf.write(0xD8)
                                }
                            } else {
                                buf.write(b)
                                if (prev == 0xFF && b == 0xD9) {
                                    inJpeg = false
                                    val bytes = buf.toByteArray()
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { frame = it }
                                }
                            }
                            prev = b
                        }
                    }
                } catch (e: Exception) {
                    if (!isActive) break
                    error = true
                    delay(2000)
                }
            }
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val f = frame
        if (f != null) Image(f.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().aspectRatio(f.width.toFloat() / f.height, matchHeightConstraintsFirst = true))
        else if (error) Text(stringResource(R.string.live_error), color = Color.White)
        else CircularProgressIndicator()
    }
}
