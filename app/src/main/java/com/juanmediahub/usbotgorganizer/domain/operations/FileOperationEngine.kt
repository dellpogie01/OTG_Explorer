package com.juanmediahub.usbotgorganizer.domain.operations

import com.juanmediahub.usbotgorganizer.domain.model.ClipboardState
import com.juanmediahub.usbotgorganizer.domain.model.ConflictPolicy
import com.juanmediahub.usbotgorganizer.domain.model.FileCategory
import com.juanmediahub.usbotgorganizer.domain.model.OperationKind
import com.juanmediahub.usbotgorganizer.domain.model.OperationOutcome
import com.juanmediahub.usbotgorganizer.domain.model.OperationProgress
import com.juanmediahub.usbotgorganizer.domain.model.OperationSummary
import com.juanmediahub.usbotgorganizer.domain.model.OrganizePlanItem
import com.juanmediahub.usbotgorganizer.domain.model.StorageItem
import com.juanmediahub.usbotgorganizer.domain.model.StorageRef
import com.juanmediahub.usbotgorganizer.domain.model.TransferMode
import com.juanmediahub.usbotgorganizer.domain.storage.StorageAccess
import com.juanmediahub.usbotgorganizer.domain.storage.StorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.IOException

fun interface ConflictResolver {
    suspend fun resolve(name: String): ConflictPolicy
}

/**
 * Foreground-only operation engine. All destination writes stream through a
 * bounded buffer, conflicts are resolved before anything is overwritten, and
 * a source is deleted only after its destination copy has been verified.
 */
