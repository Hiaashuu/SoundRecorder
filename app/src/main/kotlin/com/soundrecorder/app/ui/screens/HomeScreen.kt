package com.soundrecorder.app.ui.screens

import android.Manifest
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.soundrecorder.app.data.Recording
import com.soundrecorder.app.data.ThemeOption
import com.soundrecorder.app.ui.components.*
import com.soundrecorder.app.viewmodel.RecorderViewModel
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(navController: NavController, viewModel: RecorderViewModel) {
    val context = LocalContext.current
    val isRecording by viewModel.isRecording.collectAsState()
    val recordings by viewModel.recordings.collectAsState()
    val playingId by viewModel.playingRecordingId.collectAsState()
    val isPlaybackPaused by viewModel.isPlaybackPaused.collectAsState()
    val playbackProgress by viewModel.playbackProgress.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val showFilenameDialog by viewModel.showFilenameDialog.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()

    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val snackbarHostState = remember { SnackbarHostState() }
    
    var recordingToDelete by remember { mutableStateOf<Recording?>(null) }
    var recordingDetails by remember { mutableStateOf<Recording?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    val inSelectionMode = selectedIds.isNotEmpty()

    BackHandler(enabled = inSelectionMode) {
        viewModel.clearSelection()
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToLibrary.collect { selectedTab = 1 }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearErrorMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (inSelectionMode) {
                        Text("${selectedIds.size} Selected", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text(if (isRecording || selectedTab == 0) "Recorder" else "Library", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    if (inSelectionMode) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                },
                actions = {
                    if (inSelectionMode) {
                        IconButton(onClick = { viewModel.selectAll() }) { Icon(Icons.Default.SelectAll, "Select All") }
                        IconButton(onClick = { showBulkDeleteConfirm = true }) { Icon(Icons.Default.Delete, "Delete Selected", tint = MaterialTheme.colorScheme.error) }
                    } else {
                        IconButton(onClick = {
                            val next = when (settings.themeOption) {
                                ThemeOption.DARK -> ThemeOption.LIGHT
                                ThemeOption.LIGHT -> ThemeOption.SYSTEM
                                ThemeOption.SYSTEM -> ThemeOption.DARK
                            }
                            viewModel.setTheme(next)
                        }) { Icon(if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode, null) }
                        IconButton(onClick = { navController.navigate("settings") }) { Icon(Icons.Default.Settings, null) }
                    }
                }
            )
        },
        bottomBar = {
            if (!isRecording && !inSelectionMode) {
                NavigationBar {
                    NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.Default.Mic, null) }, label = { Text("Record") })
                    NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.AutoMirrored.Filled.List, null) }, label = { Text("Library") })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            AnimatedContent(
                targetState = if (isRecording) 2 else selectedTab,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "Navigation"
            ) { target ->
                when (target) {
                    2 -> RecordingOverlay(viewModel = viewModel, onCancelClick = { showCancelConfirm = true })
                    0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (!micPermission.status.isGranted) PermissionRequestView { micPermission.launchPermissionRequest() }
                        else IdleControls { viewModel.startRecording() }
                    }
                    1 -> RecordingsListSection(
                        recordings = recordings, playingId = playingId,
                        isPlaybackPaused = isPlaybackPaused, playbackProgress = playbackProgress,
                        selectedIds = selectedIds,
                        onTogglePlayback = { viewModel.togglePlayback(it) },
                        onSeek = { viewModel.seekPlayback(it) },
                        onDeleteClick = { recordingToDelete = it },
                        onRenameClick = { r, name -> viewModel.renameRecording(r, name) },
                        onContinueClick = { viewModel.startRecording(File(it.filePath)) },
                        onShareClick = { r ->
                            val file = File(r.filePath)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Recording"))
                        },
                        onDetailsClick = { recordingDetails = it },
                        onSelect = { viewModel.toggleSelection(it.id) },
                        onLongClick = { viewModel.toggleSelection(it.id) }
                    )
                }
            }
        }
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Discard session?") },
            text = { Text("Are you sure you want to cancel? This recording will be permanently deleted.") },
            confirmButton = { Button(onClick = { viewModel.cancelRecording(); showCancelConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("DISCARD") } },
            dismissButton = { TextButton(onClick = { showCancelConfirm = false }) { Text("CANCEL") } }
        )
    }

    if (showBulkDeleteConfirm) {
        val count = selectedIds.size
        val isSingle = count == 1
        val isAllSelected = count == recordings.size && recordings.isNotEmpty()
        
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text(if (isSingle) "Delete recording?" else if (isAllSelected) "Delete all recordings?" else "Delete selected recordings?") },
            text = { Text("Are you sure you want to permanently delete ${if (isSingle) "this recording" else if (isAllSelected) "all recordings" else "these $count recordings"}?") },
            confirmButton = { 
                Button(
                    onClick = { viewModel.deleteSelectedRecordings(); showBulkDeleteConfirm = false }, 
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { 
                    Text(
                        when {
                            isAllSelected -> "DELETE ALL"
                            isSingle -> "DELETE 1 FILE"
                            else -> "DELETE $count FILES"
                        }
                    ) 
                } 
            },
            dismissButton = { 
                TextButton(onClick = { showBulkDeleteConfirm = false }) { 
                    Text("CANCEL") 
                } 
            }
        )
    }

    recordingToDelete?.let { r -> DeleteConfirmDialog(r.name, { viewModel.deleteRecording(r); recordingToDelete = null }, { recordingToDelete = null }) }
    recordingDetails?.let { r -> RecordingDetailsDialog(r) { recordingDetails = null } }
    if (showFilenameDialog) FilenameDialog({ viewModel.confirmFilename(it) }, { viewModel.dismissFilenameDialog() })
}

