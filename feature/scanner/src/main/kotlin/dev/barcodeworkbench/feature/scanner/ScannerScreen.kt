package dev.barcodeworkbench.feature.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import dev.barcodeworkbench.core.model.SymbologyRegistry

@Composable
fun ScannerScreen(
    modifier: Modifier = Modifier,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }
    var showSymbologies by remember { mutableStateOf(false) }

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionRequested = true
    }

    // Asked on arrival rather than behind a button: the screen has no other purpose,
    // so an extra tap would be friction without informing the decision.
    LaunchedEffect(Unit) {
        if (!hasPermission) requestPermission.launch(Manifest.permission.CAMERA)
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            }
            if (bitmap != null) viewModel.decodeImage(bitmap)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // weight(1f), not heightIn(min = ...). A minimum height is only a
                // floor, and the preview inside fills whatever it is given, so the
                // Box grew to the full screen and pushed every control off the
                // bottom of a non-scrollable Column. With a weight, the controls are
                // measured at their natural height first and the preview takes
                // exactly what is left.
                .weight(1f)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            when {
                hasPermission && !state.isFinished -> CameraPreview(
                    torchOn = state.torchOn,
                    onFrame = viewModel::analyseFrame,
                    onTorchAvailability = viewModel::setTorchAvailable,
                    modifier = Modifier.fillMaxSize(),
                )

                hasPermission && state.isFinished -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Scan captured",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Button(onClick = viewModel::restartAfterSingleScan) { Text("Scan again") }
                }

                else -> PermissionPrompt(
                    requested = permissionRequested,
                    onRequest = { requestPermission.launch(Manifest.permission.CAMERA) },
                )
            }

            if (state.isDecodingFile) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Scrollable so a long capture list or a narrow screen cannot hide
                // the controls again.
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScanMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.mode == mode,
                        onClick = { viewModel.setMode(mode) },
                        label = {
                            Text(if (mode == ScanMode.SINGLE) "Single" else "Continuous")
                        },
                    )
                }
                if (state.torchAvailable) {
                    FilterChip(
                        selected = state.torchOn,
                        onClick = { viewModel.setTorch(!state.torchOn) },
                        label = { Text("Torch") },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickImage.launch(arrayOf("image/*")) }) {
                    Text("From image…")
                }
                OutlinedButton(onClick = { showSymbologies = true }) {
                    Text("Symbologies (${state.enabledSymbologies.size})")
                }
                if (state.hasCaptures) {
                    TextButton(onClick = viewModel::clearCaptures) { Text("Clear") }
                }
            }

            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (state.mode == ScanMode.CONTINUOUS) {
                Text(
                    text = "${state.captureCount} captured",
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            if (state.hasCaptures) {
                // A plain Column, not LazyColumn: nesting a lazy list inside a
                // vertically scrollable parent is an unbounded-height error at
                // runtime. Capture counts here are small enough that laziness buys
                // nothing.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.captures.reversed().take(MAX_VISIBLE_CAPTURES).forEach { capture ->
                        CaptureRow(capture = capture, onClick = { viewModel.inspect(capture) })
                    }
                    if (state.captures.size > MAX_VISIBLE_CAPTURES) {
                        Text(
                            text = "and ${state.captures.size - MAX_VISIBLE_CAPTURES} more",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Surfaced because a decode time creeping up is the visible symptom of
            // too many enabled symbologies.
            if (state.lastFrameDecodeMs > 0) {
                Text(
                    text = "Last frame decoded in ${state.lastFrameDecodeMs} ms, " +
                        "${state.enabledSymbologies.size} of " +
                        "${SymbologyRegistry.readable.size} symbologies enabled",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showSymbologies) {
        SymbologySettingsSheet(
            enabled = state.enabledSymbologies,
            onToggle = viewModel::toggleSymbology,
            onEnableAll = viewModel::enableAllSymbologies,
            onDismiss = { showSymbologies = false },
        )
    }

    state.inspecting?.let { capture ->
        ScanResultSheet(
            capture = capture,
            onDismiss = { viewModel.inspect(null) },
            onScanAgain = if (state.isFinished) {
                {
                    viewModel.inspect(null)
                    viewModel.restartAfterSingleScan()
                }
            } else {
                null
            },
            libraryNames = state.libraries.map { it.name },
            onSave = { name -> viewModel.saveToLibrary(capture, name) },
        )
    }
}

private const val MAX_VISIBLE_CAPTURES = 20

@Composable
private fun PermissionPrompt(requested: Boolean, onRequest: () -> Unit) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (requested) {
                "Camera access is needed to scan. You can grant it in system settings."
            } else {
                "Camera access is needed to scan."
            },
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onRequest) { Text("Grant access") }
    }
}

@Composable
private fun CaptureRow(capture: CapturedScan, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = capture.barcode.symbology
                    ?.let { SymbologyRegistry.find(it)?.displayName }
                    ?: capture.barcode.rawFormatName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = capture.barcode.text.ifEmpty {
                    "${capture.barcode.bytes.size} bytes of binary data"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
