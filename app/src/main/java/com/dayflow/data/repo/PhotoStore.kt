package com.dayflow.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * Photos attached to reflections. Picked images are copied into app storage (scaled down to
 * [MAX_SIDE] and saved upright as JPEG), so they keep working even if the original is deleted.
 */
class PhotoStore(context: Context) {
    private val resolver = context.contentResolver
    private val dir = File(context.filesDir, "reflection_photos")

    fun file(name: String): File = File(dir, name)

    suspend fun import(uri: Uri, prefix: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decode(uri) ?: return@runCatching null
            dir.mkdirs()
            val name = "$prefix-${System.currentTimeMillis()}.jpg"
            File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bitmap.recycle()
            name
        }.getOrNull()
    }

    fun delete(name: String?) {
        if (name != null) File(dir, name).delete()
    }

    fun deleteAll() {
        dir.deleteRecursively()
    }

    private fun decode(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies the photo's rotation, so portrait shots aren't saved sideways.
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }

    companion object {
        const val MAX_SIDE = 1600
    }
}
