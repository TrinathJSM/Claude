package com.facemorphapp.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.facemorphapp.data.local.db.MorphResultDao
import com.facemorphapp.data.local.db.MorphResultEntity
import com.facemorphapp.domain.model.FaceDetectionResult
import com.facemorphapp.domain.model.MorphMode
import com.facemorphapp.domain.model.MorphProgress
import com.facemorphapp.domain.model.MorphResult
import com.facemorphapp.domain.repository.FaceMorphRepository
import com.facemorphapp.engine.FaceDetectorWrapper
import com.facemorphapp.engine.FaceMorphEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import kotlin.math.max

class FaceMorphRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: MorphResultDao,
    private val faceDetector: FaceDetectorWrapper,
    private val engine: FaceMorphEngine
) : FaceMorphRepository {

    override suspend fun detectFaces(bitmap: Bitmap): Result<List<FaceDetectionResult>> =
        faceDetector.detectFaces(bitmap)

    override fun morphFaces(
        sourceBitmap: Bitmap,
        targetBitmap: Bitmap,
        sourceFace: FaceDetectionResult,
        targetFace: FaceDetectionResult,
        useGpu: Boolean
    ): Flow<MorphProgress> = engine.morph(sourceBitmap, targetBitmap, sourceFace, targetFace)
        .map { ep ->
            MorphProgress(
                step = ep.step,
                stepProgress = ep.stepProgress,
                overallProgress = ep.overallProgress,
                frameIndex = ep.frameIndex,
                totalFrames = ep.totalFrames,
                result = ep.result
            )
        }

    override suspend fun saveMorphResult(result: MorphResult): Long =
        dao.insertResult(result.toEntity())

    override fun getRecentResults(limit: Int): Flow<List<MorphResult>> =
        dao.getRecentResults(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun deleteResult(id: Long) = dao.deleteById(id)

    override suspend fun saveResultToMediaStore(bitmap: Bitmap): Uri =
        withContext(Dispatchers.IO) {
            val filename = "FaceMorph_${System.currentTimeMillis()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FaceMorph")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Failed to create MediaStore entry")
            resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        }

    override suspend fun loadBitmapFromUri(uri: Uri): Result<Bitmap> =
        withContext(Dispatchers.IO) {
            runCatching {
                val stream: InputStream = openInputStream(uri)
                    ?: throw IOException("Cannot open stream for URI: $uri")

                // Sample down large images to stay within the 1024-edge budget
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                stream.use { BitmapFactory.decodeStream(it, null, opts) }

                val maxEdge = max(opts.outWidth, opts.outHeight)
                val sampleSize = if (maxEdge > 1024) maxEdge / 1024 else 1

                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }

                val stream2: InputStream = openInputStream(uri)
                    ?: throw IOException("Cannot open stream for URI: $uri")
                stream2.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
                    ?: throw IOException("BitmapFactory returned null for URI: $uri")
            }
        }

    /**
     * Opens an InputStream for [uri], handling both regular content/file URIs and
     * bundled asset URIs in the form file:///android_asset/<path>.
     */
    private fun openInputStream(uri: Uri): InputStream? {
        val path = uri.path
        if (uri.scheme == "file" && path != null && path.startsWith("/android_asset/")) {
            val assetPath = path.removePrefix("/android_asset/")
            return context.assets.open(assetPath)
        }
        return context.contentResolver.openInputStream(uri)
    }

    private fun MorphResult.toEntity() = MorphResultEntity(
        id = id, sourceUri = sourceUri, targetUri = targetUri,
        resultUri = resultUri, createdAt = createdAt, durationMs = durationMs,
        landmarkCount = landmarkCount, morphMode = morphMode.name
    )

    private fun MorphResultEntity.toDomain() = MorphResult(
        id = id, sourceUri = sourceUri, targetUri = targetUri,
        resultUri = resultUri, createdAt = createdAt, durationMs = durationMs,
        landmarkCount = landmarkCount,
        morphMode = runCatching { MorphMode.valueOf(morphMode) }.getOrDefault(MorphMode.CPU)
    )
}
