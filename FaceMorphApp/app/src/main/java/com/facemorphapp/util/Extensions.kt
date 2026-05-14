package com.facemorphapp.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes

fun Context.showToast(@StringRes resId: Int) =
    Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

fun Context.showToast(message: String) =
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

fun Bitmap.downscaleIfNeeded(maxEdge: Int = 1024): Bitmap {
    val largest = maxOf(width, height)
    if (largest <= maxEdge) return this
    val scale = maxEdge.toFloat() / largest
    return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
}

fun Long.formatDuration(): String {
    val seconds = this / 1_000
    val ms = this % 1_000
    return if (seconds > 0) "${seconds}s ${ms}ms" else "${ms}ms"
}
