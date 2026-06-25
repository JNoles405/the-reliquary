package com.reliquary.app.scan

import androidx.compose.runtime.Composable

// Desktop has no camera scanner; USB barcode scanners type into the text field
// directly, so manual entry covers the desktop case.
@Composable
actual fun rememberBarcodeScanner(): BarcodeScanner? = null
