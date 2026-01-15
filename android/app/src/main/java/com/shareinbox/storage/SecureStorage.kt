package com.shareinbox.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

/**
 * Secure storage for pairing configuration
 *
 * Uses Android's EncryptedSharedPreferences backed by the Keystore.
 *
 * SECURITY NOTES:
 * - Data is encrypted at rest
 * - Backup is disabled in manifest
 * - Auto-wipe on expiration
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
        private const val KEY_SECRET = "secret"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_WINDOW_SECONDS = "window_seconds"
        private const val KEY_SERVER = "server"
        private const val KEY_PAIRED_AT = "paired_at"
    }

    /**
     * Check if device is paired
     */
    fun isPaired(): Boolean {
        return prefs.contains(KEY_SECRET) && !isExpired()
    }

    /**
     * Check if pairing has expired
     */
    fun isExpired(): Boolean {
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0)
        if (expiresAt == 0L) return true
        // expiresAt is stored in milliseconds
        return System.currentTimeMillis() >= expiresAt
    }

    /**
     * Get the secret (if paired and not expired)
     *
     * NOTE: Do not log or store this value. Use it and let it go out of scope.
     */
    fun getSecret(): String? {
        if (isExpired()) {
            // Auto-wipe on expiration
            clear()
            return null
        }
        return prefs.getString(KEY_SECRET, null)
    }

    /**
     * Get window size in seconds
     */
    fun getWindowSeconds(): Long {
        return prefs.getLong(KEY_WINDOW_SECONDS, 21600L) // Default 6 hours
    }

    /**
     * Get server URL
     */
    fun getServer(): String {
        return prefs.getString(KEY_SERVER, "https://ntfy.sh") ?: "https://ntfy.sh"
    }

    /**
     * Get expiration timestamp (unix seconds)
     */
    fun getExpiresAt(): Long {
        return prefs.getLong(KEY_EXPIRES_AT, 0)
    }

    /**
     * Get days remaining until expiration
     */
    fun getDaysRemaining(): Int {
        val expiresAt = getExpiresAt()
        if (expiresAt == 0L) return 0
        val nowMs = System.currentTimeMillis()
        val remainingMs = expiresAt - nowMs
        if (remainingMs <= 0) return 0
        return (remainingMs / 86400000).toInt() + 1 // ms per day, round up
    }

    /**
     * Save pairing data from QR code payload
     *
     * Expected JSON format:
     * {"s":"secret","e":1234567890,"w":21600,"u":"https://ntfy.sh"}
     */
    fun savePairing(qrPayload: String): Boolean {
        return try {
            val json = JSONObject(qrPayload)

            val secret = json.getString("s")
            val expiresAt = json.getLong("e")
            val windowSeconds = json.optLong("w", 21600)
            val server = json.optString("u", "https://ntfy.sh")

            // Validate
            if (secret.length != 64) {
                return false // Invalid secret length
            }
            // expiresAt is in milliseconds
            if (expiresAt <= System.currentTimeMillis()) {
                return false // Already expired
            }

            // Save
            prefs.edit()
                .putString(KEY_SECRET, secret)
                .putLong(KEY_EXPIRES_AT, expiresAt)
                .putLong(KEY_WINDOW_SECONDS, windowSeconds)
                .putString(KEY_SERVER, server)
                .putLong(KEY_PAIRED_AT, System.currentTimeMillis() / 1000)
                .apply()

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clear all stored data
     *
     * Called on:
     * - Expiration
     * - User request
     * - Failed verification
     */
    fun clear() {
        prefs.edit().clear().apply()
    }

    /**
     * Get status info for UI display
     */
    fun getStatusInfo(): PairingStatus {
        if (!prefs.contains(KEY_SECRET)) {
            return PairingStatus.NotPaired
        }

        if (isExpired()) {
            clear()
            return PairingStatus.Expired
        }

        return PairingStatus.Paired(
            expiresAt = getExpiresAt(),
            daysRemaining = getDaysRemaining(),
            server = getServer()
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
}
