package com.example.myapplication.shared.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin JSON/HTTP client for gateway-service, mirroring mobile/lib/core/network/api_client.dart's
 * behavior: attaches `Authorization: Bearer <token>` from [tokenProvider] on every request except
 * `/auth/login`, and on a 401 (outside login) calls [onUnauthorized] once instead of retrying —
 * the Flutter app does the same (no silent refresh-token retry; the user just logs in again).
 */
class ApiClient(
    private val tokenProvider: () -> String?,
    private val onUnauthorized: () -> Unit,
    private val explicitBaseUrl: String? = null,
) {
    private val baseUrl: String get() = explicitBaseUrl ?: ApiConfig.baseUrl

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun get(path: String): JSONObject = asObject(send("GET", path, null))
    suspend fun getList(path: String): JSONArray = asArray(send("GET", path, null))
    suspend fun post(path: String, body: JSONObject? = null): JSONObject = asObject(send("POST", path, body))
    suspend fun postNoContent(path: String, body: JSONObject? = null) {
        send("POST", path, body)
    }

    private suspend fun send(method: String, path: String, body: JSONObject?): String? =
        withContext(Dispatchers.IO) {
            val url = if (path.startsWith("http")) path else "$baseUrl$path"
            val builder = Request.Builder().url(url)
            val token = tokenProvider()
            if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
            val reqBody = body?.toString()?.toRequestBody(jsonMedia)
            when (method) {
                "GET" -> builder.get()
                "POST" -> builder.post(reqBody ?: JSONObject().toString().toRequestBody(jsonMedia))
                else -> throw IllegalArgumentException("Unsupported method $method")
            }
            try {
                http.newCall(builder.build()).execute().use { res ->
                    val text = res.body?.string()
                    if (res.code == 401 && !path.contains("/auth/login")) {
                        onUnauthorized()
                    }
                    if (!res.isSuccessful) {
                        throw ApiException(res.code, errorMessage(text, res.code), isNetwork = false)
                    }
                    if (res.code == 204 || text.isNullOrBlank()) null else text
                }
            } catch (e: ApiException) {
                throw e
            } catch (e: IOException) {
                throw ApiException(null, e.message ?: "Erro de rede", isNetwork = true)
            }
        }

    private fun errorMessage(body: String?, status: Int): String {
        if (body.isNullOrBlank()) return "Erro $status"
        return runCatching { JSONObject(body).optString("error") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "Erro $status"
    }

    private fun asObject(raw: String?): JSONObject = if (raw.isNullOrBlank()) JSONObject() else JSONObject(raw)

    private fun asArray(raw: String?): JSONArray = if (raw.isNullOrBlank()) JSONArray() else JSONArray(raw)
}
