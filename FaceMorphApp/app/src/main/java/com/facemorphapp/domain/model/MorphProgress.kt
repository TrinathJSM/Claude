package com.facemorphapp.domain.model

import android.graphics.Bitmap

data class MorphProgress(
    val step: MorphStep,
    val stepProgress: Float,
    val overallProgress: Float,
    val frameIndex: Int = 0,
    val totalFrames: Int = 1,
    val result: Bitmap? = null
)

enum class MorphStep(val label: String, val weight: Float) {
    SEGMENTATION("Segmenting face region…",  0.10f),
    ALIGNMENT("Aligning faces…",             0.10f),
    WARP("Warping geometry…",                0.30f),
    BLEND("Poisson blending…",               0.25f),
    COLOR_CORRECTION("Matching skin tone…",  0.15f),
    POSTPROCESS("Smoothing edges…",          0.10f)
}
