package com.facemorphapp.domain.repository

import com.facemorphapp.domain.model.AppSettings
import com.facemorphapp.domain.model.OutputQuality
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getSettings(): Flow<AppSettings>
    suspend fun setGpuAcceleration(enabled: Boolean)
    suspend fun setOutputQuality(quality: OutputQuality)
    suspend fun setSaveOriginal(save: Boolean)
    suspend fun acknowledgePrivacy()
    suspend fun isPrivacyAcknowledged(): Boolean
}
