package com.alis.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alis.app.R
import com.alis.app.Updater
import com.alis.core.ui.focusRing
import kotlinx.coroutines.launch

/** Ayarlar → Hakkında: güncelleme durumu, "Güncellemeleri denetle" ve otomatik denetim düğmesi (kumandayla da kullanılır). */
@Composable
fun AboutUpdate(auto: Boolean, onAuto: (Boolean) -> Unit) {
    val st by Updater.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (!Updater.supported) {
        Text(stringResource(R.string.upd_dev), style = MaterialTheme.typography.bodySmall, color = muted)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (val s = st) {
            is Updater.State.Available -> {
                Text(stringResource(R.string.upd_available, s.version), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { if (Updater.ensureInstallPermission(ctx)) scope.launch { Updater.install(ctx.applicationContext, s) } },
                        modifier = Modifier.focusRing(),
                    ) { Text(stringResource(R.string.upd_install)) }
                    if (s.page.isNotEmpty()) {
                        TextButton(
                            onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                            modifier = Modifier.focusRing(),
                        ) { Text(stringResource(R.string.upd_notes)) }
                    }
                }
                Text(stringResource(R.string.upd_perm_note), style = MaterialTheme.typography.bodySmall, color = muted)
            }
            is Updater.State.Downloading -> {
                Text(stringResource(R.string.upd_downloading, s.version, (s.progress * 100).toInt()), style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
            }
            Updater.State.Installing -> Text(stringResource(R.string.upd_installing), style = MaterialTheme.typography.titleMedium)
            else -> {
                when (s) {
                    Updater.State.Checking -> Text(stringResource(R.string.upd_checking), style = MaterialTheme.typography.bodySmall, color = muted)
                    Updater.State.UpToDate -> Text(stringResource(R.string.upd_latest), style = MaterialTheme.typography.bodySmall, color = muted)
                    is Updater.State.Failed -> Text(stringResource(R.string.upd_failed, s.message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    else -> Unit
                }
                FilledTonalButton(
                    onClick = { scope.launch { Updater.check(ctx.applicationContext, force = true) } },
                    enabled = st !is Updater.State.Checking,
                    modifier = Modifier.focusRing(),
                ) { Text(stringResource(R.string.upd_check)) }
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.upd_auto), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.upd_auto_sub), style = MaterialTheme.typography.bodySmall, color = muted)
            }
            Switch(auto, onAuto, modifier = Modifier.focusRing())
        }
    }
}
