package com.surya.miniconnect.data.livekit

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Fetches a LiveKit access token from our token server. The server holds the
 * API secret and enforces "join only if the room exists", so a random code is
 * rejected with an error instead of silently creating an empty call.
 */
class TokenService {

    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    data class TokenResult(val token: String)

    suspend fun fetchToken(
        roomCode: String,
        identity: String,
        create: Boolean
    ): Result<TokenResult> = withContext(Dispatchers.IO) {
        try {
            val url = "${LiveKitConfig.TOKEN_URL}" +
                "?room=$roomCode&identity=$identity&create=$create"
            val request = Request.Builder().url(url).get().build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching { JSONObject(body).optString("error") }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: if (response.code == 404) "Call not found. Ask the host for a valid code."
                        else "Couldn't reach the call server."
                    return@withContext Result.failure(Exception(message))
                }
                val token = JSONObject(body).getString("token")
                Result.success(TokenResult(token))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token fetch failed", e)
            Result.failure(Exception("Couldn't reach the call server."))
        }
    }

    companion object {
        private const val TAG = "TokenService"
    }
}
