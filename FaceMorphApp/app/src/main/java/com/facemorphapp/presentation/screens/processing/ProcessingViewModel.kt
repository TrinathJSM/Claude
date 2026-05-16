package com.facemorphapp.presentation.screens.processing

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facemorphapp.domain.model.FaceDetectionResult
import com.facemorphapp.domain.model.MorphMode
import com.facemorphapp.domain.model.MorphResult
import com.facemorphapp.domain.model.MorphStep
import com.facemorphapp.domain.repository.FaceMorphRepository
import com.facemorphapp.domain.usecase.MorphFaceUseCase
import com.facemorphapp.engine.FaceMorphEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProcessingUiState(
    val progress: Float = 0f,
    val stepLabel: String = "",
    val currentStep: MorphStep? = null,
    val isComplete: Boolean = false,
    val resultUri: Uri? = null,
    val error: String? = null,
    val durationMs: Long = 0,
    val landmarkCount: Int = 0,
    val morphMode: MorphMode = MorphMode.CPU
)

@HiltViewModel
class ProcessingViewModel @Inject constructor(
    private val morphFace: MorphFaceUseCase,
    private val repository: FaceMorphRepository,
    private val engine: FaceMorphEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProcessingUiState())
    val uiState: StateFlow<ProcessingUiState> = _uiState.asStateFlow()

    private var morphJob: Job? = null
    private var resultBitmap: Bitmap? = null

    fun startMorph(
        sourceUri: Uri,
        targetUri: Uri,
        sourceFace: FaceDetectionResult,
        targetFace: FaceDetectionResult
    ) {
        morphJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            _uiState.update { it.copy(morphMode = engine.morphMode) }

            val srcResult = repository.loadBitmapFromUri(sourceUri)
            if (srcResult.isFailure) {
                _uiState.update { it.copy(error = srcResult.exceptionOrNull()?.message ?: "Failed to load source image") }
                return@launch
            }
            val tgtResult = repository.loadBitmapFromUri(targetUri)
            if (tgtResult.isFailure) {
                _uiState.update { it.copy(error = tgtResult.exceptionOrNull()?.message ?: "Failed to load target image") }
                return@launch
            }

            val srcBitmap = srcResult.getOrThrow()
            val tgtBitmap = tgtResult.getOrThrow()

            // Always detect the target face from the actual target bitmap so we use
            // real contour landmarks, not the sourceFace fallback from NavStore.
            val effectiveTargetFace = repository.detectFaces(tgtBitmap)
                .getOrNull()
                ?.firstOrNull()
                ?: targetFace

            try {
                morphFace(srcBitmap, tgtBitmap, sourceFace, effectiveTargetFace)
                    .collect { progress ->
                        // Capture result bitmap from the final emission
                        progress.result?.let { resultBitmap = it }
                        _uiState.update {
                            it.copy(
                                progress = progress.overallProgress,
                                stepLabel = progress.step.label,
                                currentStep = progress.step
                            )
                        }
                    }

                val elapsed = System.currentTimeMillis() - startTime
                val finalBitmap = resultBitmap ?: tgtBitmap

                val savedUri = repository.saveResultToMediaStore(finalBitmap)
                repository.saveMorphResult(
                    MorphResult(
                        sourceUri = sourceUri.toString(),
                        targetUri = targetUri.toString(),
                        resultUri = savedUri.toString(),
                        createdAt = System.currentTimeMillis(),
                        durationMs = elapsed,
                        landmarkCount = effectiveTargetFace.landmarks.size,
                        morphMode = engine.morphMode
                    )
                )
                _uiState.update {
                    it.copy(
                        isComplete = true,
                        resultUri = savedUri,
                        durationMs = elapsed,
                        landmarkCount = effectiveTargetFace.landmarks.size,
                        progress = 1f
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Morphing failed") }
            }
        }
    }

    fun cancel() {
        morphJob?.cancel()
        resultBitmap?.recycle()
        resultBitmap = null
    }

    override fun onCleared() {
        super.onCleared()
        cancel()
    }
}
