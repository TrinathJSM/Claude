package com.facemorphapp.presentation.screens.upload

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facemorphapp.domain.model.FaceDetectionResult
import com.facemorphapp.domain.usecase.DetectFaceUseCase
import com.facemorphapp.domain.repository.FaceMorphRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UploadUiState(
    val sourceUri: Uri? = null,
    val sourceBitmap: Bitmap? = null,
    val detectedFaces: List<FaceDetectionResult> = emptyList(),
    val selectedFace: FaceDetectionResult? = null,
    val isDetecting: Boolean = false,
    val error: String? = null,
    val lowConfidenceWarning: Boolean = false,
    val multipleFacesDialogVisible: Boolean = false
)

@HiltViewModel
class UploadViewModel @Inject constructor(
    private val detectFace: DetectFaceUseCase,
    private val repository: FaceMorphRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    fun onImageSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(sourceUri = uri, isDetecting = true, error = null) }

            repository.loadBitmapFromUri(uri)
                .onSuccess { bitmap ->
                    _uiState.update { it.copy(sourceBitmap = bitmap) }
                    runDetection(bitmap)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isDetecting = false, error = e.message) }
                }
        }
    }

    private suspend fun runDetection(bitmap: Bitmap) {
        detectFace(bitmap)
            .onSuccess { faces ->
                when {
                    faces.isEmpty() -> _uiState.update {
                        it.copy(isDetecting = false, error = "No face found. Try a clearer, well-lit photo.")
                    }
                    faces.size == 1 -> {
                        val face = faces.first()
                        _uiState.update {
                            it.copy(
                                isDetecting = false,
                                detectedFaces = faces,
                                selectedFace = face,
                                lowConfidenceWarning = face.confidence < 0.85f
                            )
                        }
                    }
                    else -> _uiState.update {
                        it.copy(
                            isDetecting = false,
                            detectedFaces = faces,
                            multipleFacesDialogVisible = true
                        )
                    }
                }
            }
            .onFailure { e ->
                _uiState.update { it.copy(isDetecting = false, error = e.message) }
            }
    }

    fun onFaceSelected(face: FaceDetectionResult) {
        _uiState.update {
            it.copy(
                selectedFace = face,
                multipleFacesDialogVisible = false,
                lowConfidenceWarning = face.confidence < 0.85f
            )
        }
    }

    fun dismissMultipleFacesDialog() {
        _uiState.update { it.copy(multipleFacesDialogVisible = false) }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
}
