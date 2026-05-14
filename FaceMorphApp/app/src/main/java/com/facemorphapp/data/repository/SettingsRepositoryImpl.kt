package com.facemorphapp.data.repository

import com.facemorphapp.data.local.datastore.SettingsDataStore
import com.facemorphapp.domain.model.AppSettings
import com.facemorphapp.domain.model.OutputQuality
import com.facemorphapp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: SettingsDataStore
) : SettingsRepository {
    override fun getSettings(): Flow<AppSettings> = dataStore.settings
    override suspend fun setGpuAcceleration(enabled: Boolean) = dataStore.setGpuAcceleration(enabled)
    override suspend fun setOutputQuality(quality: OutputQuality) = dataStore.setOutputQuality(quality)
    override suspend fun setSaveOriginal(save: Boolean) = dataStore.setSaveOriginal(save)
    override suspend fun acknowledgePrivacy() = dataStore.acknowledgePrivacy()
    override suspend fun isPrivacyAcknowledged(): Boolean = dataStore.isPrivacyAcknowledged()
}
