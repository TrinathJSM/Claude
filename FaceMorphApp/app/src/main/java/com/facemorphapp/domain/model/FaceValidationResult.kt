package com.facemorphapp.domain.model

sealed class FaceValidationResult {
    data class Valid(val result: FaceDetectionResult) : FaceValidationResult()
    object NoFace : FaceValidationResult()
    data class MultipleFaces(val faces: List<FaceDetectionResult>) : FaceValidationResult()
    object LowConfidence : FaceValidationResult()
    object TooSmall : FaceValidationResult()
}
