package so.lai.recalo.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
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

        val imageBytes = readImageBytes(context, uri)
        val orientation = readExifOrientation(imageBytes)
        val bounds = readImageBounds(imageBytes)
        val decodedBitmap = decodeBitmap(imageBytes, bounds, maxEdgePx)
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

    private fun readImageBytes(context: Context, uri: Uri): ByteArray {
        val resolver = context.contentResolver

        runCatching {
            resolver.openInputStream(uri)?.use { input ->
                return input.readBytes()
            }
        }

        runCatching {
            resolver.openTypedAssetFileDescriptor(uri, "image/*", null)?.use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { input ->
                    input.channel.position(descriptor.startOffset)
                    return input.readDescriptorBytes(descriptor.declaredLength)
                }
            }
        }

        runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { input ->
                    input.channel.position(descriptor.startOffset)
                    return input.readDescriptorBytes(descriptor.declaredLength)
                }
            }
        }

        throw IOException("Unable to open image")
    }

    private fun FileInputStream.readDescriptorBytes(declaredLength: Long): ByteArray {
        if (declaredLength < 0) return readBytes()

        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var remaining = declaredLength
        while (remaining > 0) {
            val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read == -1) break
            output.write(buffer, 0, read)
            remaining -= read
        }
        return output.toByteArray()
    }

    private fun readImageBounds(imageBytes: ByteArray): BitmapFactory.Options {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw IOException("Unable to decode image bounds")
        }

        return options
    }

    private fun decodeBitmap(
        imageBytes: ByteArray,
        bounds: BitmapFactory.Options,
        maxEdgePx: Int
    ): Bitmap {
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxEdgePx)
        }

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
            ?: throw IOException("Unable to decode image")
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

    private fun readExifOrientation(imageBytes: ByteArray): Int {
        return try {
            ByteArrayInputStream(imageBytes).use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
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
