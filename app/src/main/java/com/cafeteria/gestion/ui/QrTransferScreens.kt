package com.cafeteria.gestion.ui

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.util.Size as AndroidSize
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.cafeteria.gestion.data.CafeRepository
import com.cafeteria.gestion.data.Roles
import com.cafeteria.gestion.data.transfer.QrFrameAssembler
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

/**
 * Traspaso por QR sin internet.
 *
 * Enviar: el teléfono muestra una animación de códigos QR (uno por fragmento, en bucle) y un PIN de 6 dígitos.
 * Recibir: el otro teléfono escanea con la cámara, en cualquier orden; cuando tiene todos los fragmentos pide el PIN,
 * descifra, verifica e importa. Funciona en modo avión: solo usa cámara y pantalla.
 */
@Composable
fun QrTransferHub(state: CafeteriaUiState, vm: CafeteriaViewModel, onClose: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    var outgoing by remember { mutableStateOf<CafeRepository.QrOutgoing?>(null) }
    var preparing by remember { mutableStateOf(false) }
    var prepareError by remember { mutableStateOf<String?>(null) }
    val isOwner = state.session?.role == Roles.OWNER

    fun prepare() {
        preparing = true
        prepareError = null
        vm.prepareQrTransfer { result ->
            preparing = false
            result.onSuccess { outgoing = it }
            result.onFailure { prepareError = it.message ?: "No se pudo preparar el traspaso." }
        }
    }

    LaunchedEffect(tab) {
        if (tab == 0 && outgoing == null && !preparing && prepareError == null) prepare()
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Traspaso por QR", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            if (isOwner) "Sin internet: envía inventario y recibe las ventas del equipo"
                            else "Sin internet: pasa tus ventas al siguiente turno",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Cerrar") }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp)
                ) {
                    listOf("Enviar" to Icons.Outlined.QrCode2, "Recibir" to Icons.Outlined.QrCodeScanner).forEachIndexed { i, entry ->
                        val selected = tab == i
                        Row(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .clickable { tab = i }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                entry.second, null, modifier = Modifier.size(20.dp),
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                entry.first,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (tab == 0) {
                    QrSenderPanel(outgoing, preparing, prepareError, isOwner) {
                        outgoing = null
                        prepare()
                    }
                } else {
                    QrReceiverPanel(state, vm, isOwner, onClose)
                }
            }
        }
    }
}

// ───────────────────────────── Enviar ─────────────────────────────

@Composable
private fun QrSenderPanel(
    outgoing: CafeRepository.QrOutgoing?,
    preparing: Boolean,
    error: String?,
    isOwner: Boolean,
    onRefresh: () -> Unit
) {
    KeepScreenOn(bright = true)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            preparing -> {
                Spacer(Modifier.height(48.dp))
                CircularProgressIndicator()
                Text("Preparando y cifrando el paquete…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error != null -> {
                QrNotice(error, isError = true)
                Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Reintentar") }
            }
            outgoing != null -> SenderBody(outgoing, isOwner, onRefresh)
        }
    }
}

@Composable
private fun SenderBody(outgoing: CafeRepository.QrOutgoing, isOwner: Boolean, onRefresh: () -> Unit) {
    var index by remember(outgoing) { mutableStateOf(0) }
    var playing by remember(outgoing) { mutableStateOf(true) }
    var fps by remember(outgoing) { mutableStateOf(3) }
    val total = outgoing.frames.size

    LaunchedEffect(outgoing, playing, fps) {
        if (playing && total > 1) {
            while (true) {
                delay(1000L / fps)
                index = (index + 1) % total
            }
        }
    }

    val summary = buildList {
        if (outgoing.salesCount > 0) add("${outgoing.salesCount} venta(s)")
        if (outgoing.catalogCount > 0) add("inventario de ${outgoing.catalogCount} producto(s)")
    }.joinToString(" + ")

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text("PIN para quien escanea", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            outgoing.pin.chunked(3).joinToString(" "),
            fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 44.sp,
            letterSpacing = 4.sp, color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Díctaselo o enséñaselo: sin PIN el paquete no se abre.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
    }

    QrCodeCanvas(
        text = outgoing.frames[index.coerceIn(0, total - 1)],
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
    )

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Código ${index + 1} de $total", fontWeight = FontWeight.SemiBold)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(progress = { (index + 1f) / total }, modifier = Modifier.fillMaxWidth())
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(onClick = { playing = !playing }) {
            Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (playing) "Pausar" else "Seguir")
        }
        listOf("Lento" to 2, "Normal" to 3, "Rápido" to 5).forEach { (label, value) ->
            FilterChip(selected = fps == value, onClick = { fps = value }, label = { Text(label) })
        }
    }

    QrNotice(
        if (isOwner) "Mantén esta pantalla abierta hasta que el otro teléfono llegue al 100 %. Las trabajadoras reciben tu inventario y precios actuales; tú recibes sus ventas con el mismo método."
        else "Mantén esta pantalla abierta hasta que el otro teléfono llegue al 100 %. Si se salta algún código no pasa nada: se repiten en bucle. Usa «Lento» si la cámara del otro teléfono es modesta.",
        isError = false
    )
    OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Preparar de nuevo (PIN y ventas nuevos)")
    }
    Spacer(Modifier.height(12.dp))
}

