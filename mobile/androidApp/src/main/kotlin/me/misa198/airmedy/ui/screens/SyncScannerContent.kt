package me.misa198.airmedy.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Composable
internal fun SyncScannerContent(
    onQrScanned: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = LocalAirmedyColors.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var cameraGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var invalidQr by remember { mutableStateOf(false) }
    val claimed = remember { AtomicBoolean(false) }
    val scanner = remember { BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()) }
    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(LifecycleCameraController.IMAGE_ANALYSIS)
        }
    }
    val acceptCode: (String) -> Unit = { value ->
        if (claimed.compareAndSet(false, true) && !onQrScanned(value)) {
            invalidQr = true
            claimed.set(false)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null || !claimed.compareAndSet(false, true)) return@rememberLauncherForActivityResult
        runCatching { InputImage.fromFilePath(context, uri) }
            .onSuccess { image ->
                scanner.process(image)
                    .addOnSuccessListener { codes ->
                        val value = codes.firstOrNull()?.rawValue
                        if (value == null || !onQrScanned(value)) {
                            invalidQr = true
                            claimed.set(false)
                        }
                    }
                    .addOnFailureListener { invalidQr = true; claimed.set(false) }
            }
            .onFailure { invalidQr = true; claimed.set(false) }
    }

    DisposableEffect(scanner) {
        onDispose { scanner.close() }
    }
    DisposableEffect(controller, lifecycleOwner, cameraGranted) {
        if (cameraGranted) {
            controller.bindToLifecycle(lifecycleOwner)
            controller.setImageAnalysisAnalyzer(ContextCompat.getMainExecutor(context), QrAnalyzer(scanner, acceptCode))
        }
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
        }
    }
    LaunchedEffect(cameraGranted) {
        if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.sync_scan_description),
            color = colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp),
        )
        Box(
            modifier = Modifier.fillMaxWidth().widthIn(max = 300.dp).aspectRatio(1f)
                .clip(RoundedCornerShape(28.dp)).background(colors.glassElevated)
                .border(1.dp, colors.borderGlass, RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (cameraGranted) {
                AndroidView(
                    factory = { PreviewView(it).apply { this.controller = controller } },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(text = stringResource(R.string.sync_camera_required), color = colors.textMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(28.dp))
            }
        }
        Text(
            text = if (invalidQr) stringResource(R.string.sync_error_invalid_qr) else stringResource(R.string.sync_scan_hint),
            color = colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        OutlinedButton(
            onClick = { imageLauncher.launch("image/*") },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textMain),
            modifier = Modifier.padding(top = 20.dp),
        ) {
            Text(stringResource(R.string.sync_select_qr_image))
        }
    }
}

private class QrAnalyzer(
    private val scanner: BarcodeScanner,
    private val onValue: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    override fun analyze(imageProxy: ImageProxy) {
        val image = imageProxy.image ?: run { imageProxy.close(); return }
        scanner.process(InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { codes -> codes.firstOrNull()?.rawValue?.let(onValue) }
            .addOnCompleteListener { imageProxy.close() }
    }
}
