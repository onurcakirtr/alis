package com.alis.app.mobile

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alis.app.R
import com.alis.app.ui.nav
import com.alis.core.ui.focusRing
import com.alis.core.ui.theme.LocalAppTheme

/** Ekran başlığı: geri düğmesi (yığında önceki ekran varsa), başlık, sağda eylemler. */
@Composable
fun TopBar(title: String, subtitle: String? = null, back: Boolean = true, actions: @Composable RowScope.() -> Unit = {}) {
    val nav = nav()
    val upper = LocalAppTheme.current.uppercaseTitles
    Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back && nav.canPop) {
            IconButton(onClick = { nav.pop() }, modifier = Modifier.focusRing(CircleShape)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(
                if (upper) title.uppercase() else title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        actions()
    }
}

/** Odak halkalı ikon düğmesi. */
@Composable
fun ActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.focusRing(CircleShape)) { Icon(icon, label) }
}
