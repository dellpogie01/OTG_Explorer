package com.juanmediahub.usbotgorganizer.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.juanmediahub.usbotgorganizer.R
import com.juanmediahub.usbotgorganizer.data.preferences.UserSettings
import com.juanmediahub.usbotgorganizer.data.storage.TreePickResult
import com.juanmediahub.usbotgorganizer.di.AppContainer
import com.juanmediahub.usbotgorganizer.domain.model.BrowserStatus
import com.juanmediahub.usbotgorganizer.domain.model.ConflictPolicy
import com.juanmediahub.usbotgorganizer.domain.model.DocumentCapabilities
import com.juanmediahub.usbotgorganizer.domain.model.LocationAvailability
import com.juanmediahub.usbotgorganizer.domain.model.OperationKind
import com.juanmediahub.usbotgorganizer.domain.model.OperationOutcome
import com.juanmediahub.usbotgorganizer.domain.model.OperationSummary
import com.juanmediahub.usbotgorganizer.domain.model.SavedLocationView
import com.juanmediahub.usbotgorganizer.domain.model.SortField
import com.juanmediahub.usbotgorganizer.domain.model.SortSpec
import com.juanmediahub.usbotgorganizer.domain.model.StorageItem
import com.juanmediahub.usbotgorganizer.domain.model.StorageRef
import com.juanmediahub.usbotgorganizer.domain.model.ThemeMode
import com.juanmediahub.usbotgorganizer.domain.model.TransferMode
import com.juanmediahub.usbotgorganizer.domain.model.ViewMode
import com.juanmediahub.usbotgorganizer.domain.operations.ConflictResolver
import com.juanmediahub.usbotgorganizer.domain.operations.FileOperationEngine
import com.juanmediahub.usbotgorganizer.domain.operations.FileSorter
import com.juanmediahub.usbotgorganizer.domain.operations.FilenameTools
import com.juanmediahub.usbotgorganizer.domain.storage.StorageException
import com.juanmediahub.usbotgorganizer.ui.model.AppDialog
import com.juanmediahub.usbotgorganizer.ui.model.AppScreen
import com.juanmediahub.usbotgorganizer.ui.model.AppUiState
import com.juanmediahub.usbotgorganizer.ui.model.BrowserState
import com.juanmediahub.usbotgorganizer.ui.model.ConflictPrompt
import com.juanmediahub.usbotgorganizer.ui.model.OrganizeState
import com.juanmediahub.usbotgorganizer.ui.model.UiEvent
import com.juanmediahub.usbotgorganizer.ui.model.UiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel(private val container: AppContainer) : ViewModel() {
    private val operations: FileOperationEngine = container.operations
    private val storage = container.storage
    private val preferences = container.preferences
    private val locations = container.locations

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private var conflictDeferred: CompletableDeferred<ConflictPolicy>? = null
    private var conflictCounter = 0L
    private var messageCounter = 0L
    private var loadGeneration = 0

    init {
        observe()
    }

    private fun observe() {
        viewModelScope.launch {
            preferences.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
                refilterBrowser()
            }
        }
        viewModelScope.launch {
            locations.locations.collect {
                refreshLocationsNow()
            }
        }
        viewModelScope.launch {
            operations.progress.collect { progress ->
                _uiState.update { it.copy(progress = progress) }
            }
        }
        viewModelScope.launch {
            operations.clipboard.collect { clipboard ->
                _uiState.update { it.copy(clipboard = clipboard) }
            }
        }
        viewModelScope.launch {
            operations.summary.collect { summary ->
                if (summary == null) return@collect
                _uiState.update {
                    it.copy(summary = summary, summaryDetail = summaryDetail(summary))
                }
                finishOperation(summary)
            }
        }
    }

    // ---------------------------------------------------------------- locations

    fun refreshLocations() {
        viewModelScope.launch { refreshLocationsNow() }
    }

    private suspend fun refreshLocationsNow() {
        _uiState.update { it.copy(locationsLoading = true, locationsProblem = null) }
        try {
            val views = locations.views()
            _uiState.update { state ->
                val browser = state.browser
                val updated = browser?.let { current ->
                    views.firstOrNull { it.location.id == current.location.location.id }
                }
                val nextBrowser = when {
                    browser == null -> null
                    updated == null -> browser
                    else -> browser.copy(location = updated, readOnly = updated.readOnly)
                }
                state.copy(
                    locations = views,
                    locationsLoading = false,
                    browser = nextBrowser
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update {
                it.copy(locationsLoading = false, locationsProblem = errorText(e))
            }
        }
    }

    fun openLocation(id: String) {
        viewModelScope.launch {
            val view = _uiState.value.locations.firstOrNull { it.location.id == id } ?: return@launch
            if (view.availability != LocationAvailability.AVAILABLE) {
                showMessage(
                    if (view.availability == LocationAvailability.PERMISSION_REQUIRED) {
                        R.string.recovery_permission_body
                    } else {
                        R.string.recovery_unavailable_body
                    }
                )
                return@launch
            }
            runCatching { locations.markOpened(id) }
            openRoot(view)
        }
    }

    private suspend fun openRoot(view: SavedLocationView) {
        val rootRef = try {
            storage.rootRef(view.location.treeUri)
        } catch (e: Exception) {
            showMessage(R.string.error_permission_denied)
            return
        }
        val loaded = runCatching { storage.getItem(rootRef) }.getOrNull()
        val root = loaded ?: StorageItem(
            ref = rootRef,
            name = view.location.displayName,
            mimeType = null,
            isDirectory = true,
            sizeBytes = null,
            modifiedEpochMs = null,
            capabilities = DocumentCapabilities(
                canRead = true,
                canWrite = !view.readOnly,
                canCreate = !view.readOnly
            )
        )
        val named = if (root.name.isBlank()) root.copy(name = view.location.displayName) else root
        val settings = _uiState.value.settings
        val browser = BrowserState(
            location = view,
            stack = listOf(named),
            status = BrowserStatus.LOADING,
            viewMode = settings.viewMode,
            sortSpec = settings.sortSpec,
            readOnly = view.readOnly
        )
        loadGeneration++
        _uiState.update {
            it.copy(
                screen = AppScreen.BROWSER,
                backStack = listOf(AppScreen.LOCATIONS),
                browser = browser,
                detailsItem = null,
                organize = null,
                dialog = null,
                conflict = null
            )
        }
        loadFolder(named, listOf(named))
    }

    fun onLocationPicked(result: TreePickResult) {
        viewModelScope.launch {
            try {
                val saved = locations.addFromPicker(
                    result,
                    container.appContext.getString(R.string.default_location_name)
                )
                refreshLocationsNow()
                val view = _uiState.value.locations.firstOrNull { it.location.id == saved.id }
                when {
                    view == null -> showMessage(R.string.error_generic)
                    view.availability != LocationAvailability.AVAILABLE ->
                        showMessage(R.string.permission_not_granted)
                    else -> {
                        runCatching { locations.markOpened(saved.id) }
                        openRoot(view)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(errorRes(e))
            }
        }
    }

    fun onPickerCancelled() {
        showMessage(R.string.picker_cancelled)
    }

    fun beginRenameLocation(id: String) {
        val view = _uiState.value.locations.firstOrNull { it.location.id == id } ?: return
        _uiState.update {
            it.copy(dialog = AppDialog.RenameLocation(view, view.location.displayName))
        }
    }

    fun beginForgetLocation(id: String) {
        val view = _uiState.value.locations.firstOrNull { it.location.id == id } ?: return
        _uiState.update { it.copy(dialog = AppDialog.ForgetLocation(view)) }
    }

    fun confirmForgetLocation() {
        val dialog = _uiState.value.dialog as? AppDialog.ForgetLocation ?: return
        _uiState.update { it.copy(dialog = null) }
        viewModelScope.launch {
            try {
                locations.forget(dialog.location.location.id)
                val clip = operations.clipboard.value
                if (clip != null && clip.items.any { it.ref.treeUri == dialog.location.location.treeUri }) {
                    operations.clearClipboard()
                }
                if (_uiState.value.browser?.location?.location?.id == dialog.location.location.id) {
                    goToLocations()
                }
                refreshLocationsNow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(errorRes(e))
            }
        }
    }

    fun retryLocation(id: String) {
        viewModelScope.launch {
            refreshLocationsNow()
            val view = _uiState.value.locations.firstOrNull { it.location.id == id }
            showMessage(
                if (view?.availability == LocationAvailability.AVAILABLE) {
                    R.string.status_available
                } else {
                    R.string.recovery_note
                }
            )
        }
    }

    // ------------------------------------------------------------------ browser

    fun onQueryChange(value: String) {
        _uiState.update { state ->
            state.copy(browser = state.browser?.copy(query = value))
        }
        refilterBrowser()
    }

    fun clearSearch() = onQueryChange("")

    fun toggleViewMode() {
        _uiState.update { state ->
            val browser = state.browser ?: return@update state
            state.copy(
                browser = browser.copy(
                    viewMode = if (browser.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                )
            )
        }
    }

    fun beginSort(forBrowser: Boolean) {
        val spec = if (forBrowser) {
            _uiState.value.browser?.sortSpec ?: _uiState.value.settings.sortSpec
        } else {
            _uiState.value.settings.sortSpec
        }
        _uiState.update { it.copy(dialog = AppDialog.Sort(spec, forBrowser)) }
    }

    fun applySort(spec: SortSpec, forBrowser: Boolean) {
        _uiState.update { it.copy(dialog = null) }
        if (forBrowser) {
            _uiState.update { state ->
                state.copy(browser = state.browser?.copy(sortSpec = spec))
            }
            refilterBrowser()
        } else {
            viewModelScope.launch { preferences.setSort(spec) }
        }
    }

    fun toggleSelection(key: String) {
        _uiState.update { state ->
            val browser = state.browser ?: return@update state
            val selection = if (key in browser.selection) {
                browser.selection - key
            } else {
                browser.selection + key
            }
            state.copy(browser = browser.copy(selection = selection))
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            val browser = state.browser ?: return@update state
            state.copy(browser = browser.copy(selection = browser.items.mapTo(mutableSetOf()) { it.key }))
        }
    }

    fun clearSelection() {
        _uiState.update { state ->
            state.copy(browser = state.browser?.copy(selection = emptySet()))
        }
    }

    fun openItem(item: StorageItem) {
        if (item.isDirectory) {
            openFolder(item)
            return
        }
        try {
            val uri = storage.documentUri(item.ref)
            _events.tryEmit(UiEvent.OpenDocument(uri, item.mimeType))
        } catch (e: Exception) {
            showMessage(R.string.error_generic)
        }
    }

    fun onOpenFailed() {
        showMessage(R.string.error_no_app)
    }

    fun openFolder(folder: StorageItem) {
        if (!folder.isDirectory) return
        val browser = _uiState.value.browser ?: return
        val newStack = browser.stack + folder
        viewModelScope.launch { loadFolder(folder, newStack) }
    }

    fun goUp(): Boolean {
        val browser = _uiState.value.browser ?: return false
        if (browser.stack.size <= 1) return false
        val newStack = browser.stack.dropLast(1)
        val folder = newStack.last()
        viewModelScope.launch { loadFolder(folder, newStack) }
        return true
    }

    fun navigateToCrumb(index: Int) {
        val browser = _uiState.value.browser ?: return
        if (index < 0 || index >= browser.stack.lastIndex) return
        val newStack = browser.stack.take(index + 1)
        viewModelScope.launch { loadFolder(newStack.last(), newStack) }
    }

    fun refreshBrowser() {
        viewModelScope.launch {
            reloadCurrentFolder(quiet = false)
            refreshLocationsNow()
        }
    }

    private suspend fun reloadCurrentFolder(quiet: Boolean) {
        val browser = _uiState.value.browser ?: return
        val current = browser.current ?: return
        loadFolder(current, browser.stack, quiet)
    }

    private suspend fun loadFolder(
        folder: StorageItem,
        stack: List<StorageItem>,
        quiet: Boolean = false
    ) {
        val generation = ++loadGeneration
        if (!quiet) {
            _uiState.update { state ->
                state.copy(
                    browser = state.browser?.copy(
                        stack = stack,
                        status = BrowserStatus.LOADING,
                        selection = emptySet(),
                        problem = null
                    )
                )
            }
        } else {
            _uiState.update { state ->
                state.copy(browser = state.browser?.copy(stack = stack))
            }
        }
        try {
            val children = storage.listChildren(folder.ref)
            if (generation != loadGeneration) return
            val state = _uiState.value
            val browser = state.browser ?: return
            val visible = FileSorter.sort(
                children,
                browser.sortSpec,
                browser.query,
                state.settings.showHidden
            )
            _uiState.update { current ->
                val b = current.browser ?: return@update current
                current.copy(
                    browser = b.copy(
                        stack = stack,
                        allItems = children,
                        items = visible,
                        status = if (children.isEmpty()) BrowserStatus.EMPTY else BrowserStatus.READY,
                        problem = null
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (generation != loadGeneration) return
            val status = when ((e as? StorageException)?.kind) {
                StorageException.Kind.PERMISSION -> BrowserStatus.PERMISSION_DENIED
                StorageException.Kind.DISCONNECTED -> BrowserStatus.DISCONNECTED
                else -> BrowserStatus.ERROR
            }
            _uiState.update { state ->
                val b = state.browser ?: return@update state
                state.copy(
                    browser = b.copy(
                        stack = stack,
                        allItems = emptyList(),
                        items = emptyList(),
                        status = status,
                        problem = errorText(e)
                    )
                )
            }
        }
    }

    private fun refilterBrowser() {
        _uiState.update { state ->
            val browser = state.browser ?: return@update state
            val visible = FileSorter.sort(
                browser.allItems,
                browser.sortSpec,
                browser.query,
                state.settings.showHidden
            )
            state.copy(browser = browser.copy(items = visible))
        }
    }

    // ------------------------------------------------------------------- details

    fun showDetails(item: StorageItem) {
        _uiState.update { state ->
            if (state.screen == AppScreen.DETAILS) {
                state.copy(detailsItem = item)
            } else {
                state.copy(
                    detailsItem = item,
                    screen = AppScreen.DETAILS,
                    backStack = state.backStack + state.screen
                )
            }
        }
    }

    fun closeDetails() {
        if (_uiState.value.screen == AppScreen.DETAILS) popScreen()
    }

    // ----------------------------------------------------------------- dialogs

    fun beginNewFolder() {
        val browser = _uiState.value.browser ?: return
        if (browser.readOnly) {
            showMessage(R.string.error_read_only)
            return
        }
        val current = browser.current ?: return
        _uiState.update { it.copy(dialog = AppDialog.NewFolder(current.ref, "")) }
    }

    fun beginRenameItem(item: StorageItem) {
        _uiState.update { it.copy(dialog = AppDialog.RenameItem(item, item.name)) }
    }

    fun beginDelete(items: List<StorageItem>) {
        if (items.isEmpty()) return
        _uiState.update { it.copy(dialog = AppDialog.DeleteItems(items)) }
    }

    fun closeDialog() {
        _uiState.update { it.copy(dialog = null) }
    }

    fun onDialogNameChanged(value: String) {
        _uiState.update { state ->
            val updated = when (val dialog = state.dialog) {
                is AppDialog.NewFolder -> dialog.copy(name = value, error = null)
                is AppDialog.RenameItem -> dialog.copy(name = value, error = null)
                is AppDialog.RenameLocation -> dialog.copy(name = value, error = null)
                else -> dialog
            }
            state.copy(dialog = updated)
        }
    }

    fun confirmNewFolder() {
        val dialog = _uiState.value.dialog as? AppDialog.NewFolder ?: return
        val name = FilenameTools.normalize(dialog.name)
        if (!FilenameTools.validate(name)) {
            _uiState.update {
                it.copy(dialog = dialog.copy(error = container.appContext.getString(R.string.invalid_name)))
            }
            return
        }
        _uiState.update { it.copy(dialog = null) }
        viewModelScope.launch {
            operations.createFolder(dialog.parent, name)
                .onSuccess {
                    showMessage(R.string.folder_created)
                    reloadCurrentFolder(quiet = true)
                }
                .onFailure { showMessage(errorRes(it)) }
        }
    }

    fun confirmRenameItem() {
        val dialog = _uiState.value.dialog as? AppDialog.RenameItem ?: return
        val name = FilenameTools.normalize(dialog.name)
        if (!FilenameTools.validate(name)) {
            _uiState.update {
                it.copy(dialog = dialog.copy(error = container.appContext.getString(R.string.invalid_name)))
            }
            return
        }
        _uiState.update { it.copy(dialog = null) }
        viewModelScope.launch {
            operations.rename(dialog.item.ref, name)
                .onSuccess { renamed ->
                    _uiState.update { state ->
                        val details = if (state.detailsItem?.key == dialog.item.key) renamed else state.detailsItem
                        state.copy(detailsItem = details)
                    }
                    showMessage(R.string.item_renamed)
                    reloadCurrentFolder(quiet = true)
                }
                .onFailure { showMessage(errorRes(it)) }
        }
    }

    fun confirmRenameLocation() {
        val dialog = _uiState.value.dialog as? AppDialog.RenameLocation ?: return
        val name = dialog.name.trim()
        if (name.isEmpty()) {
            _uiState.update {
                it.copy(dialog = dialog.copy(error = container.appContext.getString(R.string.name_required)))
            }
            return
        }
        _uiState.update { it.copy(dialog = null) }
        viewModelScope.launch {
            try {
                locations.rename(dialog.location.location.id, name)
                refreshLocationsNow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(errorRes(e))
            }
        }
    }

    fun confirmDelete() {
        val dialog = _uiState.value.dialog as? AppDialog.DeleteItems ?: return
        _uiState.update {
            it.copy(
                dialog = null,
                browser = it.browser?.copy(selection = emptySet())
            )
        }
        viewModelScope.launch {
            operations.deleteItems(dialog.items)
        }
    }

    // ---------------------------------------------------------------- clipboard

    fun copySelected() = setClipboardFromSelection(TransferMode.COPY)

    fun moveSelected() = setClipboardFromSelection(TransferMode.MOVE)

    private fun setClipboardFromSelection(mode: TransferMode) {
        val browser = _uiState.value.browser ?: return
        val items = browser.selectedItems
        if (items.isEmpty()) {
            showMessage(R.string.error_no_pending_items)
            return
        }
        operations.setClipboard(items, mode)
        _uiState.update { it.copy(browser = browser.copy(selection = emptySet())) }
        showMessage(if (mode == TransferMode.COPY) R.string.copy_ready else R.string.move_ready)
    }

    fun copyItem(item: StorageItem) {
        operations.setClipboard(listOf(item), TransferMode.COPY)
        showMessage(R.string.copy_ready)
    }

    fun moveItem(item: StorageItem) {
        operations.setClipboard(listOf(item), TransferMode.MOVE)
        showMessage(R.string.move_ready)
    }

    fun clearClipboard() {
        operations.clearClipboard()
    }

    fun pasteHere() {
        val browser = _uiState.value.browser ?: return
        if (browser.readOnly) {
            showMessage(R.string.error_read_only)
            return
        }
        val current = browser.current ?: return
        if (operations.clipboard.value == null) {
            showMessage(R.string.error_no_pending_items)
            return
        }
        viewModelScope.launch {
            operations.paste(current.ref, ConflictResolver { name -> resolveConflictName(name) })
        }
    }

    // ----------------------------------------------------------------- organize

    fun beginOrganize() {
        val state = _uiState.value
        val browser = state.browser ?: return
        val current = browser.current ?: return
        val files = browser.selectedItems.filter { !it.isDirectory }
        if (files.isEmpty()) {
            showMessage(R.string.organize_no_files)
            return
        }
        val plans = operations.previewOrganize(files, current)
        _uiState.update {
            it.copy(
                organize = OrganizeState(parent = current, plans = plans),
                screen = AppScreen.ORGANIZE,
                backStack = it.backStack + it.screen
            )
        }
    }

    fun toggleOrganizePlan(key: String) {
        _uiState.update { state ->
            val organize = state.organize ?: return@update state
            state.copy(
                organize = organize.copy(
                    plans = organize.plans.map {
                        if (it.source.key == key) it.copy(selected = !it.selected) else it
                    }
                )
            )
        }
    }

    fun setOrganizeMode(mode: TransferMode) {
        _uiState.update { state ->
            state.copy(organize = state.organize?.copy(mode = mode))
        }
    }

    fun setOrganizeConflict(policy: ConflictPolicy) {
        _uiState.update { state ->
            state.copy(organize = state.organize?.copy(conflictPolicy = policy))
        }
    }

    fun closeOrganize() {
        _uiState.update { it.copy(organize = null) }
        if (_uiState.value.screen == AppScreen.ORGANIZE) popScreen()
    }

    fun applyOrganize() {
        val state = _uiState.value
        val organize = state.organize ?: return
        val current = state.browser?.current ?: return
        if (organize.selectedPlans.isEmpty()) {
            showMessage(R.string.organize_no_files)
            return
        }
        val policy = organize.conflictPolicy
        viewModelScope.launch {
            operations.organize(
                current.ref,
                organize.plans,
                organize.mode,
                ConflictResolver { policy }
            )
        }
    }

    // ---------------------------------------------------------------- operations

    fun cancelOperation() {
        conflictDeferred?.complete(ConflictPolicy.CANCEL)
        conflictDeferred = null
        _uiState.update { it.copy(conflict = null) }
        operations.cancel()
    }

    private suspend fun resolveConflictName(name: String): ConflictPolicy {
        val id = ++conflictCounter
        val deferred = CompletableDeferred<ConflictPolicy>()
        conflictDeferred = deferred
        _uiState.update { it.copy(conflict = ConflictPrompt(id, name)) }
        return try {
            deferred.await()
        } finally {
            if (conflictDeferred === deferred) conflictDeferred = null
            _uiState.update { state ->
                if (state.conflict?.id == id) state.copy(conflict = null) else state
            }
        }
    }

    fun resolveConflict(policy: ConflictPolicy) {
        val deferred = conflictDeferred ?: return
        conflictDeferred = null
        _uiState.update { it.copy(conflict = null) }
        deferred.complete(policy)
    }

    fun dismissSummary() {
        operations.clearSummary()
        _uiState.update { it.copy(summary = null, summaryDetail = null) }
    }

    private suspend fun finishOperation(summary: OperationSummary) {
        _uiState.update { state ->
            state.copy(
                browser = state.browser?.copy(selection = emptySet())
            )
        }
        if (summary.kind == OperationKind.ORGANIZE) {
            _uiState.update { it.copy(organize = null) }
            if (_uiState.value.screen == AppScreen.ORGANIZE) popScreen()
        }
        if (summary.kind == OperationKind.DELETE && _uiState.value.screen == AppScreen.DETAILS) {
            popScreen()
        }
        if (summary.kind == OperationKind.COPY ||
            summary.kind == OperationKind.MOVE ||
            summary.kind == OperationKind.DELETE ||
            summary.kind == OperationKind.ORGANIZE
        ) {
            reloadCurrentFolder(quiet = true)
            refreshLocationsNow()
        }
    }

    // ------------------------------------------------------------------ settings

    fun setTheme(mode: ThemeMode) {
        _uiState.update { state ->
            state.copy(settings = state.settings.copy(themeMode = mode))
        }
        viewModelScope.launch { preferences.setTheme(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(settings = state.settings.copy(dynamicColor = enabled))
        }
        viewModelScope.launch { preferences.setDynamicColor(enabled) }
    }

    fun setDefaultViewMode(mode: ViewMode) {
        _uiState.update { state ->
            state.copy(settings = state.settings.copy(viewMode = mode))
        }
        viewModelScope.launch { preferences.setViewMode(mode) }
    }

    fun setShowHidden(value: Boolean) {
        _uiState.update { state ->
            state.copy(settings = state.settings.copy(showHidden = value))
        }
        viewModelScope.launch { preferences.setShowHidden(value) }
        refilterBrowser()
    }

    fun openSettings() {
        val state = _uiState.value
        if (state.screen == AppScreen.SETTINGS) return
        _uiState.update {
            it.copy(screen = AppScreen.SETTINGS, backStack = it.backStack + it.screen)
        }
    }

    // ---------------------------------------------------------------- navigation

    fun goToLocations() {
        loadGeneration++
        _uiState.update {
            it.copy(
                screen = AppScreen.LOCATIONS,
                backStack = emptyList(),
                browser = null,
                detailsItem = null,
                organize = null,
                dialog = null,
                conflict = null
            )
        }
    }

    fun onBack(): Boolean {
        val state = _uiState.value
        return when (state.screen) {
            AppScreen.LOCATIONS -> false
            AppScreen.BROWSER -> {
                val browser = state.browser
                when {
                    browser != null && browser.selectionMode -> {
                        clearSelection()
                        true
                    }
                    browser != null && browser.stack.size > 1 -> {
                        goUp()
                        true
                    }
                    else -> {
                        goToLocations()
                        true
                    }
                }
            }
            AppScreen.DETAILS, AppScreen.ORGANIZE, AppScreen.SETTINGS -> {
                if (state.screen == AppScreen.ORGANIZE) {
                    _uiState.update { it.copy(organize = null) }
                }
                popScreen()
            }
        }
    }

    private fun popScreen(): Boolean {
        val state = _uiState.value
        val stack = state.backStack
        if (stack.isEmpty()) return false
        _uiState.update {
            it.copy(screen = stack.last(), backStack = stack.dropLast(1))
        }
        return true
    }

    // ------------------------------------------------------------------- helper

    fun consumeMessage(id: Long) {
        _uiState.update { state ->
            if (state.message?.id == id) state.copy(message = null) else state
        }
    }

    private fun showMessage(@StringRes res: Int) {
        showMessage(container.appContext.getString(res))
    }

    private fun showMessage(text: String) {
        val message = UiMessage(++messageCounter, text)
        _uiState.update { it.copy(message = message) }
    }

    private fun errorText(e: Throwable): String = container.appContext.getString(errorRes(e))

    private fun errorRes(e: Throwable): Int {
        if (e is StorageException) {
            return when (e.kind) {
                StorageException.Kind.PERMISSION -> R.string.error_permission_denied
                StorageException.Kind.DISCONNECTED -> R.string.error_disconnected
                StorageException.Kind.READ_ONLY -> R.string.error_read_only
                StorageException.Kind.INSUFFICIENT_SPACE -> R.string.error_insufficient_space
                StorageException.Kind.UNSUPPORTED -> R.string.error_unsupported
                StorageException.Kind.NOT_FOUND -> R.string.error_not_found
                StorageException.Kind.GENERIC -> codeRes(e.message)
                else -> R.string.error_generic
            }
        }
        if (e is SecurityException) return R.string.error_permission_denied
        return R.string.error_generic
    }

    private fun codeRes(code: String?): Int = when (code) {
        FileOperationEngine.CODE_BUSY -> R.string.operation_busy
        FileOperationEngine.CODE_INVALID_NAME -> R.string.error_name_invalid
        FileOperationEngine.CODE_SELF_COPY -> R.string.error_self_copy
        FileOperationEngine.CODE_AMBIGUOUS_FOLDER -> R.string.error_ambiguous_folder_copy
        FileOperationEngine.CODE_PERMISSION -> R.string.error_permission_denied
        FileOperationEngine.CODE_DISCONNECTED -> R.string.error_disconnected
        FileOperationEngine.CODE_READ_ONLY -> R.string.error_read_only
        FileOperationEngine.CODE_NOT_FOUND -> R.string.error_not_found
        FileOperationEngine.CODE_INSUFFICIENT_SPACE -> R.string.error_insufficient_space
        FileOperationEngine.CODE_UNSUPPORTED -> R.string.error_unsupported
        FileOperationEngine.CODE_NAME_CONFLICT -> R.string.error_provider
        else -> R.string.error_generic
    }

    private fun summaryDetail(summary: OperationSummary): String? {
        val res = when (summary.message) {
            FileOperationEngine.CODE_BUSY -> R.string.operation_busy
            FileOperationEngine.CODE_NOTHING -> R.string.error_no_pending_items
            FileOperationEngine.CODE_INVALID_NAME -> R.string.error_name_invalid
            FileOperationEngine.CODE_INSUFFICIENT_SPACE -> R.string.error_insufficient_space
            FileOperationEngine.CODE_PERMISSION -> R.string.error_permission_denied
            FileOperationEngine.CODE_DISCONNECTED -> R.string.error_disconnected
            FileOperationEngine.CODE_READ_ONLY -> R.string.error_read_only
            FileOperationEngine.CODE_UNSUPPORTED -> R.string.error_unsupported
            FileOperationEngine.CODE_NOT_FOUND -> R.string.error_not_found
            FileOperationEngine.CODE_SELF_COPY -> R.string.error_self_copy
            FileOperationEngine.CODE_AMBIGUOUS_FOLDER -> R.string.error_ambiguous_folder_copy
            FileOperationEngine.CODE_VERIFY_FAILED -> R.string.summary_originals_kept
            FileOperationEngine.CODE_NESTED_SKIPPED -> R.string.summary_originals_kept
            FileOperationEngine.CODE_CLEANUP_FAILED -> R.string.summary_cleanup_failed
            FileOperationEngine.CODE_SOURCE_DELETE_FAILED -> R.string.summary_source_delete_failed
            FileOperationEngine.CODE_COPY_THEN_DELETE -> R.string.summary_not_atomic
            else -> null
        }
        return res?.let { container.appContext.getString(it) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AppViewModel(container) as T
                }
            }
        }
    }
}
