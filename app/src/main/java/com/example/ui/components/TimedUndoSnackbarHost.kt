package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.example.ui.UndoEvent
import com.example.ui.UndoSnapshot
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun BindFiveSecondUndoSnackbar(
    undoEvent: UndoEvent?,
    snackbarHostState: SnackbarHostState,
    onUndo: (UndoSnapshot) -> Unit,
    onConsumeEvent: () -> Unit
) {
    LaunchedEffect(undoEvent?.eventId) {
        val current = undoEvent ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        val result = withTimeoutOrNull(5_000L) {
            snackbarHostState.showSnackbar(
                message = current.message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Indefinite
            )
        }
        if (result == SnackbarResult.ActionPerformed) {
            onUndo(current.snapshot)
        } else {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
        onConsumeEvent()
    }
}

@Composable
fun CircadiaSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
    ) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            actionColor = MaterialTheme.colorScheme.primary
        )
    }
}
