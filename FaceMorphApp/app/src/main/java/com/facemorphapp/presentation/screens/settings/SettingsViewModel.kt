package com.facemorphapp.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facemorphapp.domain.model.AppSettings
import com.facemorphapp.domain.model.OutputQuality
import com.facemorphapp.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.getSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setGpuAcceleration(enabled: Boolean) {
        viewModelScope.launch { repository.setGpuAcceleration(enabled) }
    }

    fun setOutputQuality(quality: OutputQuality) {
        viewModelScope.launch { repository.setOutputQuality(quality) }
    }

    fun setSaveOriginal(save: Boolean) {
        viewModelScope.launch { repository.setSaveOriginal(save) }
    }
}
