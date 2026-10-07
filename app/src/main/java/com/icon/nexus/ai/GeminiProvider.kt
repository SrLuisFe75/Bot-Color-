package com.icon.nexus.ai

import com.icon.nexus.data.SettingsRepository
import com.icon.nexus.domain.Author
import com.icon.nexus.domain.Message
import com.icon.nexus.settings.ModelProviderId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Generative Language `models/{model}:streamGenerateContent` client.
 * The key is read from [SettingsRepository] on each call and is never a
 * constant. A blank key emits [AIEvent.Failed] and does not open a connection.
 * [AIManager] keeps this provider unselected while demo mode is on.
 */
class GeminiProvider(
    private val settings: SettingsRepository,
    private val client: OkHttpClient = defaultClient(),
    private val model: String = DEFAULT_MODEL,
) : AIProvider {
    override val id: String = ModelProviderId.GEMINI.wireName

    override fun streamReply(request: AIRequest): Flow<AIEvent> = flow {
        val key = settings.get().apiKey.trim()
        if (key.isEmpty()) {
            emit(AIEvent.Failed("Gemini API key is blank."))
            return@flow
        }
        val call = client.newCall(buildRequest(request, key))
        currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyText = response.body?.string().orEmpty().replace(key, "[redacted]")
                    emit(AIEvent.Failed("Gemini HTTP ${response.code}: ${bodyText.take(300)}"))
                    return@use
                }
                val source = response.body?.source()
                if (source == null) {
                    emit(AIEvent.Failed("Gemini response had no body."))
                    return@use
                }
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    val payload = ssePayload(line) ?: continue
                    val text = runCatching { textFromChunk(payload) }.getOrDefault("")
                    if (text.isNotEmpty()) emit(AIEvent.Token(text))
                }
                emit(AIEvent.Completed(request.turnId))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (io: IOException) {
            val message = (io.message ?: io.javaClass.simpleName).replace(key, "[redacted]")
            emit(AIEvent.Failed("Gemini connection failed: $message"))
        }
    }.flowOn(Dispatchers.IO)

    private fun buildRequest(request: AIRequest, key: String): Request {
        val payload = json.encodeToString(GeminiRequest.serializer(), requestBody(request))
        return Request.Builder()
            .url("$ENDPOINT_ROOT/models/$model:streamGenerateContent?alt=sse")
            .header("x-goog-api-key", key)
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody(JSON_MEDIA))
            .build()
    }

    private fun requestBody(request: AIRequest): GeminiRequest {
        val contents = buildList {
            request.history.filter { it.author != Author.System }.forEach { add(it.toContent()) }
            val lastText = lastOrNull()?.parts?.lastOrNull()?.text
            if (request.userText.isNotBlank() && request.userText != lastText) {
                add(GeminiContent(role = "user", parts = listOf(GeminiPart(request.userText))))
            }
            if (isEmpty()) {
                add(GeminiContent(role = "user", parts = listOf(GeminiPart(request.userText))))
            }
        }
        val personality = request.persona.personality.trim()
        val system = if (personality.isEmpty()) {
            null
        } else {
            GeminiContent(parts = listOf(GeminiPart(personality)))
        }
        return GeminiRequest(contents = contents, systemInstruction = system)
    }

    private fun Message.toContent(): GeminiContent {
        val role = when (author) {
            Author.User -> "user"
            Author.Assistant -> "model"
            Author.System -> "user"
        }
        return GeminiContent(role = role, parts = listOf(GeminiPart(text)))
    }

    private fun textFromChunk(payload: String): String {
        val chunk = json.decodeFromString(GeminiStreamChunk.serializer(), payload)
        return chunk.candidates
            .asSequence()
            .mapNotNull { it.content }
            .flatMap { it.parts.asSequence() }
            .joinToString(separator = "") { it.text }
    }

    private companion object {
        const val ENDPOINT_ROOT = "https://generativelanguage.googleapis.com/v1beta"
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        fun ssePayload(line: String): String? {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith(":")) return null
            val data = when {
                trimmed.startsWith("data:") -> trimmed.removePrefix("data:").trim()
                trimmed.startsWith("{") -> trimmed
                else -> return null
            }
            if (data.isEmpty() || data == "[DONE]") return null
            return data
        }
    }
}

@Serializable
private data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
)

@Serializable
private data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

@Serializable
private data class GeminiPart(
    val text: String,
)

@Serializable
private data class GeminiStreamChunk(
    val candidates: List<GeminiCandidate> = emptyList(),
)

@Serializable
private data class GeminiCandidate(
    val content: GeminiContent? = null,
)
