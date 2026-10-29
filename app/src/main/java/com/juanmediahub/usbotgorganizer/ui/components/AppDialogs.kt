package com.juanmediahub.usbotgorganizer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.juanmediahub.usbotgorganizer.R
import com.juanmediahub.usbotgorganizer.domain.model.ConflictPolicy
import com.juanmediahub.usbotgorganizer.domain.model.OperationKind
import com.juanmediahub.usbotgorganizer.domain.model.OperationOutcome
import com.juanmediahub.usbotgorganizer.domain.model.OperationProgress
import com.juanmediahub.usbotgorganizer.domain.model.OperationSummary
import com.juanmediahub.usbotgorganizer.domain.model.SavedLocationView
import com.juanmediahub.usbotgorganizer.domain.model.SortDirection
import com.juanmediahub.usbotgorganizer.domain.model.SortField
import com.juanmediahub.usbotgorganizer.domain.model.SortSpec
import com.juanmediahub.usbotgorganizer.domain.model.StorageItem

@Composable
fun ProgressDialog(
    progress: OperationProgress,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(text = operationTitle(progress.kind)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.foreground_ops_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                val itemFraction = progress.itemFraction
                if (itemFraction != null) {
                    LinearProgressIndicator(
                        progress = { itemFraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(10.dp))
                val current = progress.currentName
                if (!current.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.current_file),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = current,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                }
                val total = progress.totalItems
                Text(
                    text = if (total != null) {
                        stringResource(R.string.items_progress, progress.completedItems, total)
                    } else {
                        stringResource(R.string.items_progress_unknown, progress.completedItems)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                val totalBytes = progress.totalBytes
                Text(
                    text = if (totalBytes != null) {
                        stringResource(
                            R.string.transferred_of,
                            Formatters.size(androidx.compose.ui.platform.LocalContext.current, progress.bytesTransferred),
                            Formatters.size(androidx.compose.ui.platform.LocalContext.current, totalBytes)
                        )
                    } else {
                        stringResource(
                            R.string.transferred,
                            Formatters.size(androidx.compose.ui.platform.LocalContext.current, progress.bytesTransferred)
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(R.string.cancel_operation))
            }
        }
    )
}

@Composable
private fun operationTitle(kind: OperationKind): String = when (kind) {
    OperationKind.COPY -> stringResource(R.string.operation_copy)
    OperationKind.MOVE -> stringResource(R.string.operation_move)
    OperationKind.DELETE -> stringResource(R.string.operation_delete)
    OperationKind.ORGANIZE -> stringResource(R.string.operation_organize)
    OperationKind.CREATE_FOLDER -> stringResource(R.string.operation_create_folder)
    OperationKind.RENAME -> stringResource(R.string.operation_rename)
}

@Composable
fun ConflictDialog(
    name: String,
    onKeepBoth: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(text = stringResource(R.string.conflict_title)) },
        text = {
            Column {
                Text(text = stringResource(R.string.conflict_body, name))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.conflict_keep_both_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onSkip) {
                    Text(text = stringResource(R.string.skip))
                }
                Button(onClick = onKeepBoth) {
                    Text(text = stringResource(R.string.keep_both))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(R.string.cancel_whole_operation))
            }
        }
    )
}

@Composable
fun SummaryDialog(
    summary: OperationSummary,
    detail: String?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.summary_title)) },
        text = {
            Column {
                Text(
                    text = when (summary.outcome) {
                        OperationOutcome.SUCCESS -> stringResource(R.string.summary_success)
                        OperationOutcome.PARTIAL -> stringResource(R.string.summary_partial)
                        OperationOutcome.FAILURE -> stringResource(R.string.summary_failed)
                        OperationOutcome.CANCELLED -> stringResource(R.string.summary_cancelled)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                if (summary.completedItems > 0) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.summary_completed,
                            summary.completedItems,
                            summary.completedItems
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (summary.skippedItems > 0) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.summary_skipped,
                            summary.skippedItems,
                            summary.skippedItems
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (summary.failedItems > 0) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.summary_failed_items,
                            summary.failedItems,
                            summary.failedItems
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (summary.sourceDeleteFailures > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.summary_source_delete_failed),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (summary.incompleteCleanupFailures > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.summary_cleanup_failed),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (summary.usedCopyThenDelete) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.summary_not_atomic),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (detail != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.done))
            }
        }
    )
}

@Composable
fun NewFolderDialog(
    name: String,
    error: String?,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    NameDialog(
        title = stringResource(R.string.create_folder),
        label = stringResource(R.string.folder_name),
        name = name,
        error = error,
        confirmLabel = stringResource(R.string.create),
        onNameChange = onNameChange,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
fun RenameItemDialog(
    name: String,
    error: String?,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    NameDialog(
        title = stringResource(R.string.rename),
        label = stringResource(R.string.new_name),
        name = name,
        error = error,
        confirmLabel = stringResource(R.string.save),
        onNameChange = onNameChange,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
fun RenameLocationDialog(
    name: String,
    error: String?,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    NameDialog(
        title = stringResource(R.string.rename_location),
        label = stringResource(R.string.display_name_label),
        name = name,
        error = error,
        confirmLabel = stringResource(R.string.save),
        onNameChange = onNameChange,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
private fun NameDialog(
    title: String,
    label: String,
    name: String,
    error: String?,
    confirmLabel: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = { Text(text = label) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = if (error != null) {
                        { Text(text = error) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm() })
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = name.isNotBlank()) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun DeleteDialog(
    items: List<StorageItem>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.delete_title)) },
        text = {
            Text(
                text = stringResource(
                    R.string.delete_count_body,
                    stringResource(R.string.delete_body),
                    items.size
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.delete_confirm),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun ForgetLocationDialog(
    location: SavedLocationView,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.forget_location_title)) },
        text = {
            Column {
                Text(text = location.location.displayName, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(text = stringResource(R.string.forget_location_body))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.forget_location),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun SortDialog(
    spec: SortSpec,
    onApply: (SortSpec) -> Unit,
    onDismiss: () -> Unit
) {
    var field by remember(spec) { mutableStateOf(spec.field) }
    var direction by remember(spec) { mutableStateOf(spec.direction) }
    var foldersFirst by remember(spec) { mutableStateOf(spec.foldersFirst) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.sort_by)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                SortChoice(
                    label = stringResource(R.string.sort_name),
                    selected = field == SortField.NAME,
                    onClick = { field = SortField.NAME }
                )
                SortChoice(
                    label = stringResource(R.string.sort_size),
                    selected = field == SortField.SIZE,
                    onClick = { field = SortField.SIZE }
                )
                SortChoice(
                    label = stringResource(R.string.sort_modified),
                    selected = field == SortField.MODIFIED,
                    onClick = { field = SortField.MODIFIED }
                )
                SortChoice(
                    label = stringResource(R.string.sort_type),
                    selected = field == SortField.TYPE,
                    onClick = { field = SortField.TYPE }
                )
                Spacer(Modifier.height(8.dp))
                SortChoice(
                    label = stringResource(R.string.sort_ascending),
                    selected = direction == SortDirection.ASC,
                    onClick = { direction = SortDirection.ASC }
                )
                SortChoice(
                    label = stringResource(R.string.sort_descending),
                    selected = direction == SortDirection.DESC,
                    onClick = { direction = SortDirection.DESC }
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { foldersFirst = !foldersFirst }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(checked = foldersFirst, onCheckedChange = { foldersFirst = it })
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.sort_folders_first),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.sort_folders_first_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(SortSpec(field, direction, foldersFirst)) }) {
                Text(text = stringResource(R.string.apply_sort))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun SortChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
