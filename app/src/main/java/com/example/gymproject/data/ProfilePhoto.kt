package com.example.gymproject.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.math.roundToInt

// Keep one small, portable image in the profile, including in manual backups.
fun readProfilePhoto(context: Context, uri: Uri): String {
    val resolver = context.contentResolver
    val bitmap = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            val scale = (512.0 / maxOf(info.size.width, info.size.height)).coerceAtMost(1.0)
            decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1), (info.size.height * scale).roundToInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        require(options.outWidth > 0 && options.outHeight > 0)
        options.inJustDecodeBounds = false
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1024) options.inSampleSize *= 2
        val source = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: error("Foto tidak terbaca")
        val orientation = runCatching { resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } }.getOrNull()
        val matrix = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(-90f); postScale(-1f, 1f) }
                8 -> setRotate(-90f)
            }
        }
        val oriented = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (oriented !== source) source.recycle()
        val scale = (512.0 / maxOf(oriented.width, oriented.height)).coerceAtMost(1.0)
        Bitmap.createScaledBitmap(oriented, (oriented.width * scale).roundToInt().coerceAtLeast(1),
            (oriented.height * scale).roundToInt().coerceAtLeast(1), true).also { if (it !== oriented) oriented.recycle() }
    }
    return try {
        val output = ByteArrayOutputStream()
        @Suppress("DEPRECATION")
        val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        check(bitmap.compress(format, 85, output))
        Base64.getEncoder().encodeToString(output.toByteArray()).also { require(it.length <= 500_000) }
    } finally { bitmap.recycle() }
}

fun decodeProfilePhoto(encoded: String): Bitmap? = runCatching {
    require(encoded.length in 1..500_000)
    val bytes = Base64.getDecoder().decode(encoded)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth in 1..512 && bounds.outHeight in 1..512)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()
