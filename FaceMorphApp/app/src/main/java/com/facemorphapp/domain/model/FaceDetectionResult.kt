package com.facemorphapp.domain.model

import android.graphics.PointF
import android.graphics.RectF

data class FaceDetectionResult(
    val boundingBox: RectF,
    val landmarks: List<PointF>,
    val faceOutline: List<PointF>,
    val confidence: Float,
    val leftEyeCenter: PointF,
    val rightEyeCenter: PointF,
    val noseTip: PointF,
    val mouthCenter: PointF
)
