package com.facemorphapp.domain.repository

import android.graphics.Bitmap
import android.net.Uri
import com.facemorphapp.domain.model.FaceDetectionResult
import com.facemorphapp.domain.model.MorphProgress
import com.facemorphapp.domain.model.MorphResult
import kotlinx.coroutines.flow.Flow

interface FaceMorphRepository {
    suspend fun detectFaces(bitmap: Bitmap): Result<List<FaceDetectionResult>>
    fun morphFaces(
        sourceBitmap: Bitmap,
        targetBitmap: Bitmap,
        sourceFace: FaceDetectionResult,
        targetFace: FaceDetectionResult,
        useGpu: Boolean
    ): Flow<MorphProgress>
    suspend fun saveMorphResult(result: MorphResult): Long
    fun getRecentResults(limit: Int = 20): Flow<List<MorphResult>>
    suspend fun deleteResult(id: Long)
    suspend fun saveResultToMediaStore(bitmap: Bitmap): Uri
    suspend fun loadBitmapFromUri(uri: Uri): Result<Bitmap>
}
