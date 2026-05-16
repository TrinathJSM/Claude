package com.facemorphapp.presentation.screens.upload

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facemorphapp.domain.model.FaceDetectionResult
import com.facemorphapp.domain.model.FaceValidationResult
import com.facemorphapp.domain.usecase.DetectFaceUseCase
import com.facemorphapp.domain.repository.FaceMorphRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MIN_CONFIDENCE = 0.70f
private const val MIN_FACE_FRACTION = 0.03f

data class UploadUiState(
    val sourceUri: Uri? = null,
    val sourceBitmap: Bitmap? = null,
    val detectedFaces: List<FaceDetectionResult> = emptyList(),
    val selectedFace: FaceDetectionResult? = null,
    val isDetecting: Boolean = false,
    val error: String? = null,
    val validationResult: FaceValidationResult? = null,
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
            _uiState.update { it.copy(sourceUri = uri, isDetecting = true, error = null, validationResult = null) }

            val result = repository.loadBitmapFromUri(uri)
            if (result.isFailure) {
                _uiState.update { it.copy(isDetecting = false, error = result.exceptionOrNull()?.message) }
                return@launch
            }
            val bitmap = result.getOrThrow()
            _uiState.update { it.copy(sourceBitmap = bitmap) }
            runDetection(bitmap)
        }
    }

    private suspend fun runDetection(bitmap: Bitmap) {
        detectFace(bitmap)
            .onSuccess { faces ->
                val validation: FaceValidationResult = when {
                    faces.isEmpty() -> FaceValidationResult.NoFace
                    faces.size > 1 -> FaceValidationResult.MultipleFaces(faces)
                    faces.first().boundingBox.let { it.width() * it.height() } <
                        bitmap.width * bitmap.height * MIN_FACE_FRACTION ->
                        FaceValidationResult.TooSmall
                    faces.first().confidence < MIN_CONFIDENCE ->
                        FaceValidationResult.LowConfidence
                    else -> FaceValidationResult.Valid(faces.first())
                }

                when (validation) {
                    is FaceValidationResult.Valid ->
                        _uiState.update {
                            it.copy(
                                isDetecting = false,
                                detectedFaces = faces,
                                selectedFace = validation.result,
                                validationResult = validation
                            )
                        }
                    is FaceValidationResult.MultipleFaces ->
                        _uiState.update {
                            it.copy(
                                isDetecting = false,
                                detectedFaces = faces,
                                multipleFacesDialogVisible = true,
                                validationResult = validation
                            )
                        }
                    is FaceValidationResult.LowConfidence ->
                        _uiState.update {
                            it.copy(
                                isDetecting = false,
                                detectedFaces = faces,
                                selectedFace = faces.first(),
                                validationResult = validation
                            )
                        }
                    is FaceValidationResult.TooSmall ->
                        _uiState.update {
                            it.copy(
                                isDetecting = false,
                                validationResult = validation,
                                error = "Face is too small. Move closer to the camera."
                            )
                        }
                    is FaceValidationResult.NoFace ->
                        _uiState.update {
                            it.copy(
                                isDetecting = false,
                                validationResult = validation,
                                error = "No face detected. Use a clear, well-lit, front-facing photo."
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
                validationResult = if (face.confidence < MIN_CONFIDENCE)
                    FaceValidationResult.LowConfidence
                else
                    FaceValidationResult.Valid(face)
            )
        }
    }

    fun dismissMultipleFacesDialog() {
        _uiState.update { it.copy(multipleFacesDialogVisible = false) }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
}