@Composable
private fun PermissionRequestView(onRequestPermission: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Mic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp)); Text("Microphone Permission Required", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp)); Button(onClick = onRequestPermission, shape = RoundedCornerShape(12.dp)) { Text("GRANT ACCESS", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun IdleControls(onStartRecording: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(Modifier.size(180.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer.copy(0.2f)) {
            Box(contentAlignment = Alignment.Center) { Button(onClick = onStartRecording, Modifier.size(110.dp), shape = CircleShape) { Icon(Icons.Default.Mic, null, Modifier.size(52.dp)) } }
        }
        Spacer(Modifier.height(32.dp)); Text("Tap to start recording", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecordingOverlay(viewModel: RecorderViewModel, onCancelClick: () -> Unit) {
    val duration by viewModel.recordingDurationSeconds.collectAsState(); val isPaused by viewModel.isPaused.collectAsState(); val amplitudes by viewModel.amplitudeHistory.collectAsState()
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(if (isPaused) "RECORDING PAUSED" else "RECORDING ACTIVE", fontWeight = FontWeight.Bold, color = Color.Red, letterSpacing = 2.sp)
        Spacer(Modifier.height(24.dp)); Text(String.format(Locale.getDefault(), "%02d:%02d", duration / 60, duration % 60), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(48.dp)); WaveformVisualizer(amplitudes, !isPaused, Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(0.05f)))
        Spacer(Modifier.height(64.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = { if (isPaused) viewModel.resumeRecording() else viewModel.pauseRecording() }, Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
                Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null); Spacer(Modifier.width(12.dp)); Text(if (isPaused) "RESUME" else "PAUSE", fontWeight = FontWeight.Bold)
            }
            Button(onClick = { viewModel.stopRecording() }, Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)) {
                Icon(Icons.Default.Stop, null); Spacer(Modifier.width(12.dp)); Text("STOP & SAVE", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onCancelClick) { Text("DISCARD RECORDING", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun WaveformVisualizer(amplitudes: List<Float>, isActive: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val count = amplitudes.size.coerceAtLeast(1); val slotWidth = size.width / count; val centerY = size.height / 2f; val maxBarHalf = size.height * 0.45f
        amplitudes.forEachIndexed { i, amp ->
            val h = (amp * maxBarHalf).coerceAtLeast(4f); drawRoundRect(color = if (isActive) Color.Red else Color.Gray.copy(0.5f), topLeft = Offset(i * slotWidth + 2f, centerY - h), size = Size(slotWidth * 0.6f, h * 2f), cornerRadius = CornerRadius(4f, 4f))
        }
    }
}

@Composable
private fun RecordingsListSection(recordings: List<Recording>, playingId: Long?, isPlaybackPaused: Boolean, playbackProgress: Float, selectedIds: Set<Long>, onTogglePlayback: (Recording) -> Unit, onSeek: (Float) -> Unit, onDeleteClick: (Recording) -> Unit, onRenameClick: (Recording, String) -> Unit, onContinueClick: (Recording) -> Unit, onShareClick: (Recording) -> Unit, onDetailsClick: (Recording) -> Unit, onSelect: (Recording) -> Unit, onLongClick: (Recording) -> Unit) {
    if (recordings.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No recordings found", color = MaterialTheme.colorScheme.outline) }
    else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(recordings, key = { it.id }) { r ->
            RecordingItem(r, playingId == r.id, isPlaybackPaused, if (playingId == r.id) playbackProgress else 0f, selectedIds.contains(r.id), selectedIds.isNotEmpty(), { onTogglePlayback(r) }, onSeek, { onDeleteClick(r) }, { onRenameClick(r, it) }, { onContinueClick(r) }, { onShareClick(r) }, { onDetailsClick(r) }, { onSelect(r) }, { onLongClick(r) })
        }
    }
}