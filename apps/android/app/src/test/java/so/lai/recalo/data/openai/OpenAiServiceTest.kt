package so.lai.recalo.data.openai

import com.google.gson.Gson
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpenAiServiceTest {
    private lateinit var server: MockWebServer
    private lateinit var service: OpenAiService
    private val gson = Gson()
    private lateinit var tempFile: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = OpenAiService(
            apiKey = "fake-key",
            baseUrl = server.url("/v1/responses").toString()
        )
        tempFile = File.createTempFile("test_meal", ".jpg")
        Files.write(tempFile.toPath(), ByteArray(10))
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempFile.delete()
    }

    @Test
    fun `analyzeNutrition returns parsed result on success`() = runTest {
        val mockResponseJson = """
        {
          "id": "resp_123",
          "output": [
            {
              "type": "message",
              "content": [
                {
                  "type": "output_text",
                  "text": "{\"title\": \"Grilled Salmon\", \"calories\": 450, \"confidence\": 0.9, \"nutrients\": [{\"name\": \"Protein\", \"amount\": 30, \"unit\": \"g\"}], \"items\": [{\"name\": \"Salmon\", \"quantity\": \"1 fillet\", \"calories\": 300, \"nutrients\": [{\"name\": \"Protein\", \"amount\": 25, \"unit\": \"g\"}]}]}"
                }
              ]
            }
          ]
        }
        """.trimIndent()

        server.enqueue(MockResponse().setBody(mockResponseJson).setResponseCode(200))

        val result = service.analyzeNutrition(tempFile.absolutePath)

        assertTrue(result.isSuccess)
        val data = result.getOrNull()
        assertNotNull(data)
        assertEquals("Grilled Salmon", data?.title)
        assertEquals(450.0, data?.calories!!, 0.01)
        assertEquals(1, data?.items?.size)
        assertEquals("Salmon", data?.items?.first()?.name)
    }

    @Test
    fun `analyzeNutrition returns failure on API error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val result = service.analyzeNutrition(tempFile.absolutePath)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("500") == true)
    }
}
