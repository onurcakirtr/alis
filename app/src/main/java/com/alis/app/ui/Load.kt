package com.alis.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alis.app.R
import com.alis.core.data.Graph
import com.alis.core.data.graph
import com.alis.core.model.AccessException
import com.alis.core.model.AccessState
import com.alis.core.model.Server
import kotlinx.coroutines.CancellationException

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Fail(val error: Throwable) : Load<Nothing>
}

class LoadHandle<T>(val state: Load<T>, val reload: () -> Unit, val refreshing: Boolean = false) {
    val value: T? get() = (state as? Load.Ok<T>)?.value
}

/**
 * Bir veriyi yükler; anahtarlar değişince ya da [LoadHandle.reload] ile yeniden.
 * Yeniden yüklemede eski veri ekranda kalır (titreme olmasın).
 */
@Composable
fun <T> rememberLoad(vararg keys: Any?, block: suspend () -> T): LoadHandle<T> {
    var tick by remember { mutableIntStateOf(0) }
    var state by remember(*keys) { mutableStateOf<Load<T>>(Load.Loading) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(*keys, tick) {
        busy = true
        if (state !is Load.Ok) state = Load.Loading
        try {
            state = try {
                Load.Ok(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Load.Fail(e)
            }
        } finally {
            // aynı veri geri gelse de (durum değişmese de) yenileniyor göstergesi kapanır
            busy = false
        }
    }
    return LoadHandle(state, { tick++ }, busy && state is Load.Ok)
}

@Composable
fun graph(): Graph = LocalContext.current.graph

@Composable
fun rememberServer(id: String): Server? {
    val g = graph()
    val list by g.servers.servers.collectAsStateWithLifecycle()
    return list.firstOrNull { it.id == id } ?: g.servers.byId(id)
}

/** Hata metni (erişim durumları dahil). */
@Composable
fun errorText(e: Throwable): String = when ((e as? AccessException)?.state) {
    AccessState.OFFLINE -> stringResource(R.string.err_offline)
    AccessState.BLOCKED -> stringResource(R.string.acc_blocked)
    AccessState.OFF -> stringResource(R.string.err_off)
    AccessState.PIN, AccessState.PIN_LOCKED -> stringResource(R.string.acc_pin_title)
    AccessState.APPROVAL -> stringResource(R.string.acc_wait_title)
    AccessState.LOGIN -> stringResource(R.string.acc_login_title)
    else -> when (e.message) {
        "pair" -> stringResource(R.string.fm_need_pair)
        "device" -> stringResource(R.string.fm_need_permission)
        "off" -> stringResource(R.string.err_off)
        "exists" -> stringResource(R.string.fm_exists)
        else -> e.message ?: e.javaClass.simpleName
    }
}
