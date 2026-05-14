package com.facemorphapp.domain.usecase

import android.graphics.Bitmap
import com.facemorphapp.domain.model.FaceDetectionResult
import com.facemorphapp.domain.model.MorphProgress
import com.facemorphapp.domain.repository.FaceMorphRepository
import com.facemorphapp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class MorphFaceUseCase @Inject constructor(
    private val morphRepository: FaceMorphRepository,
    private val settingsRepository: SettingsRepository
) {
    operator fun invoke(
        sourceBitmap: Bitmap,
        targetBitmap: Bitmap,
        sourceFace: FaceDetectionResult,
        targetFace: FaceDetectionResult
    ): Flow<MorphProgress> {
        return morphRepository.morphFaces(
            sourceBitmap = sourceBitmap,
            targetBitmap = targetBitmap,
            sourceFace = sourceFace,
            targetFace = targetFace,
            useGpu = true
        )
    }
}
