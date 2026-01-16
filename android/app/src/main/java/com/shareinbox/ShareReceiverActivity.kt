package com.shareinbox

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shareinbox.network.InboxSender
import com.shareinbox.storage.SecureStorage
import com.shareinbox.ui.theme.ShareToInboxTheme
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * Receives shared content from other apps
 *
 * This activity appears in the Android share menu.
 * It's designed to be fast and minimal - share, send, dismiss.
 *
 * Multi-channel support:
 * - 1 channel: sends immediately (no UI)
 * - 2+ channels: shows quick picker
 *
 * SECURITY:
 * - No logging of content
 * - Topic computed and immediately discarded (key evaporation)
 * - Auto-dismiss on completion
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val storage = SecureStorage(this)
        val channels = storage.getChannels()

        // Check pairing status
        if (channels.isEmpty()) {
            showError(R.string.not_paired_error)
            return
        }

        // Extract shared content
        val content = extractContent(intent)
        if (content == null) {
            showError(R.string.sent_error)
            return
        }

        // Check if launched via share shortcut with specific channel
        val shortcutChannelName = ChannelShortcuts.getChannelFromIntent(intent)
        val targetChannel = if (shortcutChannelName != null) {
            channels.find { it.name == shortcutChannelName }
        } else null

        // Show UI based on channel count and shortcut selection
        setContent {
            ShareToInboxTheme {
                when {
                    // Specific channel selected via shortcut
                    targetChannel != null -> {
                        SendingScreen(
                            content = content,
                            channel = targetChannel,
                            onComplete = { success ->
                                showResult(success)
                                finish()
                            }
                        )
                    }
                    // Single channel - send immediately
                    channels.size == 1 -> {
                        SendingScreen(
                            content = content,
                            channel = channels.first(),
                            onComplete = { success ->
                                showResult(success)
                                finish()
                            }
                        )
                    }
                    // Multiple channels - show picker
                    else -> {
                        ChannelPickerScreen(
                            channels = channels,
                            content = content,
                            onChannelSelected = { },
                            onCancel = { finish() },
                            onComplete = { success ->
                                showResult(success)
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun extractContent(intent: Intent): String? {
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                when {
                    intent.type?.startsWith("text/") == true -> {
                        intent.getStringExtra(Intent.EXTRA_TEXT)
                    }
                    intent.type?.startsWith("image/") == true -> {
                        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                        uri?.let { encodeImage(it) }
                    }
                    else -> null
                }
            }
            else -> null
        }
    }

    /**
     * Read image from URI, resize if needed, and base64 encode
     */
    private fun encodeImage(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (originalBitmap == null) return null

            // Resize if too large (max 1200px on longest side)
            val maxSize = 1200
            val bitmap = if (originalBitmap.width > maxSize || originalBitmap.height > maxSize) {
                val ratio = minOf(
                    maxSize.toFloat() / originalBitmap.width,
                    maxSize.toFloat() / originalBitmap.height
                )
                val newWidth = (originalBitmap.width * ratio).toInt()
                val newHeight = (originalBitmap.height * ratio).toInt()
                Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
            } else {
                originalBitmap
            }

            // Compress to JPEG
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val imageBytes = outputStream.toByteArray()

            // Base64 encode with prefix
            val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            "[IMAGE:data:image/jpeg;base64,$base64]"
        } catch (e: Exception) {
            null
        }
    }

    private fun showError(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun showResult(success: Boolean) {
        val messageRes = if (success) R.string.sent_success else R.string.sent_error
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun ChannelPickerScreen(
    channels: List<SecureStorage.Channel>,
    content: String,
    onChannelSelected: (SecureStorage.Channel) -> Unit,
    onCancel: () -> Unit,
    onComplete: (Boolean) -> Unit
) {
    var selectedChannel by remember { mutableStateOf<SecureStorage.Channel?>(null) }
    var isSending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // If a channel is selected, send to it
    LaunchedEffect(selectedChannel) {
        selectedChannel?.let { channel ->
            isSending = true
            scope.launch {
                val result = InboxSender.send(
                    content = content,
                    secret = channel.secret,
                    server = channel.server,
                    windowSeconds = channel.windowSeconds
                )
                onComplete(result is InboxSender.SendResult.Success)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable { onCancel() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(32.dp)
                .clickable { /* prevent click-through */ },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isSending) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Sending to ${selectedChannel?.name}...")
                } else {
                    Text(
                        "Send to",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        items(channels) { channel ->
                            ChannelItem(
                                channel = channel,
                                onClick = { selectedChannel = channel }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelItem(
    channel: SecureStorage.Channel,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    channel.name,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                Text(
                    "${channel.getDaysRemaining()} days left",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun SendingScreen(
    content: String,
    channel: SecureStorage.Channel,
    onComplete: (Boolean) -> Unit
) {
    var isSending by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            val result = InboxSender.send(
                content = content,
                secret = channel.secret,
                server = channel.server,
                windowSeconds = channel.windowSeconds
            )
            isSending = false
            onComplete(result is InboxSender.SendResult.Success)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isSending) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Sending...")
                }
            }
        }
    }
}
