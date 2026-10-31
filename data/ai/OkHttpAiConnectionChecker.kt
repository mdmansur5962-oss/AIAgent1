package com.aiagent.assistant.data.ai

import com.aiagent.assistant.domain.ai.AiConnectionChecker
import com.aiagent.assistant.domain.ai.AiProvider
import com.aiagent.assistant.domain.ai.ApiStyle
import com.aiagent.assistant.domain.ai.ConnectionTestResult
import com.aiagent.assistant.domain.ai.ModelListResult
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
private const val ANTHROPIC_VERSION = "2023-06-01"

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
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

/** OkHttp দিয়ে provider-এর সাথে সংযোগ পরীক্ষা ও model-এর তালিকা আনা */
@Singleton
class OkHttpAiConnectionChecker @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) : AiConnectionChecker {

    override suspend fun test(provider: AiProvider): ConnectionTestResult {
        val started = System.currentTimeMillis()
        return try {
            var (code, body) = execute(buildTestRequest(provider, useCompletionTokens = false))
            // নতুন OpenAI model max_tokens নেয় না, max_completion_tokens চায়
            if (code == 400 && body.contains("max_completion_tokens")) {
                val retry = execute(buildTestRequest(provider, useCompletionTokens = true))
                code = retry.first
                body = retry.second
            }
            val latency = System.currentTimeMillis() - started
            if (code in 200..299) {
                ConnectionTestResult(true, "Connected ✓ · সংযোগ সফল", latency)
            } else {
                ConnectionTestResult(false, describeHttpError(code, body, provider), latency)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ConnectionTestResult(
                false,
                "Network error · নেটওয়ার্ক সমস্যা\n" + mask(e.message.orEmpty(), provider.apiKey).take(160),
                System.currentTimeMillis() - started
            )
        } catch (e: Exception) {
            ConnectionTestResult(
                false,
                "Could not send request · অনুরোধ পাঠানো যায়নি\n" + mask(e.message.orEmpty(), provider.apiKey).take(160),
                System.currentTimeMillis() - started
            )
        }
    }

    override suspend fun listModels(provider: AiProvider): ModelListResult {
        return try {
            val (code, body) = execute(buildModelsRequest(provider))
            if (code !in 200..299) {
                return ModelListResult.Failure(describeHttpError(code, body, provider))
            }
            val root = json.parseToJsonElement(body)
            val data = (root as? JsonObject)?.get("data") as? JsonArray
            val ids = data
                ?.mapNotNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull }
                ?.distinct()
                ?.sorted()
                .orEmpty()
            if (ids.isEmpty()) {
                ModelListResult.Failure("No models returned · কোনো model পাওয়া যায়নি, নিজে লিখুন")
            } else {
                ModelListResult.Success(ids)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ModelListResult.Failure(
                "Could not load models · model তালিকা আনা যায়নি\n" + mask(e.message.orEmpty(), provider.apiKey).take(160)
            )
        }
    }

    // ───────────── requests ─────────────

    private suspend fun execute(request: Request): Pair<Int, String> {
        val response = client.newCall(request).await()
        return response.use { it.code to (it.body?.string().orEmpty()) }
    }

    private fun Request.Builder.withAuth(provider: AiProvider): Request.Builder {
        val key = provider.apiKey.trim()
        return when (provider.type.apiStyle) {
            ApiStyle.ANTHROPIC -> {
                header("anthropic-version", ANTHROPIC_VERSION)
                if (key.isNotEmpty()) header("x-api-key", key) else this
            }
            ApiStyle.OPENAI_COMPATIBLE -> {
                if (key.isNotEmpty()) header("Authorization", "Bearer $key") else this
            }
        }
    }

    private fun buildModelsRequest(p: AiProvider): Request {
        val base = p.baseUrl.trim().trimEnd('/')
        val url = when (p.type.apiStyle) {
            ApiStyle.ANTHROPIC -> base.removeSuffix("/v1") + "/v1/models"
            ApiStyle.OPENAI_COMPATIBLE -> "$base/models"
        }
        return Request.Builder().url(url).withAuth(p).get().build()
    }

    private fun buildTestRequest(p: AiProvider, useCompletionTokens: Boolean): Request {
        val base = p.baseUrl.trim().trimEnd('/')
        val prompt = "Reply with the single word OK."
        return when (p.type.apiStyle) {
            ApiStyle.ANTHROPIC -> {
                val body = buildJsonObject {
                    put("model", p.model.trim())
                    put("max_tokens", 16)
                    put(
                        "messages",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("role", "user")
                                    put("content", prompt)
                                }
                            )
                        }
                    )
                }
                Request.Builder()
                    .url(base.removeSuffix("/v1") + "/v1/messages")
                    .withAuth(p)
                    .post(body.toString().toRequestBody(JSON_MEDIA))
                    .build()
            }
            ApiStyle.OPENAI_COMPATIBLE -> {
                val body = buildJsonObject {
                    put("model", p.model.trim())
                    put(
                        "messages",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("role", "user")
                                    put("content", prompt)
                                }
                            )
                        }
                    )
                    put(if (useCompletionTokens) "max_completion_tokens" else "max_tokens", 16)
                }
                Request.Builder()
                    .url("$base/chat/completions")
                    .withAuth(p)
                    .post(body.toString().toRequestBody(JSON_MEDIA))
                    .build()
            }
        }
    }

    // ───────────── errors ─────────────

    private fun describeHttpError(code: Int, body: String, p: AiProvider): String {
        val detail = extractError(body)?.let { mask(it, p.apiKey).take(200) }
        val head = when (code) {
            400 -> "Bad request · অনুরোধ ঠিক নয় (model-এর নাম দেখুন)"
            401, 403 -> "Invalid or unauthorized API key · API key ঠিক নয়"
            404 -> "Not found · URL বা model ভুল"
            429 -> "Rate limit or no credit · সীমা শেষ বা ক্রেডিট নেই"
            in 500..599 -> "Server error · সার্ভারে সমস্যা ($code)"
            else -> "HTTP $code"
        }
        return if (detail.isNullOrBlank()) head else "$head\n$detail"
    }

    private fun extractError(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val root = json.parseToJsonElement(body)
            val error = (root as? JsonObject)?.get("error")
            when (error) {
                is JsonObject -> (error["message"] as? JsonPrimitive)?.contentOrNull
                is JsonPrimitive -> error.contentOrNull
                else -> null
            } ?: body.take(160)
        } catch (e: Exception) {
            body.take(160)
        }
    }

    /** ত্রুটির বার্তায় API key ভেসে এলে ঢেকে দেয় */
    private fun mask(text: String, key: String): String {
        val k = key.trim()
        return if (k.length >= 6) text.replace(k, "***") else text
    }
}
