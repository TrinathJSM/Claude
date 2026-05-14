package com.facemorphapp.domain.usecase

import android.graphics.Bitmap
import com.facemorphapp.domain.model.FaceDetectionResult
import com.facemorphapp.domain.repository.FaceMorphRepository
import javax.inject.Inject

class DetectFaceUseCase @Inject constructor(
    private val repository: FaceMorphRepository
) {
    suspend operator fun invoke(bitmap: Bitmap): Result<List<FaceDetectionResult>> =
        repository.detectFaces(bitmap)
}
