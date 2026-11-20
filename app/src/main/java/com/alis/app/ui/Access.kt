package com.alis.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.ToggleOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alis.app.R
import com.alis.core.akis.LoginResult
import com.alis.core.akis.PairResult
import com.alis.core.model.AccessException
import com.alis.core.model.AccessState
import com.alis.core.model.Server
import com.alis.core.ui.ExpressiveCard
import com.alis.core.ui.IconBadge
import com.alis.core.ui.LoadingBox
import com.alis.core.ui.MessageBox
import com.alis.core.ui.focusRing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Yükleme durumunu çizer: yükleniyor / hata / erişim gerekiyor / içerik.
 * Akış onay, PIN ya da uzaktan giriş isterse ilgili paneli gösterir; tamamlanınca yeniden yükler.
 */
@Composable
fun <T> LoadView(handle: LoadHandle<T>, server: Server?, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    when (val st = handle.state) {
        is Load.Loading -> LoadingBox(modifier)
        is Load.Ok -> content(st.value)
        is Load.Fail -> {
            val acc = (st.error as? AccessException)?.state
            if (server != null && acc != null && acc != AccessState.OFFLINE && acc != AccessState.OFF) {
                AccessPanel(server, acc, modifier, onDone = handle.reload)
            } else {
                MessageBox(
                    when (acc) {
                        AccessState.OFFLINE -> Icons.Rounded.CloudOff
                        AccessState.OFF -> Icons.Rounded.ToggleOff
                        else -> Icons.Rounded.ErrorOutline
                    },
                    errorText(st.error),
                    if (acc == AccessState.OFFLINE) stringResource(R.string.err_offline_sub) else null,
                    stringResource(R.string.retry),
                    modifier,
                    onAction = handle.reload,
                )
            }
        }
    }
}