private fun buildQrMatrix(text: String): BitMatrix {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 0,
        EncodeHintType.CHARACTER_SET to "UTF-8"
    )
    return QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
}

/** Dibuja el QR con celdas de tamaño entero (nítido), fondo blanco y zona de silencio de 4 módulos. */
@Composable
private fun QrCodeCanvas(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { runCatching { buildQrMatrix(text) }.getOrNull() }
    Canvas(modifier) {
        drawRect(Color.White)
        val m = matrix ?: return@Canvas
        val quiet = 4
        val modules = m.width + quiet * 2
        val cell = kotlin.math.floor(size.minDimension / modules)
        if (cell < 1f) return@Canvas
        val used = cell * modules
        val left = (size.width - used) / 2f + quiet * cell
        val top = (size.height - used) / 2f + quiet * cell
        for (y in 0 until m.height) {
            for (x in 0 until m.width) {
                if (m.get(x, y)) {
                    drawRect(Color.Black, topLeft = Offset(left + x * cell, top + y * cell), size = Size(cell, cell))
                }
            }
        }
    }
}

// ───────────────────────────── Recibir ─────────────────────────────

private enum class RxPhase { SCANNING, ASK_PIN, DONE }

@Composable
private fun QrReceiverPanel(state: CafeteriaUiState, vm: CafeteriaViewModel, isOwner: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    KeepScreenOn(bright = false)
    val assembler = remember { QrFrameAssembler() }
    var phase by remember { mutableStateOf(RxPhase.SCANNING) }
    var received by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var assembled by remember { mutableStateOf<ByteArray?>(null) }
    var pin by remember { mutableStateOf("") }
    var attempts by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var foreignSeen by remember { mutableStateOf(false) }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA) }

    fun restart() {
        assembler.reset()
        phase = RxPhase.SCANNING
        received = 0; total = 0; assembled = null; pin = ""; attempts = 0; error = null; resultMessage = null; foreignSeen = false
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        when (phase) {
            RxPhase.SCANNING -> {
                if (!granted) {
                    QrNotice("Necesito permiso de cámara para leer los códigos QR. No se guarda ninguna foto ni video.", isError = true)
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth()) { Text("Permitir cámara") }
                    Text(
                        "Si ya lo negaste dos veces, actívalo en Ajustes del teléfono → Aplicaciones → Adenium Coofe → Permisos.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp))
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
                    ) {
                        QrCameraPreview(
                            modifier = Modifier.fillMaxSize(),
                            enabled = phase == RxPhase.SCANNING,
                            onQr = { text ->
                                when (assembler.accept(text)) {
                                    QrFrameAssembler.Result.ACCEPTED -> {
                                        received = assembler.receivedCount()
                                        total = assembler.total()
                                        if (assembler.isComplete()) {
                                            assembled = assembler.assemble()
                                            phase = RxPhase.ASK_PIN
                                        }
                                    }
                                    QrFrameAssembler.Result.OTHER_SESSION -> { foreignSeen = true }
                                    else -> Unit
                                }
                            }
                        )
                    }
                    if (total == 0) {
                        Text(
                            "Apunta la cámara al QR del otro teléfono. Empieza a leer solo.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Recibidos $received de $total", fontWeight = FontWeight.SemiBold)
                            Text("${received * 100 / total} %", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(progress = { received.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                        ProgressMosaic(total = total, received = received, has = assembler::has)
                        Text(
                            "Sigue apuntando: los códigos que falten volverán a pasar. No importa el orden.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (foreignSeen) {
                        QrNotice("Estás viendo códigos de otro envío distinto al que empezaste a leer. Si quieres cambiar de envío, pulsa «Empezar de nuevo».", isError = false)
                    }
                    if (total > 0) {
                        OutlinedButton(onClick = { restart() }, modifier = Modifier.fillMaxWidth()) { Text("Empezar de nuevo") }
                    }
                }
                QrNotice(
                    if (isOwner) "Aquí recibes las ventas de tus trabajadoras. Se suman a tu registro sin duplicarse y se enviarán al servidor cuando tengas internet."
                    else "Aquí recibes el inventario de Yuri o las ventas de tu compañera del turno anterior. Tu inventario se actualiza solo.",
                    isError = false
                )
            }
            RxPhase.ASK_PIN -> {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text("Paquete completo ($total códigos)", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("Escribe el PIN de 6 dígitos que muestra el teléfono que envía.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { v -> pin = v.filter { it.isDigit() }.take(6); error = null },
                        label = { Text("PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let { QrNotice(it, isError = true) }
                    Button(
                        onClick = {
                            val bytes = assembled
                            if (bytes != null) {
                                error = null
                                vm.receiveQrTransfer(bytes, pin) { result ->
                                    result.onSuccess {
                                        resultMessage = it
                                        phase = RxPhase.DONE
                                    }
                                    result.onFailure {
                                        attempts += 1
                                        if (attempts >= 5) {
                                            restart()
                                            error = "Demasiados intentos con PIN incorrecto. Vuelve a escanear el paquete."
                                        } else {
                                            error = it.message ?: "No se pudo abrir el paquete."
                                        }
                                    }
                                }
                            }
                        },
                        enabled = pin.length == 6 && !state.busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        else Text("Importar", fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "Se comprueba que el paquete es de esta cafetería, que no fue alterado y que ninguna venta se duplique.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { restart() }, modifier = Modifier.fillMaxWidth()) { Text("Escanear otra vez") }
            }
            RxPhase.DONE -> {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text("Traspaso recibido", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Text(resultMessage.orEmpty(), color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Listo") }
                OutlinedButton(onClick = { restart() }, modifier = Modifier.fillMaxWidth()) { Text("Recibir otro paquete") }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** Cuadrícula con un cuadrito por fragmento: se rellena a medida que se reciben. */
@Composable
private fun ProgressMosaic(total: Int, received: Int, has: (Int) -> Boolean) {
    val filled = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val columns = if (total <= 40) 10 else 20
    val rows = (total + columns - 1) / columns
    val rowHeight = if (total <= 40) 18.dp else 10.dp
    Canvas(Modifier.fillMaxWidth().height(rowHeight * rows)) {
        val gap = 2.dp.toPx()
        val cell = (size.width - gap * (columns - 1)) / columns
        val corner = CornerRadius(cell / 4f, cell / 4f)
        val cellHeight = rowHeight.toPx() - gap
        // `received` participa en el dibujo para que Compose repinte cuando cambia.
        val seen = received
        for (i in 0 until total) {
            val r = i / columns
            val c = i % columns
            drawRoundRect(
                color = if (seen >= 0 && has(i)) filled else empty,
                topLeft = Offset(c * (cell + gap), r * rowHeight.toPx()),
                size = Size(cell, cellHeight),
                cornerRadius = corner
            )
        }
    }
}

// ───────────────────────────── Cámara ─────────────────────────────

private fun Context.findLifecycleOwner(): LifecycleOwner? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return null
}

private class QrAnalyzer(
    private val active: () -> Boolean,
    private val onText: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true
            )
        )
    }

    override fun analyze(image: ImageProxy) {
        try {
            if (!active()) return
            val plane = image.planes[0]
            val width = image.width
            val height = image.height
            val rowStride = plane.rowStride
            val buffer = plane.buffer
            val data = ByteArray(width * height)
            for (row in 0 until height) {
                buffer.position(row * rowStride)
                buffer.get(data, row * width, width)
            }
            val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
            val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            result.text?.let(onText)
        } catch (_: Exception) {
            // Sin QR legible en este cuadro: es lo normal, se intenta con el siguiente.
        } finally {
            reader.reset()
            image.close()
        }
    }
}

@Composable
private fun QrCameraPreview(modifier: Modifier, enabled: Boolean, onQr: (String) -> Unit) {
    val context = LocalContext.current
    val owner = remember(context) { context.findLifecycleOwner() }
    val enabledState = rememberUpdatedState(enabled)
    val onQrState = rememberUpdatedState(onQr)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val providerHolder = remember { arrayOfNulls<ProcessCameraProvider>(1) }

    DisposableEffect(Unit) {
        onDispose {
            providerHolder[0]?.unbindAll()
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val mainExecutor = ContextCompat.getMainExecutor(ctx)
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val lifecycleOwner = owner ?: return@addListener
                try {
                    val provider = future.get()
                    providerHolder[0] = provider
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val selector = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(AndroidSize(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                        )
                        .build()
                    val analysis = ImageAnalysis.Builder()
                        .setResolutionSelector(selector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(
                        executor,
                        QrAnalyzer(active = { enabledState.value }, onText = { text -> mainExecutor.execute { onQrState.value(text) } })
                    )
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (_: Exception) {
                    // Cámara no disponible: la pantalla seguirá mostrando el visor vacío y el texto de ayuda.
                }
            }, mainExecutor)
            previewView
        }
    )
}

// ───────────────────────────── Utilidades ─────────────────────────────

@Composable
private fun KeepScreenOn(bright: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, bright) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val previous = window?.attributes?.screenBrightness
        if (window != null) {
            if (bright) window.attributes = window.attributes.also { it.screenBrightness = 1f }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (window != null) {
                if (bright) window.attributes = window.attributes.also {
                    it.screenBrightness = previous ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}

@Composable
private fun QrNotice(message: String, isError: Boolean) {
    val bg = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg).padding(12.dp), verticalAlignment = Alignment.Top) {
        Icon(if (isError) Icons.Outlined.WarningAmber else Icons.Outlined.QrCode2, null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(message, color = fg, style = MaterialTheme.typography.bodySmall)
    }
}
