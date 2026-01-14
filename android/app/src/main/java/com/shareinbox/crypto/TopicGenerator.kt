package com.shareinbox.crypto

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * TOTP-style topic generation for share-to-inbox
 *
 * CRITICAL: This must produce identical output to the JavaScript implementation.
 *
 * Topic = HMAC-SHA256(secret, floor(unixTime / windowSeconds)).hex().substring(0, 32)
 *
 * Test vectors (must all pass):
 * - secret: 0123456789abcdef... (repeated), window: 0 -> acbc9dd34781c8264d36e5754f663a64
 * - secret: 0123456789abcdef... (repeated), window: 1000000 -> d298ee8d38cd98a093dbf71b8950d095
 * - secret: ffffffff... (all F), window: 12345 -> e3c1b82ce9caccc56cb932877b100cb9
 */
object TopicGenerator {

    private const val DEFAULT_WINDOW_SECONDS = 21600L // 6 hours
    private const val DEFAULT_TOPIC_LENGTH = 32

    /**
     * Compute the current time window index
     */
    fun getWindowIndex(
        windowSeconds: Long = DEFAULT_WINDOW_SECONDS,
        timestampMillis: Long = System.currentTimeMillis()
    ): Long {
        val unixSeconds = timestampMillis / 1000
        return unixSeconds / windowSeconds
    }

    /**
     * Generate a topic for a specific window
     *
     * @param secretHex The shared secret as a hex string (64 chars = 256 bits)
     * @param windowIndex The time window index
     * @param topicLength Length of the resulting topic (default 32)
     * @return The topic as a hex string (no prefix, no identifier)
     */
    fun generateTopic(
        secretHex: String,
        windowIndex: Long,
        topicLength: Int = DEFAULT_TOPIC_LENGTH
    ): String {
        // Convert window index to big-endian 8-byte array
        val windowBuffer = ByteBuffer.allocate(8)
        windowBuffer.putLong(windowIndex)
        val windowBytes = windowBuffer.array()

        // Convert hex secret to bytes
        val secretBytes = secretHex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

        // HMAC-SHA256
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(secretBytes, "HmacSHA256")
        mac.init(keySpec)
        val hash = mac.doFinal(windowBytes)

        // Convert to hex and take first N characters
        val hexHash = hash.joinToString("") { "%02x".format(it) }
        return hexHash.take(topicLength)
    }

    /**
     * Get the current topic
     *
     * NOTE: This is computed in memory and should NOT be stored.
     * After use, let the variable go out of scope (key evaporation).
     */
    fun getCurrentTopic(
        secretHex: String,
        windowSeconds: Long = DEFAULT_WINDOW_SECONDS,
        topicLength: Int = DEFAULT_TOPIC_LENGTH
    ): String {
        val windowIndex = getWindowIndex(windowSeconds)
        return generateTopic(secretHex, windowIndex, topicLength)
    }

    /**
     * Verify implementation against test vectors
     *
     * Call this during development to ensure correctness.
     * Must match the JavaScript implementation exactly.
     */
    fun verifyTestVectors(): Boolean {
        data class TestVector(
            val secret: String,
            val windowIndex: Long,
            val expected: String
        )

        val vectors = listOf(
            TestVector(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                0,
                "acbc9dd34781c8264d36e5754f663a64"
            ),
            TestVector(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                1000000,
                "d298ee8d38cd98a093dbf71b8950d095"
            ),
            TestVector(
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                12345,
                "e3c1b82ce9caccc56cb932877b100cb9"
            )
        )

        for (vector in vectors) {
            val result = generateTopic(vector.secret, vector.windowIndex)
            if (result != vector.expected) {
                // In debug builds only - release builds have logging stripped
                android.util.Log.e("TopicGenerator",
                    "FAIL: window=${vector.windowIndex}, expected=${vector.expected}, got=$result")
                return false
            }
        }
        return true
    }
}
