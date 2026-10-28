package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class GatewayHealth(
    val httpCode: Int,
    val pressConfigured: Boolean = false,
    val footballConfigured: Boolean = false,
    val weatherConfigured: Boolean = false,
) {
    val available: Boolean get() = httpCode in 200..299
}

/** Read-only health check; it never returns or stores provider credentials. */
class GatewayHealthService(private val client: OkHttpClient) {
    suspend fun check(): GatewayHealth = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiGatewayConfig.BASE_URL}/health")
            .header("Accept", "application/json")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val configured = runCatching { JSONObject(body).optJSONObject("configured") }.getOrNull()
                GatewayHealth(
                    httpCode = response.code,
                    pressConfigured = configured?.optBoolean("press") == true,
                    footballConfigured = configured?.optBoolean("football") == true,
                    weatherConfigured = configured?.optBoolean("weather") == true,
                )
            }
        } catch (_: Exception) {
            GatewayHealth(httpCode = 0)
        }
    }
}
