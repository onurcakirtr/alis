package com.alis.app.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alis.app.R
import com.alis.app.mobile.sortItems
import com.alis.app.ui.LoadView
import com.alis.app.ui.Open
import com.alis.app.ui.Route
import com.alis.app.ui.ServerIcon
import com.alis.app.ui.Thumb
import com.alis.app.ui.graph
import com.alis.app.ui.itemMeta
import com.alis.app.ui.kindIcon
import com.alis.app.ui.nav
import com.alis.app.ui.rememberLoad
import com.alis.app.ui.rememberServer
import com.alis.core.data.Feature
import com.alis.core.model.AccessException
import com.alis.core.model.Capabilities
import com.alis.core.model.Item
import com.alis.core.model.Kind
import com.alis.core.model.Server
import com.alis.core.ui.ExpressiveCard
import com.alis.core.ui.IconBadge
import com.alis.core.ui.LiveDot
import com.alis.core.ui.MessageBox
import com.alis.core.ui.focusRing
import com.alis.core.ui.theme.LocalAppTheme
import kotlinx.coroutines.launch

/** Odaklanınca hatırlanan konumlar: Geri ile dönünce odak aynı öğeye gelir. */
object TvFocusMemory {
    val map = HashMap<String, Int>()
}

@Composable
fun TvHeader(title: String, subtitle: String? = null, actions: @Composable () -> Unit = {}) {
    val nav = nav()
    val upper = LocalAppTheme.current.uppercaseTitles
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (nav.canPop) {
            Surface(onClick = { nav.pop() }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(48.dp).focusRing(CircleShape, 1.1f)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(if (upper) title.uppercase() else title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/** TV kartı: büyük küçük resim, odaklanınca büyür ve arka plan resmini değiştirir. */
@Composable
fun TvCard(
    title: String,
    subtitle: String,
    thumb: String?,
    icon: ImageVector,
    width: Dp,
    aspect: Float,
    progress: Float = 0f,
    watched: Boolean = false,
    focusRequester: FocusRequester? = null,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Column(Modifier.width(width)) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.large,
            color = cs.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().aspectRatio(aspect)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { if (it.isFocused) { focused = true; onFocus() } else focused = false }
                .focusRing(MaterialTheme.shapes.large, 1.08f, 4.dp),
        ) {
            Box {
                Thumb(thumb, icon, Modifier.fillMaxSize(), 48.dp)
                if (progress > 0f) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(5.dp).align(Alignment.BottomCenter), trackColor = Color.Black.copy(alpha = 0.4f))
                }
                if (watched) Icon(Icons.Rounded.CheckCircle, null, Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp), tint = Color.White)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            color = if (focused) cs.primary else cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun RowTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 18.dp, bottom = 10.dp))
}

/** Bir kitaplık öğesi kartı (video 16:9, diğerleri kare). */
@Composable
fun TvItemCard(server: Server, item: Item, list: List<Item>, folderId: String?, fr: FocusRequester? = null, onFocus: () -> Unit = {}) {
    val ctx = LocalContext.current
    val nav = nav()
    val video = item.kind == Kind.VIDEO
    TvCard(
        item.title, itemMeta(item), if (item.isFolder) null else item.thumb, kindIcon(item.kind),
        if (video) 280.dp else 180.dp, if (video) 16f / 9f else 1f, item.progress, item.watched, fr,
        onFocus = {
            if (!item.isFolder && item.thumb != null) TvBackdrop.url.value = item.thumb
            onFocus()
        },
    ) { Open.item(ctx, nav, server, item, list, folderId) }
}

// ---------------- Ana sayfa ----------------

