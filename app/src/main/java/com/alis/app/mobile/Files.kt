package com.alis.app.mobile

import androidx.compose.runtime.Composable
import com.alis.app.ui.FileManager
import com.alis.app.ui.rememberServer

@Composable
fun FilesScreen(serverId: String, path: String) {
    val s = rememberServer(serverId) ?: return
    FileManager(s, path, tv = false) { title, actions ->
        TopBar(title) { actions() }
    }
}
