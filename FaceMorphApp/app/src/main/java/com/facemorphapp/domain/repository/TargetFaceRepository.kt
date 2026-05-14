package com.facemorphapp.domain.repository

import android.net.Uri
import com.facemorphapp.domain.model.TargetFace
import kotlinx.coroutines.flow.Flow

interface TargetFaceRepository {
    fun getBundledTargets(): Flow<List<TargetFace>>
    suspend fun getCustomTargets(): List<TargetFace>
    suspend fun addCustomTarget(uri: Uri): TargetFace
    fun searchTargets(query: String, all: List<TargetFace>): List<TargetFace>
}
