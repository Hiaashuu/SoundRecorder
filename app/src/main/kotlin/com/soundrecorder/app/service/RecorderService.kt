package com.soundrecorder.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.soundrecorder.app.MainActivity
import com.soundrecorder.app.R
import com.soundrecorder.app.data.AppPreferences
import com.soundrecorder.app.data.AppSettings
import com.soundrecorder.app.data.RecordingFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecorderService : Service() {

    companion object {
        const val ACTION_START = "com.soundrecorder.app.ACTION_START"
        const val ACTION_STOP = "com.soundrecorder.app.ACTION_STOP"
        const val ACTION_PAUSE = "com.soundrecorder.app.ACTION_PAUSE"
        const val ACTION_RESUME = "com.soundrecorder.app.ACTION_RESUME"
        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var isPaused = false

    private lateinit var preferences: AppPreferences

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(applicationContext)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_PAUSE -> pauseRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording(continueFile: File? = null) {
        try {
            if (mediaRecorder != null) {
                mediaRecorder?.reset()
            } else {
                mediaRecorder = MediaRecorder()
            }

            // Get current settings safely
            val settings: AppSettings = runBlocking {
                preferences.settingsFlow.first()
            }

            val outputFile = continueFile ?: createNewRecordingFile(settings.recordingFormat)
            currentFile = outputFile

            mediaRecorder?.apply {
                // === MAIN FIX FOR LOW VOLUME RECORDING ===
                // Use UNPROCESSED audio source for cleaner and louder input on modern devices
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAudioSource(MediaRecorder.AudioSource.UNPROCESSED)
                } else {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                }

                val format = settings.recordingFormat

                when (format) {
                    RecordingFormat.WAV -> {
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setAudioEncodingBitRate(256000)
                        setAudioSamplingRate(44100)
                    }
                    RecordingFormat.AAC, RecordingFormat.MP3 -> {
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setAudioEncodingBitRate(settings.bitRate.bitsPerSecond)
                        setAudioSamplingRate(settings.sampleRate.rateHz)
                    }
                    RecordingFormat.AMR -> {
                        setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                        setAudioSamplingRate(8000)
                    }
                }

                setOutputFile(outputFile.absolutePath)

                prepare()
                start()
            }

            isPaused = false
            startForegroundWithNotification(paused = false)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNewRecordingFile(format: RecordingFormat): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = getExternalFilesDir(null) ?: filesDir
        val fileName = "REC_${timestamp}.${format.extension}"
        return File(dir, fileName)
    }

    private fun pauseRecording() {
        try {
            mediaRecorder?.pause()
            isPaused = true
            startForegroundWithNotification(paused = true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resumeRecording() {
        try {
            mediaRecorder?.resume()
            isPaused = false
            startForegroundWithNotification(paused = false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    // ==================== ORIGINAL NOTIFICATION CODE (Fully Retained - Photocopier Rule) ====================

    private fun startForegroundWithNotification(paused: Boolean) {
        val notification = buildNotification(paused)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(paused: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RecorderService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeAction = if (paused) {
            val resumeIntent = Intent(this, RecorderService::class.java).apply { action = ACTION_RESUME }
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play, "Resume",
                PendingIntent.getService(this, 2, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            ).build()
        } else {
            val pauseIntent = Intent(this, RecorderService::class.java).apply { action = ACTION_PAUSE }
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause, "Pause",
                PendingIntent.getService(this, 3, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            ).build()
        }

        val statusText = if (paused) "Recording is currently paused" else "Recording is active and in progress"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sound Recorder")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(pauseResumeAction)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(statusText))

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recording Active Controls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows controls for the active recording session"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }
}