package com.juanmediahub.usbotgorganizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juanmediahub.usbotgorganizer.data.storage.OpenDocumentTreeContract
import com.juanmediahub.usbotgorganizer.ui.AppViewModel
import com.juanmediahub.usbotgorganizer.ui.OtgExplorerRoot
import com.juanmediahub.usbotgorganizer.ui.theme.OtgExplorerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as OtgExplorerApp).container
        setContent {
            val appViewModel: AppViewModel = viewModel(factory = AppViewModel.factory(container))
            val state by appViewModel.uiState.collectAsStateWithLifecycle()
            val launcher = rememberLauncherForActivityResult(OpenDocumentTreeContract()) { result ->
                if (result != null) {
                    appViewModel.onLocationPicked(result)
                } else {
                    appViewModel.onPickerCancelled()
                }
            }
            OtgExplorerTheme(
                themeMode = state.settings.themeMode,
                dynamicColor = state.settings.dynamicColor
            ) {
                OtgExplorerRoot(
                    state = state,
                    viewModel = appViewModel,
                    onPickLocation = { initial -> launcher.launch(initial) }
                )
            }
        }
    }
}
