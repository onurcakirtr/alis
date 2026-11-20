package com.alis.app.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alis.app.R
import com.alis.app.ui.AlisRoot
import com.alis.app.ui.ServerIcon
import com.alis.app.ui.graph
import com.alis.app.ui.toast
import com.alis.app.ui.uriInfo
import com.alis.core.data.Feature
import com.alis.core.ui.ExpressiveCard
import com.alis.core.ui.IconBadge
import com.alis.core.ui.LiveDot
import com.alis.core.ui.SectionTitle
import com.alis.core.ui.focusRing

/**
 * Paylaş menüsü → Alış: seçilen Akış'a yükle ya da bir dosya isteme bağlantısına gönder.
 * Akış'ın paylaşım (/share/…) ve dosya isteme (/drop/…) bağlantıları da burada açılır.
 */
class ShareActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = intent
        val uris: List<Uri> = when (i.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        val link = if (i.action == Intent.ACTION_VIEW) i.data else null
        setContent {
            AlisRoot(this, tv = false) {
                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    ExpressiveCard(Modifier.widthIn(max = 520.dp)) {
                        when {
                            link != null && link.path?.startsWith("/drop/") == true -> DropLink(link.toString()) { finish() }
                            link != null && link.path?.startsWith("/share/") == true -> ShareLink(link) { finish() }
                            uris.isNotEmpty() -> SendFiles(uris) { finish() }
                            else -> { LaunchedEffect(Unit) { finish() } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SendFiles(uris: List<Uri>, done: () -> Unit) {
    val g = graph()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val servers by g.servers.servers.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { g.servers.scan() }
    var drop by remember { mutableStateOf("") }
    val queued = ctx.resources.getQuantityString(R.plurals.upload_queued, uris.size, uris.size)
    val off = stringResource(R.string.share_off)
    val names = remember(uris) { uris.map { uriInfo(ctx, it).first } }
    if (!g.settings.current.on(Feature.SHARE_SEND)) {
        Text(off)
        TextButton(onClick = done) { Text(stringResource(R.string.cancel)) }
        return
    }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.CloudUpload)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.share_title), style = MaterialTheme.typography.titleLarge)
                Text(names.take(3).joinToString(", ") + if (names.size > 3) " +${names.size - 3}" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
        SectionTitle(stringResource(R.string.share_to_akis))
        val akis = servers.filter { it.isAkis }
        if (akis.isEmpty()) Text(stringResource(R.string.share_no_server), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        akis.forEach { s ->
            ExpressiveCard(onClick = {
                val tr = g.transfers
                uris.forEach { u ->
                    runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    val (n, size, mime) = uriInfo(ctx, u)
                    tr.upload(s.id, u, n, size, mime, null)
                }
                toast(ctx, queued)
                done()
            }, color = MaterialTheme.colorScheme.surfaceContainerHigh, padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ServerIcon(s.icon, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(s.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    LiveDot(s.online, 9.dp)
                }
            }
        }
        SectionTitle(stringResource(R.string.share_to_drop))
        OutlinedTextField(drop, { drop = it.trim() }, label = { Text(stringResource(R.string.share_drop_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            uris.forEach { u ->
                val (n, size, mime) = uriInfo(ctx, u)
                g.transfers.upload("", u, n, size, mime, null, drop = drop.substringBefore('?').trimEnd('/'))
            }
            toast(ctx, queued)
            done()
        }, enabled = drop.contains("/drop/"), modifier = Modifier.focusRing(CircleShape)) { Text(stringResource(R.string.share_send)) }
        TextButton(onClick = done, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.cancel)) }
    }
}

@Composable
private fun DropLink(url: String, done: () -> Unit) {
    val g = graph()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { u ->
            val (n, size, mime) = uriInfo(ctx, u)
            g.transfers.upload("", u, n, size, mime, null, drop = url.substringBefore('?').trimEnd('/'))
        }
        if (uris.isNotEmpty()) toast(ctx, ctx.resources.getQuantityString(R.plurals.upload_queued, uris.size, uris.size))
        done()
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IconBadge(Icons.Rounded.Link)
        Text(stringResource(R.string.drop_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.drop_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.focusRing(CircleShape)) { Text(stringResource(R.string.drop_pick)) }
            TextButton(onClick = done) { Text(stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun ShareLink(link: Uri, done: () -> Unit) {
    val g = graph()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val queued = stringResource(R.string.download_queued)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IconBadge(Icons.Rounded.Download)
        Text(stringResource(R.string.sharelink_title), style = MaterialTheme.typography.titleLarge)
        Text(link.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val dl = link.toString().substringBefore('?').trimEnd('/').let { if (it.endsWith("/dl")) it else "$it/dl" }
                g.transfers.download("", dl, link.lastPathSegment ?: "Alis", -1)
                toast(ctx, queued)
                done()
            }, modifier = Modifier.focusRing(CircleShape)) {
                Icon(Icons.Rounded.Download, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.download))
            }
            FilledTonalButton(onClick = {
                runCatching {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, link), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                done()
            }, modifier = Modifier.focusRing(CircleShape)) { Text(stringResource(R.string.open_browser)) }
        }
    }
}
