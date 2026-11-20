package com.alis.app.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alis.app.R
import com.alis.app.mobile.ActionIcon
import com.alis.app.mobile.TopBar
import com.alis.app.ui.LoadView
import com.alis.app.ui.NameDialog
import com.alis.app.ui.graph
import com.alis.app.ui.rememberLoad
import com.alis.app.ui.toast
import com.alis.core.akis.AkisClient
import com.alis.core.akis.LoginResult
import com.alis.core.model.AdminState
import com.alis.core.model.Server
import com.alis.core.ui.ExpressiveCard
import com.alis.core.ui.FeatureRow
import com.alis.core.ui.IconBadge
import com.alis.core.ui.SectionTitle
import com.alis.core.ui.SettingRow
import com.alis.core.ui.focusRing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/** Akış'ın yönetim paneli (yerel ekran). Giriş: yönetici şifresi (+ iki adımlı kod) ve telefonda onay. */
@Composable
fun AdminScreen(server: Server) {
    val g = graph()
    val client = remember(server) { g.servers.client(server) }
    var tick by remember { mutableStateOf(0) }
    val logged = rememberLoad(server.id, tick) { client.adminLoggedIn() }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.tool_admin), server.name)
        LoadView(logged, server) { ok ->
            if (ok) AdminPanel(server, client) { tick++ } else AdminLogin(server, client) { tick++ }
        }
    }
}

