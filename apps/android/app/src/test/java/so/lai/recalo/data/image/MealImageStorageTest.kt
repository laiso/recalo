package so.lai.recalo.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MealImageStorageTest {
    private lateinit var context: Context
    private lateinit var sourceFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sourceFile = File(context.cacheDir, "source_meal.png")
        val bitmap = Bitmap.createBitmap(2000, 1000, Bitmap.Config.ARGB_8888)
        FileOutputStream(sourceFile).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        bitmap.recycle()
    }

    @After
    fun tearDown() {
        sourceFile.delete()
        File(context.filesDir, "images").deleteRecursively()
    }

    @Test
    fun `saveCompressedJpeg stores resized jpeg in meal images directory`() {
        val savedFile = MealImageStorage.saveCompressedJpeg(
            context = context,
            uri = Uri.fromFile(sourceFile),
            timestampMillis = 12345L
        )

        val savedBitmap = BitmapFactory.decodeFile(savedFile.absolutePath)

        assertEquals(File(context.filesDir, "images").absolutePath, savedFile.parentFile?.absolutePath)
        assertEquals("meal_12345.jpg", savedFile.name)
        assertNotEquals(sourceFile.absolutePath, savedFile.absolutePath)
        assertTrue(maxOf(savedBitmap.width, savedBitmap.height) <= MealImageStorage.DEFAULT_MAX_EDGE_PX)
        assertEquals(Bitmap.CompressFormat.JPEG, savedFile.readCompressionFormat())

        savedBitmap.recycle()
    }

    @Test
    fun `saveCompressedJpeg fails without creating output when image cannot be decoded`() {
        val missingFile = File(context.cacheDir, "missing_meal.jpg")
        missingFile.delete()

        try {
            MealImageStorage.saveCompressedJpeg(
                context = context,
                uri = Uri.fromFile(missingFile),
                timestampMillis = 67890L
            )
            throw AssertionError("Expected image loading to fail")
        } catch (e: IOException) {
            assertTrue(e.message?.isNotBlank() == true)
        }

        assertTrue(!File(context.filesDir, "images/meal_67890.jpg").exists())
    }

    private fun File.readCompressionFormat(): Bitmap.CompressFormat {
        inputStream().use { input ->
            val header = ByteArray(2)
            input.read(header)
            if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()) {
                return Bitmap.CompressFormat.JPEG
            }
        }
        throw AssertionError("File is not a JPEG")
    }
}
