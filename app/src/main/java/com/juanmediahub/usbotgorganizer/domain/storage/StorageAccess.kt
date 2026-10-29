package com.juanmediahub.usbotgorganizer.domain.storage

import com.juanmediahub.usbotgorganizer.domain.model.StorageItem
import com.juanmediahub.usbotgorganizer.domain.model.StorageRef
import java.io.InputStream
import java.io.OutputStream

class StorageException(
    val kind: Kind,
    message: String? = null,
    cause: Throwable? = null
) : Exception(message, cause) {
    enum class Kind {
        PERMISSION,
        DISCONNECTED,
        READ_ONLY,
        NOT_FOUND,
        NAME_CONFLICT,
        INSUFFICIENT_SPACE,
        UNSUPPORTED,
        IO,
        CANCELLED,
        GENERIC
    }
}

/**
 * Replaceable storage abstraction. All methods must be safe to call from a
 * coroutine; implementations are responsible for moving provider work off the
 * main thread.
 */
interface StorageAccess {
    suspend fun getItem(ref: StorageRef): StorageItem?
    suspend fun listChildren(folder: StorageRef): List<StorageItem>
    suspend fun findChild(parent: StorageRef, name: String): StorageItem?
    suspend fun createFolder(parent: StorageRef, name: String): StorageItem
    suspend fun createFile(parent: StorageRef, mimeType: String, name: String): StorageItem
    suspend fun rename(item: StorageRef, newName: String): StorageItem
    suspend fun delete(item: StorageRef)
    suspend fun openInput(item: StorageRef): InputStream
    suspend fun openOutput(item: StorageRef): OutputStream
    suspend fun tryProviderMove(source: StorageRef, destParent: StorageRef): Boolean
    suspend fun ancestorDocumentIds(item: StorageRef): List<String>
    fun sameTree(a: StorageRef, b: StorageRef): Boolean
    fun rootRef(treeUri: String): StorageRef
    suspend fun probeTree(treeUri: String, rootDocumentId: String): ProbeResult
}

enum class ProbeResult {
    AVAILABLE,
    UNAVAILABLE,
    PERMISSION_REQUIRED
}
