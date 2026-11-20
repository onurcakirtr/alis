package com.alis.app.tv

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.alis.app.R
import com.alis.app.ui.DocScreen
import com.alis.app.ui.FileManager
import com.alis.app.ui.InfoScreen
import com.alis.app.ui.MusicScreen
import com.alis.app.ui.PairPanel
import com.alis.app.ui.PhotoViewer
import com.alis.app.ui.Route
import com.alis.app.ui.ZipScreen
import com.alis.app.ui.graph
import com.alis.app.ui.nav
import com.alis.app.ui.rememberServer
import com.alis.app.ui.tools.AdminScreen
import com.alis.app.ui.tools.AppsScreen
import com.alis.app.ui.tools.InboxScreen
import com.alis.app.ui.tools.LiveScreen
import com.alis.app.ui.tools.NotifsScreen
import com.alis.app.ui.tools.StatusScreen
import com.alis.app.ui.tools.TransfersScreen
import com.alis.core.player.Audio
import com.alis.core.ui.focusRing
import com.alis.core.ui.themedBackdrop

/** Odaktaki öğenin arka plan resmi (TV'de ana sayfa ve gezginde). */
object TvBackdrop {
    val url = mutableStateOf<String?>(null)
}

private data class RailItem(val route: Route, val label: Int, val icon: ImageVector)

/**
 * TV kökü: solda odaklanınca genişleyen ray, sağda içerik. Kenarlarda ~%5 güvenli alan (overscan).
 * Geri: önceki ekran; ana sayfada iki kez basınca çıkar.
 */
@Composable
fun TvApp() {
    val nav = nav()
    val ctx = LocalContext.current
    val g = graph()
    LaunchedEffect(Unit) { g.servers.scan() }
    var lastBack by remember { mutableLongStateOf(0L) }
    val exitHint = stringResource(R.string.back_again)
    BackHandler(enabled = nav.canPop) { nav.pop() }
    BackHandler(enabled = !nav.canPop) {
        val now = System.currentTimeMillis()
        if (now - lastBack < 2000) (ctx as? android.app.Activity)?.finish()
        else {
            lastBack = now
            Toast.makeText(ctx, exitHint, Toast.LENGTH_SHORT).show()
        }
    }
    val now by Audio.now.collectAsStateWithLifecycle()
    val rail = buildList {
        add(RailItem(Route.Servers, R.string.tv_home, Icons.Rounded.Home))
        add(RailItem(Route.Search(null), R.string.tab_search, Icons.Rounded.Search))
        if (now.item != null) add(RailItem(Route.Music, R.string.now_playing, Icons.Rounded.MusicNote))
        add(RailItem(Route.Transfers, R.string.tab_downloads, Icons.Rounded.Download))
        add(RailItem(Route.Settings, R.string.tab_settings, Icons.Rounded.Settings))
    }
    val full = nav.top is Route.Photos
    val backdrop by TvBackdrop.url
    val cs = MaterialTheme.colorScheme

    Box(Modifier.fillMaxSize().themedBackdrop()) {
        // Odaktaki öğenin resmi, yumuşak geçişle
        AnimatedContent(backdrop, transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(500)) }, label = "bg") { u ->
            if (u != null && (nav.top == Route.Servers || nav.top is Route.Browse || nav.top is Route.Home)) {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(u, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = 0.32f)
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color.Transparent, cs.background.copy(alpha = 0.85f), cs.background), startY = 0f),
                        ),
                    )
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(cs.background, Color.Transparent), endX = 900f)))
                }
            }
        }
        Row(Modifier.fillMaxSize()) {
            if (!full) TvRail(rail, nav.stack.first())
            Box(Modifier.weight(1f).fillMaxHeight().padding(start = if (full) 0.dp else 12.dp, end = if (full) 0.dp else 48.dp, top = if (full) 0.dp else 27.dp, bottom = if (full) 0.dp else 20.dp)) {
                AnimatedContent(
                    targetState = nav.top,
                    transitionSpec = { fadeIn(spring(stiffness = 300f)) togetherWith fadeOut(spring(stiffness = 600f)) },
                    label = "tv",
                ) { r -> TvScreen(r) }
            }
        }
    }
}

@Composable
private fun TvRail(items: List<RailItem>, root: Route) {
    val nav = nav()
    var open by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (open) 230.dp else 84.dp, spring(dampingRatio = 0.85f, stiffness = 500f), label = "rail")
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.width(width).fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(cs.surfaceContainer.copy(alpha = if (open) 0.98f else 0.6f), Color.Transparent)))
            .onFocusChanged { open = it.hasFocus }
            .padding(vertical = 27.dp, horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.padding(start = 10.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).background(cs.primary, MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
                Text("A", color = cs.onPrimary, fontWeight = FontWeight.Bold)
            }
            AnimatedVisibility(open) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
            }
        }
        items.forEach { it ->
            val selected = it.route::class == root::class
            Surface(
                onClick = { nav.root(it.route) },
                shape = MaterialTheme.shapes.medium,
                color = if (selected) cs.secondaryContainer else Color.Transparent,
                contentColor = if (selected) cs.onSecondaryContainer else cs.onSurface,
                modifier = Modifier.fillMaxWidth().height(52.dp).focusRing(MaterialTheme.shapes.medium, 1.05f),
            ) {
                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(it.icon, stringResource(it.label), Modifier.size(26.dp))
                    AnimatedVisibility(open) {
                        Text(stringResource(it.label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 14.dp), maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun TvScreen(r: Route) {
    val nav = nav()
    when (r) {
        Route.Servers -> TvHome()
        is Route.Home -> TvServerHome(r.server)
        is Route.Browse -> TvBrowse(r.server, r.id, r.title)
        is Route.Search -> TvSearch(r.server)
        is Route.Files -> rememberServer(r.server)?.let { s ->
            FileManager(s, r.path, tv = true) { title, actions -> TvHeader(title) { actions() } }
        }
        is Route.Zip -> rememberServer(r.server)?.let { ZipScreen(it, r.path, r.name) }
        is Route.Doc -> rememberServer(r.server)?.let { DocScreen(it, r.path, r.name) }
        is Route.Info -> rememberServer(r.server)?.let { InfoScreen(it, r.id, r.path, r.title) }
        is Route.Status -> rememberServer(r.server)?.let { StatusScreen(it) }
        is Route.Apps -> rememberServer(r.server)?.let { AppsScreen(it) }
        is Route.Live -> rememberServer(r.server)?.let { LiveScreen(it) }
        is Route.Notifs -> rememberServer(r.server)?.let { NotifsScreen(it) }
        is Route.Inbox -> rememberServer(r.server)?.let { InboxScreen(it) }
        is Route.Admin -> rememberServer(r.server)?.let { AdminScreen(it) }
        is Route.Pair -> rememberServer(r.server)?.let { s ->
            Column {
                TvHeader(stringResource(R.string.pair_action))
                PairPanel(s, r.withCode) { nav.pop() }
            }
        }
        Route.AddServer, Route.Scan -> TvAddServer()
        Route.Photos -> PhotoViewer()
        Route.Music -> MusicScreen()
        Route.Transfers -> TransfersScreen()
        Route.Settings -> Column {
            TvHeader(stringResource(R.string.tab_settings))
            com.alis.app.ui.SettingsContent(columns = 2, contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp))
        }
    }
}
