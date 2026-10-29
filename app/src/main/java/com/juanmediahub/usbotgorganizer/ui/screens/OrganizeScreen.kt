package com.juanmediahub.usbotgorganizer.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juanmediahub.usbotgorganizer.R
import com.juanmediahub.usbotgorganizer.domain.model.ConflictPolicy
import com.juanmediahub.usbotgorganizer.domain.model.FileCategory
import com.juanmediahub.usbotgorganizer.domain.model.OrganizePlanItem
import com.juanmediahub.usbotgorganizer.domain.model.TransferMode
import com.juanmediahub.usbotgorganizer.ui.AppViewModel
import com.juanmediahub.usbotgorganizer.ui.components.AppTopBar
import com.juanmediahub.usbotgorganizer.ui.components.EmptyState
import com.juanmediahub.usbotgorganizer.ui.components.InfoBanner
import com.juanmediahub.usbotgorganizer.ui.components.StatusChip
import com.juanmediahub.usbotgorganizer.ui.components.iconForItem
import com.juanmediahub.usbotgorganizer.ui.model.AppUiState

@Composable
fun OrganizeScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val organize = state.organize
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        AppTopBar(
            title = stringResource(R.string.organize_title),
            subtitle = organize?.parent?.name,
            onBack = viewModel::closeOrganize
        )

        if (organize == null) {
            EmptyState(
                icon = Icons.Filled.Category,
                title = stringResource(R.string.organize_title),
                body = stringResource(R.string.organize_no_files)
            )
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            InfoBanner(
                modifier = Modifier.padding(top = 8.dp),
                text = stringResource(R.string.organize_body),
                icon = Icons.Filled.Category
            )

            Spacer(Modifier.height(12.dp))
            OptionLabel(stringResource(R.string.organize_action))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = organize.mode == TransferMode.COPY,
                    onClick = { viewModel.setOrganizeMode(TransferMode.COPY) },
                    label = { Text(text = stringResource(R.string.organize_mode_copy)) }
                )
                FilterChip(
                    selected = organize.mode == TransferMode.MOVE,
                    onClick = { viewModel.setOrganizeMode(TransferMode.MOVE) },
                    label = { Text(text = stringResource(R.string.organize_mode_move)) }
                )
            }

            Spacer(Modifier.height(10.dp))
            OptionLabel(stringResource(R.string.organize_conflict_policy))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = organize.conflictPolicy == ConflictPolicy.KEEP_BOTH,
                    onClick = { viewModel.setOrganizeConflict(ConflictPolicy.KEEP_BOTH) },
                    label = { Text(text = stringResource(R.string.organize_keep_both)) }
                )
                FilterChip(
                    selected = organize.conflictPolicy == ConflictPolicy.SKIP,
                    onClick = { viewModel.setOrganizeConflict(ConflictPolicy.SKIP) },
                    label = { Text(text = stringResource(R.string.organize_skip)) }
                )
                FilterChip(
                    selected = organize.conflictPolicy == ConflictPolicy.CANCEL,
                    onClick = { viewModel.setOrganizeConflict(ConflictPolicy.CANCEL) },
                    label = { Text(text = stringResource(R.string.organize_cancel_conflict)) }
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.organize_root, organize.parent.name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    R.string.organize_selected_count,
                    organize.selectedPlans.size,
                    organize.activePlans.size
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(organize.plans, key = { it.source.key }) { plan ->
                    OrganizePlanRow(
                        plan = plan,
                        onToggle = { viewModel.toggleOrganizePlan(plan.source.key) }
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.organize_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = viewModel::applyOrganize,
                        enabled = organize.selectedPlans.isNotEmpty() && !state.busy
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(text = stringResource(R.string.organize_apply))
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun OrganizePlanRow(
    plan: OrganizePlanItem,
    onToggle: () -> Unit
) {
    val enabled = !plan.alreadyInPlace
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (plan.selected && enabled) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = plan.selected && enabled,
                onCheckedChange = { if (enabled) onToggle() },
                enabled = enabled
            )
            Icon(
                imageVector = iconForItem(plan.source),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plan.source.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = categoryLabel(plan.category),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = plan.destinationFolderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (plan.alreadyInPlace) {
                StatusChip(text = stringResource(R.string.organize_already_in_place))
            }
        }
    }
}

@Composable
private fun categoryLabel(category: FileCategory): String = when (category) {
    FileCategory.IMAGES -> stringResource(R.string.category_images)
    FileCategory.VIDEOS -> stringResource(R.string.category_videos)
    FileCategory.AUDIO -> stringResource(R.string.category_audio)
    FileCategory.DOCUMENTS -> stringResource(R.string.category_documents)
    FileCategory.ARCHIVES -> stringResource(R.string.category_archives)
    FileCategory.APKS -> stringResource(R.string.category_apks)
    FileCategory.OTHER -> stringResource(R.string.category_other)
}
