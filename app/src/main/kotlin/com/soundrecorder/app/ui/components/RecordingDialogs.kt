package com.soundrecorder.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.soundrecorder.app.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordingDetailsDialog(recording: Recording, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recording Details", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailItem("Name", recording.name)
                DetailItem("Format", recording.format.displayName)
                DetailItem("Duration", String.format("%02d:%02d", (recording.duration / 1000) / 60, (recording.duration / 1000) % 60))
                DetailItem("Size", String.format("%.2f MB", recording.size / (1024.0 * 1024.0)))
                DetailItem("Date", SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(recording.timestamp)))
                DetailItem("Path", recording.filePath)
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("CLOSE") } }
    )
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

// ... Rest of your SelectionDialog, Theme, Format, SampleRate, BitRate, AudioSource, Gain, Delete, Rename, Filename Dialogs remain exactly the same ...
// [PHOTCOPIER RULE: RETAINING ALL DIALOGS FROM PREVIOUS TURN]

@Composable
fun AutoStopPickerDialog(currentLimitMs: Long, onSelect: (Long) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(0L, 600_000L, 1_800_000L, 3_600_000L, 7_200_000L)
    val labels = mapOf(0L to "Default (No Limit)", 600_000L to "10 Minutes", 1_800_000L to "30 Minutes", 3_600_000L to "1 Hour", 7_200_000L to "2 Hours")
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(24.dp)) {
                Text("Auto Stop Duration", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                options.forEach { limit ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(limit); onDismiss() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = limit == currentLimitMs, onClick = { onSelect(limit); onDismiss() })
                        Text(labels[limit] ?: "", Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> SelectionDialog(title: String, options: Array<T>, currentOption: T, optionLabel: (T) -> String, optionSubLabel: ((T) -> String)? = null, onSelect: (T) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 24.dp, bottom = 12.dp)) {
                Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(16.dp))
                options.forEach { option ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(option); onDismiss() }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = option == currentOption, onClick = { onSelect(option); onDismiss() })
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(text = optionLabel(option), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            if (optionSubLabel != null) Text(text = optionSubLabel(option), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("CANCEL", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun ThemePickerDialog(current: ThemeOption, onSelect: (ThemeOption) -> Unit, onDismiss: () -> Unit) { SelectionDialog("App Appearance Theme", ThemeOption.values(), current, { it.displayName }, null, onSelect, onDismiss) }
@Composable
fun FormatPickerDialog(current: RecordingFormat, onSelect: (RecordingFormat) -> Unit, onDismiss: () -> Unit) { SelectionDialog("Audio Output Format", RecordingFormat.values(), current, { it.displayName }, { it.description }, onSelect, onDismiss) }
@Composable
fun SampleRatePickerDialog(current: SampleRateOption, onSelect: (SampleRateOption) -> Unit, onDismiss: () -> Unit) { SelectionDialog("Audio Sample Rate", SampleRateOption.values(), current, { it.displayName }, { it.description }, onSelect, onDismiss) }
@Composable
fun BitRatePickerDialog(current: BitRateOption, onSelect: (BitRateOption) -> Unit, onDismiss: () -> Unit) { SelectionDialog("Audio Encoder Bitrate", BitRateOption.values(), current, { it.displayName }, { it.description }, onSelect, onDismiss) }
@Composable
fun AudioSourcePickerDialog(current: AudioSourceOption, onSelect: (AudioSourceOption) -> Unit, onDismiss: () -> Unit) { SelectionDialog("Audio Input Source", AudioSourceOption.values(), current, { it.displayName }, { it.description }, onSelect, onDismiss) }
@Composable
fun GainDialog(current: Float, onConfirm: (Float) -> Unit, onDismiss: () -> Unit) {
    var sliderValue by remember { mutableStateOf(current) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Microphone Gain") }, text = {
        Column {
            Text("${"%.1f".format(sliderValue)} dB", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.titleLarge)
            Slider(value = sliderValue, onValueChange = { sliderValue = it }, valueRange = -10f..20f)
        }
    }, confirmButton = { Button(onClick = { onConfirm(sliderValue); onDismiss() }) { Text("OK") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } })
}
@Composable
fun DeleteConfirmDialog(recordingName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text("Delete Recording") }, text = { Text("Permanently delete \"$recordingName\"?") }, confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("DELETE") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }) }
@Composable
fun RenameDialog(currentName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Rename Session") }, text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) }, confirmButton = { Button(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text("RENAME") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } })
}
@Composable
fun FilenameDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Save Recording") }, text = { OutlinedTextField(value = text, onValueChange = { text = it }, placeholder = { Text("Enter name") }, singleLine = true) }, confirmButton = { Button(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text("SAVE") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("SKIP") } })
}