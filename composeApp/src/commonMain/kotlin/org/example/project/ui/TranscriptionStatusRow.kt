// Phase 5.1 — TranscriptionStatusRow: status indicator for transcription states

package org.example.project.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.project.data.TranscriptionUiState

/**
 * Pure composable showing transcription status for a recording.
 *
 * Displays one of four states:
 * - Pending: Small spinner + "Transcription pending..."
 * - InProgress: LinearProgressIndicator + "Transcribing..."
 * - Done: "View Transcription" button + text snippet
 * - Error: Error message (red) + "Retry" button
 *
 * @param transcriptionState The current transcription state
 * @param onRetry Called when user taps "Retry" button (Error state only)
 * @param onViewTranscription Called when user taps "View Transcription" button (Done state only)
 * @param modifier Composable modifier for layout customization
 */
@Composable
fun TranscriptionStatusRow(
    transcriptionState: TranscriptionUiState,
    onRetry: () -> Unit = {},
    onViewTranscription: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    when (transcriptionState) {
        is TranscriptionUiState.Pending -> {
            PendingStatus(modifier)
        }
        is TranscriptionUiState.InProgress -> {
            InProgressStatus(modifier)
        }
        is TranscriptionUiState.Done -> {
            DoneStatus(
                text = transcriptionState.text,
                onViewTranscription = onViewTranscription,
                modifier = modifier
            )
        }
        is TranscriptionUiState.Error -> {
            ErrorStatus(
                message = transcriptionState.message,
                onRetry = onRetry,
                modifier = modifier
            )
        }
    }
}

/**
 * PENDING state: Small spinner + "Transcription pending..."
 */
@Composable
private fun PendingStatus(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .width(20.dp)
                .height(20.dp),
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Transcription pending...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * IN_PROGRESS state: LinearProgressIndicator + "Transcribing..."
 */
@Composable
private fun InProgressStatus(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Transcribing...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * DONE state: "View Transcription" button with text snippet
 */
@Composable
private fun DoneStatus(
    text: String,
    onViewTranscription: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Text snippet (truncated)
        Text(
            text = text.take(50) + if (text.length > 50) "..." else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(
            onClick = onViewTranscription,
            modifier = Modifier.height(40.dp)
        ) {
            Text("View Transcription")
        }
    }
}

/**
 * ERROR state: Error message (red) + "Retry" button
 */
@Composable
private fun ErrorStatus(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "❌ $message",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(
            onClick = onRetry,
            modifier = Modifier.height(40.dp)
        ) {
            Text("Retry")
        }
    }
}
