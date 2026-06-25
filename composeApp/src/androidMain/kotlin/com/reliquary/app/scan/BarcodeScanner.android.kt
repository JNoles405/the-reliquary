package com.reliquary.app.scan

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Composable
actual fun rememberBarcodeScanner(): BarcodeScanner? {
    val context = LocalContext.current
    return remember(context) { AndroidBarcodeScanner(context) }
}

/**
 * Uses Google Play services' ML Kit code scanner, which presents its own camera
 * UI and needs no camera permission in the manifest. Targets the formats found
 * on books, discs, games, and comics.
 */
private class AndroidBarcodeScanner(context: Context) : BarcodeScanner {
    private val scanner = GmsBarcodeScanning.getClient(
        context,
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
            )
            .build(),
    )

    override suspend fun scan(): String? = suspendCancellableCoroutine { cont ->
        scanner.startScan()
            .addOnSuccessListener { barcode -> if (cont.isActive) cont.resume(barcode.rawValue) }
            .addOnCanceledListener { if (cont.isActive) cont.resume(null) }
            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
    }
}
