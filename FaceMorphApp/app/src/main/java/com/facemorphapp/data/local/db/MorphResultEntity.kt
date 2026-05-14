package com.facemorphapp.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "morph_results")
data class MorphResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceUri: String,
    val targetUri: String,
    val resultUri: String,
    val createdAt: Long,
    val durationMs: Long,
    val landmarkCount: Int = 0,
    val morphMode: String = "CPU"
)
