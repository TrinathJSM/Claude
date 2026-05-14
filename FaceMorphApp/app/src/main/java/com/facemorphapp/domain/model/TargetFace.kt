package com.facemorphapp.domain.model

import android.net.Uri

data class TargetFace(
    val id: String,
    val name: String,
    val uri: Uri,
    val isAsset: Boolean = true
)
