package com.reliquary.app.util

/**
 * Open a native file picker for an image and copy the chosen file into the app's
 * photos directory, returning the saved path. Returns null if cancelled or
 * unsupported (e.g. Android, for now).
 */
expect fun pickAndStoreImage(): String?
