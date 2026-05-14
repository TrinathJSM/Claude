package com.facemorphapp.data.repository

import android.content.Context
import android.net.Uri
import com.facemorphapp.domain.model.TargetFace
import com.facemorphapp.domain.repository.TargetFaceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class TargetFaceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : TargetFaceRepository {

    override fun getBundledTargets(): Flow<List<TargetFace>> = flow {
        val assetManager = context.assets
        val files = assetManager.list("targets") ?: emptyArray()
        val targets = files
            .filter { it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".gif") }
            .mapIndexed { index, fileName ->
                TargetFace(
                    id = "bundled_$index",
                    name = fileName.substringBeforeLast(".").replace("_", " "),
                    uri = Uri.parse("file:///android_asset/targets/$fileName"),
                    isAsset = true
                )
            }
        emit(targets)
    }

    override suspend fun getCustomTargets(): List<TargetFace> = emptyList()

    override suspend fun addCustomTarget(uri: Uri): TargetFace {
        val fileName = uri.lastPathSegment ?: "custom_${System.currentTimeMillis()}"
        return TargetFace(
            id = "custom_${System.currentTimeMillis()}",
            name = fileName.substringBeforeLast("."),
            uri = uri,
            isAsset = false
        )
    }

    override fun searchTargets(query: String, all: List<TargetFace>): List<TargetFace> {
        if (query.isBlank()) return all
        val lower = query.lowercase()
        return all.filter { it.name.lowercase().contains(lower) }
    }
}
