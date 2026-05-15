package com.soundrecorder.app.data

import android.media.MediaRecorder
import android.os.Build

data class Recording(
    val id: Long,
    val name: String,
    val filePath: String,
    val duration: Long,
    val size: Long,
    val format: RecordingFormat,
    val timestamp: Long,
    val sampleRate: Int,
    val bitRate: Int
)

enum class RecordingFormat(
    val displayName: String,
    val extension: String,
    val description: String,
    val outputFormat: Int,
    val audioEncoder: Int
) {
    WAV(
        displayName = "PCM (wav)",
        extension = "wav",
        description = "CD High Quality (Lossless)",
        outputFormat = -1,
        audioEncoder = -1
    ),
    AAC(
        displayName = "AAC (m4a)",
        extension = "m4a",
        description = "Good quality (Compressed)",
        outputFormat = MediaRecorder.OutputFormat.MPEG_4,
        audioEncoder = MediaRecorder.AudioEncoder.AAC
    ),
    AMR(
        displayName = "AMR (3gp)",
        extension = "3gp",
        description = "Small files (Voice only)",
        outputFormat = MediaRecorder.OutputFormat.THREE_GPP,
        audioEncoder = MediaRecorder.AudioEncoder.AMR_NB
    ),
    MP3(
        displayName = "MP3",
        extension = "mp3",
        description = "Standard Compatibility",
        outputFormat = MediaRecorder.OutputFormat.MPEG_4,
        audioEncoder = MediaRecorder.AudioEncoder.AAC
    )
}

enum class SampleRateOption(
    val displayName: String,
    val rateHz: Int,
    val description: String
) {
    CD_QUALITY("44.1 kHz", 44100, "CD Quality"),
    FM_QUALITY("32 kHz", 32000, "FM Quality"),
    AM_QUALITY("22 kHz", 22050, "AM Quality"),
    MEDIUM("16 kHz", 16000, "Voice Quality"),
    PHONE("8 kHz", 8000, "Telephone Quality")
}

enum class BitRateOption(
    val displayName: String,
    val bitsPerSecond: Int,
    val description: String
) {
    RATE_320("320 kbps", 320_000, "Ultra Quality"),
    RATE_256("256 kbps", 256_000, "High Quality"),
    RATE_192("192 kbps", 192_000, "Good Quality"),
    RATE_128("128 kbps", 128_000, "Standard"),
    RATE_64("64 kbps", 64_000, "Economy")
}

enum class AudioSourceOption(
    val displayName: String,
    val description: String,
    val sourceValue: Int
) {
    DEFAULT("Default", "System default", MediaRecorder.AudioSource.DEFAULT),
    MIC("Main microphone", "Internal mic", MediaRecorder.AudioSource.MIC),
    UNPROCESSED("Main (unprocessed)", "Raw audio data", if (Build.VERSION.SDK_INT >= 24) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.MIC),
    CAMCORDER("Camera microphone", "Directional mic", MediaRecorder.AudioSource.CAMCORDER),
    VOICE_RECOGNITION("Voice recognition", "Optimized for speech", MediaRecorder.AudioSource.VOICE_RECOGNITION),
    REMOTE_SUBMIX("Device Audio", "System internal sound", MediaRecorder.AudioSource.REMOTE_SUBMIX),
    VOICE_COMMUNICATION("VoIP / Bluetooth", "Communication tuning", MediaRecorder.AudioSource.VOICE_COMMUNICATION)
}

enum class ThemeOption(val displayName: String) {
    SYSTEM("System Default"),
    LIGHT("Light"),
    DARK("Dark")
}

data class AppSettings(
    val themeOption: ThemeOption = ThemeOption.SYSTEM,
    val recordingFormat: RecordingFormat = RecordingFormat.WAV,
    val sampleRate: SampleRateOption = SampleRateOption.CD_QUALITY,
    val bitRate: BitRateOption = BitRateOption.RATE_256,
    val audioSource: AudioSourceOption = AudioSourceOption.MIC,
    val defaultGainDb: Float = 0f,
    val askForFilename: Boolean = false,
    val recordStereo: Boolean = false,
    val recordOnStart: Boolean = false,
    val ledNotification: Boolean = true,
    val stopWhenLowMemory: Boolean = true,
    val stopOnCall: Boolean = true,
    val screenOn: Boolean = true,
    val autoStartAppLaunch: Boolean = false,
    val automaticGainControl: Boolean = false,
    val noiseSuppression: Boolean = false,
    val echoCancellation: Boolean = false,
    val notificationControls: Boolean = true,
    val autoStopLimitMs: Long = 3600000L, // Default 1 hour
    val stopOutOfMemory: Boolean = true
)