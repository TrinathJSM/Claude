package com.facemorphapp.presentation.screens.targetpicker

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facemorphapp.domain.model.TargetFace
import com.facemorphapp.domain.repository.TargetFaceRepository
import com.facemorphapp.domain.usecase.GetTargetFacesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TargetPickerViewModel @Inject constructor(
    private val getTargetFaces: GetTargetFacesUseCase,
    private val targetFaceRepository: TargetFaceRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _allTargets = MutableStateFlow<List<TargetFace>>(emptyList())

    val filteredTargets: StateFlow<List<TargetFace>> = combine(_allTargets, _searchQuery) { all, query ->
        targetFaceRepository.searchTargets(query, all)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            getTargetFaces().collect { _allTargets.value = it }
        }
    }

    fun onSearchChanged(query: String) { _searchQuery.value = query }

    fun addCustomTarget(uri: Uri) {
        viewModelScope.launch {
            val target = targetFaceRepository.addCustomTarget(uri)
            _allTargets.update { it + target }
        }
    }
}
