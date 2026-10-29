package com.juanmediahub.usbotgorganizer.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juanmediahub.usbotgorganizer.R
import com.juanmediahub.usbotgorganizer.domain.model.LocationAvailability
import com.juanmediahub.usbotgorganizer.domain.model.SavedLocationView
import com.juanmediahub.usbotgorganizer.ui.components.AppTopBar
import com.juanmediahub.usbotgorganizer.ui.components.EmptyState
import com.juanmediahub.usbotgorganizer.ui.components.Formatters
import com.juanmediahub.usbotgorganizer.ui.components.InfoBanner
import com.juanmediahub.usbotgorganizer.ui.components.StatusChip
import com.juanmediahub.usbotgorganizer.ui.model.AppUiState

@Composable
fun LocationsScreen(
    state: AppUiState,
    onAddLocation: () -> Unit,
    onChooseAgain: (String) -> Unit,
    onOpenLocation: (String) -> Unit,
    onRenameLocation: (String) -> Unit,
    onForgetLocation: (String) -> Unit,
    onRetryLocation: (String) -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        AppTopBar(
            title = stringResource(R.string.app_name),
            subtitle = stringResource(R.string.saved_locations),
            actions = {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.cd_refresh_locations)
                    )
                }
                IconButton(onClick = onSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.cd_open_settings)
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.locationsLoading && state.locations.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.locations_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (state.locationsProblem != null) {
                item {
                    InfoBanner(
                        text = state.locationsProblem,
                        icon = Icons.Filled.Warning,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            if (state.locations.isEmpty() && !state.locationsLoading) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Storage,
                        title = stringResource(R.string.welcome_title),
                        body = stringResource(R.string.welcome_body),
                        action = {
                            Button(onClick = onAddLocation) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(text = stringResource(R.string.add_location))
                            }
                        }
                    )
                }
                item { PickerScopeNote() }
            } else if (state.locations.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.saved_locations),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = pluralStringResource(
                                R.plurals.saved_location_count,
                                state.locations.size,
                                state.locations.size
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(state.locations, key = { it.location.id }) { view ->
                    LocationCard(
                        view = view,
                        onOpen = { onOpenLocation(view.location.id) },
                        onRename = { onRenameLocation(view.location.id) },
                        onForget = { onForgetLocation(view.location.id) },
                        onRetry = { onRetryLocation(view.location.id) },
                        onChooseAgain = { onChooseAgain(view.location.treeUri) }
                    )
                }
                item {
                    OutlinedButton(
                        onClick = onAddLocation,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(R.string.add_location))
                    }
                }
                item { PickerScopeNote() }
            }
        }
    }
}

@Composable
private fun PickerScopeNote() {
    InfoBanner(
        text = stringResource(R.string.locations_scope_note),
        icon = Icons.Filled.Folder,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LocationCard(
    view: SavedLocationView,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onForget: () -> Unit,
    onRetry: () -> Unit,
    onChooseAgain: () -> Unit
) {
    val context = LocalContext.current
    val location = view.location
    var menuOpen by remember { mutableStateOf(false) }
    val available = view.availability == LocationAvailability.AVAILABLE
    val statusText = when (view.availability) {
        LocationAvailability.AVAILABLE -> stringResource(R.string.status_available)
        LocationAvailability.UNAVAILABLE -> stringResource(R.string.status_unavailable)
        LocationAvailability.PERMISSION_REQUIRED -> stringResource(R.string.status_permission_required)
    }
    val statusColor = when (view.availability) {
        LocationAvailability.AVAILABLE -> MaterialTheme.colorScheme.secondaryContainer
        LocationAvailability.UNAVAILABLE -> MaterialTheme.colorScheme.errorContainer
        LocationAvailability.PERMISSION_REQUIRED -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val statusContent = when (view.availability) {
        LocationAvailability.AVAILABLE -> MaterialTheme.colorScheme.onSecondaryContainer
        LocationAvailability.UNAVAILABLE -> MaterialTheme.colorScheme.onErrorContainer
        LocationAvailability.PERMISSION_REQUIRED -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = available, onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .then(
                            Modifier.padding(0.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = location.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = location.lastOpenedEpochMs?.let {
                            stringResource(
                                R.string.last_opened,
                                Formatters.date(context, it)
                            )
                        } ?: stringResource(R.string.never_opened),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    StatusChip(
                        text = statusText,
                        containerColor = statusColor,
                        contentColor = statusContent
                    )
                    if (view.readOnly) {
                        Spacer(Modifier.height(4.dp))
                        StatusChip(text = stringResource(R.string.status_read_only))
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.cd_location_options)
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.rename_location)) },
                            onClick = {
                                menuOpen = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.choose_location)) },
                            onClick = {
                                menuOpen = false
                                onChooseAgain()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.forget_location)) },
                            onClick = {
                                menuOpen = false
                                onForget()
                            }
                        )
                    }
                }
            }

            if (!available) {
                Spacer(Modifier.height(10.dp))
                InfoBanner(
                    text = if (view.availability == LocationAvailability.PERMISSION_REQUIRED) {
                        stringResource(R.string.recovery_permission_body)
                    } else {
                        stringResource(R.string.recovery_unavailable_body)
                    },
                    icon = Icons.Filled.Warning,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onRetry) {
                        Text(text = stringResource(R.string.retry))
                    }
                    TextButton(onClick = onChooseAgain) {
                        Text(text = stringResource(R.string.choose_location))
                    }
                    TextButton(onClick = onForget) {
                        Text(
                            text = stringResource(R.string.forget_location),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else if (view.readOnly) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.recovery_read_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
