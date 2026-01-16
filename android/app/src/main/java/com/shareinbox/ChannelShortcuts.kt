package com.shareinbox

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.shareinbox.storage.SecureStorage

/**
 * Manages share shortcuts for channels
 *
 * Creates the expandable dropdown in the Android share sheet
 * so users can pick a channel directly without an extra tap.
 */
object ChannelShortcuts {

    private const val CATEGORY_SHARE = "com.shareinbox.category.SHARE_TARGET"

    /**
     * Update shortcuts to match current channels
     * Call this after adding, removing, or renaming channels
     */
    fun updateShortcuts(context: Context) {
        val storage = SecureStorage(context)
        val channels = storage.getChannels()

        // Remove all existing dynamic shortcuts
        ShortcutManagerCompat.removeAllDynamicShortcuts(context)

        if (channels.isEmpty()) return

        // Create a shortcut for each channel
        val shortcuts = channels.mapIndexed { index, channel ->
            ShortcutInfoCompat.Builder(context, "channel_${channel.name}")
                .setShortLabel(channel.name)
                .setLongLabel("Send to ${channel.name}")
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_launcher_foreground))
                .setIntent(Intent(context, ShareReceiverActivity::class.java).apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(EXTRA_CHANNEL_NAME, channel.name)
                })
                .setCategories(setOf(CATEGORY_SHARE))
                .setRank(index)
                .build()
        }

        ShortcutManagerCompat.addDynamicShortcuts(context, shortcuts)
    }

    /**
     * Get channel name from share intent if launched via shortcut
     */
    fun getChannelFromIntent(intent: Intent): String? {
        return intent.getStringExtra(EXTRA_CHANNEL_NAME)
    }

    const val EXTRA_CHANNEL_NAME = "com.shareinbox.CHANNEL_NAME"
}
