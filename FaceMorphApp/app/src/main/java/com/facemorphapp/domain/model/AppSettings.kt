package com.facemorphapp.domain.model

data class AppSettings(
    val gpuAccelerationEnabled: Boolean = true,
    val outputQuality: OutputQuality = OutputQuality.HIGH,
    val saveOriginal: Boolean = false,
    val privacyAcknowledged: Boolean = false
)

enum class OutputQuality { LOW, MEDIUM, HIGH }
