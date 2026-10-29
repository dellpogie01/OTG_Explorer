package com.juanmediahub.usbotgorganizer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juanmediahub.usbotgorganizer.R
import com.juanmediahub.usbotgorganizer.domain.model.BrowserStatus
import com.juanmediahub.usbotgorganizer.domain.model.StorageItem
import com.juanmediahub.usbotgorganizer.domain.model.TransferMode
import com.juanmediahub.usbotgorganizer.domain.model.ViewMode
import com.juanmediahub.usbotgorganizer.ui.AppViewModel
import com.juanmediahub.usbotgorganizer.ui.components.AppTopBar
import com.juanmediahub.usbotgorganizer.ui.components.Breadcrumbs
import com.juanmediahub.usbotgorganizer.ui.components.EmptyState
import com.juanmediahub.usbotgorganizer.ui.components.FileGridItem
import com.juanmediahub.usbotgorganizer.ui.components.FileListItem
import com.juanmediahub.usbotgorganizer.ui.components.InfoBanner
import com.juanmediahub.usbotgorganizer.ui.components.SelectionActionsBar
import com.juanmediahub.usbotgorganizer.ui.model.AppUiState
import com.juanmediahub.usbotgorganizer.ui.model.BrowserState

@Composable
fun BrowserScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    onPickLocation: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val browser = state.browser
    if (browser == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val clipboard = state.clipboard
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        BrowserTopBar(
            browser = browser,
            viewModel = viewModel,
            onBrowseBack = { viewModel.onBack() },
            onSearch = { searchOpen = true }
        )

        if (browser.selectionMode) {
            SelectionActionsBar(
                onSelectAll = viewModel::selectAll,
                onCopy = viewModel::copySelected,
                onMove = viewModel::moveSelected,
                onOrganize = viewModel::beginOrganize,
                onDelete = {
                    val selected = browser.selectedItems
                    if (selected.isNotEmpty()) viewModel.beginDelete(selected)
                },
                actionsEnabled = !state.busy
            )
        }

        if (searchOpen || browser.query.isNotEmpty()) {
            SearchRow(
                browser = browser,
                onQueryChange = viewModel::onQueryChange,
                onClear = viewModel::clearSearch,
                onClose = {
                    searchOpen = false
                    viewModel.clearSearch()
                }
            )
        }

        if (clipboard != null) {
            InfoBanner(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                text = if (clipboard.mode == TransferMode.COPY) {
                    pluralStringResource(
                        R.plurals.clipboard_copy_pending,
                        clipboard.items.size,
                        clipboard.items.size
                    )
                } else {
                    pluralStringResource(
                        R.plurals.clipboard_move_pending,
                        clipboard.items.size,
                        clipboard.items.size
                    )
                },
                title = if (clipboard.mode == TransferMode.COPY) {
                    stringResource(R.string.clipboard_copy)
                } else {
                    stringResource(R.string.clipboard_move)
                },
                icon = if (clipboard.mode == TransferMode.COPY) Icons.Filled.Refresh else Icons.Filled.Sort,
                action = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = viewModel::pasteHere,
                            enabled = !browser.readOnly && !state.busy
                        ) {
                            Text(text = stringResource(R.string.paste_here))
                        }
                        TextButton(onClick = viewModel::clearClipboard) {
                            Text(text = stringResource(R.string.clear_pending))
                        }
                    }
                }
            )
        }

        if (browser.readOnly) {
            InfoBanner(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                text = stringResource(R.string.read_only_banner_body),
                title = stringResource(R.string.read_only_banner),
                icon = Icons.Filled.Warning,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }

        Breadcrumbs(
            labels = browser.stack.mapIndexed { index, item ->
                if (index == 0) browser.location.location.displayName else item.name
            },
            onCrumbClick = viewModel::navigateToCrumb
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            BrowserContent(
                browser = browser,
                busy = state.busy,
                onRetry = viewModel::refreshBrowser,
                onChooseAgain = {
                    onPickLocation(browser.location.location.treeUri)
                },
                onForget = {
                    viewModel.beginForgetLocation(browser.location.location.id)
                },
                onBackToLocations = viewModel::goToLocations,
                onClearSearch = viewModel::clearSearch,
                onNewFolder = viewModel::beginNewFolder,
                onOpenItem = { item ->
                    if (browser.selectionMode) {
                        viewModel.toggleSelection(item.key)
                    } else {
                        viewModel.openItem(item)
                    }
                },
                onLongClick = { item -> viewModel.toggleSelection(item.key) },
                onDetails = viewModel::showDetails
            )

            if (browser.canWrite &&
                (browser.status == BrowserStatus.READY || browser.status == BrowserStatus.EMPTY) &&
                !browser.selectionMode
            ) {
                FloatingActionButton(
                    onClick = viewModel::beginNewFolder,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(18.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CreateNewFolder,
                        contentDescription = stringResource(R.string.new_folder)
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserTopBar(
    browser: BrowserState,
    viewModel: AppViewModel,
    onBrowseBack: () -> Unit,
    onSearch: () -> Unit
) {
    if (browser.selectionMode) {
        AppTopBar(
            title = "${browser.selection.size} · ${stringResource(R.string.selection_mode)}",
            onBack = viewModel::clearSelection,
            backDescription = stringResource(R.string.clear_selection),
            actions = {
                IconButton(onClick = viewModel::selectAll) {
                    Icon(
                        imageVector = Icons.Filled.SelectAll,
                        contentDescription = stringResource(R.string.select_all)
                    )
                }
                IconButton(
                    onClick = {
                        val selected = browser.selectedItems
                        if (selected.isNotEmpty()) viewModel.beginDelete(selected)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.cd_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        )
        return
    }

    var menuOpen by remember { mutableStateOf(false) }
    val readOnlyLabel = stringResource(R.string.read_only_short)
    val folderSubtitle = if (browser.readOnly) {
        "${browser.location.location.displayName} · $readOnlyLabel"
    } else {
        browser.location.location.displayName
    }
    AppTopBar(
        title = browser.current?.name ?: stringResource(R.string.current_folder),
        subtitle = folderSubtitle,
        onBack = onBrowseBack,
        actions = {
            IconButton(onClick = onSearch) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.cd_search)
                )
            }
            IconButton(onClick = viewModel::refreshBrowser) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.cd_refresh)
                )
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.cd_more)
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.sort_by)) },
                    leadingIcon = { Icon(Icons.Filled.Sort, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        viewModel.beginSort(forBrowser = true)
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (browser.viewMode == ViewMode.LIST) {
                                stringResource(R.string.view_grid)
                            } else {
                                stringResource(R.string.view_list)
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (browser.viewMode == ViewMode.LIST) {
                                Icons.Filled.GridView
                            } else {
                                Icons.Filled.ViewList
                            },
                            contentDescription = null
                        )
                    },
                    onClick = {
                        menuOpen = false
                        viewModel.toggleViewMode()
                    }
                )
                if (browser.canWrite) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.new_folder)) },
                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            viewModel.beginNewFolder()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.select_items)) },
                    leadingIcon = { Icon(Icons.Filled.SelectAll, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        viewModel.selectAll()
                    }
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.settings)) },
                    leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        viewModel.openSettings()
                    }
                )
            }
        }
    )
}