class FileOperationEngine(
    private val storage: StorageAccess,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val mutex = Mutex()
    private var runningJob: Job? = null
    private var counters = OperationCounters()

    private val _progress = MutableStateFlow<OperationProgress?>(null)
    val progress: StateFlow<OperationProgress?> = _progress.asStateFlow()

    private val _summary = MutableStateFlow<OperationSummary?>(null)
    val summary: StateFlow<OperationSummary?> = _summary.asStateFlow()

    private val _clipboard = MutableStateFlow<ClipboardState?>(null)
    val clipboard: StateFlow<ClipboardState?> = _clipboard.asStateFlow()

    val isBusy: Boolean get() = _progress.value?.running == true

    fun setClipboard(items: List<StorageItem>, mode: TransferMode) {
        if (items.isNotEmpty()) _clipboard.value = ClipboardState(items.toList(), mode)
    }

    fun clearClipboard() {
        _clipboard.value = null
    }

    fun clearSummary() {
        _summary.value = null
    }

    fun cancel() {
        runningJob?.cancel()
    }

    suspend fun createFolder(parent: StorageRef, name: String): Result<StorageItem> {
        val normalized = FilenameTools.normalize(name)
        if (!FilenameTools.validate(normalized)) {
            return Result.failure(StorageException(StorageException.Kind.GENERIC, CODE_INVALID_NAME))
        }
        return runResult(OperationKind.CREATE_FOLDER) {
            storage.createFolder(parent, normalized)
        }
    }

    suspend fun rename(item: StorageRef, newName: String): Result<StorageItem> {
        val normalized = FilenameTools.normalize(newName)
        if (!FilenameTools.validate(normalized)) {
            return Result.failure(StorageException(StorageException.Kind.GENERIC, CODE_INVALID_NAME))
        }
        return runResult(OperationKind.RENAME) {
            storage.rename(item, normalized)
        }
    }

    suspend fun deleteItems(items: List<StorageItem>): OperationSummary {
        if (items.isEmpty()) {
            return OperationSummary(OperationOutcome.SUCCESS, OperationKind.DELETE, message = CODE_NOTHING)
        }
        return runSummary(OperationKind.DELETE, items.size, null) { counters ->
            var processed = 0
            var reason: String? = null
            for (item in items) {
                ensureActiveInternal()
                updateProgress(OperationKind.DELETE, item.name, processed, items.size)
                try {
                    storage.delete(item.ref)
                    counters.completed++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    counters.failed++
                    reason = reason ?: reasonFor(e)
                }
                processed++
            }
            OperationSummary(
                outcome = outcomeFor(counters),
                kind = OperationKind.DELETE,
                completedItems = counters.completed,
                failedItems = counters.failed,
                message = reason
            )
        }
    }

    suspend fun paste(destination: StorageRef, resolver: ConflictResolver): OperationSummary {
        val clip = _clipboard.value ?: return OperationSummary(
            outcome = OperationOutcome.FAILURE,
            kind = OperationKind.COPY,
            message = CODE_NOTHING
        )
        val result = transfer(clip.items, destination, clip.mode, resolver)
        if (clip.mode == TransferMode.MOVE && result.completedItems > 0) {
            clearClipboard()
        }
        return result
    }

    suspend fun transfer(
        items: List<StorageItem>,
        destination: StorageRef,
        mode: TransferMode,
        resolver: ConflictResolver
    ): OperationSummary {
        val kind = if (mode == TransferMode.MOVE) OperationKind.MOVE else OperationKind.COPY
        if (items.isEmpty()) {
            return OperationSummary(OperationOutcome.SUCCESS, kind, message = CODE_NOTHING)
        }
        val totalBytes = knownTotalBytes(items)
        return runSummary(kind, items.size, totalBytes) { counters ->
            TransferSession(destination, mode, resolver, kind, counters).run(items)
        }
    }

    fun previewOrganize(files: List<StorageItem>, currentFolder: StorageItem): List<OrganizePlanItem> {
        return files.filter { !it.isDirectory }.map { item ->
            val category = FileCategorizer.category(item.mimeType, item.name)
            val folderName = FileCategorizer.folderName(category)
            OrganizePlanItem(
                source = item,
                category = category,
                destinationFolderName = folderName,
                selected = true,
                alreadyInPlace = currentFolder.name.equals(folderName, ignoreCase = true)
            )
        }
    }

    suspend fun organize(
        currentFolder: StorageRef,
        plans: List<OrganizePlanItem>,
        mode: TransferMode,
        resolver: ConflictResolver
    ): OperationSummary {
        val selected = plans.filter { it.selected && !it.source.isDirectory && !it.alreadyInPlace }
        if (selected.isEmpty()) {
            return OperationSummary(
                outcome = OperationOutcome.SUCCESS,
                kind = OperationKind.ORGANIZE,
                message = CODE_NOTHING
            )
        }
        return runSummary(OperationKind.ORGANIZE, selected.size, null) { counters ->
            val session = TransferSession(currentFolder, mode, resolver, OperationKind.ORGANIZE, counters)
            val folders = mutableMapOf<FileCategory, StorageRef>()
            val folderFailures = mutableSetOf<FileCategory>()
            var processed = 0
            var reason: String? = null
            var cancelled = false

            for (plan in selected) {
                ensureActiveInternal()
                updateProgress(OperationKind.ORGANIZE, plan.source.name, processed, selected.size)
                try {
                    val category = plan.category
                    if (category in folderFailures) {
                        counters.failed++
                        processed++
                        continue
                    }
                    val destFolder = folders[category] ?: run {
                        val folder = storage.findChild(currentFolder, plan.destinationFolderName)
                            ?: storage.createFolder(currentFolder, plan.destinationFolderName)
                        folders[category] = folder.ref
                        folder.ref
                    }
                    if (isSameParent(plan.source, destFolder)) {
                        counters.skipped++
                        processed++
                        continue
                    }
                    when (val result = session.process(plan.source, destFolder)) {
                        is ItemResult.Completed -> {
                            counters.completed++
                            counters.skipped += result.nestedSkipped
                        }
                        ItemResult.Skipped -> counters.skipped++
                        is ItemResult.Failed -> {
                            counters.failed++
                            reason = reason ?: result.reason
                        }
                        is ItemResult.SourceDeleteFailed -> {
                            counters.completed++
                            counters.skipped += result.nestedSkipped
                            counters.sourceDeleteFailures++
                            reason = reason ?: result.reason
                        }
                        ItemResult.Cancelled -> {
                            cancelled = true
                            break
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    counters.failed++
                    reason = reason ?: reasonFor(e)
                    plan.category.let { folderFailures += it }
                }
                processed++
            }

            OperationSummary(
                outcome = if (cancelled) OperationOutcome.CANCELLED else outcomeFor(counters),
                kind = OperationKind.ORGANIZE,
                completedItems = counters.completed,
                skippedItems = counters.skipped,
                failedItems = counters.failed,
                sourceDeleteFailures = counters.sourceDeleteFailures,
                incompleteCleanupFailures = counters.cleanupFailures,
                usedCopyThenDelete = counters.usedCopyThenDelete,
                message = when {
                    cancelled -> CODE_CANCELLED
                    counters.sourceDeleteFailures > 0 -> CODE_SOURCE_DELETE_FAILED
                    counters.cleanupFailures > 0 -> CODE_CLEANUP_FAILED
                    counters.usedCopyThenDelete && mode == TransferMode.MOVE && counters.completed > 0 ->
                        CODE_COPY_THEN_DELETE
                    else -> reason
                }
            )
        }
    }

    private inner class TransferSession(
        private val destination: StorageRef,
        private val mode: TransferMode,
        private val resolver: ConflictResolver,
        private val kind: OperationKind,
        private val counters: OperationCounters
    ) {
        suspend fun run(items: List<StorageItem>): OperationSummary {
            var processed = 0
            var reason: String? = null
            var cancelled = false

            for (item in items) {
                ensureActiveInternal()
                updateProgress(kind, item.name, processed, items.size)
                try {
                    if (mode == TransferMode.MOVE && isSameParent(item, destination)) {
                        counters.skipped++
                    } else {
                        when (val result = process(item, destination)) {
                            is ItemResult.Completed -> {
                                counters.completed++
                                counters.skipped += result.nestedSkipped
                            }
                            ItemResult.Skipped -> counters.skipped++
                            is ItemResult.Failed -> {
                                counters.failed++
                                reason = reason ?: result.reason
                            }
                            is ItemResult.SourceDeleteFailed -> {
                                counters.completed++
                                counters.skipped += result.nestedSkipped
                                counters.sourceDeleteFailures++
                                reason = reason ?: result.reason
                            }
                            ItemResult.Cancelled -> {
                                cancelled = true
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    // process() already cleaned the partial item.
                    throw e
                } catch (e: Exception) {
                    counters.failed++
                    reason = reason ?: reasonFor(e)
                }
                processed++
                if (cancelled) break
            }

            return OperationSummary(
                outcome = if (cancelled) OperationOutcome.CANCELLED else outcomeFor(counters),
                kind = kind,
                completedItems = counters.completed,
                skippedItems = counters.skipped,
                failedItems = counters.failed,
                sourceDeleteFailures = counters.sourceDeleteFailures,
                incompleteCleanupFailures = counters.cleanupFailures,
                usedCopyThenDelete = counters.usedCopyThenDelete,
                message = when {
                    cancelled -> CODE_CANCELLED
                    counters.sourceDeleteFailures > 0 -> CODE_SOURCE_DELETE_FAILED
                    counters.cleanupFailures > 0 -> CODE_CLEANUP_FAILED
                    counters.usedCopyThenDelete && mode == TransferMode.MOVE && counters.completed > 0 ->
                        CODE_COPY_THEN_DELETE
                    else -> reason
                }
            )
        }

        /** Processes one top-level item and cleans up incomplete destination work on failure. */
        suspend fun process(item: StorageItem, destParent: StorageRef): ItemResult {
            val created = mutableListOf<StorageRef>()
            try {
                val result = transferOne(item, destParent, created)
                // A verified destination is final. Never remove it, even when a
                // source delete failed or a nested item was skipped.
                created.clear()
                return result
            } catch (e: ConflictCancelled) {
                cleanup(created)
                return ItemResult.Cancelled
            } catch (e: CancellationException) {
                cleanup(created)
                throw e
            } catch (e: Exception) {
                cleanup(created)
                throw e
            }
        }

        private suspend fun transferOne(
            item: StorageItem,
            destParent: StorageRef,
            created: MutableList<StorageRef>
        ): ItemResult {
            if (item.isDirectory && AncestryGuard.isUnsafeFolderTarget(storage, item, destParent)) {
                val reason = if (item.ref.documentId == destParent.documentId) {
                    CODE_SELF_COPY
                } else {
                    CODE_AMBIGUOUS_FOLDER
                }
                return ItemResult.Failed(reason)
            }

            val nameChoice = resolveName(destParent, item.name)
            val destName = when (nameChoice) {
                NameChoice.Cancel -> return ItemResult.Cancelled
                NameChoice.Skip -> return ItemResult.Skipped
                is NameChoice.Use -> nameChoice.name
            }

            if (mode == TransferMode.MOVE) {
                if (destName == item.name &&
                    !isSameParent(item, destParent) &&
                    item.capabilities.supportsMove &&
                    storage.sameTree(item.ref, destParent)
                ) {
                    val moved = try {
                        storage.tryProviderMove(item.ref, destParent)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        false
                    }
                    if (moved) {
                        return ItemResult.Completed()
                    }
                    val sourceStillExists = try {
                        storage.getItem(item.ref) != null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        true
                    }
                    if (!sourceStillExists) {
                        // The provider moved the item even though it reported a
                        // failure. Do not copy or delete anything else.
                        return ItemResult.Completed()
                    }
                }

                counters.usedCopyThenDelete = true
                val copy = copyRecursive(item, destParent, destName, created)
                if (!verifyCopy(item, copy)) {
                    throw OperationFailure(CODE_VERIFY_FAILED)
                }
                if (copy.skippedKeys.isNotEmpty()) {
                    return ItemResult.SourceDeleteFailed(copy.skippedKeys.size, CODE_NESTED_SKIPPED)
                }
                return try {
                    storage.delete(item.ref)
                    ItemResult.Completed(copy.skippedKeys.size)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ItemResult.SourceDeleteFailed(
                        copy.skippedKeys.size,
                        reasonFor(e) ?: CODE_SOURCE_DELETE_FAILED
                    )
                }
            }

            val copy = copyRecursive(item, destParent, destName, created)
            if (!verifyCopy(item, copy)) {
                throw OperationFailure(CODE_VERIFY_FAILED)
            }
            return ItemResult.Completed(copy.skippedKeys.size)
        }

        private suspend fun copyRecursive(
            source: StorageItem,
            destParent: StorageRef,
            destName: String,
            created: MutableList<StorageRef>
        ): CopySuccess {
            ensureActiveInternal()

            if (source.isDirectory) {
                val folder = try {
                    storage.createFolder(destParent, destName)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw wrapCopyFailure(e)
                }
                created += folder.ref
                val copies = mutableMapOf<String, CopyRecord>()
                copies[source.key] = CopyRecord(source, folder)
                val skippedKeys = mutableSetOf<String>()
                val children = try {
                    storage.listChildren(source.ref)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw wrapCopyFailure(e)
                }
                for (child in children) {
                    ensureActiveInternal()
                    when (val childChoice = resolveName(folder.ref, child.name)) {
                        NameChoice.Cancel -> throw ConflictCancelled()
                        NameChoice.Skip -> skippedKeys += child.key
                        is NameChoice.Use -> {
                            val childOutcome = copyRecursive(child, folder.ref, childChoice.name, created)
                            copies.putAll(childOutcome.copies)
                            skippedKeys += childOutcome.skippedKeys
                        }
                    }
                }
                return CopySuccess(folder, copies, skippedKeys)
            }

            val mime = source.mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
            val dest = try {
                storage.createFile(destParent, mime, destName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw wrapCopyFailure(e)
            }
            created += dest.ref
            streamCopy(source.ref, dest.ref, source.name)
            return CopySuccess(dest, mapOf(source.key to CopyRecord(source, dest)), emptySet())
        }

        private suspend fun streamCopy(source: StorageRef, dest: StorageRef, name: String) {
            val input = try {
                storage.openInput(source)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw wrapCopyFailure(e)
            }
            try {
                val output = try {
                    storage.openOutput(dest)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw wrapCopyFailure(e)
                }
                try {
                    val buffer = ByteArray(BUFFER_SIZE)
                    var transferred = _progress.value?.bytesTransferred ?: 0L
                    input.use { ins ->
                        output.use { outs ->
                            while (true) {
                                ensureActiveInternal()
                                val read = try {
                                    ins.read(buffer)
                                } catch (e: IOException) {
                                    throw mapIoFailure(e)
                                }
                                if (read < 0) break
                                if (read == 0) continue
                                try {
                                    outs.write(buffer, 0, read)
                                } catch (e: IOException) {
                                    throw mapIoFailure(e)
                                }
                                transferred += read
                                updateBytes(name, transferred)
                            }
                            try {
                                outs.flush()
                            } catch (e: IOException) {
                                throw mapIoFailure(e)
                            }
                        }
                    }
                } finally {
                    runCatching { output.close() }
                }
            } finally {
                runCatching { input.close() }
            }
        }

        private suspend fun verifyCopy(source: StorageItem, copy: CopySuccess): Boolean {
            val top = try {
                storage.getItem(copy.item.ref)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return false
            } ?: return false

            if (source.isDirectory && !top.isDirectory) return false
            if (!source.isDirectory && top.isDirectory) return false

            for (record in copy.copies.values) {
                if (record.source.isDirectory) continue
                val expected = record.source.sizeBytes ?: continue
                val destMeta = try {
                    storage.getItem(record.dest.ref)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    return false
                } ?: return false
                val actual = destMeta.sizeBytes ?: continue
                if (expected != actual) return false
            }
            return true
        }

        private suspend fun resolveName(parent: StorageRef, original: String): NameChoice {
            val existing = try {
                storage.findChild(parent, original)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw wrapCopyFailure(e)
            }
            if (existing == null) return NameChoice.Use(original)
            return when (resolver.resolve(original)) {
                ConflictPolicy.CANCEL -> NameChoice.Cancel
                ConflictPolicy.SKIP -> NameChoice.Skip
                ConflictPolicy.KEEP_BOTH -> {
                    val names = try {
                        storage.listChildren(parent).mapTo(mutableSetOf()) { it.name }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        throw wrapCopyFailure(e)
                    }
                    var candidate = FilenameTools.keepBothName(original, names)
                    var attempts = 0
                    while (attempts < MAX_NAME_ATTEMPTS) {
                        val found = try {
                            storage.findChild(parent, candidate)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            throw wrapCopyFailure(e)
                        }
                        if (found == null) break
                        names += candidate
                        candidate = FilenameTools.keepBothName(original, names)
                        attempts++
                    }
                    NameChoice.Use(candidate)
                }
            }
        }

        private suspend fun cleanup(created: MutableList<StorageRef>) {
            if (created.isEmpty()) return
            withContext(NonCancellable) {
                var failures = 0
                for (ref in created.asReversed()) {
                    try {
                        storage.delete(ref)
                    } catch (_: Exception) {
                        failures++
                    }
                }
                created.clear()
                counters.cleanupFailures += failures
            }
        }
    }

    private suspend fun <T> runResult(kind: OperationKind, block: suspend () -> T): Result<T> {
        if (!mutex.tryLock()) {
            return Result.failure(StorageException(StorageException.Kind.GENERIC, CODE_BUSY))
        }
        runningJob = currentCoroutineContext()[Job]
        _progress.value = OperationProgress(kind, null, 0, 1, 0, null, true)
        return try {
            val value = withContext(ioDispatcher) { block() }
            _progress.value = null
            Result.success(value)
        } catch (e: CancellationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _progress.value = null
            runningJob = null
            mutex.unlock()
        }
    }

    private suspend fun runSummary(
        kind: OperationKind,
        total: Int,
        totalBytes: Long?,
        block: suspend (OperationCounters) -> OperationSummary
    ): OperationSummary {
        if (!mutex.tryLock()) {
            return OperationSummary(OperationOutcome.FAILURE, kind, message = CODE_BUSY)
        }
        counters = OperationCounters()
        runningJob = currentCoroutineContext()[Job]
        _progress.value = OperationProgress(kind, null, 0, total, 0, totalBytes, true)
        return try {
            val result = withContext(ioDispatcher) { block(counters) }
            _summary.value = result
            result
        } catch (e: CancellationException) {
            val cancelled = summaryOf(counters, kind, OperationOutcome.CANCELLED, CODE_CANCELLED)
            _summary.value = cancelled
            cancelled
        } catch (e: Exception) {
            val failed = summaryOf(
                counters,
                kind,
                outcomeFor(counters),
                reasonFor(e) ?: CODE_IO_FAILED
            )
            _summary.value = failed
            failed
        } finally {
            _progress.value = null
            runningJob = null
            mutex.unlock()
        }
    }

    private fun summaryOf(
        counters: OperationCounters,
        kind: OperationKind,
        outcome: OperationOutcome,
        message: String?
    ): OperationSummary = OperationSummary(
        outcome = outcome,
        kind = kind,
        completedItems = counters.completed,
        skippedItems = counters.skipped,
        failedItems = counters.failed,
        sourceDeleteFailures = counters.sourceDeleteFailures,
        incompleteCleanupFailures = counters.cleanupFailures,
        usedCopyThenDelete = counters.usedCopyThenDelete,
        message = message
    )

    private fun updateProgress(kind: OperationKind, name: String?, completed: Int, total: Int?) {
        val current = _progress.value
        _progress.value = if (current == null) {
            OperationProgress(kind, name, completed, total, 0, null, true)
        } else {
            current.copy(
                kind = kind,
                currentName = name,
                completedItems = completed,
                totalItems = total,
                running = true
            )
        }
    }

    private fun updateBytes(name: String?, bytes: Long) {
        val current = _progress.value ?: return
        _progress.value = current.copy(currentName = name, bytesTransferred = bytes, running = true)
    }

    private fun outcomeFor(counters: OperationCounters): OperationOutcome = when {
        counters.failed == 0 &&
            counters.sourceDeleteFailures == 0 &&
            counters.cleanupFailures == 0 -> OperationOutcome.SUCCESS
        counters.completed == 0 && counters.skipped == 0 -> OperationOutcome.FAILURE
        else -> OperationOutcome.PARTIAL
    }

    private suspend fun ensureActiveInternal() {
        currentCoroutineContext().ensureActive()
    }

    private fun isSameParent(item: StorageItem, parent: StorageRef): Boolean {
        return item.ref.treeUri == parent.treeUri && item.ref.parentDocumentId == parent.documentId
    }

    private fun knownTotalBytes(items: List<StorageItem>): Long? {
        if (items.any { it.isDirectory || it.sizeBytes == null }) return null
        return items.sumOf { it.sizeBytes ?: 0L }
    }

    private fun wrapCopyFailure(e: Exception): Exception {
        if (e is StorageException || e is OperationFailure) return e
        if (e is SecurityException) {
            return StorageException(StorageException.Kind.PERMISSION, cause = e)
        }
        return OperationFailure(reasonFor(e) ?: CODE_IO_FAILED, e)
    }

    private fun mapIoFailure(e: IOException): StorageException {
        val message = e.message.orEmpty().lowercase()
        val kind = when {
            message.contains("space") || message.contains("enospc") -> StorageException.Kind.INSUFFICIENT_SPACE
            message.contains("read-only") || message.contains("readonly") -> StorageException.Kind.READ_ONLY
            message.contains("permission") || message.contains("denied") -> StorageException.Kind.PERMISSION
            else -> StorageException.Kind.IO
        }
        return StorageException(kind, e.message, e)
    }

    private fun reasonFor(e: Throwable): String? = when (e) {
        is StorageException -> when (e.kind) {
            StorageException.Kind.PERMISSION -> CODE_PERMISSION
            StorageException.Kind.DISCONNECTED -> CODE_DISCONNECTED
            StorageException.Kind.READ_ONLY -> CODE_READ_ONLY
            StorageException.Kind.NOT_FOUND -> CODE_NOT_FOUND
            StorageException.Kind.NAME_CONFLICT -> CODE_NAME_CONFLICT
            StorageException.Kind.INSUFFICIENT_SPACE -> CODE_INSUFFICIENT_SPACE
            StorageException.Kind.UNSUPPORTED -> CODE_UNSUPPORTED
            StorageException.Kind.IO -> CODE_IO_FAILED
            StorageException.Kind.CANCELLED -> CODE_CANCELLED
            StorageException.Kind.GENERIC -> e.message ?: CODE_IO_FAILED
        }
        is OperationFailure -> e.code
        is SecurityException -> CODE_PERMISSION
        else -> null
    }

    private sealed interface NameChoice {
        data class Use(val name: String) : NameChoice
        data object Skip : NameChoice
        data object Cancel : NameChoice
    }

    private sealed interface ItemResult {
        data class Completed(val nestedSkipped: Int = 0) : ItemResult
        data object Skipped : ItemResult
        data class Failed(val reason: String? = null) : ItemResult
        data class SourceDeleteFailed(val nestedSkipped: Int, val reason: String) : ItemResult
        data object Cancelled : ItemResult
    }

    private data class CopyRecord(val source: StorageItem, val dest: StorageItem)

    private class OperationCounters {
        var completed: Int = 0
        var skipped: Int = 0
        var failed: Int = 0
        var sourceDeleteFailures: Int = 0
        var cleanupFailures: Int = 0
        var usedCopyThenDelete: Boolean = false
    }

    private data class CopySuccess(
        val item: StorageItem,
        val copies: Map<String, CopyRecord>,
        val skippedKeys: Set<String>
    )

    private class ConflictCancelled : Exception()

    private class OperationFailure(val code: String, cause: Throwable? = null) : Exception(code, cause)

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private const val MAX_NAME_ATTEMPTS = 10_000

        const val CODE_BUSY = "busy"
        const val CODE_NOTHING = "nothing"
        const val CODE_INVALID_NAME = "invalid_name"
        const val CODE_CANCELLED = "cancelled"
        const val CODE_SELF_COPY = "self_copy"
        const val CODE_AMBIGUOUS_FOLDER = "ambiguous_folder"
        const val CODE_VERIFY_FAILED = "verify_failed"
        const val CODE_NESTED_SKIPPED = "nested_skipped_source_kept"
        const val CODE_SOURCE_DELETE_FAILED = "source_delete_failed"
        const val CODE_CLEANUP_FAILED = "cleanup_failed"
        const val CODE_COPY_THEN_DELETE = "copy_then_delete"
        const val CODE_PERMISSION = "permission_denied"
        const val CODE_DISCONNECTED = "disconnected"
        const val CODE_READ_ONLY = "read_only"
        const val CODE_NOT_FOUND = "not_found"
        const val CODE_NAME_CONFLICT = "name_conflict"
        const val CODE_INSUFFICIENT_SPACE = "insufficient_space"
        const val CODE_UNSUPPORTED = "unsupported"
        const val CODE_IO_FAILED = "io_error"
    }
}
