package com.alis.app.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.alis.app.R
import com.alis.app.ui.ItemRowContent
import com.alis.app.ui.LoadView
import com.alis.app.ui.Open
import com.alis.app.ui.graph
import com.alis.app.ui.itemMeta
import com.alis.app.ui.kindIcon
import com.alis.app.ui.nav
import com.alis.app.ui.rememberLoad
import com.alis.core.data.Feature
import com.alis.core.model.Item
import com.alis.core.model.Server
import com.alis.core.ui.MessageBox
import com.alis.core.ui.SectionTitle
import com.alis.core.ui.focusRing
import kotlinx.coroutines.delay

/** Arama: tek sunucuda ya da (birleşik kitaplık açıksa) tüm sunucularda aynı anda. */
@Composable
fun SearchScreen(serverId: String?) {
    val g = graph()
    val nav = nav()
    val ctx = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val all = serverId == null
    val server = serverId?.let { g.servers.byId(it) }
    LaunchedEffect(text) {
        delay(450)
        query = text.trim()
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.search), server?.name ?: stringResource(R.string.search_all), back = !all)
        OutlinedTextField(
            text, { text = it },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).focusRequester(focus),
        )
        if (query.length < 2) return@Column
        val load = rememberLoad(query, serverId) {
            if (server != null) listOf(server to g.library.search(server, query))
            else if (g.settings.current.on(Feature.MERGED_LIBRARY)) g.library.searchAll(query)
            else {
                val s = g.servers.servers.value.firstOrNull { it.id == g.settings.current.lastServer } ?: g.servers.servers.value.firstOrNull { it.online }
                if (s == null) emptyList() else listOf(s to g.library.search(s, query))
            }
        }
        LoadView(load, server) { groups ->
            if (groups.all { it.second.isEmpty() }) {
                MessageBox(Icons.Rounded.SearchOff, stringResource(R.string.search_none))
                return@LoadView
            }
            LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for ((s, items) in groups) {
                    if (groups.size > 1 || all) item(key = "h" + s.id) { SectionTitle(s.name, Modifier.padding(horizontal = 8.dp)) }
                    items(items, key = { s.id + "|" + it.id }) { it -> ResultRow(s, it, items) { i -> Open.item(ctx, nav, s, i, items, null) } }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(s: Server, item: Item, all: List<Item>, onClick: (Item) -> Unit) {
    Surface(
        onClick = { onClick(item) },
        color = Color.Transparent,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().focusRing(MaterialTheme.shapes.medium, 1.01f),
    ) {
        ItemRowContent(item.title, itemMeta(item), if (item.isFolder) null else item.thumb, kindIcon(item.kind), false)
    }
}
