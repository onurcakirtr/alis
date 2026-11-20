package com.alis.app.mobile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.alis.app.R
import com.alis.app.ui.SettingsContent

@Composable
fun SettingsScreen() {
    val wide = LocalConfiguration.current.screenWidthDp >= 840
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.tab_settings), back = false)
        SettingsContent(columns = if (wide) 2 else 1)
    }
}
