package com.juanmediahub.usbotgorganizer.domain.model

data class StorageRef(
    val treeUri: String,
    val documentId: String,
    val parentDocumentId: String? = null,
    val ancestorDocumentIds: List<String> = emptyList()
) {
    val key: String get() = "$treeUri::$documentId"
}

data class DocumentCapabilities(
    val canRead: Boolean = true,
    val canWrite: Boolean = false,
    val canRename: Boolean = false,
    val canDelete: Boolean = false,
    val canCreate: Boolean = false,
    val supportsCopy: Boolean = false,
    val supportsMove: Boolean = false,
    val isVirtual: Boolean = false
)

data class StorageItem(
    val ref: StorageRef,
    val name: String,
    val mimeType: String?,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
    val modifiedEpochMs: Long?,
    val capabilities: DocumentCapabilities,
    val ancestorDocumentIds: List<String> = emptyList()
) {
    val key: String get() = ref.key
}

data class SavedLocation(
    val id: String,
    val treeUri: String,
    val displayName: String,
    val rootDocumentId: String,
    val addedAtEpochMs: Long,
    val lastOpenedEpochMs: Long?,
    val hasWritePermission: Boolean
)

enum class LocationAvailability {
    AVAILABLE,
    UNAVAILABLE,
    PERMISSION_REQUIRED
}

data class SavedLocationView(
    val location: SavedLocation,
    val availability: LocationAvailability,
    val readOnly: Boolean
)

enum class ViewMode { LIST, GRID }

enum class SortField { NAME, SIZE, MODIFIED, TYPE }

enum class SortDirection { ASC, DESC }

data class SortSpec(
    val field: SortField = SortField.NAME,
    val direction: SortDirection = SortDirection.ASC,
    val foldersFirst: Boolean = true
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class FileCategory {
    IMAGES, VIDEOS, AUDIO, DOCUMENTS, ARCHIVES, APKS, OTHER
}

enum class ConflictPolicy { KEEP_BOTH, SKIP, CANCEL }

enum class TransferMode { COPY, MOVE }

enum class BrowserStatus {
    LOADING,
    READY,
    EMPTY,
    PERMISSION_DENIED,
    DISCONNECTED,
    ERROR
}

enum class OperationKind { COPY, MOVE, DELETE, ORGANIZE, CREATE_FOLDER, RENAME }

enum class OperationOutcome { SUCCESS, PARTIAL, FAILURE, CANCELLED }

data class OperationProgress(
    val kind: OperationKind,
    val currentName: String?,
    val completedItems: Int,
    val totalItems: Int?,
    val bytesTransferred: Long,
    val totalBytes: Long?,
    val running: Boolean
) {
    val itemFraction: Float?
        get() = totalItems?.takeIf { it > 0 }?.let { (completedItems.toFloat() / it).coerceIn(0f, 1f) }

    val byteFraction: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let { (bytesTransferred.toFloat() / it).coerceIn(0f, 1f) }
}

data class OperationSummary(
    val outcome: OperationOutcome,
    val kind: OperationKind,
    val completedItems: Int = 0,
    val skippedItems: Int = 0,
    val failedItems: Int = 0,
    val sourceDeleteFailures: Int = 0,
    val incompleteCleanupFailures: Int = 0,
    val usedCopyThenDelete: Boolean = false,
    val message: String? = null
)

data class ClipboardState(
    val items: List<StorageItem>,
    val mode: TransferMode
)

data class OrganizePlanItem(
    val source: StorageItem,
    val category: FileCategory,
    val destinationFolderName: String,
    val selected: Boolean = true,
    val alreadyInPlace: Boolean = false
)

sealed class AppError {
    data object PickerCancelled : AppError()
    data object PermissionRevoked : AppError()
    data object ReadOnly : AppError()
    data object Disconnected : AppError()
    data object CreateFolderFailed : AppError()
    data object RenameUnsupported : AppError()
    data object MoveUnsupported : AppError()
    data object InsufficientSpace : AppError()
    data object NoCompatibleApp : AppError()
    data object InvalidName : AppError()
    data object SelfCopy : AppError()
    data object Busy : AppError()
    data class Provider(val reason: String? = null) : AppError()
    data class Generic(val reason: String? = null) : AppError()
}
