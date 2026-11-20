package com.alis.app.mobile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alis.app.R
import com.alis.app.ui.LoadView
import com.alis.app.ui.MediaCard
import com.alis.app.ui.Open
import com.alis.app.ui.Route
import com.alis.app.ui.ServerIcon
import com.alis.app.ui.graph
import com.alis.app.ui.nav
import com.alis.app.ui.rememberLoad
import com.alis.app.ui.rememberServer
import com.alis.app.ui.uploadUris
import com.alis.core.data.Feature
import com.alis.core.model.Capabilities
import com.alis.core.model.Category
import com.alis.core.model.Item
import com.alis.core.model.Server
import com.alis.core.ui.ExpressiveCard
import com.alis.core.ui.IconBadge
import com.alis.core.ui.MessageBox
import com.alis.core.ui.SectionTitle
import com.alis.core.ui.focusRing

/** Bir sunucunun ana sayfası: Akış'ta raflar ve araçlar, genel DLNA'da kök klasörler. */
@Composable
fun ServerHomeScreen(serverId: String, embedded: Boolean = false) {
    val server = rememberServer(serverId)
    if (server == null) {
        MessageBox(Icons.Rounded.FolderOpen, stringResource(R.string.server_gone))
        return
    }
    val g = graph()
    val nav = nav()
    val caps = rememberLoad(server.id, server.token) { g.servers.capabilities(server, refresh = true) }
    Column(Modifier.fillMaxSize()) {
        TopBar(server.name, server.base.substringAfter("://"), back = !embedded) {
            ActionIcon(Icons.Rounded.Search, stringResource(R.string.search)) { nav.push(Route.Search(server.id)) }
            ActionIcon(Icons.Rounded.Refresh, stringResource(R.string.refresh)) { caps.reload() }
        }
        LoadView(caps, server) { c ->
            if (server.isAkis) AkisHome(server, c) else DlnaHome(server)
        }
    }
}

private data class Tool(val icon: ImageVector, val label: Int, val onClick: () -> Unit)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AkisHome(server: Server, caps: Capabilities) {
    val g = graph()
    val nav = nav()
    val ctx = LocalContext.current
    val s = g.settings.current
    val home = rememberLoad(server.id) { g.library.home(server) ?: com.alis.core.model.Home() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> uploadUris(ctx, server, uris, null) }

    val tools = buildList {
        if (caps.files && s.on(Feature.FILE_MANAGER)) add(Tool(Icons.Rounded.FolderOpen, R.string.tool_files) { nav.push(Route.Files(server.id, "")) })
        if (caps.upload && s.on(Feature.SHARE_SEND)) add(Tool(Icons.Rounded.Upload, R.string.tool_upload) { pick.launch(arrayOf("*/*")) })
        if (caps.status && s.on(Feature.PHONE_STATUS)) add(Tool(Icons.Rounded.BatteryStd, R.string.tool_status) { nav.push(Route.Status(server.id)) })
        if (caps.apps && s.on(Feature.APPS)) add(Tool(Icons.Rounded.Apps, R.string.tool_apps) { nav.push(Route.Apps(server.id)) })
        if ((caps.camera || caps.screen || caps.mic) && s.on(Feature.LIVE)) add(Tool(Icons.Rounded.Videocam, R.string.tool_live) { nav.push(Route.Live(server.id)) })
        if (caps.notifs && s.on(Feature.NOTIF_MIRROR)) add(Tool(Icons.Rounded.Notifications, R.string.tool_notifs) { nav.push(Route.Notifs(server.id)) })
        if (caps.inbox && s.on(Feature.INBOX)) add(Tool(Icons.Rounded.Inbox, R.string.tool_inbox) { nav.push(Route.Inbox(server.id)) })
        if (caps.admin && !server.remote && s.on(Feature.ADMIN)) add(Tool(Icons.Rounded.AdminPanelSettings, R.string.tool_admin) { nav.push(Route.Admin(server.id)) })
    }

    LoadView(home, server) { h ->
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                ServerHeader(server, caps)
            }
            if (tools.isNotEmpty()) item {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tools.forEach { t -> ToolChip(t.icon, stringResource(t.label), t.onClick) }
                }
            }
            val marks = s.bookmarks[server.id].orEmpty()
            if (marks.isNotEmpty() && caps.files) item {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    marks.forEach { m -> ToolChip(Icons.Rounded.Bookmark, m.trimEnd('/').substringAfterLast('/')) { nav.push(Route.Files(server.id, m)) } }
                }
            }
            if (h.cont.isNotEmpty()) item { Shelf(stringResource(R.string.shelf_continue), Icons.Rounded.History, server, h.cont, null) }
            if (h.recent.isNotEmpty()) item { Shelf(stringResource(R.string.shelf_recent), Icons.Rounded.NewReleases, server, h.recent, "r") }
            item { SectionTitle(stringResource(R.string.categories), Modifier.padding(horizontal = 16.dp)) }
            item {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    h.cats.forEach { c -> CategoryCard(c) { nav.push(Route.Browse(server.id, c.id, c.title)) } }
                }
            }
        }
    }
}

@Composable
private fun ServerHeader(server: Server, caps: Capabilities) {
    val nav = nav()
    ExpressiveCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ServerIcon(server.icon, 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(caps.name.ifEmpty { server.name }, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(if (server.token != null) R.string.home_paired else if (server.remote) R.string.home_remote else R.string.home_not_paired),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (server.token == null && !server.remote) {
            Spacer(Modifier.size(10.dp))
            Text(stringResource(R.string.home_pair_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(6.dp))
            FilledTonalButton(onClick = { nav.push(Route.Pair(server.id)) }, modifier = Modifier.focusRing(MaterialTheme.shapes.extraLarge)) {
                Icon(Icons.Rounded.Link, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.pair_action))
            }
        }
    }
}

@Composable
fun ToolChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.focusRing(MaterialTheme.shapes.medium),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Shelf(title: String, icon: ImageVector, server: Server, items: List<Item>, folderId: String?) {
    val nav = nav()
    val ctx = LocalContext.current
    Column {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            SectionTitle(title, Modifier.padding(0.dp))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.id }) { it ->
                Surface(
                    onClick = { Open.item(ctx, nav, server, it, items, folderId) },
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.width(if (it.kind == com.alis.core.model.Kind.VIDEO) 220.dp else 140.dp).focusRing(MaterialTheme.shapes.medium),
                ) { MediaCard(it, false) }
            }
        }
    }
}

@Composable
private fun CategoryCard(c: Category, onClick: () -> Unit) {
    val icon = when (c.id) {
        "v" -> Icons.Rounded.Movie
        "a" -> Icons.Rounded.Audiotrack
        "i" -> Icons.Rounded.Image
        "f", "pd:" -> Icons.Rounded.FolderOpen
        "h:u", "h:c" -> Icons.Rounded.History
        else -> Icons.Rounded.Folder
    }
    ExpressiveCard(Modifier.width(160.dp), onClick = onClick, padding = 14.dp) {
        IconBadge(icon, size = 44.dp)
        Spacer(Modifier.size(10.dp))
        Text(c.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (c.count >= 0) Text(c.count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Genel DLNA sunucusu: kök klasörü gezgin olarak açar. */
@Composable
private fun DlnaHome(server: Server) {
    BrowseContent(server, "0", Modifier.fillMaxWidth(), header = false)
}
