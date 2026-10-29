package com.juanmediahub.usbotgorganizer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juanmediahub.usbotgorganizer.R
import com.juanmediahub.usbotgorganizer.domain.model.StorageItem
import com.juanmediahub.usbotgorganizer.ui.AppViewModel
import com.juanmediahub.usbotgorganizer.ui.components.AppTopBar
import com.juanmediahub.usbotgorganizer.ui.components.EmptyState
import com.juanmediahub.usbotgorganizer.ui.components.Formatters
import com.juanmediahub.usbotgorganizer.ui.components.InfoBanner
import com.juanmediahub.usbotgorganizer.ui.components.iconForItem
import com.juanmediahub.usbotgorganizer.ui.components.itemTypeLabel
import com.juanmediahub.usbotgorganizer.ui.model.AppUiState

@Composable
fun DetailsScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val item = state.detailsItem
    val browser = state.browser
    val readOnly = browser?.readOnly == true
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        AppTopBar(
            title = stringResource(R.string.details_title),
            onBack = viewModel::closeDetails,
            actions = {
                if (item != null && !readOnly) {
                    TextButton(onClick = { viewModel.beginRenameItem(item) }) {
                        Text(text = stringResource(R.string.rename))
                    }
                }
            }
        )

        if (item == null) {
            EmptyState(
                icon = Icons.Filled.Warning,
                title = stringResource(R.string.error_not_found),
                body = stringResource(R.string.error_not_found)
            )
            return@Column
        }

        val context = LocalContext.current
        val sizeText = if (item.isDirectory) {
            stringResource(R.string.folder_size_not_calculated)
        } else {
            Formatters.size(context, item.sizeBytes)
        }
        val path = browser?.stack?.joinToString(" / ") { it.name }.orEmpty()
        val locationName = browser?.location?.location?.displayName.orEmpty()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = iconForItem(item),
                                contentDescription = null,
                                modifier = Modifier.size(38.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = itemTypeLabel(item),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (readOnly) {
                item {
                    InfoBanner(
                        text = stringResource(R.string.capability_read_only),
                        icon = Icons.Filled.Warning,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        DetailRow(stringResource(R.string.name), item.name)
                        HorizontalDivider()
                        DetailRow(stringResource(R.string.type), itemTypeLabel(item))
                        HorizontalDivider()
                        DetailRow(stringResource(R.string.size), sizeText)
                        HorizontalDivider()
                        DetailRow(
                            stringResource(R.string.modified),
                            Formatters.date(context, item.modifiedEpochMs)
                        )
                        HorizontalDivider()
                        DetailRow(stringResource(R.string.location), locationName)
                        HorizontalDivider()
                        DetailRow(stringResource(R.string.breadcrumb), path)
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.capabilities),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CapabilityChip(stringResource(R.string.can_read), item.capabilities.canRead)
                            CapabilityChip(stringResource(R.string.can_write), item.capabilities.canWrite)
                            CapabilityChip(stringResource(R.string.can_rename), item.capabilities.canRename)
                            CapabilityChip(stringResource(R.string.can_delete), item.capabilities.canDelete)
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!item.isDirectory) {
                        Button(onClick = { viewModel.openItem(item) }) {
                            Icon(
                                imageVector = Icons.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(text = stringResource(R.string.open))
                        }
                    }
                    OutlinedButton(onClick = { viewModel.copyItem(item) }) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(text = stringResource(R.string.copy))
                    }
                    OutlinedButton(
                        onClick = { viewModel.moveItem(item) },
                        enabled = !readOnly
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DriveFileMove,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(text = stringResource(R.string.move))
                    }
                    OutlinedButton(
                        onClick = { viewModel.beginRenameItem(item) },
                        enabled = !readOnly
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(text = stringResource(R.string.rename))
                    }
                    OutlinedButton(
                        onClick = { viewModel.beginDelete(listOf(item)) },
                        enabled = !readOnly
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(92.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CapabilityChip(label: String, enabled: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (enabled) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (enabled) Icons.Filled.Check else Icons.Filled.Block,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
