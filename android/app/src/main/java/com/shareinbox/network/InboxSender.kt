package com.shareinbox.network

import com.shareinbox.crypto.TopicGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Sends content to the inbox via ntfy.sh
 *
 * KEY EVAPORATION: The topic is computed, used, and immediately discarded.
 * No topic history is stored.
 *
 * NOTE: ntfy.sh automatically converts messages >4KB to attachments
 */
object InboxSender {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Send content to the inbox
     *
     * @param content The text content to send
     * @param secret The shared secret
     * @param server The ntfy server URL
     * @param windowSeconds The TOTP window size
     * @return Result indicating success or failure
     */
    suspend fun send(
        content: String,
        secret: String,
        server: String,
        windowSeconds: Long
    ): SendResult = withContext(Dispatchers.IO) {
        try {
            // Compute topic (in memory only - KEY EVAPORATION)
            val topic = TopicGenerator.getCurrentTopic(secret, windowSeconds)

            // Build URL
            val url = "$server/$topic"

            // Create request - ntfy.sh auto-converts large messages to attachments
            val request = Request.Builder()
                .url(url)
                .post(content.toRequestBody("text/plain".toMediaType()))
                .build()

            // Execute
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    SendResult.Success
                } else {
                    SendResult.Error("HTTP ${response.code}")
                }
            }

            // Topic variable goes out of scope here - evaporated
        } catch (e: Exception) {
            SendResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Result of a send operation
     */
    sealed class SendResult {
        object Success : SendResult()
        data class Error(val message: String) : SendResult()
    }
}
