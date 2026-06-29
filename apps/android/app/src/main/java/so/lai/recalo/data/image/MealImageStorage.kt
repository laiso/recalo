package so.lai.recalo.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object MealImageStorage {
    const val DEFAULT_MAX_EDGE_PX = 1280
    const val DEFAULT_JPEG_QUALITY = 80

    fun saveCompressedJpeg(
        context: Context,
        uri: Uri,
        maxEdgePx: Int = DEFAULT_MAX_EDGE_PX,
        jpegQuality: Int = DEFAULT_JPEG_QUALITY,
        timestampMillis: Long = System.currentTimeMillis()
    ): File {
        require(maxEdgePx > 0) { "maxEdgePx must be positive" }
        require(jpegQuality in 0..100) { "jpegQuality must be between 0 and 100" }

        val orientation = readExifOrientation(context, uri)
        val bounds = readImageBounds(context, uri)
        val decodedBitmap = decodeBitmap(context, uri, bounds, maxEdgePx)
        var workingBitmap: Bitmap? = decodedBitmap

        return try {
            val scaledBitmap = scaleToMaxEdge(decodedBitmap, maxEdgePx)
            if (scaledBitmap !== decodedBitmap) {
                workingBitmap = scaledBitmap
                decodedBitmap.recycle()
            }

            val orientedBitmap = applyExifOrientation(scaledBitmap, orientation)
            if (orientedBitmap !== scaledBitmap) {
                workingBitmap = orientedBitmap
                scaledBitmap.recycle()
            }

            val destination = createDestinationFile(context, timestampMillis)
            FileOutputStream(destination).use { output ->
                if (!orientedBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, output)) {
                    destination.delete()
                    throw IOException("Failed to compress meal image")
                }
            }

            destination
        } finally {
            workingBitmap?.recycle()
        }
    }

    private fun createDestinationFile(context: Context, timestampMillis: Long): File {
        val fileName = "meal_${timestampMillis}.jpg"
        val directory = File(context.filesDir, "images")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Failed to create image directory: ${directory.absolutePath}")
        }
        return File(directory, fileName)
    }

    private fun readImageBounds(context: Context, uri: Uri): BitmapFactory.Options {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: throw IOException("Unable to open image")

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw IOException("Unable to decode image bounds")
        }

        return options
    }

    private fun decodeBitmap(
        context: Context,
        uri: Uri,
        bounds: BitmapFactory.Options,
        maxEdgePx: Int
    ): Bitmap {
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxEdgePx)
        }

        return context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: throw IOException("Unable to open image")
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxEdgePx: Int): Int {
        var inSampleSize = 1
        while (width / inSampleSize > maxEdgePx || height / inSampleSize > maxEdgePx) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun scaleToMaxEdge(bitmap: Bitmap, maxEdgePx: Int): Bitmap {
        val longestEdge = maxOf(bitmap.width, bitmap.height)
        if (longestEdge <= maxEdgePx) return bitmap

        val scale = maxEdgePx.toFloat() / longestEdge.toFloat()
        val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun readExifOrientation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(-90f)
            else -> return bitmap
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
