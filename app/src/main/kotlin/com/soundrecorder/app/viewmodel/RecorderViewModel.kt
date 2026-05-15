package com.soundrecorder.app.viewmodel

import android.content.Context
import android.content.Intent
import android.media.*
import android.media.audiofx.*
import android.os.*
import android.util.Log
import androidx.lifecycle.*
import com.soundrecorder.app.data.*
import com.soundrecorder.app.service.RecorderService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import java.nio.*
import java.text.SimpleDateFormat
import java.util.*

class RecorderViewModel(private val appContext: Context) : ViewModel() {

    companion object {
        private const val TAG = "RecorderViewModel"
        private const val WAVEFORM_BUFFER_SIZE = 70
        private const val AMPLITUDE_POLL_MS = 80L
        private const val TIMER_INTERVAL_MS = 1000L

        fun provideFactory(context: Context): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(RecorderViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return RecorderViewModel(context.applicationContext) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }
    }

    private val preferences = AppPreferences(appContext)
    
    private val _settings = MutableStateFlow(AppSettings())
    val settings = _settings.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme = _isDarkTheme.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused = _isPaused.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0L)
    val recordingDurationSeconds = _recordingDurationSeconds.asStateFlow()

    private val _amplitudeHistory = MutableStateFlow<List<Float>>(List(WAVEFORM_BUFFER_SIZE) { 0f })
    val amplitudeHistory = _amplitudeHistory.asStateFlow()

    private val _recordings = MutableStateFlow<List<Recording>>(emptyList())
    val recordings = _recordings.asStateFlow()

    private val _isLoadingRecordings = MutableStateFlow(false)
    val isLoadingRecordings = _isLoadingRecordings.asStateFlow()

    private val _playingRecordingId = MutableStateFlow<Long?>(null)
    val playingRecordingId = _playingRecordingId.asStateFlow()

    private val _isPlaybackPaused = MutableStateFlow(false)
    val isPlaybackPaused = _isPlaybackPaused.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress = _playbackProgress.asStateFlow()

    private val _showFilenameDialog = MutableStateFlow(false)
    val showFilenameDialog = _showFilenameDialog.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _navigateToLibrary = MutableSharedFlow<Unit>()
    val navigateToLibrary = _navigateToLibrary.asSharedFlow()

    // Selection State
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds = _selectedIds.asStateFlow()

    private var pendingFilePath = ""
    private var isDiscarding = false
    private var mediaRecorder: MediaRecorder? = null
    private var audioRecord: AudioRecord? = null
    private var wavBytesWritten = 0L
    private var agc: AutomaticGainControl? = null
    private var ns: NoiseSuppressor? = null
    private var aec: AcousticEchoCanceler? = null
    private var timerJob: Job? = null
    private var amplitudeJob: Job? = null
    private var wavJob: Job? = null
    private var playbackProgressJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    // LoudnessEnhancer for playback volume boost beyond hardware maximum
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentRecordingFormat = RecordingFormat.WAV
    private var currentFile: File? = null

    init {
        viewModelScope.launch {
            preferences.settingsFlow.collect { newSettings ->
                _settings.value = newSettings
                val uiMode = appContext.resources.configuration.uiMode
                val nightMask = android.content.res.Configuration.UI_MODE_NIGHT_MASK
                val nightYes = android.content.res.Configuration.UI_MODE_NIGHT_YES
                
                _isDarkTheme.value = when (newSettings.themeOption) {
                    ThemeOption.DARK -> true
                    ThemeOption.LIGHT -> false
                    ThemeOption.SYSTEM -> (uiMode and nightMask) == nightYes
                }
                
                if (newSettings.autoStartAppLaunch && !_isRecording.value) {
                    startRecording()
                }
            }
        }
        loadRecordings()
    }

    fun getRecordingsDir(): File {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val dir = File(musicDir, "SoundRecorder")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun loadRecordings() {
        viewModelScope.launch {
            _isLoadingRecordings.value = true
            withContext(Dispatchers.IO) {
                val extensions = setOf("m4a", "wav", "3gp", "mp3")
                val files = getRecordingsDir().listFiles()
                    ?.filter { it.isFile && it.extension in extensions }
                    ?.sortedByDescending { it.lastModified() }
                    ?: emptyList()

                val list = files.map { file ->
                    val format = when (file.extension) {
                        "m4a" -> RecordingFormat.AAC
                        "wav" -> RecordingFormat.WAV
                        "3gp" -> RecordingFormat.AMR
                        "mp3" -> RecordingFormat.MP3
                        else -> RecordingFormat.AAC
                    }
                    Recording(
                        id = file.lastModified(),
                        name = file.nameWithoutExtension,
                        filePath = file.absolutePath,
                        duration = getFileDuration(file),
                        size = file.length(),
                        format = format,
                        timestamp = file.lastModified(),
                        sampleRate = 0,
                        bitRate = 0
                    )
                }
                withContext(Dispatchers.Main) {
                    _recordings.value = list
                    _isLoadingRecordings.value = false
                }
            }
        }
    }

    private fun getFileDuration(file: File): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val ms = durationStr?.toLongOrNull() ?: 0L
            retriever.release()
            ms
        } catch (e: Exception) {
            0L
        }
    }

    fun startRecording(continueFrom: File? = null) {
        if (_isRecording.value) return
        
        if (continueFrom != null && _settings.value.recordingFormat != RecordingFormat.WAV) {
            _errorMessage.value = "Continue recording is only supported for WAV format."
            return
        }

        isDiscarding = false
        val s = _settings.value
        currentRecordingFormat = s.recordingFormat

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = if (continueFrom == null) "Recording_$timestamp" else continueFrom.nameWithoutExtension
        val file = if (continueFrom == null) {
            File(getRecordingsDir(), "$fileName.${s.recordingFormat.extension}")
        } else {
            continueFrom
        }
        currentFile = file

        try {
            // Priority: VOICE_RECOGNITION usually has higher sensitivity (volume) than MIC
            val optimizedSource = if (s.audioSource.sourceValue == MediaRecorder.AudioSource.MIC) {
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            } else {
                s.audioSource.sourceValue
            }

            if (s.recordingFormat == RecordingFormat.WAV) {
                startWavRecording(file, s.sampleRate.rateHz, s.recordStereo, optimizedSource, s, continueFrom != null)
            } else {
                startMediaRecorder(file, s.recordingFormat.outputFormat, s.recordingFormat.audioEncoder, s.sampleRate.rateHz, s.bitRate.bitsPerSecond, optimizedSource, s.recordStereo)
            }
            
            _isRecording.value = true
            _isPaused.value = false
            _recordingDurationSeconds.value = if (continueFrom == null) 0L else getFileDuration(continueFrom) / 1000L
            
            startTimer()
            startAmplitudePolling()
            if (s.notificationControls) {
                startForegroundService()
            }
        } catch (e: Exception) {
            _errorMessage.value = "Start failed: ${e.message}"
            cleanupRecorder()
        }
    }

    private fun startMediaRecorder(file: File, outputFormat: Int, audioEncoder: Int, sampleRate: Int, bitRate: Int, audioSource: Int, isStereo: Boolean) {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        
        recorder.setAudioSource(audioSource)
        recorder.setOutputFormat(outputFormat)
        recorder.setAudioEncoder(audioEncoder)
        recorder.setAudioChannels(if (isStereo) 2 else 1)
        recorder.setAudioSamplingRate(sampleRate)
        recorder.setAudioEncodingBitRate(bitRate)
        
        recorder.setOutputFile(file.absolutePath)
        recorder.prepare()
        recorder.start()
        mediaRecorder = recorder
    }

    private fun startWavRecording(file: File, sampleRate: Int, isStereo: Boolean, audioSource: Int, s: AppSettings, isAppend: Boolean) {
        val channelConfig = if (isStereo) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT)
        val bufferSize = minBuffer.coerceAtLeast(8192)

        val recorder = AudioRecord(audioSource, sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord initialization failed")
        }

        val sessionId = recorder.audioSessionId
        
        try {
            if (s.automaticGainControl && AutomaticGainControl.isAvailable()) {
                agc = AutomaticGainControl.create(sessionId)
                agc?.enabled = true
            }
            if (s.noiseSuppression && NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(sessionId)
                ns?.enabled = true
            }
            if (s.echoCancellation && AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(sessionId)
                aec?.enabled = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Effect init failed", e)
        }

        var existingDataSize = 0L
        if (isAppend && file.exists()) {
            existingDataSize = file.length() - 44
        }

        val outputStream = FileOutputStream(file, isAppend)
        if (!isAppend) {
            writeWavHeader(outputStream, 0L, sampleRate, if (isStereo) 2 else 1)
        }

        // LOUD FIX: Base multiplier raised from 1.5 to 6.0 for strong output.
        // Combined with user gain (default 15 dB), this produces a fully loud recording.
        val gainFactor = (Math.pow(10.0, s.defaultGainDb / 20.0) * 6.0).toFloat()

        wavBytesWritten = 0L
        audioRecord = recorder
        recorder.startRecording()

        val channels = if (isStereo) 2 else 1
        wavJob = viewModelScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(bufferSize)
            try {
                while (isActive && _isRecording.value) {
                    if (!_isPaused.value) {
                        val read = recorder.read(buffer, 0, bufferSize)
                        if (read > 0) {
                            // High-Precision Digital Volume Boost
                            for (i in 0 until read step 2) {
                                val lo = buffer[i].toInt() and 0xFF
                                val hi = buffer[i + 1].toInt()
                                val sample = ((hi shl 8) or lo).toShort()
                                var boosted = (sample * gainFactor).toInt()
                                boosted = boosted.coerceIn(-32768, 32767)
                                buffer[i] = (boosted and 0xFF).toByte()
                                buffer[i + 1] = ((boosted shr 8) and 0xFF).toByte()
                            }

                            outputStream.write(buffer, 0, read)
                            wavBytesWritten += read
                            checkAutoStopAndMemory(s)
                            val rms = calculateRms(buffer, read)
                            withContext(Dispatchers.Main) {
                                pushAmplitude(rms)
                            }
                        }
                    } else {
                        delay(50L)
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    try { recorder.stop() } catch (e: Exception) {}
                    recorder.release()
                    try { 
                        outputStream.flush()
                        outputStream.close() 
                    } catch (e: Exception) {}
                    
                    fixWavHeader(file.absolutePath, existingDataSize + wavBytesWritten, sampleRate, channels)
                    
                    audioRecord = null
                    agc?.release()
                    ns?.release()
                    aec?.release()
                    
                    withContext(Dispatchers.Main) {
                        if (!isDiscarding) {
                            if (_settings.value.askForFilename && !isAppend) {
                                pendingFilePath = file.absolutePath
                                _showFilenameDialog.value = true
                            } else {
                                loadRecordings()
                                _navigateToLibrary.emit(Unit)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkAutoStopAndMemory(s: AppSettings) {
        if (s.autoStopLimitMs > 0 && _recordingDurationSeconds.value * 1000 >= s.autoStopLimitMs) {
            viewModelScope.launch(Dispatchers.Main) {
                stopRecording()
                _errorMessage.value = "Auto-stopped: reached limit"
            }
        }
        if (s.stopOutOfMemory) {
            val stats = StatFs(getRecordingsDir().absolutePath)
            val available = stats.availableBlocksLong * stats.blockSizeLong
            if (available < 20 * 1024 * 1024) {
                viewModelScope.launch(Dispatchers.Main) {
                    stopRecording()
                    _errorMessage.value = "Storage critical: Session saved."
                }
            }
        }
    }

    private fun calculateRms(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        val count = length / 2
        for (i in 0 until count) {
            val lo = buffer[i * 2].toInt() and 0xFF
            val hi = buffer[i * 2 + 1].toInt()
            val sample = ((hi shl 8) or lo).toShort().toDouble()
            sum += sample * sample
        }
        val rms = Math.sqrt(sum / count.coerceAtLeast(1))
        return (rms / 32768.0).toFloat().coerceIn(0f, 1f)
    }

    fun pauseRecording() {
        if (!_isRecording.value || _isPaused.value) return
        if (currentRecordingFormat != RecordingFormat.WAV) {
            try { mediaRecorder?.pause() } catch (e: Exception) {}
        }
        _isPaused.value = true
        timerJob?.cancel()
        amplitudeJob?.cancel()
        updateForegroundService(true)
    }

    fun resumeRecording() {
        if (!_isRecording.value || !_isPaused.value) return
        if (currentRecordingFormat != RecordingFormat.WAV) {
            try { mediaRecorder?.resume() } catch (e: Exception) {}
        }
        _isPaused.value = false
        startTimer()
        startAmplitudePolling()
        updateForegroundService(false)
    }

    fun stopRecording() {
        if (!_isRecording.value) return
        isDiscarding = false
        _isRecording.value = false
        _isPaused.value = false
        timerJob?.cancel()
        amplitudeJob?.cancel()
        resetAmplitudeHistory()
        stopForegroundService()
        
        if (currentRecordingFormat != RecordingFormat.WAV) {
            try { mediaRecorder?.stop() } catch (e: Exception) {}
            mediaRecorder?.release()
            mediaRecorder = null
            if (_settings.value.askForFilename) {
                _showFilenameDialog.value = true
            } else {
                loadRecordings()
                viewModelScope.launch { _navigateToLibrary.emit(Unit) }
            }
        }
    }

    fun cancelRecording() {
        if (!_isRecording.value) return
        isDiscarding = true
        val fileToDelete = currentFile
        _isRecording.value = false
        _isPaused.value = false
        cleanupRecorder()
        stopForegroundService()
        viewModelScope.launch(Dispatchers.IO) {
            fileToDelete?.delete()
            withContext(Dispatchers.Main) {
                loadRecordings()
            }
        }
    }

    fun confirmFilename(newName: String) {
        _showFilenameDialog.value = false
        val path = pendingFilePath
        if (path.isNotBlank() && newName.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                val oldFile = File(path)
                if (oldFile.exists()) {
                    val newFile = File(oldFile.parent, "$newName.${oldFile.extension}")
                    oldFile.renameTo(newFile)
                }
                withContext(Dispatchers.Main) {
                    pendingFilePath = ""
                    loadRecordings()
                    _navigateToLibrary.emit(Unit)
                }
            }
        } else {
            pendingFilePath = ""
            loadRecordings()
            viewModelScope.launch { _navigateToLibrary.emit(Unit) }
        }
    }

    fun dismissFilenameDialog() {
        _showFilenameDialog.value = false
        pendingFilePath = ""
        loadRecordings()
        viewModelScope.launch { _navigateToLibrary.emit(Unit) }
    }

    fun togglePlayback(recording: Recording) {
        if (_playingRecordingId.value == recording.id) {
            if (_isPlaybackPaused.value) {
                mediaPlayer?.start()
                _isPlaybackPaused.value = false
                startPlaybackProgressPolling()
            } else {
                mediaPlayer?.pause()
                _isPlaybackPaused.value = true
                playbackProgressJob?.cancel()
            }
        } else {
            stopPlayback()
            startPlayback(recording)
        }
    }

    private fun startPlayback(recording: Recording) {
    try {
        val player = MediaPlayer()

        // Correctly route to STREAM_MUSIC so hardware volume buttons
        // and user's media volume setting control this playback.
        // Do NOT touch system volume — user controls that themselves.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
        } else {
            @Suppress("DEPRECATION")
            player.setAudioStreamType(AudioManager.STREAM_MUSIC)
        }

        player.setDataSource(recording.filePath)
        player.prepare()

        // setVolume() is player-internal software gain only — does NOT touch
        // system volume. Keeps player's own mix level at full so no signal
        // is lost inside the MediaPlayer pipeline before it hits the stream.
        player.setVolume(1.0f, 1.0f)

        // LoudnessEnhancer boosts the audio signal itself, not system volume.
        // User's volume wheel still fully controls final loudness.
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(player.audioSessionId)
            loudnessEnhancer?.setTargetGain(500) // +5 dB signal boost, clean and safe
            loudnessEnhancer?.enabled = true
        } catch (e: Exception) {
            Log.w(TAG, "LoudnessEnhancer unavailable on this device", e)
        }

        player.start()
        player.setOnCompletionListener { stopPlayback() }
        mediaPlayer = player
        _playingRecordingId.value = recording.id
        _isPlaybackPaused.value = false
        startPlaybackProgressPolling()
    } catch (e: Exception) {
        _errorMessage.value = "Playback error"
        stopPlayback()
    }
}
    fun seekPlayback(progress: Float) {
        mediaPlayer?.let { player ->
            try {
                val msec = (progress * player.duration).toInt()
                player.seekTo(msec)
                _playbackProgress.value = progress
            } catch (e: Exception) {
                Log.e(TAG, "Seek failed", e)
            }
        }
    }

    fun stopPlayback() {
        playbackProgressJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {}
        // Release LoudnessEnhancer alongside MediaPlayer
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        mediaPlayer = null
        _playingRecordingId.value = null
        _isPlaybackPaused.value = false
        _playbackProgress.value = 0f
    }

    private fun startPlaybackProgressPolling() {
        playbackProgressJob?.cancel()
        playbackProgressJob = viewModelScope.launch {
            while (isActive) {
                val p = mediaPlayer ?: break
                if (p.isPlaying) {
                    val pos = p.currentPosition.toLong()
                    val dur = p.duration.toLong().coerceAtLeast(1L)
                    _playbackProgress.value = (pos.toFloat() / dur).coerceIn(0f, 1f)
                }
                delay(200L)
            }
        }
    }

    fun toggleSelection(id: Long) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedIds.value = current
    }

    fun selectAll() {
        _selectedIds.value = _recordings.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun deleteSelectedRecordings() {
        val idsToDelete = _selectedIds.value
        viewModelScope.launch(Dispatchers.IO) {
            _recordings.value.filter { it.id in idsToDelete }.forEach { recording ->
                if (_playingRecordingId.value == recording.id) withContext(Dispatchers.Main) { stopPlayback() }
                val f = File(recording.filePath)
                if (f.exists()) f.delete()
            }
            withContext(Dispatchers.Main) {
                _selectedIds.value = emptySet()
                loadRecordings()
            }
        }
    }

    fun deleteRecording(recording: Recording) {
        if (_playingRecordingId.value == recording.id) stopPlayback()
        viewModelScope.launch(Dispatchers.IO) {
            val f = File(recording.filePath)
            if (f.exists()) f.delete()
            withContext(Dispatchers.Main) { loadRecordings() }
        }
    }

    fun renameRecording(recording: Recording, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val oldFile = File(recording.filePath)
            if (oldFile.exists()) {
                val newFile = File(oldFile.parent, "$newName.${oldFile.extension}")
                oldFile.renameTo(newFile)
            }
            withContext(Dispatchers.Main) { loadRecordings() }
        }
    }

    fun setTheme(t: ThemeOption) { viewModelScope.launch { preferences.updateTheme(t) } }
    fun setRecordingFormat(f: RecordingFormat) { viewModelScope.launch { preferences.updateFormat(f) } }
    fun setSampleRate(r: SampleRateOption) { viewModelScope.launch { preferences.updateSampleRate(r) } }
    fun setBitRate(r: BitRateOption) { viewModelScope.launch { preferences.updateBitRate(r) } }
    fun setAudioSource(s: AudioSourceOption) { viewModelScope.launch { preferences.updateAudioSource(s) } }
    fun setGain(g: Float) { viewModelScope.launch { preferences.updateGain(g) } }
    fun setAskForFilename(e: Boolean) { viewModelScope.launch { preferences.updateAskForFilename(e) } }
    fun setRecordStereo(e: Boolean) { viewModelScope.launch { preferences.updateRecordStereo(e) } }
    fun setStopOnCall(e: Boolean) { viewModelScope.launch { preferences.updateStopOnCall(e) } }
    fun setScreenOn(e: Boolean) { viewModelScope.launch { preferences.updateScreenOn(e) } }
    fun setAutoStart(e: Boolean) { viewModelScope.launch { preferences.updateAutoStart(e) } }
    fun setAgc(e: Boolean) { viewModelScope.launch { preferences.updateAgc(e) } }
    fun setNs(e: Boolean) { viewModelScope.launch { preferences.updateNs(e) } }
    fun setAec(e: Boolean) { viewModelScope.launch { preferences.updateAec(e) } }
    fun setNotificationControls(e: Boolean) { viewModelScope.launch { preferences.updateNotificationControls(e) } }
    fun setAutoStopLimit(ms: Long) { viewModelScope.launch { preferences.updateAutoStopLimit(ms) } }
    fun setStopOutOfMemory(e: Boolean) { viewModelScope.launch { preferences.updateStopOutOfMemory(e) } }
    fun clearErrorMessage() { _errorMessage.value = null }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                if (_isRecording.value && !_isPaused.value) {
                    _recordingDurationSeconds.value++
                }
            }
        }
    }

    private fun startAmplitudePolling() {
        if (currentRecordingFormat == RecordingFormat.WAV) return
        amplitudeJob?.cancel()
        amplitudeJob = viewModelScope.launch {
            while (isActive) {
                delay(AMPLITUDE_POLL_MS)
                if (_isRecording.value && !_isPaused.value) {
                    val raw = mediaRecorder?.maxAmplitude ?: 0
                    pushAmplitude((raw / 32768f).coerceIn(0f, 1f))
                }
            }
        }
    }

    private fun pushAmplitude(v: Float) {
        val list = _amplitudeHistory.value.toMutableList()
        list.removeAt(0)
        list.add(v)
        _amplitudeHistory.value = list.toList()
    }

    private fun resetAmplitudeHistory() {
        _amplitudeHistory.value = List(WAVEFORM_BUFFER_SIZE) { 0f }
    }

    private fun startForegroundService() {
        val intent = Intent(appContext, RecorderService::class.java)
        intent.action = RecorderService.ACTION_START
        appContext.startForegroundService(intent)
    }

    private fun updateForegroundService(paused: Boolean) {
        val intent = Intent(appContext, RecorderService::class.java)
        intent.action = if (paused) RecorderService.ACTION_PAUSE else RecorderService.ACTION_RESUME
        appContext.startService(intent)
    }

    private fun stopForegroundService() {
        val intent = Intent(appContext, RecorderService::class.java)
        intent.action = RecorderService.ACTION_STOP
        appContext.startService(intent)
    }

    private fun writeWavHeader(out: OutputStream, length: Long, rate: Int, channels: Int) {
        val bits = 16
        val byteRate = (rate * channels * bits / 8).toLong()
        val align = (channels * bits / 8).toShort()
        val total = length + 36
        
        fun intLE(v: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
        fun shortLE(v: Short): ByteArray = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v).array()
        
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.write(intLE(total.toInt()))
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        out.write(intLE(16))
        out.write(shortLE(1.toShort()))
        out.write(shortLE(channels.toShort()))
        out.write(intLE(rate))
        out.write(intLE(byteRate.toInt()))
        out.write(shortLE(align))
        out.write(shortLE(bits.toShort()))
        out.write("data".toByteArray(Charsets.US_ASCII))
        out.write(intLE(length.toInt()))
    }

    private fun fixWavHeader(path: String, length: Long, rate: Int, channels: Int) {
        try {
            val bits = 16
            val byteRate = (rate * channels * bits / 8).toLong()
            val align = (channels * bits / 8).toShort()
            val total = length + 36
            
            fun intLE(v: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
            fun shortLE(v: Short): ByteArray = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v).array()
            
            val raf = RandomAccessFile(path, "rw")
            raf.seek(0)
            raf.write("RIFF".toByteArray(Charsets.US_ASCII))
            raf.write(intLE(total.toInt()))
            raf.write("WAVE".toByteArray(Charsets.US_ASCII))
            raf.write("fmt ".toByteArray(Charsets.US_ASCII))
            raf.write(intLE(16))
            raf.write(shortLE(1.toShort()))
            raf.write(shortLE(channels.toShort()))
            raf.write(intLE(rate))
            raf.write(intLE(byteRate.toInt()))
            raf.write(shortLE(align))
            raf.write(shortLE(bits.toShort()))
            raf.write("data".toByteArray(Charsets.US_ASCII))
            raf.write(intLE(length.toInt()))
            raf.close()
        } catch (e: Exception) {
            Log.e(TAG, "fixWavHeader failed", e)
        }
    }

    private fun cleanupRecorder() {
        _isRecording.value = false
        _isPaused.value = false
        timerJob?.cancel()
        amplitudeJob?.cancel()
        try { audioRecord?.stop() } catch (e: Exception) {}
        audioRecord?.release()
        audioRecord = null
        try { mediaRecorder?.stop() } catch (e: Exception) {}
        mediaRecorder?.release()
        mediaRecorder = null
        wavJob?.cancel()
        agc?.release()
        ns?.release()
        aec?.release()
        resetAmplitudeHistory()
    }

    override fun onCleared() {
        super.onCleared()
        cleanupRecorder()
        stopPlayback()
    }
}