/** Akış'ın erişim istekleri: telefonda onay, PIN, uzaktan giriş (şifre + iki adımlı kod). */
@Composable
fun AccessPanel(server: Server, state: AccessState, modifier: Modifier = Modifier, onDone: () -> Unit) {
    val g = graph()
    val nav = nav()
    val scope = rememberCoroutineScope()
    val client = remember(server) { g.servers.client(server) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val wrongPin = stringResource(R.string.acc_pin_wrong)
    val wrongPw = stringResource(R.string.acc_login_wrong)
    val locked = stringResource(R.string.acc_locked)

    Box(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), contentAlignment = Alignment.Center) {
        ExpressiveCard(Modifier.widthIn(max = 520.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                when (state) {
                    AccessState.APPROVAL -> {
                        // Akış'ta "İzin ver" basılınca kendiliğinden devam eder
                        LaunchedEffect(Unit) {
                            while (true) {
                                delay(2500)
                                if (runCatching { client.conf() }.isSuccess) {
                                    onDone()
                                    break
                                }
                            }
                        }
                        IconBadge(Icons.Rounded.HourglassTop, size = 72.dp)
                        Text(stringResource(R.string.acc_wait_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        Text(stringResource(R.string.acc_wait_text, server.name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        CircularProgressIndicator(Modifier.size(28.dp))
                        if (!server.remote) PairHint(server)
                    }
                    AccessState.PIN, AccessState.PIN_LOCKED -> {
                        var pin by remember { mutableStateOf("") }
                        IconBadge(Icons.Rounded.Password, size = 72.dp)
                        Text(stringResource(R.string.acc_pin_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.acc_pin_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        val submit = {
                            busy = true
                            scope.launch {
                                val ok = runCatching { client.pin(pin) }.getOrDefault(false)
                                busy = false
                                if (ok) onDone() else error = wrongPin
                            }
                            Unit
                        }
                        OutlinedTextField(
                            pin, { pin = it.filter(Char::isDigit).take(12); error = null },
                            label = { Text(stringResource(R.string.acc_pin_label)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            isError = error != null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (state == AccessState.PIN_LOCKED) Text(locked, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Button(onClick = submit, enabled = !busy && pin.isNotEmpty(), modifier = Modifier.focusRing(CircleShape)) { Text(stringResource(R.string.acc_unlock)) }
                        if (!server.remote) PairHint(server)
                    }
                    AccessState.LOGIN -> {
                        var pw by remember { mutableStateOf("") }
                        var code by remember { mutableStateOf("") }
                        var waitId by remember { mutableStateOf<String?>(null) }
                        val wid = waitId
                        if (wid != null) {
                            LaunchedEffect(wid) {
                                while (true) {
                                    delay(1500)
                                    when (client.remoteLoginStatus(wid)) {
                                        "ok" -> { onDone(); return@LaunchedEffect }
                                        "pending" -> {}
                                        else -> { waitId = null; error = wrongPw; return@LaunchedEffect }
                                    }
                                }
                            }
                        }
                        IconBadge(if (wid != null) Icons.Rounded.PhonelinkLock else Icons.Rounded.Lock, size = 72.dp)
                        Text(stringResource(if (wid != null) R.string.acc_wait_title else R.string.acc_login_title), style = MaterialTheme.typography.titleLarge)
                        if (wid != null) {
                            Text(stringResource(R.string.acc_login_wait), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            CircularProgressIndicator(Modifier.size(28.dp))
                        } else {
                            Text(stringResource(R.string.acc_login_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            OutlinedTextField(
                                pw, { pw = it; error = null }, label = { Text(stringResource(R.string.acc_password)) }, singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                code, { code = it.filter(Char::isDigit).take(8); error = null }, label = { Text(stringResource(R.string.acc_code)) }, singleLine = true,
                                supportingText = { Text(stringResource(R.string.acc_code_hint)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            Button(
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        when (val r = runCatching { client.remoteLogin(pw, code) }.getOrElse { LoginResult.Error("net") }) {
                                            LoginResult.Ok -> onDone()
                                            is LoginResult.Wait -> waitId = r.id
                                            is LoginResult.Error -> error = if (r.reason == "locked") locked else wrongPw
                                        }
                                        busy = false
                                    }
                                },
                                enabled = !busy && pw.isNotEmpty(),
                                modifier = Modifier.focusRing(CircleShape),
                            ) { Text(stringResource(R.string.acc_login)) }
                        }
                    }
                    AccessState.BLOCKED -> {
                        IconBadge(Icons.Rounded.Block, size = 72.dp)
                        Text(stringResource(R.string.acc_blocked), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        Text(stringResource(R.string.acc_blocked_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        FilledTonalButton(onClick = onDone, modifier = Modifier.focusRing(CircleShape)) { Text(stringResource(R.string.retry)) }
                    }
                    else -> {
                        Text(stringResource(R.string.err_off), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { nav.pop() }) { Text(stringResource(R.string.back)) }
                    }
                }
            }
        }
    }
}

/** Erişim panellerinde "kalıcı çözüm" önerisi: eşleştir. */
@Composable
private fun PairHint(server: Server) {
    val nav = nav()
    Text(stringResource(R.string.acc_pair_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    FilledTonalButton(onClick = { nav.push(Route.Pair(server.id)) }, modifier = Modifier.focusRing(CircleShape)) {
        androidx.compose.material3.Icon(Icons.Rounded.Link, null, Modifier.size(18.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.pair_action))
    }
}

/**
 * Eşleştirme: Akış'a istek gönderir, telefonda "İzin ver"e basılınca anahtar kaydedilir.
 * ("Bu ağa güveniyorum" açıksa hemen.) İsteğe bağlı yedek: Akış'ın gösterdiği 6 haneli kod.
 */
@Composable
fun PairPanel(server: Server, startWithCode: Boolean, modifier: Modifier = Modifier, onPaired: () -> Unit) {
    val g = graph()
    val scope = rememberCoroutineScope()
    val client = remember(server) { g.servers.client(server) }
    val name = g.settings.current.deviceName
    var phase by remember { mutableStateOf(if (startWithCode) "code" else "request") } // request | wait | denied | failed | code
    var reqId by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf(false) }

    fun ok(token: String) {
        g.servers.setToken(server.id, token)
        onPaired()
    }

    if (phase == "request") {
        LaunchedEffect(Unit) {
            when (val r = runCatching { client.pairRequest(name) }.getOrElse { PairResult.Failed }) {
                is PairResult.Ok -> ok(r.token)
                is PairResult.Pending -> { reqId = r.id; phase = "wait" }
                PairResult.Denied -> phase = "denied"
                PairResult.Failed -> phase = "failed"
            }
        }
    }
    val rid = reqId
    if (phase == "wait" && rid != null) {
        LaunchedEffect(rid) {
            while (true) {
                delay(2000)
                when (val r = runCatching { client.pairStatus(rid) }.getOrElse { PairResult.Pending(rid) }) {
                    is PairResult.Ok -> { ok(r.token); return@LaunchedEffect }
                    is PairResult.Pending -> {}
                    PairResult.Denied -> { phase = "denied"; return@LaunchedEffect }
                    PairResult.Failed -> { phase = "failed"; return@LaunchedEffect }
                }
            }
        }
    }

    Box(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), contentAlignment = Alignment.Center) {
        ExpressiveCard(Modifier.widthIn(max = 560.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                IconBadge(Icons.Rounded.Link, size = 72.dp)
                Text(stringResource(R.string.pair_title, server.name), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                when (phase) {
                    "request", "wait" -> {
                        Text(stringResource(R.string.pair_wait_text, name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                    "denied" -> Text(stringResource(R.string.pair_denied), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    "failed" -> Text(stringResource(R.string.pair_failed), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    "code" -> {
                        Text(stringResource(R.string.pair_code_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        OutlinedTextField(
                            code, { code = it.filter(Char::isDigit).take(6); codeError = false },
                            label = { Text(stringResource(R.string.pair_code_label)) }, singleLine = true, isError = codeError,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (codeError) Text(stringResource(R.string.pair_code_wrong), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = {
                                scope.launch {
                                    when (val r = runCatching { client.pairWithCode(code, name) }.getOrElse { PairResult.Failed }) {
                                        is PairResult.Ok -> ok(r.token)
                                        else -> codeError = true
                                    }
                                }
                            },
                            enabled = code.length == 6,
                            modifier = Modifier.focusRing(CircleShape),
                        ) { Text(stringResource(R.string.pair_action)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (phase != "request" && phase != "wait") {
                        FilledTonalButton(onClick = { phase = "request" }, modifier = Modifier.focusRing(CircleShape)) { Text(stringResource(R.string.pair_send_again)) }
                    }
                    if (phase != "code") {
                        TextButton(onClick = { phase = "code" }, modifier = Modifier.focusRing(CircleShape)) { Text(stringResource(R.string.pair_with_code)) }
                    }
                }
            }
        }
    }
}
