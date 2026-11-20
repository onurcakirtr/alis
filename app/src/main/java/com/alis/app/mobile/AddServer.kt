package com.alis.app.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.alis.app.R
import com.alis.app.ui.Route
import com.alis.app.ui.device
import com.alis.app.ui.graph
import com.alis.app.ui.nav
import com.alis.core.data.Feature
import com.alis.core.ui.ExpressiveCard
import com.alis.core.ui.SectionTitle
import com.alis.core.ui.focusRing
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/** Adres / QR ile sunucu ekleme. QR yalnızca kameralı cihazda ve özellik açıksa görünür. */
@Composable
fun AddServerScreen() {
    val g = graph()
    val nav = nav()
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val qr = device().hasCamera && g.settings.current.on(Feature.QR_ADD)

    fun add() {
        busy = true
        error = false
        scope.launch {
            val r = g.servers.addAddress(address)
            busy = false
            r.onSuccess { s -> nav.replace(Route.Home(s.id)) }.onFailure { error = true }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.add_server))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ExpressiveCard {
                Text(stringResource(R.string.add_address_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    address, { address = it.trim(); error = false },
                    label = { Text(stringResource(R.string.add_address_label)) },
                    placeholder = { Text("192.168.1.20:8200") },
                    singleLine = true,
                    isError = error,
                    supportingText = { Text(stringResource(if (error) R.string.add_failed else R.string.add_address_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (address.isNotEmpty()) add() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { add() }, enabled = !busy && address.isNotEmpty(), modifier = Modifier.focusRing(CircleShape)) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.add))
                }
            }
            if (qr) {
                ExpressiveCard(onClick = { nav.push(Route.Scan) }) {
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        com.alis.core.ui.IconBadge(Icons.Rounded.QrCodeScanner)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(stringResource(R.string.scan_qr), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.scan_qr_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            SectionTitle(stringResource(R.string.add_tips))
            ExpressiveCard {
                Text(stringResource(R.string.add_tips_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** QR okuyucu: Akış'ın adres QR'ı, QR kartı ya da eşleştirme QR'ı (alis://pair). */
@Composable
fun ScanScreen() {
    val ctx = LocalContext.current
    val g = graph()
    val nav = nav()
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var handled by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val wifiMsg = stringResource(R.string.scan_wifi)
    val badMsg = stringResource(R.string.scan_unknown)

    fun onCode(text: String) {
        if (handled) return
        when {
            text.startsWith("alis://") -> {
                handled = true
                (ctx as? MobileActivity)?.pairFromUri(Uri.parse(text))
            }
            text.startsWith("http://") || text.startsWith("https://") -> {
                handled = true
                scope.launch {
                    val r = g.servers.addAddress(text.substringBefore("/#").substringBefore("?"))
                    r.onSuccess { s -> nav.pop(); nav.replace(Route.Home(s.id)) }.onFailure { handled = false; message = badMsg }
                }
            }
            text.startsWith("WIFI:") -> message = wifiMsg
            else -> message = badMsg
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.scan_qr))
        if (!granted) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.scan_need_camera), textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.grant)) }
            }
            DisposableEffect(Unit) {
                launcher.launch(Manifest.permission.CAMERA)
                onDispose { }
            }
        } else {
            Box(Modifier.fillMaxSize().padding(16.dp)) {
                CameraQr(Modifier.fillMaxSize().border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)) { t -> onCode(t) }
                Text(
                    message ?: stringResource(R.string.scan_hint),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                )
            }
        }
    }
}

@Composable
private fun CameraQr(modifier: Modifier, onResult: (String) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val exec = remember { Executors.newSingleThreadExecutor() }
    val main = remember { ContextCompat.getMainExecutor(ctx) }
    AndroidView(
        factory = { c ->
            val view = PreviewView(c)
            val future = ProcessCameraProvider.getInstance(c)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                val reader = MultiFormatReader().apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))) }
                analysis.setAnalyzer(exec) { img: ImageProxy ->
                    try {
                        val plane = img.planes[0]
                        val buf = plane.buffer
                        val data = ByteArray(buf.remaining())
                        buf.get(data)
                        val src = PlanarYUVLuminanceSource(data, plane.rowStride, img.height, 0, 0, img.width, img.height, false)
                        val r = reader.decodeWithState(BinaryBitmap(HybridBinarizer(src)))
                        main.execute { onResult(r.text) }
                    } catch (_: Exception) {
                    } finally {
                        reader.reset()
                        img.close()
                    }
                }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            }, main)
            view
        },
        modifier = modifier,
    )
    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(ctx).get().unbindAll() }
            exec.shutdown()
        }
    }
}
