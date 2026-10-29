package com.juanmediahub.usbotgorganizer.ui.screens

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juanmediahub.usbotgorganizer.BuildConfig
import com.juanmediahub.usbotgorganizer.R
import com.juanmediahub.usbotgorganizer.domain.model.SortDirection
import com.juanmediahub.usbotgorganizer.domain.model.SortField
import com.juanmediahub.usbotgorganizer.domain.model.SortSpec
import com.juanmediahub.usbotgorganizer.domain.model.ThemeMode
import com.juanmediahub.usbotgorganizer.domain.model.ViewMode
import com.juanmediahub.usbotgorganizer.ui.AppViewModel
import com.juanmediahub.usbotgorganizer.ui.components.AppTopBar
import com.juanmediahub.usbotgorganizer.ui.components.InfoBanner
import com.juanmediahub.usbotgorganizer.ui.model.AppUiState

@Composable
fun SettingsScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val settings = state.settings
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        AppTopBar(
            title = stringResource(R.string.settings),
            onBack = { viewModel.onBack() }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsCard(title = stringResource(R.string.theme)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChip(
                        label = stringResource(R.string.theme_system),
                        selected = settings.themeMode == ThemeMode.SYSTEM,
                        onClick = { viewModel.setTheme(ThemeMode.SYSTEM) }
                    )
                    ThemeChip(
                        label = stringResource(R.string.theme_light),
                        selected = settings.themeMode == ThemeMode.LIGHT,
                        onClick = { viewModel.setTheme(ThemeMode.LIGHT) }
                    )
                    ThemeChip(
                        label = stringResource(R.string.theme_dark),
                        selected = settings.themeMode == ThemeMode.DARK,
                        onClick = { viewModel.setTheme(ThemeMode.DARK) }
                    )
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            viewModel.setDynamicColor(!settings.dynamicColor)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.dynamic_color),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.dynamic_color_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                        onCheckedChange = { viewModel.setDynamicColor(it) }
                    )
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.dynamic_color_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SettingsCard(title = stringResource(R.string.default_view)) {
                Text(
                    text = stringResource(R.string.default_view_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChip(
                        label = stringResource(R.string.view_list),
                        selected = settings.viewMode == ViewMode.LIST,
                        onClick = { viewModel.setDefaultViewMode(ViewMode.LIST) }
                    )
                    ThemeChip(
                        label = stringResource(R.string.view_grid),
                        selected = settings.viewMode == ViewMode.GRID,
                        onClick = { viewModel.setDefaultViewMode(ViewMode.GRID) }
                    )
                }
            }

            SettingsCard(title = stringResource(R.string.default_sort)) {
                Text(
                    text = stringResource(R.string.default_sort_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = sortSummary(settings.sortSpec),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { viewModel.beginSort(forBrowser = false) }) {
                    Icon(
                        imageVector = Icons.Filled.Sort,
                        contentDescription = null,
                        modifier = Modifier.width(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(text = stringResource(R.string.sort_by))
                }
            }

            SettingsCard(title = stringResource(R.string.show_hidden)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setShowHidden(!settings.showHidden) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.show_hidden),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.show_hidden_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.showHidden,
                        onCheckedChange = { viewModel.setShowHidden(it) }
                    )
                }
            }

            SettingsCard(title = stringResource(R.string.compatibility)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Filled.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.compatibility_body),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            SettingsCard(title = stringResource(R.string.foreground_limits)) {
                Text(
                    text = stringResource(R.string.foreground_limits_body),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            SettingsCard(title = stringResource(R.string.about)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.about_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            InfoBanner(
                text = stringResource(R.string.settings_offline_note),
                icon = Icons.Filled.Info
            )
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ThemeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label) }
    )
}

@Composable
private fun sortSummary(spec: SortSpec): String {
    val field = when (spec.field) {
        SortField.NAME -> stringResource(R.string.sort_name)
        SortField.SIZE -> stringResource(R.string.sort_size)
        SortField.MODIFIED -> stringResource(R.string.sort_modified)
        SortField.TYPE -> stringResource(R.string.sort_type)
    }
    val direction = when (spec.direction) {
        SortDirection.ASC -> stringResource(R.string.sort_ascending)
        SortDirection.DESC -> stringResource(R.string.sort_descending)
    }
    val folders = if (spec.foldersFirst) {
        " · ${stringResource(R.string.sort_folders_first)}"
    } else {
        ""
    }
    return "$field · $direction$folders"
}
