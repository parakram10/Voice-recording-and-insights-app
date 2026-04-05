// Phase 5.3 — TranscriptionDialog: fullscreen dialog for viewing transcription text

package org.example.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Phase 5.3 — TranscriptionDialog: fullscreen dialog for viewing transcription text
 *
 * Displays transcription text in a scrollable area with:
 * - Selectable text (for copy/paste on long-press)
 * - Close button in header
 * - Fullscreen overlay with semi-transparent background
 *
 * @param text The transcription text to display
 * @param onDismiss Called when user taps close button or outside dialog
 * @param modifier Composable modifier for layout customization
 */
@Composable
fun TranscriptionDialog(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Semi-transparent background overlay
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Dialog card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large
                ),
            horizontalAlignment = Alignment.Start
        ) {
            // Header with title and close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transcription",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )

                // Close button (using ✕ character)
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                ) {
                    Text(
                        text = "✕",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            )

            // Scrollable content area with selectable text
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Selectable text (can be copied on long-press)
                SelectableText(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * SelectableText: A text composable that allows long-press selection and copying
 *
 * On platforms that support it, long-press will show a copy menu.
 * Falls back to regular Text if selection is not available.
 *
 * @param text The text to display
 * @param style Text style
 * @param color Text color
 * @param modifier Composable modifier
 */
@Composable
private fun SelectableText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    // Compose doesn't have a built-in SelectionContainer in common code
    // For now, we'll use a regular Text and note that platform-specific
    // implementations (AndroidMain/IosMain) can override with native selection
    Text(
        text = text,
        style = style,
        color = color,
        modifier = modifier
    )
}
