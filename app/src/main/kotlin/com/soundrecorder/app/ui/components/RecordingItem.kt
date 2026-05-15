package com.soundrecorder.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.soundrecorder.app.data.Recording
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingItem(
    recording: Recording,
    isPlaying: Boolean,
    isPlaybackPaused: Boolean,
    playbackProgress: Float,
    isSelected: Boolean,
    inSelectionMode: Boolean,
    onTogglePlayback: () -> Unit,
    onSeek: (Float) -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onContinueClick: () -> Unit,
    onShare: () -> Unit,
    onDetails: () -> Unit,
    onSelect: () -> Unit,
    onLongClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var localDragProgress by remember { mutableStateOf(0f) }

    val cardShape = RoundedCornerShape(20.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            // CRITICAL FIX: Clip before combinedClickable to ensure ripples and highlights stay inside the rounded border
            .clip(cardShape)
            .combinedClickable(
                onClick = { if (inSelectionMode) onSelect() else onTogglePlayback() },
                onLongClick = { onLongClick() }
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
            else if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(0.3f) 
            else MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (inSelectionMode) {
                    Checkbox(
                        checked = isSelected, 
                        onCheckedChange = { onSelect() },
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Default.AudioFile, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                }
                
                Column(Modifier.weight(1f)) {
                    Text(
                        text = recording.name, 
                        style = MaterialTheme.typography.bodyLarge, 
                        fontWeight = FontWeight.Bold, 
                        maxLines = 1, 
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildMeta(recording), 
                        style = MaterialTheme.typography.bodySmall, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (!inSelectionMode) {
                    IconButton(onClick = onTogglePlayback) {
                        Icon(if (isPlaying && !isPlaybackPaused) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { showMenu = false; showRenameDialog = true })
                            DropdownMenuItem(text = { Text("Share file") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { showMenu = false; onShare() })
                            DropdownMenuItem(text = { Text("Details") }, leadingIcon = { Icon(Icons.Default.Info, null) }, onClick = { showMenu = false; onDetails() })
                            DropdownMenuItem(text = { Text("Continue recording") }, leadingIcon = { Icon(Icons.Default.Mic, null) }, onClick = { showMenu = false; onContinueClick() })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Delete session", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDelete() })
                        }
                    }
                }
            }
            if (isPlaying && !inSelectionMode) {
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = if (isDragging) localDragProgress else playbackProgress,
                    onValueChange = { isDragging = true; localDragProgress = it },
                    onValueChangeFinished = { onSeek(localDragProgress); isDragging = false },
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                    )
                )
            }
        }
    }
    if (showRenameDialog) RenameDialog(recording.name, { onRename(it); showRenameDialog = false }, { showRenameDialog = false })
}

private fun buildMeta(r: Recording): String {
    val date = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(r.timestamp))
    val duration = String.format("%02d:%02d", (r.duration / 1000) / 60, (r.duration / 1000) % 60)
    val size = String.format("%.1f MB", r.size / (1024.0 * 1024.0))
    return "$date · $duration · $size"
}