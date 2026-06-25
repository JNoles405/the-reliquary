package com.reliquary.app.scan

import androidx.compose.runtime.Composable

/** A camera-based barcode scanner. Returns the scanned value, or null if cancelled. */
interface BarcodeScanner {
    suspend fun scan(): String?
}

/**
 * Provides a platform scanner, or null when the platform has no camera scanning
 * (desktop) — callers fall back to manual / USB-scanner keyboard entry there.
 */
@Composable
expect fun rememberBarcodeScanner(): BarcodeScanner?
