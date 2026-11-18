package com.example.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.data.local.SubstanceLogEntity
import com.example.domain.DateTimeUtils

@Composable
fun ConfirmDeleteDialog(
    entry: SubstanceLogEntity?,
    onDismiss: () -> Unit,
    onConfirmDelete: (SubstanceLogEntity) -> Unit
) {
    if (entry == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete Entry?",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = "Remove ${entry.substanceName} (${formatCompactAmount(entry.dosageAmount)} ${entry.dosageUnit} at ${DateTimeUtils.formatTime(entry.takenAtMillis)}) from ${DateTimeUtils.formatDisplayDate(entry.dateIso)}? Your Sleep Disruption Score will update immediately.",
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmDelete(entry) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.testTag("confirm_delete_entry_button")
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_delete_entry_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
