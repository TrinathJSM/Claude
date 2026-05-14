package com.facemorphapp.domain.model

data class MorphProgress(
    val step: MorphStep,
    val stepProgress: Float,
    val overallProgress: Float,
    val frameIndex: Int = 0,
    val totalFrames: Int = 1
)

enum class MorphStep(val label: String, val weight: Float) {
    SEGMENTATION("Segmenting face region…", 0.15f),
    ALIGNMENT("Aligning faces…", 0.15f),
    WARP("Warping geometry…", 0.30f),
    BLEND("Blending colors…", 0.25f),
    POSTPROCESS("Smoothing result…", 0.15f)
}
