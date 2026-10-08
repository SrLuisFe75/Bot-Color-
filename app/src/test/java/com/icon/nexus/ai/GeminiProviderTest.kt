package com.icon.nexus.ai

import com.icon.nexus.data.AppSettings
import com.icon.nexus.data.SettingsRepository
import com.icon.nexus.domain.AssistantPersona
import com.icon.nexus.domain.Author
import com.icon.nexus.domain.Message
import com.icon.nexus.settings.SettingsDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class GeminiProviderTest {
    @Test
    fun blankKeyFailsWithoutOpeningAConnection() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val settings = FakeSettingsRepository(AppSettings.defaults().copy(apiKey = "   "))
            val provider = provider(settings, server)
            val events = provider.streamReply(sampleRequest("Hello")).toList()

            assertEquals(0, server.requestCount)
            assertEquals(GeminiMessages.BLANK_KEY, (events.single() as AIEvent.Failed).message)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun twoChunksAppendInOrderOnThePublicContract() = runBlocking {
        val server = MockWebServer()
        server.enqueue(sse("Hello ", "there."))
        server.start()
        try {
            val settings = FakeSettingsRepository(
                AppSettings.defaults().copy(
                    apiKey = "test-key",
                    geminiModel = "gemini-2.5-flash",
                ),
            )
            val events = provider(settings, server).streamReply(
                sampleRequest(
                    text = "Hello",
                    history = listOf(
                        Message("1", Author.User, "Earlier question", 1L),
                        Message("2", Author.Assistant, "Earlier answer", 1L),
                    ),
                ),
            ).toList()

            val recorded = server.takeRequest()
            val url = recorded.requestUrl!!
            assertEquals("POST", recorded.method)
            assertTrue(url.encodedPath.endsWith("/models/gemini-2.5-flash:streamGenerateContent"))
            assertEquals("sse", url.queryParameter("alt"))
            assertEquals("test-key", recorded.getHeader("x-goog-api-key"))
            assertFalse(url.toString().contains("test-key"))
            assertNull(url.queryParameter("key"))
            val body = recorded.body.readUtf8()
            assertFalse(body.contains("test-key"))
            assertTrue(body.contains("Your name is ICON"))
            assertTrue(body.contains("Reply in natural spoken sentences."))
            assertTrue(body.contains("Do not use Markdown."))
            assertTrue(body.contains("Do not use symbol lists."))
            assertTrue(body.contains("Earlier question"))
            assertTrue(body.contains("Earlier answer"))
            assertTrue(body.contains("\"role\":\"model\""))
            assertTrue(body.contains("Hello"))

            assertEquals(
                listOf(
                    AIEvent.Token("Hello "),
                    AIEvent.Token("there."),
                    AIEvent.Completed(4L),
                ),
                events,
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun modelFieldOverridesThePath() = runBlocking {
        val server = MockWebServer()
        server.enqueue(sse("Ok."))
        server.start()
        try {
            val settings = FakeSettingsRepository(
                AppSettings.defaults().copy(apiKey = "test-key", geminiModel = "gemini-2.0-flash"),
            )
            provider(settings, server).streamReply(sampleRequest("Hi")).toList()
            val path = server.takeRequest().requestUrl!!.encodedPath
            assertTrue(path.endsWith("/models/gemini-2.0-flash:streamGenerateContent"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun unauthorizedAndForbiddenRejectTheKey() = runBlocking {
        assertEquals(GeminiMessages.INVALID_KEY, httpFailure(401))
        assertEquals(GeminiMessages.INVALID_KEY, httpFailure(403))
    }

    @Test
    fun rateLimitUsesTheRateLimitMessage() = runBlocking {
        assertEquals(GeminiMessages.RATE_LIMIT, httpFailure(429))
    }

    @Test
    fun emptyStreamFailsWithoutCompleting() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        server.start()
        try {
            val events = provider(keyedSettings(), server).streamReply(sampleRequest("Hi")).toList()
            assertEquals(GeminiMessages.EMPTY, (events.single() as AIEvent.Failed).message)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun disconnectMapsToOffline() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.start()
        try {
            val events = provider(keyedSettings(), server).streamReply(sampleRequest("Hi")).toList()
            assertEquals(GeminiMessages.OFFLINE, (events.single() as AIEvent.Failed).message)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun stalledConnectionMapsToTimeout() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        server.start()
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .writeTimeout(1, TimeUnit.SECONDS)
                .callTimeout(1, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build()
            val events = provider(keyedSettings(), server, client).streamReply(sampleRequest("Hi")).toList()
            assertEquals(GeminiMessages.TIMEOUT, (events.single() as AIEvent.Failed).message)
        } finally {
            server.shutdown()
        }
    }

    private suspend fun httpFailure(code: Int): String {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(code).setBody("""{"error":"secret-test-key"}"""))
        server.start()
        try {
            val events = provider(keyedSettings(), server).streamReply(sampleRequest("Hi")).toList()
            val message = (events.single() as AIEvent.Failed).message
            assertFalse(message.contains("secret-test-key"))
            assertFalse(message.contains("HTTP"))
            return message
        } finally {
            server.shutdown()
        }
    }

    private fun provider(
        settings: SettingsRepository,
        server: MockWebServer,
        client: OkHttpClient = testClient(),
    ): GeminiProvider {
        return GeminiProvider(
            settings = settings,
            client = client,
            endpointRoot = server.url("v1beta").toString(),
        )
    }

    private fun keyedSettings(): SettingsRepository {
        return FakeSettingsRepository(AppSettings.defaults().copy(apiKey = "secret-test-key"))
    }

    @Test
    fun memoriesStayInTheSystemInstruction() = runBlocking {
        val server = MockWebServer()
        server.enqueue(sse("Ok."))
        server.start()
        try {
            provider(keyedSettings(), server).streamReply(
                sampleRequest("Hello", memories = listOf("I like tea")),
            ).toList()
            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains("The user asked you to keep these facts: I like tea."))
            assertTrue(body.contains("Reply in natural spoken sentences."))
            assertTrue(body.contains("Do not use Markdown."))
            assertEquals(1, body.split("I like tea").size - 1)
            assertTrue(body.contains("Hello"))
        } finally {
            server.shutdown()
        }
    }

    private fun sampleRequest(
        text: String,
        history: List<Message> = emptyList(),
        memories: List<String> = emptyList(),
    ): AIRequest {
        return AIRequest(
            turnId = 4L,
            persona = AssistantPersona(
                name = SettingsDefaults.ASSISTANT_NAME,
                personality = SettingsDefaults.PERSONALITY,
            ),
            history = history,
            userText = text,
            memories = memories,
        )
    }

    private fun sse(vararg parts: String): MockResponse {
        val body = parts.joinToString(separator = "\n") { text ->
            "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"$text\"}]}}]}\n"
        }
        return MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "text/event-stream")
            .setBody(body)
    }

    private fun testClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
}

private class FakeSettingsRepository(
    initial: AppSettings,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<AppSettings> = state.asStateFlow()

    override suspend fun get(): AppSettings = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}
