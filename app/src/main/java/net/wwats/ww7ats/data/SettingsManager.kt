package net.wwats.ww7ats.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.wwats.ww7ats.model.StreamQuality

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    companion object {
        private val CALLSIGN = stringPreferencesKey("callsign")
        private val STREAM_KEY_MARKER = stringPreferencesKey("stream_key_marker")
        private val STREAM_QUALITY = stringPreferencesKey("stream_quality")
        private val MORSE_CW_ID_ENABLED = booleanPreferencesKey("morse_cw_id_enabled")
        private val MORSE_WPM = intPreferencesKey("morse_wpm")

        const val DEFAULT_HLS_URL = "https://stream.wwats.net/index.m3u8"
        const val DEFAULT_RTMP_SERVER = "rtmp://stream.wwats.net/live"
        private const val ENCRYPTED_PREFS_NAME = "ww7ats_secure_prefs"
        private const val STREAM_KEY_PREF = "stream_key"
    }

    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            callsign = prefs[CALLSIGN] ?: "",
            streamKey = getStreamKey(),
            streamQuality = StreamQuality.fromName(prefs[STREAM_QUALITY] ?: StreamQuality.MEDIUM.name),
            morseCWIDEnabled = prefs[MORSE_CW_ID_ENABLED] ?: true,
            morseWPM = prefs[MORSE_WPM] ?: 25
        )
    }

    suspend fun updateCallsign(value: String) {
        context.dataStore.edit { it[CALLSIGN] = value }
    }

    suspend fun updateStreamKey(value: String) {
        encryptedPrefs.edit().putString(STREAM_KEY_PREF, value).apply()
        // Poke DataStore so the settings flow re-emits with the updated key
        context.dataStore.edit { it[STREAM_KEY_MARKER] = value.hashCode().toString() }
    }

    private fun getStreamKey(): String {
        return encryptedPrefs.getString(STREAM_KEY_PREF, "") ?: ""
    }

    suspend fun updateStreamQuality(quality: StreamQuality) {
        context.dataStore.edit { it[STREAM_QUALITY] = quality.name }
    }

    suspend fun updateMorseCWIDEnabled(enabled: Boolean) {
        context.dataStore.edit { it[MORSE_CW_ID_ENABLED] = enabled }
    }

    suspend fun updateMorseWPM(wpm: Int) {
        context.dataStore.edit { it[MORSE_WPM] = wpm.coerceIn(10, 40) }
    }

    fun validateRtmpEndpoint(streamKey: String): String? {
        if (streamKey.isBlank()) return null
        val url = "$DEFAULT_RTMP_SERVER/$streamKey"
        return try {
            val parsed = java.net.URI(url)
            if (parsed.scheme in listOf("rtmp", "rtmps")) url else null
        } catch (_: Exception) {
            null
        }
    }
}

data class Settings(
    val callsign: String = "",
    val streamKey: String = "",
    val streamQuality: StreamQuality = StreamQuality.MEDIUM,
    val morseCWIDEnabled: Boolean = true,
    val morseWPM: Int = 25
)
