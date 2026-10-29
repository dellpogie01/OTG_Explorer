package com.juanmediahub.usbotgorganizer.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.juanmediahub.usbotgorganizer.domain.model.ConflictPolicy
import com.juanmediahub.usbotgorganizer.domain.model.ThemeMode
import com.juanmediahub.usbotgorganizer.ui.components.ConflictDialog
import com.juanmediahub.usbotgorganizer.ui.components.DeleteDialog
import com.juanmediahub.usbotgorganizer.ui.components.ForgetLocationDialog
import com.juanmediahub.usbotgorganizer.ui.components.NewFolderDialog
import com.juanmediahub.usbotgorganizer.ui.components.ProgressDialog
import com.juanmediahub.usbotgorganizer.ui.components.RenameItemDialog
import com.juanmediahub.usbotgorganizer.ui.components.RenameLocationDialog
import com.juanmediahub.usbotgorganizer.ui.components.SortDialog
import com.juanmediahub.usbotgorganizer.ui.components.SummaryDialog
import com.juanmediahub.usbotgorganizer.ui.model.AppDialog
import com.juanmediahub.usbotgorganizer.ui.model.AppScreen
import com.juanmediahub.usbotgorganizer.ui.model.AppUiState
import com.juanmediahub.usbotgorganizer.ui.model.UiEvent
import com.juanmediahub.usbotgorganizer.ui.screens.BrowserScreen
import com.juanmediahub.usbotgorganizer.ui.screens.DetailsScreen
import com.juanmediahub.usbotgorganizer.ui.screens.LocationsScreen
import com.juanmediahub.usbotgorganizer.ui.screens.OrganizeScreen
import com.juanmediahub.usbotgorganizer.ui.screens.SettingsScreen

@Composable
fun OtgExplorerRoot(
    state: AppUiState,
    viewModel: AppViewModel,
    onPickLocation: (Uri?) -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val darkTheme = when (state.settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    SideEffect {
        val window = context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.OpenDocument -> {
                    val opened = openDocument(context, event.uri, event.mimeType)
                    if (!opened) viewModel.onOpenFailed()
                }
            }
        }
    }

    val message = state.message
    LaunchedEffect(message?.id) {
        if (message == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(message.text)
        viewModel.consumeMessage(message.id)
    }

    BackHandler(enabled = state.screen != AppScreen.LOCATIONS || state.summary != null) {
        if (state.summary != null) {
            viewModel.dismissSummary()
        } else {
            viewModel.onBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (state.screen) {
            AppScreen.LOCATIONS -> LocationsScreen(
                state = state,
                onAddLocation = { onPickLocation(null) },
                onChooseAgain = { treeUri -> onPickLocation(Uri.parse(treeUri)) },
                onOpenLocation = viewModel::openLocation,
                onRenameLocation = viewModel::beginRenameLocation,
                onForgetLocation = viewModel::beginForgetLocation,
                onRetryLocation = viewModel::retryLocation,
                onRefresh = viewModel::refreshLocations,
                onSettings = viewModel::openSettings
            )
            AppScreen.BROWSER -> BrowserScreen(
                state = state,
                viewModel = viewModel,
                onPickLocation = { treeUri -> onPickLocation(treeUri?.let(Uri::parse)) }
            )
            AppScreen.DETAILS -> DetailsScreen(
                state = state,
                viewModel = viewModel
            )
            AppScreen.ORGANIZE -> OrganizeScreen(
                state = state,
                viewModel = viewModel
            )
            AppScreen.SETTINGS -> SettingsScreen(
                state = state,
                viewModel = viewModel
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(12.dp)
        )
    }

    val conflict = state.conflict
    val progress = state.progress
    when {
        conflict != null -> ConflictDialog(
            name = conflict.name,
            onKeepBoth = { viewModel.resolveConflict(ConflictPolicy.KEEP_BOTH) },
            onSkip = { viewModel.resolveConflict(ConflictPolicy.SKIP) },
            onCancel = { viewModel.resolveConflict(ConflictPolicy.CANCEL) }
        )
        state.busy && progress != null -> ProgressDialog(
            progress = progress,
            onCancel = viewModel::cancelOperation
        )
        else -> {
            val summary = state.summary
            if (summary != null) {
                SummaryDialog(
                    summary = summary,
                    detail = state.summaryDetail,
                    onDismiss = viewModel::dismissSummary
                )
            }
            when (val dialog = state.dialog) {
                is AppDialog.NewFolder -> NewFolderDialog(
                    name = dialog.name,
                    error = dialog.error,
                    onNameChange = viewModel::onDialogNameChanged,
                    onConfirm = viewModel::confirmNewFolder,
                    onDismiss = viewModel::closeDialog
                )
                is AppDialog.RenameItem -> RenameItemDialog(
                    name = dialog.name,
                    error = dialog.error,
                    onNameChange = viewModel::onDialogNameChanged,
                    onConfirm = viewModel::confirmRenameItem,
                    onDismiss = viewModel::closeDialog
                )
                is AppDialog.RenameLocation -> RenameLocationDialog(
                    name = dialog.name,
                    error = dialog.error,
                    onNameChange = viewModel::onDialogNameChanged,
                    onConfirm = viewModel::confirmRenameLocation,
                    onDismiss = viewModel::closeDialog
                )
                is AppDialog.DeleteItems -> DeleteDialog(
                    items = dialog.items,
                    onConfirm = viewModel::confirmDelete,
                    onDismiss = viewModel::closeDialog
                )
                is AppDialog.ForgetLocation -> ForgetLocationDialog(
                    location = dialog.location,
                    onConfirm = viewModel::confirmForgetLocation,
                    onDismiss = viewModel::closeDialog
                )
                is AppDialog.Sort -> SortDialog(
                    spec = dialog.spec,
                    onApply = { spec -> viewModel.applySort(spec, dialog.forBrowser) },
                    onDismiss = viewModel::closeDialog
                )
                null -> Unit
            }
        }
    }
}

private fun openDocument(context: Context, uri: Uri, mimeType: String?): Boolean {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType?.takeIf { it.isNotBlank() } ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
