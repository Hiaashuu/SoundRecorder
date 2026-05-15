package com.soundrecorder.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.soundrecorder.app.data.*
import com.soundrecorder.app.ui.components.*
import com.soundrecorder.app.viewmodel.RecorderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: RecorderViewModel) {
    val s by viewModel.settings.collectAsState()
    var showFormat by remember { mutableStateOf(false) }
    var showSource by remember { mutableStateOf(false) }
    var showSample by remember { mutableStateOf(false) }
    var showBitRate by remember { mutableStateOf(false) }
    var showGain by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var showAutoStop by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SectionHeader("Interface") }
            item { GroupedCard {
                SettingsClickRow("Theme", s.themeOption.displayName) { showTheme = true }
                SettingsSwitchRow("Auto Recording", "Start recording on app launch", s.autoStartAppLaunch) { viewModel.setAutoStart(it) }
            } }
            item { SectionHeader("Recording Quality") }
            item { GroupedCard {
                SettingsClickRow("Format", s.recordingFormat.displayName) { showFormat = true }
                SettingsClickRow("Sample Rate", s.sampleRate.displayName) { showSample = true }
                SettingsClickRow("Audio Source", s.audioSource.displayName) { showSource = true }
                SettingsClickRow("Encoder Bitrate", s.bitRate.displayName) { showBitRate = true }
                SettingsSwitchRow("Stereo Recording", "Capture 2 channels if supported", s.recordStereo) { viewModel.setRecordStereo(it) }
                SettingsClickRow("Default Gain", "${s.defaultGainDb} dB") { showGain = true }
            } }
            item { SectionHeader("Audio Processing") }
            item { GroupedCard {
                SettingsSwitchRow("AGC", "Automatic Gain Control", s.automaticGainControl) { viewModel.setAgc(it) }
                SettingsSwitchRow("Noise Suppression", "High quality noise reduction", s.noiseSuppression) { viewModel.setNs(it) }
                SettingsSwitchRow("Echo Cancellation", "Hardware echo cancellation", s.echoCancellation) { viewModel.setAec(it) }
            } }
            item { SectionHeader("Limits & Automation") }
            item { GroupedCard {
                val durationText = when(s.autoStopLimitMs) {
                    0L -> "Disabled (Until out of memory)"
                    600_000L -> "10 Minutes"
                    1_800_000L -> "30 Minutes"
                    3_600_000L -> "1 Hour"
                    7_200_000L -> "2 Hours"
                    else -> "Custom"
                }
                SettingsClickRow("Auto Stop Duration", durationText) { showAutoStop = true }
                SettingsSwitchRow("Stop on Low Memory", "Auto stop when critical", s.stopOutOfMemory) { viewModel.setStopOutOfMemory(it) }
            } }
            item { SectionHeader("System Behavior") }
            item { GroupedCard {
                SettingsSwitchRow("Pause for Call", "Handle incoming call state", s.stopOnCall) { viewModel.setStopOnCall(it) }
                SettingsSwitchRow("Keep Screen On", "During active recording", s.screenOn) { viewModel.setScreenOn(it) }
                SettingsSwitchRow("Notification Controls", "Manage session in notifications", s.notificationControls) { viewModel.setNotificationControls(it) }
                SettingsSwitchRow("Ask for Name", "Prompt for name when stopped", s.askForFilename) { viewModel.setAskForFilename(it) }
            } }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showTheme) ThemePickerDialog(s.themeOption, { viewModel.setTheme(it) }, { showTheme = false })
    if (showFormat) FormatPickerDialog(s.recordingFormat, { viewModel.setRecordingFormat(it) }, { showFormat = false })
    if (showSource) AudioSourcePickerDialog(s.audioSource, { viewModel.setAudioSource(it) }, { showSource = false })
    if (showSample) SampleRatePickerDialog(s.sampleRate, { viewModel.setSampleRate(it) }, { showSample = false })
    if (showBitRate) BitRatePickerDialog(s.bitRate, { viewModel.setBitRate(it) }, { showBitRate = false })
    if (showGain) GainDialog(s.defaultGainDb, { viewModel.setGain(it) }, { showGain = false })
    if (showAutoStop) AutoStopPickerDialog(s.autoStopLimitMs, { viewModel.setAutoStopLimit(it) }, { showAutoStop = false })
}

@Composable
private fun GroupedCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))) { Column(content = content) }
}

@Composable
private fun SectionHeader(title: String) {
    Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 16.dp, 8.dp, 4.dp))
}

@Composable
private fun SettingsClickRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}