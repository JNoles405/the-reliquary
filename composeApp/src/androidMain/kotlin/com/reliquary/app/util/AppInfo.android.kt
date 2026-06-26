package com.reliquary.app.util

import android.content.Intent
import android.net.Uri
import com.reliquary.app.sync.AndroidAppContext

actual fun openUrl(url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        AndroidAppContext.context.startActivity(intent)
    }
}
