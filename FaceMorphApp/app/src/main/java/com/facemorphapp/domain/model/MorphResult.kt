package com.facemorphapp.domain.model

data class MorphResult(
    val id: Long = 0,
    val sourceUri: String,
    val targetUri: String,
    val resultUri: String,
    val createdAt: Long,
    val durationMs: Long,
    val landmarkCount: Int = 0,
    val morphMode: MorphMode = MorphMode.CPU
)

enum class MorphMode { CPU, GPU }
