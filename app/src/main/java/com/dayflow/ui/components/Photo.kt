package com.dayflow.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/** Loads a stored photo off the main thread, downsampled to about [maxSide] pixels. */
@Composable
fun rememberPhoto(file: File?, maxSide: Int = 1200): ImageBitmap? {
    var bitmap by remember(file?.path, maxSide) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file?.path, maxSide) { bitmap = decodePhoto(file, maxSide) }
    return bitmap
}

private suspend fun decodePhoto(file: File?, maxSide: Int): ImageBitmap? {
    if (file == null || !file.exists()) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }.getOrNull()
    }
}

@Composable
fun PhotoImage(file: File, contentDescription: String?, modifier: Modifier = Modifier, maxSide: Int = 1200) {
    val bitmap = rememberPhoto(file, maxSide)
    if (bitmap != null) {
        Image(bitmap, contentDescription, modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh))
    }
}
