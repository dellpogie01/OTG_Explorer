package com.juanmediahub.usbotgorganizer.data.preferences

import android.net.Uri
import android.provider.DocumentsContract
import com.juanmediahub.usbotgorganizer.data.storage.TreePickResult
import com.juanmediahub.usbotgorganizer.data.storage.UriPermissionManager
import com.juanmediahub.usbotgorganizer.domain.model.LocationAvailability
import com.juanmediahub.usbotgorganizer.domain.model.SavedLocation
import com.juanmediahub.usbotgorganizer.domain.model.SavedLocationView
import com.juanmediahub.usbotgorganizer.domain.storage.ProbeResult
import com.juanmediahub.usbotgorganizer.domain.storage.StorageAccess
import com.juanmediahub.usbotgorganizer.domain.storage.StorageException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

class LocationRepository(
    private val preferences: AppPreferences,
    private val permissions: UriPermissionManager,
    private val storage: StorageAccess
) {
    val locations: Flow<List<SavedLocation>> = preferences.locations

    suspend fun addFromPicker(result: TreePickResult, fallbackName: String): SavedLocation {
        val uri = result.uri
        if (!uri.toString().startsWith("content://")) {
            throw StorageException(StorageException.Kind.PERMISSION, "picker returned a non-content URI")
        }
        val access = permissions.persist(uri, result.grantedFlags)
        if (!access.canRead) {
            permissions.release(uri)
            throw StorageException(StorageException.Kind.PERMISSION, "read access was not granted")
        }

        val treeUri = uri.toString()
        val rootId = try {
            DocumentsContract.getTreeDocumentId(uri)
        } catch (e: Exception) {
            throw StorageException(StorageException.Kind.PERMISSION, "invalid tree URI", e)
        }
        val providerName = runCatching {
            storage.getItem(storage.rootRef(treeUri))?.name
        }.getOrNull()?.takeIf { it.isNotBlank() }

        val existing = preferences.locations.first().toMutableList()
        val match = existing.indexOfFirst { it.treeUri == treeUri }
        val now = System.currentTimeMillis()
        val location = if (match >= 0) {
            val current = existing[match]
            current.copy(
                lastOpenedEpochMs = now,
                hasWritePermission = access.canWrite,
                rootDocumentId = rootId,
                displayName = current.displayName.takeIf { it.isNotBlank() && it != fallbackName }
                    ?: providerName
                    ?: current.displayName
            ).also { existing[match] = it }
        } else {
            SavedLocation(
                id = UUID.randomUUID().toString(),
                treeUri = treeUri,
                displayName = providerName ?: fallbackName,
                rootDocumentId = rootId,
                addedAtEpochMs = now,
                lastOpenedEpochMs = now,
                hasWritePermission = access.canWrite
            ).also { existing += it }
        }
        preferences.saveLocations(existing)
        return location
    }

    suspend fun rename(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val updated = preferences.locations.first().map {
            if (it.id == id) it.copy(displayName = trimmed) else it
        }
        preferences.saveLocations(updated)
    }

    suspend fun markOpened(id: String) {
        val now = System.currentTimeMillis()
        val updated = preferences.locations.first().map {
            if (it.id == id) it.copy(lastOpenedEpochMs = now) else it
        }
        preferences.saveLocations(updated)
    }

    suspend fun forget(id: String) {
        val current = preferences.locations.first()
        val target = current.firstOrNull { it.id == id } ?: return
        permissions.release(Uri.parse(target.treeUri))
        preferences.saveLocations(current.filterNot { it.id == id })
    }

    suspend fun get(id: String): SavedLocation? = preferences.locations.first().firstOrNull { it.id == id }

    /**
     * Re-probes every saved location. The saved write flag is refreshed from
     * Android's persisted grants instead of being trusted from disk.
     */
    suspend fun views(): List<SavedLocationView> {
        return preferences.locations.first().map { loc -> viewOf(loc) }
    }

    suspend fun viewOf(id: String): SavedLocationView? {
        val loc = preferences.locations.first().firstOrNull { it.id == id } ?: return null
        return viewOf(loc)
    }

    private suspend fun viewOf(loc: SavedLocation): SavedLocationView {
        val uri = runCatching { Uri.parse(loc.treeUri) }.getOrNull()
        val access = uri?.let { permissions.accessFor(it) } ?: com.juanmediahub.usbotgorganizer.data.storage.PersistedAccess(
            exists = false,
            canRead = false,
            canWrite = false
        )
        val availability = when {
            !access.canRead -> LocationAvailability.PERMISSION_REQUIRED
            else -> when (runCatching { storage.probeTree(loc.treeUri, loc.rootDocumentId) }
                .getOrDefault(ProbeResult.UNAVAILABLE)) {
                ProbeResult.AVAILABLE -> LocationAvailability.AVAILABLE
                ProbeResult.PERMISSION_REQUIRED -> LocationAvailability.PERMISSION_REQUIRED
                ProbeResult.UNAVAILABLE -> LocationAvailability.UNAVAILABLE
            }
        }
        return SavedLocationView(
            location = loc.copy(hasWritePermission = access.canWrite),
            availability = availability,
            readOnly = !access.canWrite
        )
    }
}
