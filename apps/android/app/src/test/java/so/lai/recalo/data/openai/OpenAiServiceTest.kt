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

    // Characterization tests for the all-zero report. These document current
    // behavior, not the desired validation policy or a live-model reproduction.
    @Test
    fun `missing numeric fields are silently accepted as zero`() = runTest {
        enqueueNutrition(
            """
            {"title":"Meal","nutrients":[{"name":"Protein","unit":"g"}],
             "items":[{"name":"Rice","quantity":"1 bowl",
                       "nutrients":[{"name":"Protein","unit":"g"}]}]}
            """.trimIndent()
        )

        val data = service.analyzeNutrition(tempFile.absolutePath).getOrThrow()

        assertEquals(0.0, data.calories, 0.0)
        assertEquals(0.0, data.confidence, 0.0)
        assertEquals(0.0, data.nutrients.single().amount, 0.0)
        assertEquals(0.0, data.items.single().calories, 0.0)
        assertEquals(0.0, data.items.single().nutrients.single().amount, 0.0)
    }

    @Test
    fun `explicit all-zero response is accepted as success`() = runTest {
        enqueueNutrition(
            """
            {"title":"Meal","calories":0,"confidence":0,
             "nutrients":[{"name":"Protein","amount":0,"unit":"g"}],
             "items":[{"name":"Rice","quantity":"1 bowl","calories":0,
                       "nutrients":[{"name":"Protein","amount":0,"unit":"g"}]}]}
            """.trimIndent()
        )

        val data = service.analyzeNutrition(tempFile.absolutePath).getOrThrow()

        assertEquals(0.0, data.calories, 0.0)
        assertEquals(0.0, data.confidence, 0.0)
        assertTrue(data.nutrients.all { it.amount == 0.0 })
        assertEquals(0.0, data.items.single().calories, 0.0)
    }

    private fun enqueueNutrition(content: String) {
        val body = gson.toJson(
            mapOf(
                "output" to listOf(
                    mapOf(
                        "type" to "message",
                        "content" to listOf(mapOf("type" to "output_text", "text" to content))
                    )
                )
            )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
    }
}