@Composable
fun TvHome() {
    val g = graph()
    val nav = nav()
    val servers by g.servers.servers.collectAsStateWithLifecycle()
    val scanning by g.servers.scanning.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(g.settings.current.lastServer) }
    val sel = servers.firstOrNull { it.id == selected } ?: servers.firstOrNull { it.online && it.isAkis } ?: servers.firstOrNull { it.online }
    val first = remember { FocusRequester() }
    LaunchedEffect(servers.isNotEmpty()) { runCatching { first.requestFocus() } }

    LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.tv_tagline), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (scanning) CircularProgressIndicator(Modifier.size(28.dp))
            }
        }
        item { RowTitle(stringResource(R.string.servers_title)) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp)) {
                itemsIndexed(servers, key = { _, s -> s.id }) { i, s ->
                    TvServerCard(s, s.id == sel?.id, if (i == 0) first else null, onFocus = { selected = s.id; g.settings.setLastServer(s.id) }) {
                        nav.push(Route.Home(s.id))
                    }
                }
                item {
                    TvCard(stringResource(R.string.add_server), "", null, Icons.Rounded.Add, 220.dp, 16f / 10f, focusRequester = if (servers.isEmpty()) first else null) {
                        nav.push(Route.AddServer)
                    }
                }
                item {
                    TvCard(stringResource(R.string.refresh), "", null, Icons.Rounded.Refresh, 220.dp, 16f / 10f) { g.servers.scan() }
                }
            }
        }
        if (sel != null) item(key = "rows-" + sel.id) { ServerRows(sel, compact = true) }
        if (servers.isEmpty() && !scanning) item(key = "help") { TvHelp() }
    }
}

@Composable
private fun TvServerCard(s: Server, selected: Boolean, fr: FocusRequester?, onFocus: () -> Unit, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) cs.secondaryContainer else cs.surfaceContainerHigh,
        modifier = Modifier.width(300.dp).height(150.dp)
            .then(if (fr != null) Modifier.focusRequester(fr) else Modifier)
            .onFocusChanged { if (it.isFocused) { onFocus(); TvBackdrop.url.value = null } }
            .focusRing(MaterialTheme.shapes.large, 1.08f, 4.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ServerIcon(s.icon, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot(s.online, 10.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    (if (s.isAkis) stringResource(R.string.badge_akis) else s.model ?: "DLNA") + " · " + s.base.substringAfter("://"),
                    style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1,
                )
                if (s.token != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Link, null, Modifier.size(16.dp), tint = cs.primary)
                }
            }
        }
    }
}

/** Bir sunucunun rafları (TV): devam et, son eklenenler, kategoriler, araçlar. */
@Composable
fun ServerRows(server: Server, compact: Boolean) {
    val g = graph()
    val nav = nav()
    val caps = rememberLoad(server.id, server.token) { g.servers.capabilities(server) }
    val home = rememberLoad(server.id, server.token) { if (server.isAkis) g.library.home(server) else null }
    val root = rememberLoad(server.id) { if (!server.isAkis) g.library.list(server, "0").items else emptyList() }
    val c = caps.value
    val err = (caps.state as? com.alis.app.ui.Load.Fail)?.error ?: (home.state as? com.alis.app.ui.Load.Fail)?.error
    Column {
        if (err != null) {
            // Erişim / bağlantı sorunu: ayrıntılı ekranda çözülür
            RowTitle(server.name)
            TvCard(
                stringResource(if (err is AccessException) R.string.tv_access_needed else R.string.err_offline),
                stringResource(R.string.tv_open_server), null, Icons.Rounded.Lock, 320.dp, 16f / 7f,
            ) { nav.push(Route.Home(server.id)) }
            return@Column
        }
        val h = home.value
        if (h != null) {
            if (h.cont.isNotEmpty()) ItemRow(stringResource(R.string.shelf_continue), server, h.cont, null)
            if (h.recent.isNotEmpty()) ItemRow(stringResource(R.string.shelf_recent), server, h.recent, "r")
            if (h.cats.isNotEmpty()) {
                RowTitle(stringResource(R.string.categories))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp)) {
                    itemsIndexed(h.cats) { _, cat ->
                        TvCard(cat.title, if (cat.count >= 0) cat.count.toString() else "", null, catIcon(cat.id), 200.dp, 4f / 3f) {
                            nav.push(Route.Browse(server.id, cat.id, cat.title))
                        }
                    }
                }
            }
        }
        if (!server.isAkis) {
            val items = root.value.orEmpty()
            if (items.isNotEmpty()) ItemRow(server.name, server, items, "0")
        }
        if (c != null && server.isAkis) ToolsRow(server, c)
        if (compact && h == null && root.value == null) Box(Modifier.padding(20.dp)) { CircularProgressIndicator() }
    }
}

