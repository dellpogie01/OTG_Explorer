package com.juanmediahub.usbotgorganizer.data.storage

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract

data class TreePickResult(
    val uri: Uri,
    val grantedFlags: Int
)

class OpenDocumentTreeContract : ActivityResultContract<Uri?, TreePickResult?>() {
    override fun createIntent(context: Context, input: Uri?): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
            if (input != null) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, input)
            }
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): TreePickResult? {
        if (resultCode != Activity.RESULT_OK) return null
        val uri = intent?.data ?: return null
        val flags = (intent.flags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            ))
        return TreePickResult(uri, flags)
    }
}
