package com.soundrecorder.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sound_recorder_prefs"
)

class AppPreferences(private val context: Context) {

    companion object {
        private val KEY_THEME = stringPreferencesKey("theme_option")
        private val KEY_FORMAT = stringPreferencesKey("recording_format")
        private val KEY_SAMPLE_RATE = stringPreferencesKey("sample_rate")
        private val KEY_BIT_RATE = stringPreferencesKey("bit_rate")
        private val KEY_AUDIO_SOURCE = stringPreferencesKey("audio_source")
        private val KEY_GAIN = floatPreferencesKey("default_gain_db")
        private val KEY_ASK_FILENAME = booleanPreferencesKey("ask_for_filename")
        private val KEY_RECORD_STEREO = booleanPreferencesKey("record_stereo")
        private val KEY_RECORD_ON_START = booleanPreferencesKey("record_on_start")
        private val KEY_LED_NOTIFICATION = booleanPreferencesKey("led_notification")
        private val KEY_STOP_LOW_MEMORY = booleanPreferencesKey("stop_when_low_memory")
        private val KEY_STOP_ON_CALL = booleanPreferencesKey("stop_on_call")
        private val KEY_SCREEN_ON = booleanPreferencesKey("screen_on")
        private val KEY_AUTO_START = booleanPreferencesKey("auto_start_app_launch")
        private val KEY_AGC = booleanPreferencesKey("automatic_gain_control")
        private val KEY_NS = booleanPreferencesKey("noise_suppression")
        private val KEY_AEC = booleanPreferencesKey("echo_cancellation")
        private val KEY_NOTIF_CONTROLS = booleanPreferencesKey("notification_controls")
        private val KEY_AUTO_STOP_LIMIT = longPreferencesKey("auto_stop_limit_ms")
        private val KEY_STOP_OUT_OF_MEM = booleanPreferencesKey("stop_out_of_memory")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeOption = runCatching {
                ThemeOption.valueOf(prefs[KEY_THEME] ?: ThemeOption.SYSTEM.name)
            }.getOrDefault(ThemeOption.SYSTEM),
            recordingFormat = runCatching {
                RecordingFormat.valueOf(prefs[KEY_FORMAT] ?: RecordingFormat.WAV.name)
            }.getOrDefault(RecordingFormat.WAV),
            sampleRate = runCatching {
                SampleRateOption.valueOf(prefs[KEY_SAMPLE_RATE] ?: SampleRateOption.CD_QUALITY.name)
            }.getOrDefault(SampleRateOption.CD_QUALITY),
            bitRate = runCatching {
                BitRateOption.valueOf(prefs[KEY_BIT_RATE] ?: BitRateOption.RATE_256.name)
            }.getOrDefault(BitRateOption.RATE_256),
            audioSource = runCatching {
                AudioSourceOption.valueOf(prefs[KEY_AUDIO_SOURCE] ?: AudioSourceOption.MIC.name)
            }.getOrDefault(AudioSourceOption.MIC),
            defaultGainDb = prefs[KEY_GAIN] ?: 15f,
            askForFilename = prefs[KEY_ASK_FILENAME] ?: false,
            recordStereo = prefs[KEY_RECORD_STEREO] ?: false,
            recordOnStart = prefs[KEY_RECORD_ON_START] ?: false,
            ledNotification = prefs[KEY_LED_NOTIFICATION] ?: true,
            stopWhenLowMemory = prefs[KEY_STOP_LOW_MEMORY] ?: true,
            stopOnCall = prefs[KEY_STOP_ON_CALL] ?: true,
            screenOn = prefs[KEY_SCREEN_ON] ?: true,
            autoStartAppLaunch = prefs[KEY_AUTO_START] ?: false,
            automaticGainControl = prefs[KEY_AGC] ?: false,
            noiseSuppression = prefs[KEY_NS] ?: false,
            echoCancellation = prefs[KEY_AEC] ?: false,
            notificationControls = prefs[KEY_NOTIF_CONTROLS] ?: true,
            autoStopLimitMs = prefs[KEY_AUTO_STOP_LIMIT] ?: 3600000L, // 1 hour default
            stopOutOfMemory = prefs[KEY_STOP_OUT_OF_MEM] ?: true
        )
    }

    suspend fun updateTheme(theme: ThemeOption) {
        context.dataStore.edit { it[KEY_THEME] = theme.name }
    }

    suspend fun updateFormat(format: RecordingFormat) {
        context.dataStore.edit { it[KEY_FORMAT] = format.name }
    }

    suspend fun updateSampleRate(rate: SampleRateOption) {
        context.dataStore.edit { it[KEY_SAMPLE_RATE] = rate.name }
    }

    suspend fun updateBitRate(rate: BitRateOption) {
        context.dataStore.edit { it[KEY_BIT_RATE] = rate.name }
    }

    suspend fun updateAudioSource(source: AudioSourceOption) {
        context.dataStore.edit { it[KEY_AUDIO_SOURCE] = source.name }
    }

    suspend fun updateGain(gainDb: Float) {
        context.dataStore.edit { it[KEY_GAIN] = gainDb }
    }

    suspend fun updateAskForFilename(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ASK_FILENAME] = enabled }
    }

    suspend fun updateRecordStereo(enabled: Boolean) {
        context.dataStore.edit { it[KEY_RECORD_STEREO] = enabled }
    }

    suspend fun updateRecordOnStart(enabled: Boolean) {
        context.dataStore.edit { it[KEY_RECORD_ON_START] = enabled }
    }

    suspend fun updateLedNotification(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LED_NOTIFICATION] = enabled }
    }

    suspend fun updateStopWhenLowMemory(enabled: Boolean) {
        context.dataStore.edit { it[KEY_STOP_LOW_MEMORY] = enabled }
    }

    suspend fun updateStopOnCall(enabled: Boolean) {
        context.dataStore.edit { it[KEY_STOP_ON_CALL] = enabled }
    }

    suspend fun updateScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SCREEN_ON] = enabled }
    }

    suspend fun updateAutoStart(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_START] = enabled }
    }

    suspend fun updateAgc(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AGC] = enabled }
    }

    suspend fun updateNs(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NS] = enabled }
    }

    suspend fun updateAec(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AEC] = enabled }
    }

    suspend fun updateNotificationControls(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIF_CONTROLS] = enabled }
    }

    suspend fun updateAutoStopLimit(limitMs: Long) {
        context.dataStore.edit { it[KEY_AUTO_STOP_LIMIT] = limitMs }
    }

    suspend fun updateStopOutOfMemory(enabled: Boolean) {
        context.dataStore.edit { it[KEY_STOP_OUT_OF_MEM] = enabled }
    }
}