package com.alis.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alis.app.R
import com.alis.core.ui.IconBadge
import com.alis.core.ui.focusRing
import com.alis.core.ui.themedBackdrop
import kotlinx.coroutines.launch

private data class Page(val icon: ImageVector, val title: Int, val text: Int)

/** İlk açılışta kısa tanıtım (telefon / tablet ve TV için ayrı son sayfa). */
@Composable
fun Onboarding(tv: Boolean, onDone: () -> Unit) {
    val pages = listOf(
        Page(Icons.Rounded.PlayCircle, R.string.ob1_title, R.string.ob1_text),
        Page(Icons.Rounded.Radar, R.string.ob2_title, R.string.ob2_text),
        Page(Icons.Rounded.Link, R.string.ob3_title, R.string.ob3_text),
        Page(Icons.Rounded.FolderOpen, R.string.ob4_title, R.string.ob4_text),
        if (tv) Page(Icons.Rounded.SettingsRemote, R.string.ob5_title_tv, R.string.ob5_text_tv)
        else Page(Icons.Rounded.TouchApp, R.string.ob5_title, R.string.ob5_text),
    )
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val next = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { next.requestFocus() } }
    val last = pager.currentPage == pages.size - 1

    Column(Modifier.fillMaxSize().themedBackdrop().safeDrawingPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(pager, modifier = Modifier.weight(1f).fillMaxWidth()) { i ->
            val p = pages[i]
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                IconBadge(p.icon, size = if (tv) 120.dp else 96.dp)
                Spacer(Modifier.height(24.dp))
                Text(stringResource(p.title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(p.text), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 560.dp),
                )
            }
        }
        // Sayfa noktaları
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 16.dp)) {
            pages.indices.forEach { i ->
                Box(
                    Modifier.size(if (i == pager.currentPage) 22.dp else 8.dp, 8.dp).clip(CircleShape)
                        .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Row(Modifier.fillMaxWidth().widthIn(max = 560.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!last) TextButton(onClick = onDone, modifier = Modifier.focusRing(CircleShape)) { Text(stringResource(R.string.ob_skip)) }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.focusRequester(next).focusRing(CircleShape),
            ) { Text(stringResource(if (last) R.string.ob_start else R.string.ob_next)) }
            Spacer(Modifier.width(4.dp))
        }
    }
}