@Composable
private fun AdminLogin(server: Server, client: AkisClient, onDone: () -> Unit) {
    val g = graph()
    val scope = rememberCoroutineScope()
    var pw by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var waitId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val totp = g.servers.cachedCaps(server.id)?.totp == true
    val msgWrong = stringResource(R.string.acc_login_wrong)
    val msgLocked = stringResource(R.string.acc_locked)
    val msgDenied = stringResource(R.string.admin_denied)
    val wid = waitId
    if (wid != null) {
        LaunchedEffect(wid) {
            while (true) {
                delay(1500)
                when (client.adminStatus(wid)) {
                    "ok" -> { onDone(); return@LaunchedEffect }
                    "pending" -> {}
                    else -> { waitId = null; error = msgDenied; return@LaunchedEffect }
                }
            }
        }
    }
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), contentAlignment = Alignment.Center) {
        ExpressiveCard(Modifier.widthIn(max = 480.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                IconBadge(Icons.Rounded.AdminPanelSettings, size = 72.dp)
                Text(stringResource(R.string.admin_login_title), style = MaterialTheme.typography.titleLarge)
                if (wid != null) {
                    Text(stringResource(R.string.admin_wait), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CircularProgressIndicator()
                } else {
                    OutlinedTextField(pw, { pw = it; error = null }, label = { Text(stringResource(R.string.acc_password)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                    if (totp) OutlinedTextField(code, { code = it.filter(Char::isDigit).take(8) }, label = { Text(stringResource(R.string.acc_code)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = {
                        scope.launch {
                            when (val r = runCatching { client.adminLogin(pw, code) }.getOrElse { LoginResult.Error("net") }) {
                                is LoginResult.Wait -> waitId = r.id
                                LoginResult.Ok -> onDone()
                                is LoginResult.Error -> error = if (r.reason == "locked") msgLocked else msgWrong
                            }
                        }
                    }, enabled = pw.isNotEmpty(), modifier = Modifier.focusRing(CircleShape)) { Text(stringResource(R.string.acc_login)) }
                }
            }
        }
    }
}

private val SWITCH_LABELS = listOf(
    "shareVideo" to R.string.adm_share_video, "shareAudio" to R.string.adm_share_audio, "shareImage" to R.string.adm_share_image,
    "showRecent" to R.string.adm_recent, "hideFolders" to R.string.adm_hide_folders, "subtitles" to R.string.adm_subtitles,
    "search" to R.string.adm_search, "history" to R.string.adm_history, "historyMarks" to R.string.adm_history_marks, "upload" to R.string.adm_upload,
)

@Composable
private fun AdminPanel(server: Server, client: AkisClient, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val load = rememberLoad(server.id) { client.adminState() }
    var editName by remember { mutableStateOf(false) }
    var pickFolder by remember { mutableStateOf(false) }
    val failed = stringResource(R.string.admin_failed)
    fun act(body: JSONObject) {
        scope.launch {
            runCatching { client.adminDo(body) }.onFailure { toast(ctx, failed + ": " + it.message) }
            load.reload()
        }
    }
    LoadView(load, server) { st: AdminState ->
        val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { load.reload() }, modifier = Modifier.focusRing(CircleShape)) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.refresh)) }
                FilledTonalButton(onClick = { scope.launch { client.adminLogout(); onLogout() } }, modifier = Modifier.focusRing(CircleShape)) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.admin_logout))
                }
            }
            ExpressiveCard {
                SettingRow(Icons.Rounded.Badge, stringResource(R.string.adm_name), st.name, onClick = { editName = true })
                if (st.ring) {
                    com.alis.core.ui.CardDivider()
                    SettingRow(Icons.Rounded.NotificationsActive, stringResource(R.string.adm_ring), stringResource(if (st.ringing) R.string.adm_ringing else R.string.adm_ring_sub),
                        onClick = { act(JSONObject().put("op", if (st.ringing) "ringStop" else "ring")) })
                }
            }
            SectionTitle(stringResource(R.string.adm_switches))
            ExpressiveCard {
                SWITCH_LABELS.forEachIndexed { i, (key, label) ->
                    FeatureRow(Icons.Rounded.Tune, stringResource(label), "", st.switches[key] == true, divider = i > 0) { v ->
                        act(JSONObject().put("op", "set").put("key", key).put("value", v))
                    }
                }
            }
            SectionTitle(stringResource(R.string.adm_folders))
            ExpressiveCard {
                st.folders.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(f.name, Modifier.weight(1f))
                        IconButton(onClick = { act(JSONObject().put("op", "folderRemove").put("uri", f.uri)) }, modifier = Modifier.focusRing(CircleShape)) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.remove)) }
                    }
                }
                if (st.filesGranted) TextButton(onClick = { pickFolder = true }, modifier = Modifier.focusRing(CircleShape)) {
                    Icon(Icons.Rounded.CreateNewFolder, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.adm_add_folder))
                }
            }
            if (st.buckets.isNotEmpty()) {
                SectionTitle(stringResource(R.string.adm_hidden))
                ExpressiveCard {
                    st.buckets.forEach { b ->
                        SettingRow(if (b.hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, b.name, b.count.toString(), onClick = {
                            act(JSONObject().put("op", "hide").put("bucket", b.id).put("on", !b.hidden))
                        })
                    }
                }
            }
            SectionTitle(stringResource(R.string.adm_devices))
            ExpressiveCard {
                st.clients.forEach { c ->
                    var menu by remember(c.ip) { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Devices, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name + if (c.ip == st.me) " " + stringResource(R.string.adm_me) else "")
                            Text(c.ip + " · " + df.format(Date(c.last)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (c.ip != st.me) Box {
                            IconButton(onClick = { menu = true }, modifier = Modifier.focusRing(CircleShape)) { Icon(Icons.Rounded.Block, stringResource(R.string.more)) }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem({ Text(stringResource(R.string.adm_kick)) }, { menu = false; act(JSONObject().put("op", "kick").put("ip", c.ip)) })
                                listOf(15 to R.string.adm_ban_15m, 60 to R.string.adm_ban_1h, 1440 to R.string.adm_ban_1d).forEach { (m, l) ->
                                    DropdownMenuItem({ Text(stringResource(l)) }, { menu = false; act(JSONObject().put("op", "ban").put("ip", c.ip).put("min", m)) })
                                }
                            }
                        }
                    }
                }
                val blocked = st.known.filter { !it.allowed }
                if (blocked.isNotEmpty()) {
                    com.alis.core.ui.CardDivider()
                    Text(stringResource(R.string.adm_blocked), style = MaterialTheme.typography.labelLarge)
                    blocked.forEach { k ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(k.name + " · " + k.ip + if (k.until > 0) " · " + df.format(Date(k.until)) else "", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { act(JSONObject().put("op", "unblock").put("ip", k.ip)) }, modifier = Modifier.focusRing(CircleShape)) { Text(stringResource(R.string.adm_unblock)) }
                        }
                    }
                }
            }
            if (st.shares.isNotEmpty()) {
                SectionTitle(stringResource(R.string.adm_shares))
                ExpressiveCard {
                    st.shares.forEach { sh ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Link, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(sh.name)
                                Text(
                                    (if (sh.expires > 0) df.format(Date(sh.expires)) else "∞") + " · " + stringResource(R.string.adm_downloads, sh.downloads) +
                                        if (sh.once) " · " + stringResource(R.string.adm_once) else "",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { act(JSONObject().put("op", "shareClose").put("token", sh.token)) }, modifier = Modifier.focusRing(CircleShape)) {
                                Icon(Icons.Rounded.LinkOff, stringResource(R.string.adm_share_close))
                            }
                        }
                    }
                }
            }
        }
        if (editName) NameDialog(stringResource(R.string.adm_name), st.name, onDismiss = { editName = false }) { v ->
            editName = false
            act(JSONObject().put("op", "set").put("key", "name").put("value", v))
        }
        if (pickFolder) FolderPicker(client, onDismiss = { pickFolder = false }) { path ->
            pickFolder = false
            act(JSONObject().put("op", "folderAdd").put("path", path))
        }
    }
}

/** Akış'ın klasör seçicisi (/admin/dirs). */
@Composable
private fun FolderPicker(client: AkisClient, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var path by remember { mutableStateOf("") }
    val load = rememberLoad(path) { client.adminDirs(path) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.adm_add_folder)) },
        text = {
            Box(Modifier.heightIn(max = 420.dp)) {
                LoadView(load, null) { (cur, parent, items) ->
                    LazyColumn {
                        if (cur.isNotEmpty()) item {
                            Surface(onClick = { path = parent }, color = Color.Transparent, modifier = Modifier.fillMaxWidth().focusRing(MaterialTheme.shapes.small, 1f)) {
                                Text("..", Modifier.padding(10.dp))
                            }
                        }
                        items(items) { (n, p) ->
                            Surface(onClick = { path = p }, color = Color.Transparent, modifier = Modifier.fillMaxWidth().focusRing(MaterialTheme.shapes.small, 1f)) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Folder, null, Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(n)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (path.isNotEmpty()) onPick(path) }, enabled = path.isNotEmpty()) { Text(stringResource(R.string.adm_pick_this)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Suppress("unused")
@Composable
private fun Refresh(onClick: () -> Unit) = ActionIcon(Icons.Rounded.Refresh, "", onClick = onClick)