@Composable
private fun ItemRow(title: String, server: Server, items: List<Item>, folderId: String?) {
    RowTitle(title)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp)) {
        itemsIndexed(items, key = { _, it -> it.id }) { _, it -> TvItemCard(server, it, items, folderId) }
    }
}

private fun catIcon(id: String) = when (id) {
    "v" -> Icons.Rounded.Movie
    "a" -> Icons.Rounded.Audiotrack
    "i" -> Icons.Rounded.Image
    "f", "pd:" -> Icons.Rounded.FolderOpen
    "h:u", "h:c" -> Icons.Rounded.History
    else -> Icons.Rounded.Folder
}

@Composable
private fun ToolsRow(server: Server, caps: Capabilities) {
    val g = graph()
    val nav = nav()
    val s = g.settings.current
    val tools = buildList<Triple<ImageVector, Int, Route>> {
        if (caps.files && s.on(Feature.FILE_MANAGER)) add(Triple(Icons.Rounded.FolderOpen, R.string.tool_files, Route.Files(server.id, "")))
        if (caps.search) add(Triple(Icons.Rounded.Search, R.string.search, Route.Search(server.id)))
        if (caps.status && s.on(Feature.PHONE_STATUS)) add(Triple(Icons.Rounded.BatteryStd, R.string.tool_status, Route.Status(server.id)))
        if (caps.apps && s.on(Feature.APPS)) add(Triple(Icons.Rounded.Apps, R.string.tool_apps, Route.Apps(server.id)))
        if ((caps.camera || caps.screen || caps.mic) && s.on(Feature.LIVE)) add(Triple(Icons.Rounded.Videocam, R.string.tool_live, Route.Live(server.id)))
        if (caps.notifs && s.on(Feature.NOTIF_MIRROR)) add(Triple(Icons.Rounded.Notifications, R.string.tool_notifs, Route.Notifs(server.id)))
        if (caps.inbox && s.on(Feature.INBOX)) add(Triple(Icons.Rounded.Inbox, R.string.tool_inbox, Route.Inbox(server.id)))
        if (caps.admin && !server.remote && s.on(Feature.ADMIN)) add(Triple(Icons.Rounded.AdminPanelSettings, R.string.tool_admin, Route.Admin(server.id)))
        if (server.token == null && !server.remote) add(Triple(Icons.Rounded.Link, R.string.pair_action, Route.Pair(server.id)))
    }
    if (tools.isEmpty()) return
    RowTitle(stringResource(R.string.tools))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp)) {
        itemsIndexed(tools) { _, (icon, label, route) ->
            TvCard(stringResource(label), "", null, icon, 200.dp, 4f / 3f) { nav.push(route) }
        }
    }
}

// ---------------- Sunucu sayfası ----------------

@Composable
fun TvServerHome(serverId: String) {
    val server = rememberServer(serverId) ?: return MessageBox(Icons.Rounded.Dns, stringResource(R.string.server_gone))
    val g = graph()
    val caps = rememberLoad(server.id, server.token) { g.servers.capabilities(server, refresh = true) }
    Column(Modifier.fillMaxSize()) {
        TvHeader(server.name, server.base.substringAfter("://"))
        LoadView(caps, server) { _ ->
            LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
                item { ServerRows(server, compact = false) }
            }
        }
    }
}

// ---------------- Gezgin ----------------

