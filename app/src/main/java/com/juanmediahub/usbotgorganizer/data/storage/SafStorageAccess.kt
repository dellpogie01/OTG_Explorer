package com.juanmediahub.usbotgorganizer.data.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.juanmediahub.usbotgorganizer.BuildConfig
import com.juanmediahub.usbotgorganizer.domain.model.DocumentCapabilities
import com.juanmediahub.usbotgorganizer.domain.model.StorageItem
import com.juanmediahub.usbotgorganizer.domain.model.StorageRef
import com.juanmediahub.usbotgorganizer.domain.storage.ProbeResult
import com.juanmediahub.usbotgorganizer.domain.storage.StorageAccess
import com.juanmediahub.usbotgorganizer.domain.storage.StorageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/**
 * SAF implementation backed by DocumentsContract and ContentResolver. All
 * provider work happens on Dispatchers.IO, cursors are always closed, and
 * document URIs are never converted to filesystem paths.
 */
class SafStorageAccess(
    context: Context,
    private val permissions: UriPermissionManager
) : StorageAccess {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    override suspend fun getItem(ref: StorageRef): StorageItem? = withContext(Dispatchers.IO) {
        queryDocument(ref)
    }

    override suspend fun listChildren(folder: StorageRef): List<StorageItem> = withContext(Dispatchers.IO) {
        val tree = Uri.parse(folder.treeUri)
        val childrenUri = try {
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, folder.documentId)
        } catch (e: Exception) {
            throw mapException(e)
        }

        val cursor = try {
            resolver.query(childrenUri, CHILD_PROJECTION, null, null, null)
        } catch (e: SecurityException) {
            throw StorageException(StorageException.Kind.PERMISSION, cause = e)
        } catch (e: Exception) {
            throw mapException(e)
        } ?: throw StorageException(StorageException.Kind.DISCONNECTED)

        val items = mutableListOf<StorageItem>()
        cursor.use { c ->
            val idIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val flagsIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
            val ancestors = folder.ancestorDocumentIds + folder.documentId

            while (c.moveToNext()) {
                val documentId = if (idIdx >= 0) c.getString(idIdx) else null
                if (documentId.isNullOrBlank()) continue
                val name = if (nameIdx >= 0) {
                    c.getString(nameIdx)?.takeIf { it.isNotBlank() }
                } else {
                    null
                } ?: UNNAMED_ITEM
                val mime = if (mimeIdx >= 0) c.getString(mimeIdx) else null
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val size = if (sizeIdx >= 0 && !c.isNull(sizeIdx) && !isDir) {
                    c.getLong(sizeIdx).takeIf { it >= 0 }
                } else {
                    null
                }
                val modified = if (modifiedIdx >= 0 && !c.isNull(modifiedIdx)) {
                    c.getLong(modifiedIdx).takeIf { it > 0 }
                } else {
                    null
                }
                val flags = if (flagsIdx >= 0 && !c.isNull(flagsIdx)) c.getInt(flagsIdx) else 0

                items += StorageItem(
                    ref = StorageRef(
                        treeUri = folder.treeUri,
                        documentId = documentId,
                        parentDocumentId = folder.documentId,
                        ancestorDocumentIds = ancestors
                    ),
                    name = name,
                    mimeType = mime,
                    isDirectory = isDir,
                    sizeBytes = size,
                    modifiedEpochMs = modified,
                    capabilities = capabilitiesFrom(flags, isDir),
                    ancestorDocumentIds = ancestors
                )
            }
        }
        items
    }

    override suspend fun findChild(parent: StorageRef, name: String): StorageItem? {
        return listChildren(parent).firstOrNull { it.name == name }
    }

    override suspend fun createFolder(parent: StorageRef, name: String): StorageItem =
        withContext(Dispatchers.IO) {
            createDocument(parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
        }

    override suspend fun createFile(parent: StorageRef, mimeType: String, name: String): StorageItem =
        withContext(Dispatchers.IO) {
            val mime = mimeType.takeIf { it.isNotBlank() } ?: "application/octet-stream"
            createDocument(parent, mime, name)
        }

    override suspend fun rename(item: StorageRef, newName: String): StorageItem =
        withContext(Dispatchers.IO) {
            val uri = documentUri(item)
            val renamed = try {
                DocumentsContract.renameDocument(resolver, uri, newName)
            } catch (e: Exception) {
                throw mapException(e)
            } ?: throw StorageException(StorageException.Kind.UNSUPPORTED)

            val newId = try {
                DocumentsContract.getDocumentId(renamed)
            } catch (e: Exception) {
                throw mapException(e)
            }
            val newRef = item.copy(documentId = newId)
            queryDocument(newRef) ?: StorageItem(
                ref = newRef,
                name = newName,
                mimeType = null,
                isDirectory = false,
                sizeBytes = null,
                modifiedEpochMs = null,
                capabilities = DocumentCapabilities()
            )
        }

    override suspend fun delete(item: StorageRef): Unit = withContext(Dispatchers.IO) {
        val uri = documentUri(item)
        val deleted = try {
            DocumentsContract.deleteDocument(resolver, uri)
        } catch (e: Exception) {
            throw mapException(e)
        }
        if (!deleted) throw StorageException(StorageException.Kind.UNSUPPORTED)
    }

    override suspend fun openInput(item: StorageRef): InputStream = withContext(Dispatchers.IO) {
        try {
            resolver.openInputStream(documentUri(item))
                ?: throw StorageException(StorageException.Kind.IO, "null input stream")
        } catch (e: StorageException) {
            throw e
        } catch (e: SecurityException) {
            throw StorageException(StorageException.Kind.PERMISSION, cause = e)
        } catch (e: Exception) {
            throw mapException(e)
        }
    }

    override suspend fun openOutput(item: StorageRef): OutputStream = withContext(Dispatchers.IO) {
        try {
            resolver.openOutputStream(documentUri(item), "w")
                ?: throw StorageException(StorageException.Kind.IO, "null output stream")
        } catch (e: StorageException) {
            throw e
        } catch (e: SecurityException) {
            throw StorageException(StorageException.Kind.PERMISSION, cause = e)
        } catch (e: Exception) {
            throw mapException(e)
        }
    }

    override suspend fun tryProviderMove(
        source: StorageRef,
        destParent: StorageRef
    ): Boolean = withContext(Dispatchers.IO) {
        if (!sameTree(source, destParent)) return@withContext false
        val sourceParentId = source.parentDocumentId ?: return@withContext false
        val sourceParent = documentUri(source.copy(documentId = sourceParentId))
        val moved = try {
            DocumentsContract.moveDocument(
                resolver,
                documentUri(source),
                sourceParent,
                documentUri(destParent)
            )
        } catch (_: Exception) {
            null
        } ?: return@withContext false

        // Use the returned URI to prove the provider completed the move. The
        // folder listing is refreshed rather than reusing the old URI.
        try {
            DocumentsContract.getDocumentId(moved).isNotBlank()
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun ancestorDocumentIds(item: StorageRef): List<String> {
        return (item.ancestorDocumentIds + item.documentId).distinct()
    }

    override fun sameTree(a: StorageRef, b: StorageRef): Boolean = a.treeUri == b.treeUri

    override fun rootRef(treeUri: String): StorageRef {
        val tree = Uri.parse(treeUri)
        val id = DocumentsContract.getTreeDocumentId(tree)
        return StorageRef(treeUri, id, null)
    }

    override suspend fun probeTree(treeUri: String, rootDocumentId: String): ProbeResult =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(treeUri)
            val access = permissions.accessFor(uri)
            if (!access.exists || !access.canRead) return@withContext ProbeResult.PERMISSION_REQUIRED

            try {
                val treeId = DocumentsContract.getTreeDocumentId(uri)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, treeId)
                val cursor = resolver.query(
                    childrenUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                    null,
                    null,
                    null
                )
                if (cursor == null) {
                    ProbeResult.UNAVAILABLE
                } else {
                    cursor.close()
                    ProbeResult.AVAILABLE
                }
            } catch (_: SecurityException) {
                ProbeResult.PERMISSION_REQUIRED
            } catch (_: Exception) {
                ProbeResult.UNAVAILABLE
            }
        }

    /**
     * Builds a temporary-read document URI for an external viewer. This is
     * passed to an ACTION_VIEW intent; it is never exposed as a file path.
     */
    fun documentUri(ref: StorageRef): Uri {
        return DocumentsContract.buildDocumentUriUsingTree(Uri.parse(ref.treeUri), ref.documentId)
    }

    private fun createDocument(parent: StorageRef, mime: String, name: String): StorageItem {
        val uri = try {
            DocumentsContract.createDocument(resolver, documentUri(parent), mime, name)
        } catch (e: Exception) {
            throw mapException(e)
        } ?: throw StorageException(StorageException.Kind.UNSUPPORTED)

        val id = try {
            DocumentsContract.getDocumentId(uri)
        } catch (e: Exception) {
            throw mapException(e)
        }
        val ref = StorageRef(
            treeUri = parent.treeUri,
            documentId = id,
            parentDocumentId = parent.documentId,
            ancestorDocumentIds = parent.ancestorDocumentIds + parent.documentId
        )
        return queryDocument(ref) ?: StorageItem(
            ref = ref,
            name = name,
            mimeType = mime,
            isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
            sizeBytes = null,
            modifiedEpochMs = null,
            capabilities = DocumentCapabilities(),
            ancestorDocumentIds = ref.ancestorDocumentIds
        )
    }

    private fun queryDocument(ref: StorageRef): StorageItem? {
        val uri = documentUri(ref)
        val cursor = try {
            resolver.query(uri, CHILD_PROJECTION, null, null, null)
        } catch (e: SecurityException) {
            throw StorageException(StorageException.Kind.PERMISSION, cause = e)
        } catch (e: Exception) {
            throw mapException(e)
        } ?: return null

        cursor.use { c ->
            if (!c.moveToFirst()) return null
            val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val flagsIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
            val idIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val documentId = if (idIdx >= 0) c.getString(idIdx) else null
            val mime = if (mimeIdx >= 0) c.getString(mimeIdx) else null
            val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
            val name = if (nameIdx >= 0) {
                c.getString(nameIdx)?.takeIf { it.isNotBlank() }
            } else {
                null
            } ?: UNNAMED_ITEM
            val size = if (sizeIdx >= 0 && !c.isNull(sizeIdx) && !isDir) {
                c.getLong(sizeIdx).takeIf { it >= 0 }
            } else {
                null
            }
            val modified = if (modifiedIdx >= 0 && !c.isNull(modifiedIdx)) {
                c.getLong(modifiedIdx).takeIf { it > 0 }
            } else {
                null
            }
            val flags = if (flagsIdx >= 0 && !c.isNull(flagsIdx)) c.getInt(flagsIdx) else 0

            return StorageItem(
                ref = ref.copy(
                    documentId = documentId?.takeIf { it.isNotBlank() } ?: ref.documentId
                ),
                name = name,
                mimeType = mime,
                isDirectory = isDir,
                sizeBytes = size,
                modifiedEpochMs = modified,
                capabilities = capabilitiesFrom(flags, isDir)
            )
        }
    }

    private fun capabilitiesFrom(flags: Int, isDir: Boolean): DocumentCapabilities {
        return DocumentCapabilities(
            canRead = true,
            canWrite = flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0,
            canRename = flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0,
            canDelete = flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0,
            canCreate = isDir && flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0,
            supportsCopy = flags and DocumentsContract.Document.FLAG_SUPPORTS_COPY != 0,
            supportsMove = flags and DocumentsContract.Document.FLAG_SUPPORTS_MOVE != 0,
            isVirtual = flags and DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT != 0
        )
    }

    private fun mapException(e: Exception): StorageException {
        if (e is StorageException) return e
        if (BuildConfig.DEBUG) {
            android.util.Log.w(TAG, "storage provider operation failed (${e.javaClass.simpleName})")
        }
        val message = e.message.orEmpty().lowercase()
        val kind = when {
            e is SecurityException -> StorageException.Kind.PERMISSION
            e is UnsupportedOperationException -> StorageException.Kind.UNSUPPORTED
            message.contains("space") || message.contains("enospc") -> StorageException.Kind.INSUFFICIENT_SPACE
            message.contains("read-only") || message.contains("readonly") -> StorageException.Kind.READ_ONLY
            message.contains("disconnected") ||
                message.contains("unavailable") ||
                message.contains("failed to find provider") ||
                message.contains("no provider") -> StorageException.Kind.DISCONNECTED
            message.contains("not found") || message.contains("no such file") -> StorageException.Kind.NOT_FOUND
            else -> StorageException.Kind.IO
        }
        return StorageException(kind, cause = e)
    }

    companion object {
        private const val TAG = "OtgExplorer"
        private const val UNNAMED_ITEM = "Unnamed item"
        private val CHILD_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS
        )
    }
}
