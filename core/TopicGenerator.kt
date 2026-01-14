package com.shareinbox.crypto

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * TOTP-style topic generation for share-to-inbox
 *
 * This must produce identical output to core/totp.js for the same inputs.
 *
 * Topic = HMAC-SHA256(secret, floor(unixTime / windowSeconds)).hex().substring(0, 32)
 *
 * Test vectors (must all pass):
 * - secret: 0123...ef (repeated), window: 0 -> acbc9dd34781c8264d36e5754f663a64
 * - secret: 0123...ef (repeated), window: 1000000 -> d298ee8d38cd98a093dbf71b8950d095
 * - secret: ffff...ff, window: 12345 -> e3c1b82ce9caccc56cb932877b100cb9
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
     * @return The topic as a hex string (no prefix)
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
                println("FAIL: window=${vector.windowIndex}, expected=${vector.expected}, got=$result")
                return false
            }
        }
        println("All test vectors passed!")
        return true
    }
}

// For testing from command line
fun main() {
    TopicGenerator.verifyTestVectors()

    println("\n--- Current Window Demo ---")
    val testSecret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    println("Window size: 6 hours (21600 seconds)")
    println("Current window index: ${TopicGenerator.getWindowIndex()}")
    println("Current topic: ${TopicGenerator.getCurrentTopic(testSecret)}")
}
