package com.juanmediahub.usbotgorganizer.ui.model

import android.net.Uri
import com.juanmediahub.usbotgorganizer.data.preferences.UserSettings
import com.juanmediahub.usbotgorganizer.domain.model.BrowserStatus
import com.juanmediahub.usbotgorganizer.domain.model.ClipboardState
import com.juanmediahub.usbotgorganizer.domain.model.ConflictPolicy
import com.juanmediahub.usbotgorganizer.domain.model.OperationProgress
import com.juanmediahub.usbotgorganizer.domain.model.OperationSummary
import com.juanmediahub.usbotgorganizer.domain.model.OrganizePlanItem
import com.juanmediahub.usbotgorganizer.domain.model.SavedLocationView
import com.juanmediahub.usbotgorganizer.domain.model.SortSpec
import com.juanmediahub.usbotgorganizer.domain.model.StorageItem
import com.juanmediahub.usbotgorganizer.domain.model.StorageRef
import com.juanmediahub.usbotgorganizer.domain.model.TransferMode
import com.juanmediahub.usbotgorganizer.domain.model.ViewMode

enum class AppScreen { LOCATIONS, BROWSER, DETAILS, ORGANIZE, SETTINGS }

data class ConflictPrompt(
    val id: Long,
    val name: String
)

sealed interface AppDialog {
    data class NewFolder(
        val parent: StorageRef,
        val name: String,
        val error: String? = null
    ) : AppDialog

    data class RenameItem(
        val item: StorageItem,
        val name: String,
        val error: String? = null
    ) : AppDialog

    data class RenameLocation(
        val location: SavedLocationView,
        val name: String,
        val error: String? = null
    ) : AppDialog

    data class DeleteItems(val items: List<StorageItem>) : AppDialog
    data class ForgetLocation(val location: SavedLocationView) : AppDialog
    data class Sort(val spec: SortSpec, val forBrowser: Boolean) : AppDialog
}

data class BrowserState(
    val location: SavedLocationView,
    val stack: List<StorageItem>,
    val allItems: List<StorageItem> = emptyList(),
    val items: List<StorageItem> = emptyList(),
    val status: BrowserStatus = BrowserStatus.LOADING,
    val query: String = "",
    val selection: Set<String> = emptySet(),
    val viewMode: ViewMode = ViewMode.LIST,
    val sortSpec: SortSpec = SortSpec(),
    val readOnly: Boolean = false,
    val problem: String? = null
) {
    val current: StorageItem? get() = stack.lastOrNull()
    val selectionMode: Boolean get() = selection.isNotEmpty()
    val canWrite: Boolean get() = !readOnly
    val selectedItems: List<StorageItem> get() = allItems.filter { it.key in selection }
}

data class OrganizeState(
    val parent: StorageItem,
    val plans: List<OrganizePlanItem>,
    val mode: TransferMode = TransferMode.MOVE,
    val conflictPolicy: ConflictPolicy = ConflictPolicy.KEEP_BOTH
) {
    val activePlans: List<OrganizePlanItem>
        get() = plans.filter { !it.source.isDirectory && !it.alreadyInPlace }

    val selectedPlans: List<OrganizePlanItem>
        get() = activePlans.filter { it.selected }
}

data class UiMessage(val id: Long, val text: String)

data class AppUiState(
    val screen: AppScreen = AppScreen.LOCATIONS,
    val backStack: List<AppScreen> = emptyList(),
    val locations: List<SavedLocationView> = emptyList(),
    val locationsLoading: Boolean = true,
    val locationsProblem: String? = null,
    val browser: BrowserState? = null,
    val settings: UserSettings = UserSettings(),
    val progress: OperationProgress? = null,
    val summary: OperationSummary? = null,
    val summaryDetail: String? = null,
    val conflict: ConflictPrompt? = null,
    val clipboard: ClipboardState? = null,
    val detailsItem: StorageItem? = null,
    val organize: OrganizeState? = null,
    val dialog: AppDialog? = null,
    val message: UiMessage? = null
) {
    val busy: Boolean get() = progress?.running == true
}

sealed interface UiEvent {
    data class OpenDocument(val uri: Uri, val mimeType: String?) : UiEvent
}
