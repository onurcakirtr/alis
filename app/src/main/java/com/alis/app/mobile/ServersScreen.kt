package com.alis.app.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alis.app.R
import com.alis.app.ui.Route
import com.alis.app.ui.ServerIcon
import com.alis.app.ui.graph
import com.alis.app.ui.nav
import com.alis.core.model.Server
import com.alis.core.ui.ExpressiveCard
import com.alis.core.ui.LiveDot
import com.alis.core.ui.MessageBox
import com.alis.core.ui.SectionTitle

/** Sunucular: keşfedilen ve kayıtlı Akış / DLNA sunucuları. Tablette sağda seçili sunucunun ana sayfası. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(wide: Boolean) {
    val g = graph()
    val nav = nav()
    val servers by g.servers.servers.collectAsStateWithLifecycle()
    val scanning by g.servers.scanning.collectAsStateWithLifecycle()
    // Ekran açıkken sunucular 30 sn'de bir yeniden aranır (kapanıp açılan Akış kendiliğinden görünür)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            g.servers.scan()
        }
    }
    var selected by remember { mutableStateOf<String?>(null) }
    val sel = selected ?: g.settings.current.lastServer.takeIf { id -> servers.any { it.id == id } } ?: servers.firstOrNull { it.online }?.id

    val list: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            TopBar(stringResource(R.string.app_name), stringResource(R.string.servers_sub), back = false) {
                ActionIcon(Icons.Rounded.Refresh, stringResource(R.string.refresh)) { g.servers.scan() }
                ActionIcon(Icons.Rounded.Add, stringResource(R.string.add_server)) { nav.push(Route.AddServer) }
            }
            if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            if (servers.isEmpty() && !scanning) {
                MessageBox(Icons.Rounded.Dns, stringResource(R.string.servers_empty), stringResource(R.string.servers_empty_sub), stringResource(R.string.add_server)) {
                    nav.push(Route.AddServer)
                }
            } else {
                                androidx.compose.material3.pulltorefresh.PullToRefreshBox(isRefreshing = scanning, onRefresh = { g.servers.scan() }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                    val akis = servers.filter { it.isAkis }
                    val other = servers.filter { !it.isAkis }
                    if (akis.isNotEmpty()) item { SectionTitle(stringResource(R.string.servers_akis)) }
                    items(akis, key = { it.id }) { s ->
                        ServerCard(s, wide && s.id == sel) {
                            g.settings.setLastServer(s.id)
                            if (wide) selected = s.id else nav.push(Route.Home(s.id))
                        }
                    }
                    if (other.isNotEmpty()) item { SectionTitle(stringResource(R.string.servers_dlna)) }
                    items(other, key = { it.id }) { s ->
                        ServerCard(s, wide && s.id == sel) {
                            g.settings.setLastServer(s.id)
                            if (wide) selected = s.id else nav.push(Route.Home(s.id))
                        }
                    }
                }
                }
            }
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            list(Modifier.width(360.dp).fillMaxHeight())
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (sel != null) ServerHomeScreen(sel, embedded = true)
                else MessageBox(Icons.Rounded.Dns, stringResource(R.string.servers_pick))
            }
        }
    } else {
        list(Modifier.fillMaxSize())
    }
}

@Composable
fun ServerCard(s: Server, selected: Boolean, onClick: () -> Unit) {
    val g = graph()
    val nav = nav()
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    ExpressiveCard(onClick = onClick, color = if (selected) cs.secondaryContainer else cs.surfaceContainer, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ServerIcon(s.icon, 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDot(s.online, 9.dp, color = cs.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(s.base.substringAfter("://"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
                if (!s.online && s.isAkis) Text(stringResource(R.string.server_offline_hint), style = MaterialTheme.typography.bodySmall, color = cs.error)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Badge(if (s.isAkis) stringResource(R.string.badge_akis) else s.model ?: "DLNA")
                    if (s.token != null) Badge(stringResource(R.string.badge_paired), Icons.Rounded.Link)
                    if (s.remote) Badge(stringResource(R.string.badge_remote), Icons.Rounded.Public)
                    if (!s.online) Badge(stringResource(R.string.badge_offline))
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(menu, { menu = false }) {
                    if (s.isAkis && !s.remote && s.token == null) {
                        DropdownMenuItem({ Text(stringResource(R.string.pair_action)) }, { menu = false; nav.push(Route.Pair(s.id)) })
                    }
                    if (s.isAkis && s.token != null) {
                        DropdownMenuItem({ Text(stringResource(R.string.unpair)) }, { menu = false; g.servers.setToken(s.id, null) })
                    }
                    DropdownMenuItem({ Text(stringResource(R.string.rename)) }, { menu = false; rename = true })
                    if (s.saved) DropdownMenuItem({ Text(stringResource(R.string.remove)) }, { menu = false; remove = true })
                    else DropdownMenuItem({ Text(stringResource(R.string.save)) }, { menu = false; g.servers.save(s) })
                }
            }
        }
    }
    if (rename) {
        var name by remember { mutableStateOf(s.name) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text(stringResource(R.string.rename)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { g.servers.rename(s.id, name); rename = false }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (remove) {
        AlertDialog(
            onDismissRequest = { remove = false },
            title = { Text(stringResource(R.string.remove)) },
            text = { Text(stringResource(R.string.remove_server_text, s.name)) },
            confirmButton = { TextButton(onClick = { g.servers.remove(s.id); remove = false }) { Text(stringResource(R.string.remove)) } },
            dismissButton = { TextButton(onClick = { remove = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun Badge(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    androidx.compose.material3.Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Suppress("unused")
@Composable
private fun Chip(text: String, onClick: () -> Unit) = AssistChip(onClick = onClick, label = { Text(text) })
