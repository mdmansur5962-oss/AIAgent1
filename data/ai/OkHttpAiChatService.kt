package com.aiagent.assistant.data.ai

import com.aiagent.assistant.domain.ai.AiChatService
import com.aiagent.assistant.domain.ai.AiErrorKind
import com.aiagent.assistant.domain.ai.AiException
import com.aiagent.assistant.domain.ai.AiProvider
import com.aiagent.assistant.domain.ai.AiProviderRepository
import com.aiagent.assistant.domain.ai.AiRole
import com.aiagent.assistant.domain.ai.AiStreamEvent
import com.aiagent.assistant.domain.ai.AiTurn
import com.aiagent.assistant.domain.ai.ApiStyle
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

private val STREAM_MEDIA = "application/json; charset=utf-8".toMediaType()
private const val ANTHROPIC_API_VERSION = "2023-06-01"

private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
}

/**
 * Streaming AI client: OpenAI-সামঞ্জস্যপূর্ণ (SSE) ও Anthropic দুটোই। ডিফল্ট ব্যর্থ হলে ব্যাকআপে যায়।
 * ছবিসহ (vision) অনুরোধও বানাতে পারে। API key কোথাও log হয় না।
 */
@Singleton
class OkHttpAiChatService @Inject constructor(
    client: OkHttpClient,
    private val json: Json,
    private val repository: AiProviderRepository
) : AiChatService {

    /** উত্তর আসতে সময় লাগতে পারে, তাই read timeout বেশি */
    private val streamClient: OkHttpClient = client.newBuilder()
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun stream(turns: List<AiTurn>, defaultSystemPrompt: String): Flow<AiStreamEvent> = flow {
        val chain = repository.activeChain()
        if (chain.isEmpty()) {
            throw AiException(
                AiErrorKind.NO_PROVIDER,
                "No AI provider is connected yet · এখনো কোনো AI provider যুক্ত নেই। Settings → AI Model থেকে যোগ করুন।"
            )
        }
        val prepared = normalize(turns)
        if (prepared.isEmpty()) {
            throw AiException(AiErrorKind.BAD_REQUEST, "Nothing to send · পাঠানোর মতো কিছু নেই")
        }

        val errors = mutableListOf<String>()
        var lastKind = AiErrorKind.UNKNOWN

        for ((index, provider) in chain.withIndex()) {
            emit(AiStreamEvent.ProviderStarted(provider.name, isBackup = index > 0))
            var started = false
            try {
                streamOne(provider, prepared, defaultSystemPrompt) { chunk ->
                    started = true
                    emit(AiStreamEvent.Text(chunk))
                }
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                val named = if (e.providerName == null) {
                    AiException(e.kind, "${provider.name}: ${e.message}", provider.name)
                } else {
                    e
                }
                if (started) throw named
                errors.add(named.message.orEmpty())
                lastKind = named.kind
            } catch (e: IOException) {
                val mapped = mapIo(e, provider)
                if (started) throw mapped
                errors.add(mapped.message.orEmpty())
                lastKind = mapped.kind
            }
        }
        throw AiException(lastKind, errors.joinToString("\n\n"))
    }.flowOn(Dispatchers.IO)

    override suspend fun complete(turns: List<AiTurn>, defaultSystemPrompt: String): String {
        val sb = StringBuilder()
        stream(turns, defaultSystemPrompt).collect { event ->
            if (event is AiStreamEvent.Text) sb.append(event.chunk)
        }
        return sb.toString()
    }

    // ───────────── one provider ─────────────

    private suspend fun streamOne(
        provider: AiProvider,
        turns: List<AiTurn>,
        defaultSystem: String,
        onChunk: suspend (String) -> Unit
    ) {
        val system = provider.systemPrompt.ifBlank { defaultSystem }
        val (call, response) = openStream(provider, turns, system)

        // উত্তর পড়ার মাঝখানে coroutine বাতিল হলে সংযোগও বাতিল হয়
        val job = currentCoroutineContext()[Job]
        val handle = job?.invokeOnCompletion { cause -> if (cause != null) call.cancel() }
        try {
            response.use { resp ->
                val body = resp.body
                    ?: throw AiException(AiErrorKind.UNKNOWN, "Empty response · খালি উত্তর")
                val isSse = resp.header("Content-Type").orEmpty()
                    .contains("text/event-stream", ignoreCase = true)
                if (!isSse) {
                    // কিছু সার্ভার stream করে না, পুরো উত্তর একবারে পাঠায়
                    handleJsonBody(provider, body.string(), onChunk)
                    return
                }
                val source = body.source()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    if (data == "[DONE]") break
                    val chunk = when (provider.type.apiStyle) {
                        ApiStyle.OPENAI_COMPATIBLE -> parseOpenAi(data)
                        ApiStyle.ANTHROPIC -> parseAnthropic(data)
                    }
                    if (!chunk.isNullOrEmpty()) onChunk(chunk)
                }
            }
        } catch (e: IOException) {
            // বাতিলের কারণে সংযোগ ভাঙলে সেটা ত্রুটি নয়, বাতিল
            currentCoroutineContext().ensureActive()
            throw e
        } finally {
            handle?.dispose()
        }
    }

    /** সংযোগ খোলে; নতুন OpenAI model-এর অসমর্থিত parameter থাকলে সেটা বাদ দিয়ে আবার চেষ্টা করে */
    private suspend fun openStream(
        provider: AiProvider,
        turns: List<AiTurn>,
        system: String
    ): Pair<Call, Response> {
        var useCompletionTokens = false
        var omitTemperature = false
        repeat(3) { attempt ->
            val call = streamClient.newCall(
                buildRequest(provider, turns, system, useCompletionTokens, omitTemperature)
            )
            val response = call.awaitResponse()
            if (response.isSuccessful) return call to response

            val code = response.code
            val body = response.use { it.body?.string().orEmpty() }
            if (code == 400 && attempt < 2) {
                if (!useCompletionTokens && body.contains("max_completion_tokens")) {
                    useCompletionTokens = true
                    return@repeat
                }
                if (!omitTemperature && body.contains("temperature")) {
                    omitTemperature = true
                    return@repeat
                }
            }
            throw httpError(code, body, provider)
        }
        throw AiException(AiErrorKind.UNKNOWN, "${provider.name}: Could not connect · সংযোগ করা যায়নি", provider.name)
    }

    // ───────────── request building ─────────────

    private fun buildRequest(
        p: AiProvider,
        turns: List<AiTurn>,
        system: String,
        useCompletionTokens: Boolean,
        omitTemperature: Boolean
    ): Request {
        val base = p.baseUrl.trim().trimEnd('/')
        val key = p.apiKey.trim()
        return when (p.type.apiStyle) {
            ApiStyle.ANTHROPIC -> {
                val body = buildJsonObject {
                    put("model", p.model.trim())
                    put("max_tokens", p.maxTokens)
                    put("stream", true)
                    if (system.isNotBlank()) put("system", system)
                    if (!omitTemperature) put("temperature", p.temperature.coerceIn(0f, 1f).toDouble())
                    put("messages", buildJsonArray {
                        turns.forEach { turn ->
                            add(buildJsonObject {
                                put("role", roleName(turn.role))
                                put("content", anthropicContent(turn))
                            })
                        }
                    })
                }
                val builder = Request.Builder()
                    .url(base.removeSuffix("/v1") + "/v1/messages")
                    .header("anthropic-version", ANTHROPIC_API_VERSION)
                    .header("Accept", "text/event-stream")
                if (key.isNotEmpty()) builder.header("x-api-key", key)
                builder.post(body.toString().toRequestBody(STREAM_MEDIA)).build()
            }
            ApiStyle.OPENAI_COMPATIBLE -> {
                val body = buildJsonObject {
                    put("model", p.model.trim())
                    put("stream", true)
                    put(if (useCompletionTokens) "max_completion_tokens" else "max_tokens", p.maxTokens)
                    if (!omitTemperature) put("temperature", p.temperature.toDouble())
                    put("messages", buildJsonArray {
                        if (system.isNotBlank()) {
                            add(buildJsonObject {
                                put("role", "system")
                                put("content", system)
                            })
                        }
                        turns.forEach { turn ->
                            add(buildJsonObject {
                                put("role", roleName(turn.role))
                                put("content", openAiContent(turn))
                            })
                        }
                    })
                }
                val builder = Request.Builder()
                    .url("$base/chat/completions")
                    .header("Accept", "text/event-stream")
                if (key.isNotEmpty()) builder.header("Authorization", "Bearer $key")
                builder.post(body.toString().toRequestBody(STREAM_MEDIA)).build()
            }
        }
    }

    private fun roleName(role: AiRole): String = if (role == AiRole.USER) "user" else "assistant"

    private fun openAiContent(turn: AiTurn): JsonElement {
        if (turn.images.isEmpty()) return JsonPrimitive(turn.text)
        return buildJsonArray {
            add(buildJsonObject {
                put("type", "text")
                put("text", turn.text)
            })
            turn.images.forEach { image ->
                add(buildJsonObject {
                    put("type", "image_url")
                    put("image_url", buildJsonObject {
                        put("url", "data:${image.mimeType};base64,${image.base64}")
                    })
                })
            }
        }
    }

    private fun anthropicContent(turn: AiTurn): JsonElement {
        if (turn.images.isEmpty()) return JsonPrimitive(turn.text)
        return buildJsonArray {
            turn.images.forEach { image ->
                add(buildJsonObject {
                    put("type", "image")
                    put("source", buildJsonObject {
                        put("type", "base64")
                        put("media_type", image.mimeType)
                        put("data", image.base64)
                    })
                })
            }
            add(buildJsonObject {
                put("type", "text")
                put("text", turn.text)
            })
        }
    }

    /** পরপর একই ভূমিকার বার্তা জুড়ে দেয়, শুরুর assistant বাদ দেয় (দুই ধরনের API-ই এটা চায়) */
    private fun normalize(turns: List<AiTurn>): List<AiTurn> {
        val merged = mutableListOf<AiTurn>()
        for (turn in turns) {
            if (turn.text.isBlank() && turn.images.isEmpty()) continue
            val last = merged.lastOrNull()
            if (last != null && last.role == turn.role) {
                merged[merged.lastIndex] = last.copy(
                    text = last.text + "\n\n" + turn.text,
                    images = last.images + turn.images
                )
            } else {
                merged.add(turn)
            }
        }
        while (merged.isNotEmpty() && merged.first().role == AiRole.ASSISTANT) {
            merged.removeAt(0)
        }
        return merged
    }

    // ───────────── response parsing ─────────────

    private fun parseObject(data: String): JsonObject? =
        try {
            json.parseToJsonElement(data) as? JsonObject
        } catch (e: Exception) {
            null
        }

    private fun errorText(element: JsonElement): String =
        when (element) {
            is JsonObject -> (element["message"] as? JsonPrimitive)?.contentOrNull
            is JsonPrimitive -> element.contentOrNull
            else -> null
        } ?: "Provider error · provider-এর ত্রুটি"

    private fun parseOpenAi(data: String): String? {
        val obj = parseObject(data) ?: return null
        val error = obj["error"]
        if (error != null && error !is JsonNull) {
            throw AiException(AiErrorKind.SERVER, errorText(error))
        }
        val choices = obj["choices"] as? JsonArray ?: return null
        val first = choices.firstOrNull() as? JsonObject ?: return null
        val delta = first["delta"] as? JsonObject
        val content = (delta?.get("content") as? JsonPrimitive)?.contentOrNull
        if (content != null) return content
        val message = first["message"] as? JsonObject
        return (message?.get("content") as? JsonPrimitive)?.contentOrNull
    }

    private fun parseAnthropic(data: String): String? {
        val obj = parseObject(data) ?: return null
        return when ((obj["type"] as? JsonPrimitive)?.contentOrNull) {
            "content_block_delta" ->
                ((obj["delta"] as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull
            "error" -> throw AiException(AiErrorKind.SERVER, errorText(obj["error"] ?: JsonNull))
            else -> null
        }
    }

    private suspend fun handleJsonBody(
        provider: AiProvider,
        body: String,
        onChunk: suspend (String) -> Unit
    ) {
        val obj = parseObject(body)
            ?: throw AiException(AiErrorKind.UNKNOWN, "Unexpected response · অপ্রত্যাশিত উত্তর")
        val error = obj["error"]
        if (error != null && error !is JsonNull) {
            throw AiException(AiErrorKind.SERVER, errorText(error))
        }
        val raw: JsonElement? = when (provider.type.apiStyle) {
            ApiStyle.OPENAI_COMPATIBLE -> {
                val first = (obj["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
                (first?.get("message") as? JsonObject)?.get("content")
            }
            ApiStyle.ANTHROPIC -> {
                val first = (obj["content"] as? JsonArray)?.firstOrNull() as? JsonObject
                first?.get("text")
            }
        }
        val content = (raw as? JsonPrimitive)?.contentOrNull
        if (content.isNullOrEmpty()) {
            throw AiException(AiErrorKind.UNKNOWN, "Empty reply · উত্তর খালি এসেছে")
        }
        onChunk(content)
    }

    // ───────────── errors ─────────────

    private fun httpError(code: Int, body: String, p: AiProvider): AiException {
        val detail = extractDetail(body)?.let { mask(it, p.apiKey).take(160) }
        val (kind, head) = when (code) {
            401, 403 -> AiErrorKind.AUTH to "API key ঠিক নয় বা অনুমতি নেই · Invalid API key"
            404 -> AiErrorKind.MODEL_OR_URL to "URL বা model ভুল · Wrong URL or model"
            429 -> AiErrorKind.RATE_LIMIT to "সীমা শেষ বা ক্রেডিট নেই · Rate limit or no credit"
            400 -> AiErrorKind.BAD_REQUEST to "অনুরোধ গ্রহণযোগ্য নয়, model-এর নাম দেখুন · Bad request"
            in 500..599 -> AiErrorKind.SERVER to "সার্ভারে সমস্যা · Server error ($code)"
            else -> AiErrorKind.UNKNOWN to "HTTP $code"
        }
        val message = "${p.name}: $head" + if (detail.isNullOrBlank()) "" else "\n$detail"
        return AiException(kind, message, p.name)
    }

    private fun extractDetail(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val error = (json.parseToJsonElement(body) as? JsonObject)?.get("error")
            when (error) {
                is JsonObject -> (error["message"] as? JsonPrimitive)?.contentOrNull
                is JsonPrimitive -> error.contentOrNull
                else -> null
            } ?: body.take(160)
        } catch (e: Exception) {
            body.take(160)
        }
    }

    private fun mapIo(e: IOException, p: AiProvider): AiException {
        val head = when (e) {
            is UnknownHostException -> "ইন্টারনেট নেই বা URL ভুল · No internet or wrong URL"
            is SocketTimeoutException -> "সময় শেষ হয়ে গেছে · Timed out"
            else -> "সংযোগে সমস্যা · Connection problem"
        }
        return AiException(AiErrorKind.NETWORK, "${p.name}: $head", p.name)
    }

    /** ত্রুটির বার্তায় API key ভেসে এলে ঢেকে দেয় */
    private fun mask(text: String, key: String): String {
        val k = key.trim()
        return if (k.length >= 6) text.replace(k, "***") else text
    }
}
