package com.facemorphapp.presentation.screens.processing

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facemorphapp.domain.model.FaceDetectionResult
import com.facemorphapp.domain.model.MorphMode
import com.facemorphapp.domain.model.MorphProgress
import com.facemorphapp.domain.model.MorphResult
import com.facemorphapp.domain.model.MorphStep
import com.facemorphapp.domain.repository.FaceMorphRepository
import com.facemorphapp.domain.repository.SettingsRepository
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
    private val settings: SettingsRepository,
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

            repository.loadBitmapFromUri(sourceUri)
                .flatMap { src -> repository.loadBitmapFromUri(targetUri).map { tgt -> src to tgt } }
                .onSuccess { (srcBitmap, tgtBitmap) ->

                    morphFace(srcBitmap, tgtBitmap, sourceFace, targetFace)
                        .collect { progress ->
                            _uiState.update {
                                it.copy(
                                    progress = progress.overallProgress,
                                    stepLabel = progress.step.label
                                )
                            }
                        }

                    // Extract the result bitmap from the engine's final emission
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
                            landmarkCount = sourceFace.landmarks.size,
                            morphMode = engine.morphMode
                        )
                    )
                    _uiState.update {
                        it.copy(
                            isComplete = true,
                            resultUri = savedUri,
                            durationMs = elapsed,
                            landmarkCount = sourceFace.landmarks.size,
                            progress = 1f
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "Unknown error") }
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

private fun <A, B> Result<A>.flatMap(transform: (A) -> Result<B>): Result<B> =
    fold(onSuccess = { transform(it) }, onFailure = { Result.failure(it) })

private fun <A, B> Result<A>.map(transform: (A) -> B): Result<B> =
    fold(onSuccess = { Result.success(transform(it)) }, onFailure = { Result.failure(it) })
