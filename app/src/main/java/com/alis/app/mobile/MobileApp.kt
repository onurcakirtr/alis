package com.alis.app.mobile

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alis.app.R
import com.alis.app.ui.DocScreen
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
import com.alis.core.data.Feature
import com.alis.core.player.Audio
import com.alis.core.ui.focusRing
import com.alis.core.ui.theme.LocalAppTheme
import com.alis.core.ui.themedBackdrop

private data class Tab(val route: Route, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Route.Servers, R.string.tab_servers, Icons.Rounded.Dns),
    Tab(Route.Search(null), R.string.tab_search, Icons.Rounded.Search),
    Tab(Route.Transfers, R.string.tab_downloads, Icons.Rounded.Download),
    Tab(Route.Settings, R.string.tab_settings, Icons.Rounded.Settings),
)

/** Telefon: alt menü; tablet (≥600dp): yan ray + liste–ayrıntı. */
@Composable
fun MobileApp() {
    val nav = nav()
    val ctx = LocalContext.current
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val theme = LocalAppTheme.current
    val cs = MaterialTheme.colorScheme
    val g = graph()
    val notifPerm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        g.servers.scan()
        // İndirme / yükleme ilerlemesi ve müzik kontrolleri bildirimde görünsün (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

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

    val rootRoute = nav.stack.first()
    val selected = tabs.indexOfFirst { it.route::class == rootRoute::class }.coerceAtLeast(0)
    val full = nav.top is Route.Photos || nav.top is Route.Music

    Row(Modifier.fillMaxSize().themedBackdrop()) {
        if (wide && !full) {
            NavigationRail(
                containerColor = if (theme.flatNav) cs.background else cs.surfaceContainer,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                tabs.forEachIndexed { i, t ->
                    NavigationRailItem(
                        selected = selected == i,
                        onClick = { nav.root(t.route) },
                        icon = { Icon(t.icon, null) },
                        label = { Text(stringResource(t.label)) },
                        modifier = Modifier.focusRing(MaterialTheme.shapes.medium),
                    )
                }
            }
        }
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            bottomBar = {
                Column {
                    MiniPlayerBar(visible = nav.top !is Route.Music)
                    if (!wide && !full) {
                        NavigationBar(containerColor = if (theme.flatNav) cs.background else cs.surfaceContainer) {
                            tabs.forEachIndexed { i, t ->
                                NavigationBarItem(
                                    selected = selected == i,
                                    onClick = { nav.root(t.route) },
                                    icon = { Icon(t.icon, null) },
                                    label = { Text(stringResource(t.label)) },
                                )
                            }
                        }
                    }
                }
            },
            modifier = Modifier.weight(1f),
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                AnimatedContent(
                    targetState = nav.top,
                    transitionSpec = {
                        (fadeIn(spring(stiffness = 400f)) + scaleIn(spring(dampingRatio = 0.75f, stiffness = 400f), initialScale = 0.96f)) togetherWith
                            fadeOut(spring(stiffness = 800f))
                    },
                    label = "screens",
                ) { r -> MobileScreen(r, wide) }
            }
        }
    }
}

@Composable
private fun MobileScreen(r: Route, wide: Boolean) {
    val nav = nav()
    when (r) {
        Route.Servers -> ServersScreen(wide)
        is Route.Home -> ServerHomeScreen(r.server)
        is Route.Browse -> BrowseScreen(r.server, r.id, r.title)
        is Route.Search -> SearchScreen(r.server)
        is Route.Files -> FilesScreen(r.server, r.path)
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
                TopBar(stringResource(R.string.pair_action))
                PairPanel(s, r.withCode) { nav.pop() }
            }
        }
        Route.AddServer -> AddServerScreen()
        Route.Scan -> ScanScreen()
        Route.Photos -> PhotoViewer()
        Route.Music -> MusicScreen()
        Route.Transfers -> TransfersScreen()
        Route.Settings -> SettingsScreen()
    }
}

/** Ekranın altında, çalan şarkı varsa: kapak, ad, oynat/duraklat; dokununca tam ekran müzik. */
@Composable
private fun MiniPlayerBar(visible: Boolean) {
    val now by Audio.now.collectAsStateWithLifecycle()
    val nav = nav()
    AnimatedVisibility(
        visible = visible && now.item != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        com.alis.app.ui.MiniPlayer(now, Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) { nav.push(Route.Music) }
    }
}

