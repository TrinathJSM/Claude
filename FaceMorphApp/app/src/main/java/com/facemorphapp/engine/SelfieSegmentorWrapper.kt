package com.facemorphapp.engine

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.google.mlkit.vision.segmentation.Segmentation
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Wraps the ML Kit Selfie Segmentation API.  Model is bundled in the APK via
 * manifestPlaceholders["mlkitSelfieSegmentationModuleType"] = "local_model".
 *
 * Returns a [FloatArray] of per-pixel confidence scores in [0,1], matching the
 * input bitmap dimensions.  Null is returned on failure (non-fatal; engine falls
 * back to no segmentation and processes the full image).
 */
@Singleton
class SelfieSegmentorWrapper @Inject constructor() {

    private val segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
    )

    suspend fun segment(bitmap: Bitmap): FloatArray? =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            segmenter.process(image)
                .addOnSuccessListener { result ->
                    val mask = result.buffer
                    val confidence = FloatArray(bitmap.width * bitmap.height)
                    mask.rewind()
                    var i = 0
                    while (mask.hasRemaining() && i < confidence.size) {
                        confidence[i++] = mask.float
                    }
                    cont.resume(confidence)
                }
                .addOnFailureListener { cont.resume(null) }
            cont.invokeOnCancellation { segmenter.close() }
        }
}
