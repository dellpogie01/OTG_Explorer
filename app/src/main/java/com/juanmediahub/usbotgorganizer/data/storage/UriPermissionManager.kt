package com.juanmediahub.usbotgorganizer.data.storage

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import com.juanmediahub.usbotgorganizer.BuildConfig

/**
 * Persists only the URI permission modes that Android actually granted to the
 * picker result, and releases the same modes when a location is forgotten.
 */
class UriPermissionManager(private val resolver: ContentResolver) {
    fun persist(uri: Uri, grantedFlags: Int): PersistedAccess {
        val requested = grantedFlags and (READ or WRITE)
        if (requested != 0) {
            try {
                resolver.takePersistableUriPermission(uri, requested)
            } catch (_: SecurityException) {
                // Some providers accept read but reject a combined read/write
                // request. Only fall back when read itself was granted.
                if (requested and READ != 0) {
                    runCatching { resolver.takePersistableUriPermission(uri, READ) }
                }
                log("persistable permission was not granted")
            }
        } else {
            // A few providers omit the result flags. Attempt a read grant only;
            // never assume write access that was not granted.
            runCatching { resolver.takePersistableUriPermission(uri, READ) }
        }
        return accessFor(uri)
    }

    fun release(uri: Uri) {
        val existing = resolver.persistedUriPermissions.firstOrNull { it.uri == uri } ?: return
        var flags = 0
        if (existing.isReadPermission) flags = flags or READ
        if (existing.isWritePermission) flags = flags or WRITE
        if (flags == 0) return
        try {
            resolver.releasePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            // Fall back to releasing each mode separately.
            if (flags and READ != 0) {
                runCatching { resolver.releasePersistableUriPermission(uri, READ) }
            }
            if (flags and WRITE != 0) {
                runCatching { resolver.releasePersistableUriPermission(uri, WRITE) }
            }
            log("could not release every persisted permission mode")
        }
    }

    fun accessFor(uri: Uri): PersistedAccess {
        val match = resolver.persistedUriPermissions.firstOrNull { it.uri == uri }
        return PersistedAccess(
            exists = match != null,
            canRead = match?.isReadPermission == true,
            canWrite = match?.isWritePermission == true
        )
    }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) android.util.Log.w(TAG, message)
    }

    companion object {
        private const val TAG = "OtgExplorer"
        private const val READ = Intent.FLAG_GRANT_READ_URI_PERMISSION
        private const val WRITE = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}

data class PersistedAccess(
    val exists: Boolean,
    val canRead: Boolean,
    val canWrite: Boolean
)
