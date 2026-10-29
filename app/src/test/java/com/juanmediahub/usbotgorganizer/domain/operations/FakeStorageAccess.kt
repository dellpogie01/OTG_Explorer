package com.juanmediahub.usbotgorganizer.domain.operations

import com.juanmediahub.usbotgorganizer.domain.model.DocumentCapabilities
import com.juanmediahub.usbotgorganizer.domain.model.StorageItem
import com.juanmediahub.usbotgorganizer.domain.model.StorageRef
import com.juanmediahub.usbotgorganizer.domain.storage.ProbeResult
import com.juanmediahub.usbotgorganizer.domain.storage.StorageAccess
import com.juanmediahub.usbotgorganizer.domain.storage.StorageException
import kotlinx.coroutines.CompletableDeferred
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Small in-memory storage provider used by JVM tests. It behaves like a single
 * SAF tree: every document has a parent, folders contain documents, and writes
 * become visible to verification only after the output stream is closed.
 */
class FakeStorageAccess(
    private val treeUri: String = "content://fake.documents/tree/primary%3ARoot"
) : StorageAccess {
    private data class Node(
        val id: String,
        val parentId: String?,
        val name: String,
        val mime: String,
        val isDirectory: Boolean,
        val bytes: ByteArray?,
        val modified: Long?
    )

    private val nodes = LinkedHashMap<String, Node>()
    private var nextId = 0

    /** Names that fail during createFile. */
    val failCreateFileFor = mutableSetOf<String>()

    /** Document ids whose delete call throws. */
    val failDeleteOf = mutableSetOf<String>()

    /** Names whose writes are silently truncated to zero bytes. */
    val truncateWritesFor = mutableSetOf<String>()

    /** When true, provider move reports failure without moving anything. */
    var providerMoveFails: Boolean = false

    /** When set, openOutput suspends until this completes. */
    var openOutputGate: CompletableDeferred<Unit>? = null

    /** Completed when createFile has registered a destination node. */
    var createFileSignal: CompletableDeferred<String>? = null

    init {
        nodes["root"] = Node(
            id = "root",
            parentId = null,
            name = "Root",
            mime = "vnd.android.document/directory",
            isDirectory = true,
            bytes = null,
            modified = 1_000L
        )
    }

    val root: StorageRef get() = StorageRef(treeUri, "root", null, emptyList())

    fun addFile(
        name: String,
        content: ByteArray,
        mime: String = "application/octet-stream",
        parent: StorageRef = root
    ): StorageItem {
        val id = "doc-${++nextId}"
        nodes[id] = Node(
            id = id,
            parentId = parent.documentId,
            name = name,
            mime = mime,
            isDirectory = false,
            bytes = content,
            modified = 2_000L + nextId
        )
        return itemOf(id)
    }

    fun addFolder(name: String, parent: StorageRef = root): StorageItem {
        val id = "folder-${++nextId}"
        nodes[id] = Node(
            id = id,
            parentId = parent.documentId,
            name = name,
            mime = "vnd.android.document/directory",
            isDirectory = true,
            bytes = null,
            modified = 2_000L + nextId
        )
        return itemOf(id)
    }

    fun exists(parent: StorageRef = root, name: String): Boolean {
        return nodes.values.any { it.parentId == parent.documentId && it.name == name }
    }

    fun containsDocument(id: String): Boolean = id in nodes

    fun countChildren(parent: StorageRef = root): Int {
        return nodes.values.count { it.parentId == parent.documentId }
    }

    fun contentOf(parent: StorageRef = root, name: String): ByteArray? {
        return nodes.values
            .firstOrNull { it.parentId == parent.documentId && it.name == name }
            ?.bytes
    }

    fun child(parent: StorageRef = root, name: String): StorageItem? {
        return nodes.values
            .firstOrNull { it.parentId == parent.documentId && it.name == name }
            ?.let { itemOf(it.id) }
    }

    override suspend fun getItem(ref: StorageRef): StorageItem? {
        return if (ref.documentId in nodes) itemOf(ref.documentId) else null
    }

    override suspend fun listChildren(folder: StorageRef): List<StorageItem> {
        if (folder.documentId !in nodes) {
            throw StorageException(StorageException.Kind.NOT_FOUND)
        }
        return nodes.values
            .filter { it.parentId == folder.documentId }
            .map { itemOf(it.id) }
    }

    override suspend fun findChild(parent: StorageRef, name: String): StorageItem? {
        return listChildren(parent).firstOrNull { it.name == name }
    }

    override suspend fun createFolder(parent: StorageRef, name: String): StorageItem {
        requireFolder(parent)
        val id = "folder-${++nextId}"
        nodes[id] = Node(
            id = id,
            parentId = parent.documentId,
            name = name,
            mime = "vnd.android.document/directory",
            isDirectory = true,
            bytes = null,
            modified = 3_000L + nextId
        )
        return itemOf(id)
    }

    override suspend fun createFile(
        parent: StorageRef,
        mimeType: String,
        name: String
    ): StorageItem {
        requireFolder(parent)
        if (name in failCreateFileFor) {
            throw StorageException(StorageException.Kind.GENERIC, "injected create failure")
        }
        val id = "doc-${++nextId}"
        nodes[id] = Node(
            id = id,
            parentId = parent.documentId,
            name = name,
            mime = mimeType,
            isDirectory = false,
            bytes = ByteArray(0),
            modified = 3_000L + nextId
        )
        createFileSignal?.complete(id)
        return itemOf(id)
    }

    override suspend fun rename(item: StorageRef, newName: String): StorageItem {
        val node = nodes[item.documentId]
            ?: throw StorageException(StorageException.Kind.NOT_FOUND)
        nodes[item.documentId] = node.copy(name = newName)
        return itemOf(item.documentId)
    }

    override suspend fun delete(item: StorageRef) {
        if (item.documentId in failDeleteOf) {
            throw StorageException(StorageException.Kind.GENERIC, "injected delete failure")
        }
        if (item.documentId !in nodes) {
            throw StorageException(StorageException.Kind.NOT_FOUND)
        }
        removeRecursively(item.documentId)
    }

    override suspend fun openInput(item: StorageRef): InputStream {
        val node = nodes[item.documentId]
            ?: throw StorageException(StorageException.Kind.NOT_FOUND)
        if (node.isDirectory) throw StorageException(StorageException.Kind.UNSUPPORTED)
        return ByteArrayInputStream(node.bytes ?: ByteArray(0))
    }

    override suspend fun openOutput(item: StorageRef): OutputStream {
        openOutputGate?.await()
        val node = nodes[item.documentId]
            ?: throw StorageException(StorageException.Kind.NOT_FOUND)
        val truncate = node.name in truncateWritesFor
        return object : ByteArrayOutputStream() {
            override fun close() {
                super.close()
                val data = if (truncate) ByteArray(0) else toByteArray()
                nodes[node.id] = node.copy(bytes = data)
            }
        }
    }

    override suspend fun tryProviderMove(
        source: StorageRef,
        destParent: StorageRef
    ): Boolean {
        if (providerMoveFails) return false
        if (!sameTree(source, destParent)) return false
        val node = nodes[source.documentId] ?: return false
        requireFolder(destParent)
        nodes[source.documentId] = node.copy(parentId = destParent.documentId)
        return true
    }

    override suspend fun ancestorDocumentIds(item: StorageRef): List<String> {
        return ancestorsOf(item.documentId)
    }

    override fun sameTree(a: StorageRef, b: StorageRef): Boolean = a.treeUri == b.treeUri

    override fun rootRef(treeUri: String): StorageRef = StorageRef(treeUri, "root", null, emptyList())

    override suspend fun probeTree(treeUri: String, rootDocumentId: String): ProbeResult {
        return if (treeUri == this.treeUri && rootDocumentId == "root") {
            ProbeResult.AVAILABLE
        } else {
            ProbeResult.UNAVAILABLE
        }
    }

    private fun requireFolder(ref: StorageRef) {
        val node = nodes[ref.documentId]
            ?: throw StorageException(StorageException.Kind.NOT_FOUND)
        if (!node.isDirectory) throw StorageException(StorageException.Kind.UNSUPPORTED)
    }

    private fun removeRecursively(id: String) {
        val children = nodes.values.filter { it.parentId == id }.map { it.id }
        children.forEach { removeRecursively(it) }
        nodes.remove(id)
    }

    private fun ancestorsOf(id: String): List<String> {
        val result = mutableListOf<String>()
        var current = nodes[id]?.parentId
        while (current != null) {
            result.add(current)
            current = nodes[current]?.parentId
        }
        return result.asReversed()
    }

    private fun itemOf(id: String): StorageItem {
        val node = nodes.getValue(id)
        val ancestors = ancestorsOf(id)
        val mime = if (node.isDirectory) {
            "vnd.android.document/directory"
        } else {
            node.mime
        }
        return StorageItem(
            ref = StorageRef(
                treeUri = treeUri,
                documentId = node.id,
                parentDocumentId = node.parentId,
                ancestorDocumentIds = ancestors
            ),
            name = node.name,
            mimeType = mime,
            isDirectory = node.isDirectory,
            sizeBytes = node.bytes?.size?.toLong(),
            modifiedEpochMs = node.modified,
            capabilities = DocumentCapabilities(
                canRead = true,
                canWrite = true,
                canRename = true,
                canDelete = true,
                canCreate = node.isDirectory,
                supportsCopy = true,
                supportsMove = true
            ),
            ancestorDocumentIds = ancestors
        )
    }
}
