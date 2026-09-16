package com.codezmr.nullflow.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.codezmr.nullflow.AppLog
import java.io.File

/**
 * Launches the Android share sheet for a dossier PNG file.
 *
 * Uses [FileProvider.getUriForFile] to turn the cacheDir file path into a
 * secure content:// URI (required on Android 11+), then fires an
 * ACTION_SEND intent with type image/png + FLAG_GRANT_READ_URI_PERMISSION.
 */
object DossierShare {

    fun share(context: Context, filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                AppLog.e("DossierShare: file missing: $filePath")
                return
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "I deflected distractions and stayed focused with NullFlow. " +
                        "Disconnect on your terms."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Share my focus dossier")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            AppLog.d("DossierShare: share sheet launched for $uri")
        } catch (e: Exception) {
            AppLog.e("DossierShare: FAILED", e)
        }
    }
}
