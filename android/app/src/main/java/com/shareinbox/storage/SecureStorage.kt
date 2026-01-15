package com.shareinbox.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Secure storage for pairing configuration
 *
 * Supports multiple channels - each channel is a separate pairing
 * to a different computer/context.
 *
 * Uses Android's EncryptedSharedPreferences backed by the Keystore.
 *
 * SECURITY NOTES:
 * - Data is encrypted at rest
 * - Backup is disabled in manifest
 * - Auto-wipe expired channels
 * - No logging of sensitive data
 */
class SecureStorage(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "share_inbox_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_CHANNELS = "channels"

        // Legacy keys for migration
        private const val KEY_SECRET = "secret"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_WINDOW_SECONDS = "window_seconds"
        private const val KEY_SERVER = "server"

        // Word lists for random channel names
        private val ADJECTIVES = listOf(
            "swift", "bright", "calm", "bold", "warm", "cool", "quick", "soft",
            "keen", "pure", "wild", "free", "fair", "kind", "wise", "brave",
            "crisp", "fresh", "clear", "sharp", "smooth", "steady", "golden", "silver",
            "cosmic", "fuzzy", "happy", "quiet", "sunny", "misty", "snowy", "starry"
        )

        private val NOUNS = listOf(
            "falcon", "penguin", "dolphin", "tiger", "eagle", "wolf", "bear", "fox",
            "hawk", "owl", "raven", "sparrow", "otter", "seal", "whale", "shark",
            "comet", "nova", "nebula", "quasar", "photon", "prism", "crystal", "ember",
            "breeze", "storm", "river", "mountain", "forest", "meadow", "canyon", "glacier"
        )

        /**
         * Generate a random two-word channel name
         */
        fun generateChannelName(): String {
            val adj = ADJECTIVES[Random.nextInt(ADJECTIVES.size)]
            val noun = NOUNS[Random.nextInt(NOUNS.size)]
            return "$adj-$noun"
        }
    }

    init {
        // Migrate legacy single-pairing to channel format
        migrateLegacyPairing()
    }

    /**
     * Migrate old single-pairing format to new channels format
     */
    private fun migrateLegacyPairing() {
        if (prefs.contains(KEY_SECRET) && !prefs.contains(KEY_CHANNELS)) {
            val secret = prefs.getString(KEY_SECRET, null)
            val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0)
            val windowSeconds = prefs.getLong(KEY_WINDOW_SECONDS, 21600)
            val server = prefs.getString(KEY_SERVER, "https://ntfy.sh")

            if (secret != null && expiresAt > System.currentTimeMillis()) {
                val channel = Channel(
                    name = generateChannelName(),
                    secret = secret,
                    server = server ?: "https://ntfy.sh",
                    windowSeconds = windowSeconds,
                    expiresAt = expiresAt
                )

                val channels = JSONArray()
                channels.put(channel.toJson())

                prefs.edit()
                    .putString(KEY_CHANNELS, channels.toString())
                    .remove(KEY_SECRET)
                    .remove(KEY_EXPIRES_AT)
                    .remove(KEY_WINDOW_SECONDS)
                    .remove(KEY_SERVER)
                    .apply()
            }
        }
    }

    /**
     * Get all channels (auto-removes expired ones)
     */
    fun getChannels(): List<Channel> {
        val json = prefs.getString(KEY_CHANNELS, null) ?: return emptyList()

        return try {
            val array = JSONArray(json)
            val channels = mutableListOf<Channel>()
            val validChannels = JSONArray()
            var hasExpired = false

            for (i in 0 until array.length()) {
                val channel = Channel.fromJson(array.getJSONObject(i))
                if (channel.isExpired()) {
                    hasExpired = true
                } else {
                    channels.add(channel)
                    validChannels.put(channel.toJson())
                }
            }

            // Auto-clean expired channels
            if (hasExpired) {
                prefs.edit()
                    .putString(KEY_CHANNELS, validChannels.toString())
                    .apply()
            }

            channels
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get a specific channel by name
     */
    fun getChannel(name: String): Channel? {
        return getChannels().find { it.name == name }
    }

    /**
     * Check if any valid channels exist
     */
    fun hasChannels(): Boolean {
        return getChannels().isNotEmpty()
    }

    /**
     * Add a new channel from QR code payload
     *
     * Expected JSON format:
     * {"s":"secret","e":1234567890,"w":21600,"u":"https://ntfy.sh"}
     *
     * @param qrPayload The QR code JSON data
     * @param name Optional channel name (auto-generates if null)
     * @return The created channel, or null if invalid
     */
    fun addChannel(qrPayload: String, name: String? = null): Channel? {
        return try {
            val json = JSONObject(qrPayload)

            val secret = json.getString("s")
            val expiresAt = json.getLong("e")
            val windowSeconds = json.optLong("w", 21600)
            val server = json.optString("u", "https://ntfy.sh")

            // Validate
            if (secret.length != 64) {
                return null // Invalid secret length
            }
            if (expiresAt <= System.currentTimeMillis()) {
                return null // Already expired
            }

            // Generate unique name if not provided
            val channelName = name ?: generateUniqueName()

            val channel = Channel(
                name = channelName,
                secret = secret,
                server = server,
                windowSeconds = windowSeconds,
                expiresAt = expiresAt
            )

            // Add to existing channels
            val channels = getChannels().toMutableList()
            channels.add(channel)
            saveChannels(channels)

            channel
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Generate a unique channel name
     */
    private fun generateUniqueName(): String {
        val existing = getChannels().map { it.name }.toSet()
        var name = generateChannelName()
        var attempts = 0

        while (existing.contains(name) && attempts < 100) {
            name = generateChannelName()
            attempts++
        }

        return name
    }

    /**
     * Rename a channel
     */
    fun renameChannel(oldName: String, newName: String): Boolean {
        val channels = getChannels().toMutableList()
        val index = channels.indexOfFirst { it.name == oldName }

        if (index < 0) return false
        if (channels.any { it.name == newName }) return false // Name taken

        channels[index] = channels[index].copy(name = newName)
        saveChannels(channels)
        return true
    }

    /**
     * Remove a channel
     */
    fun removeChannel(name: String): Boolean {
        val channels = getChannels().toMutableList()
        val removed = channels.removeAll { it.name == name }

        if (removed) {
            saveChannels(channels)
        }
        return removed
    }

    /**
     * Save channels list
     */
    private fun saveChannels(channels: List<Channel>) {
        val array = JSONArray()
        channels.forEach { array.put(it.toJson()) }

        prefs.edit()
            .putString(KEY_CHANNELS, array.toString())
            .apply()
    }

    /**
     * Clear all stored data
     */
    fun clear() {
        prefs.edit().clear().apply()
    }

    // ========== Legacy compatibility methods ==========
    // These work with the first/only channel for backward compatibility

    /**
     * Check if device has any valid pairing
     */
    fun isPaired(): Boolean = hasChannels()

    /**
     * Get the first channel's secret (legacy compatibility)
     */
    fun getSecret(): String? = getChannels().firstOrNull()?.secret

    /**
     * Get the first channel's window seconds (legacy compatibility)
     */
    fun getWindowSeconds(): Long = getChannels().firstOrNull()?.windowSeconds ?: 21600L

    /**
     * Get the first channel's server (legacy compatibility)
     */
    fun getServer(): String = getChannels().firstOrNull()?.server ?: "https://ntfy.sh"

    /**
     * Legacy pairing method - creates first channel
     */
    fun savePairing(qrPayload: String): Boolean {
        return addChannel(qrPayload) != null
    }

    /**
     * Get status info for UI display (legacy - uses first channel)
     */
    fun getStatusInfo(): PairingStatus {
        val channels = getChannels()

        if (channels.isEmpty()) {
            return PairingStatus.NotPaired
        }

        val first = channels.first()
        return PairingStatus.Paired(
            expiresAt = first.expiresAt,
            daysRemaining = first.getDaysRemaining(),
            server = first.server
        )
    }

    sealed class PairingStatus {
        object NotPaired : PairingStatus()
        object Expired : PairingStatus()
        data class Paired(
            val expiresAt: Long,
            val daysRemaining: Int,
            val server: String
        ) : PairingStatus()
    }

    /**
     * Represents a single channel (pairing to a computer)
     */
    data class Channel(
        val name: String,
        val secret: String,
        val server: String,
        val windowSeconds: Long,
        val expiresAt: Long
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAt

        fun getDaysRemaining(): Int {
            val remainingMs = expiresAt - System.currentTimeMillis()
            if (remainingMs <= 0) return 0
            return (remainingMs / 86400000).toInt() + 1
        }

        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("name", name)
                put("secret", secret)
                put("server", server)
                put("windowSeconds", windowSeconds)
                put("expiresAt", expiresAt)
            }
        }

        companion object {
            fun fromJson(json: JSONObject): Channel {
                return Channel(
                    name = json.getString("name"),
                    secret = json.getString("secret"),
                    server = json.optString("server", "https://ntfy.sh"),
                    windowSeconds = json.optLong("windowSeconds", 21600),
                    expiresAt = json.getLong("expiresAt")
                )
            }
        }
    }
}
