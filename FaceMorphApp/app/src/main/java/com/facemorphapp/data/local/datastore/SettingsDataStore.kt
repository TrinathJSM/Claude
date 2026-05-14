package com.facemorphapp.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.facemorphapp.domain.model.AppSettings
import com.facemorphapp.domain.model.OutputQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val GPU_ENABLED = booleanPreferencesKey("gpu_enabled")
        val OUTPUT_QUALITY = stringPreferencesKey("output_quality")
        val SAVE_ORIGINAL = booleanPreferencesKey("save_original")
        val PRIVACY_ACKNOWLEDGED = booleanPreferencesKey("privacy_acknowledged")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            gpuAccelerationEnabled = prefs[Keys.GPU_ENABLED] ?: true,
            outputQuality = OutputQuality.valueOf(prefs[Keys.OUTPUT_QUALITY] ?: OutputQuality.HIGH.name),
            saveOriginal = prefs[Keys.SAVE_ORIGINAL] ?: false,
            privacyAcknowledged = prefs[Keys.PRIVACY_ACKNOWLEDGED] ?: false
        )
    }

    suspend fun setGpuAcceleration(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GPU_ENABLED] = enabled }
    }

    suspend fun setOutputQuality(quality: OutputQuality) {
        context.dataStore.edit { it[Keys.OUTPUT_QUALITY] = quality.name }
    }

    suspend fun setSaveOriginal(save: Boolean) {
        context.dataStore.edit { it[Keys.SAVE_ORIGINAL] = save }
    }

    suspend fun acknowledgePrivacy() {
        context.dataStore.edit { it[Keys.PRIVACY_ACKNOWLEDGED] = true }
    }

    suspend fun isPrivacyAcknowledged(): Boolean =
        context.dataStore.data.first()[Keys.PRIVACY_ACKNOWLEDGED] ?: false
}
