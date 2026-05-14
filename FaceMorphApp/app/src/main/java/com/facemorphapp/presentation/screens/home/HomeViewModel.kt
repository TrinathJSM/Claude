package com.facemorphapp.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facemorphapp.domain.model.MorphResult
import com.facemorphapp.domain.usecase.GetRecentMorphsUseCase
import com.facemorphapp.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    getRecentMorphs: GetRecentMorphsUseCase,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val recentMorphs: StateFlow<List<MorphResult>> = getRecentMorphs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val showPrivacyScreen: StateFlow<Boolean> = settingsRepository.getSettings()
        .map { !it.privacyAcknowledged }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