@Composable
fun TvBrowse(serverId: String, folderId: String, title: String) {
    val server = rememberServer(serverId) ?: return
    val g = graph()
    val s by g.settings.state.collectAsStateWithLifecycle()
    val load = rememberLoad(server.id, folderId) { g.library.list(server, folderId) }
    val key = "$serverId|$folderId"
    val first = remember { FocusRequester() }
    Column(Modifier.fillMaxSize()) {
        TvHeader(title.ifEmpty { load.value?.title ?: server.name }, server.name)
        LoadView(load, server) { f ->
            val items = sortItems(f.items, s.sort, s.sortDesc)
            if (items.isEmpty()) return@LoadView MessageBox(Icons.Rounded.Folder, stringResource(R.string.folder_empty))
            val start = (TvFocusMemory.map[key] ?: 0).coerceIn(0, items.size - 1)
            LaunchedEffect(items.size) { runCatching { first.requestFocus() } }
            val video = items.any { it.kind == Kind.VIDEO }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (video) 280.dp else 180.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(items, key = { _, it -> it.id }) { i, it ->
                    TvItemCard(server, it, items, folderId, if (i == start) first else null) { TvFocusMemory.map[key] = i }
                }
            }
        }
    }
}

// ---------------- Arama ----------------

@Composable
fun TvSearch(serverId: String?) {
    val g = graph()
    var text by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    val server = serverId?.let { g.servers.byId(it) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    // Yazdıkça ara (klavyenin Ara tuşu da hemen arar)
    LaunchedEffect(text) {
        kotlinx.coroutines.delay(600)
        query = text.trim()
    }
    Column(Modifier.fillMaxSize()) {
        TvHeader(stringResource(R.string.search), server?.name ?: stringResource(R.string.search_all))
        OutlinedTextField(
            text, { text = it },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (text.isNotEmpty()) androidx.compose.material3.IconButton(onClick = { text = "" }) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.clear))
                }
            },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { query = text.trim() }),
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().focusRequester(field),
        )
        if (query.length < 2) return@Column
        val load = rememberLoad(query, serverId) {
            if (server != null) listOf(server to g.library.search(server, query)) else g.library.searchAll(query)
        }
        LoadView(load, server) { groups ->
            if (groups.all { it.second.isEmpty() }) return@LoadView MessageBox(Icons.Rounded.Search, stringResource(R.string.search_none))
            LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
                groups.forEach { (s, items) ->
                    item(key = s.id) { ItemRow(s.name, s, items, null) }
                }
            }
        }
    }
}

// ---------------- Sunucu ekleme ----------------

/** TV'de kamera yok: adres yazarak ekleme (ekran klavyesiyle). Akış'lar zaten kendiliğinden bulunur. */
@Composable
fun TvAddServer() {
    val g = graph()
    val nav = nav()
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    fun add() {
        busy = true
        scope.launch {
            val r = g.servers.addAddress(address)
            busy = false
            r.onSuccess { s -> nav.replace(Route.Home(s.id)) }.onFailure { error = true }
        }
    }
    Column(Modifier.fillMaxSize()) {
        TvHeader(stringResource(R.string.add_server))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            ExpressiveCard(Modifier.weight(1f)) {
                Text(stringResource(R.string.add_address_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    address, { address = it.trim(); error = false },
                    label = { Text(stringResource(R.string.add_address_label)) },
                    placeholder = { Text("192.168.1.20:8200") },
                    singleLine = true, isError = error,
                    supportingText = { Text(stringResource(if (error) R.string.add_failed else R.string.add_address_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (address.isNotEmpty()) add() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(field),
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { add() }, enabled = !busy && address.isNotEmpty(), modifier = Modifier.focusRing(CircleShape)) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.add))
                }
            }
            ExpressiveCard(Modifier.weight(1f)) {
                IconBadge(Icons.Rounded.Dns)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.add_tips), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.tv_add_tips), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = { g.servers.scan(); nav.pop() }, modifier = Modifier.focusRing(CircleShape)) { Text(stringResource(R.string.refresh)) }
            }
        }
    }
}

/** İlk açılışta sunucu bulunamadıysa: ne yapmalı? */
@Composable
private fun TvHelp() {
    Column(Modifier.padding(top = 24.dp).widthIn(max = 900.dp)) {
        Text(stringResource(R.string.tv_help_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(R.string.tv_help_1, R.string.tv_help_2, R.string.tv_help_3).forEachIndexed { i, t ->
                ExpressiveCard(Modifier.weight(1f)) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                        Text((i + 1).toString(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(t), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
