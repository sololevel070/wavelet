package app.wavelet.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpException(val code: Int, url: String) : IOException("HTTP $code for $url")

object Http {
    const val USER_AGENT = "Wavelet/0.1 (Android music player)"

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    suspend fun get(url: HttpUrl, extraHeaders: Map<String, String> = emptyMap()): JsonElement {
        val builder = Request.Builder().url(url)
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        return request(builder.build())
    }

    suspend fun request(request: Request): JsonElement =
        json.parseToJsonElement(text(request))

    suspend fun text(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpException(response.code, request.url.toString())
            response.body?.string().orEmpty()
        }
    }
}

// Small helpers for reading loosely-typed JSON from third-party APIs.

fun JsonElement?.at(key: String): JsonElement? = (this as? JsonObject)?.get(key)

val JsonElement?.arr: List<JsonElement> get() = (this as? JsonArray) ?: emptyList()

/** String value of [key]; for arrays returns the first element. Blank values become null. */
fun JsonElement?.str(key: String): String? = when (val v = at(key)) {
    is JsonPrimitive -> v.contentOrNull
    is JsonArray -> (v.firstOrNull() as? JsonPrimitive)?.contentOrNull
    else -> null
}?.takeIf { it.isNotBlank() }

fun JsonElement?.long(key: String): Long? = str(key)?.toDoubleOrNull()?.toLong()