@Composable
private fun SearchRow(
    browser: BrowserState,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = browser.query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text(text = stringResource(R.string.search_hint)) },
            leadingIcon = {
                Icon(imageVector = Icons.Filled.Search, contentDescription = null)
            },
            trailingIcon = {
                Row {
                    if (browser.query.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.cd_clear_search)
                            )
                        }
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_close_search)
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )
    }
}

@Composable
private fun BrowserContent(
    browser: BrowserState,
    busy: Boolean,
    onRetry: () -> Unit,
    onChooseAgain: () -> Unit,
    onForget: () -> Unit,
    onBackToLocations: () -> Unit,
    onClearSearch: () -> Unit,
    onNewFolder: () -> Unit,
    onOpenItem: (StorageItem) -> Unit,
    onLongClick: (StorageItem) -> Unit,
    onDetails: (StorageItem) -> Unit
) {
    when (browser.status) {
        BrowserStatus.LOADING -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        BrowserStatus.PERMISSION_DENIED,
        BrowserStatus.DISCONNECTED,
        BrowserStatus.ERROR -> {
            RecoveryState(
                browser = browser,
                onRetry = onRetry,
                onChooseAgain = onChooseAgain,
                onForget = onForget,
                onBackToLocations = onBackToLocations
            )
        }
        BrowserStatus.EMPTY -> {
            EmptyState(
                icon = Icons.Filled.FolderOpen,
                title = stringResource(R.string.empty_folder),
                body = stringResource(R.string.empty_folder_body),
                action = if (browser.canWrite) {
                    {
                        Button(onClick = onNewFolder) {
                            Text(text = stringResource(R.string.create_folder))
                        }
                    }
                } else {
                    null
                }
            )
        }
        BrowserStatus.READY -> {
            if (browser.items.isEmpty() && browser.query.isNotBlank()) {
                EmptyState(
                    icon = Icons.Filled.Search,
                    title = stringResource(R.string.empty_search),
                    body = stringResource(R.string.empty_search_body),
                    action = {
                        OutlinedButton(onClick = onClearSearch) {
                            Text(text = stringResource(R.string.clear_search))
                        }
                    }
                )
            } else if (browser.viewMode == ViewMode.LIST) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(browser.items, key = { it.key }) { item ->
                        FileListItem(
                            item = item,
                            selected = item.key in browser.selection,
                            selectionMode = browser.selectionMode,
                            onClick = { onOpenItem(item) },
                            onLongClick = { onLongClick(item) },
                            onDetails = { onDetails(item) }
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    modifier = Modifier.fillMaxSize(),
                    columns = GridCells.Adaptive(minSize = 148.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(browser.items, key = { it.key }) { item ->
                        FileGridItem(
                            item = item,
                            selected = item.key in browser.selection,
                            selectionMode = browser.selectionMode,
                            onClick = { onOpenItem(item) },
                            onLongClick = { onLongClick(item) },
                            onDetails = { onDetails(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecoveryState(
    browser: BrowserState,
    onRetry: () -> Unit,
    onChooseAgain: () -> Unit,
    onForget: () -> Unit,
    onBackToLocations: () -> Unit
) {
    EmptyState(
        icon = Icons.Filled.Warning,
        title = stringResource(R.string.recovery_title),
        body = browser.problem ?: stringResource(R.string.recovery_generic_body),
        action = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = onRetry) {
                    Text(text = stringResource(R.string.retry))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onChooseAgain) {
                    Text(text = stringResource(R.string.choose_location))
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onBackToLocations) {
                    Text(text = stringResource(R.string.back_to_locations))
                }
                TextButton(onClick = onForget) {
                    Text(
                        text = stringResource(R.string.forget_location),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}
