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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
 * SECURITY:
 * - No logging of content
 * - Topic computed and immediately discarded (key evaporation)
 * - Auto-dismiss on completion
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val storage = SecureStorage(this)

        // Check pairing status
        when (val status = storage.getStatusInfo()) {
            is SecureStorage.PairingStatus.NotPaired -> {
                showError(R.string.not_paired_error)
                return
            }
            is SecureStorage.PairingStatus.Expired -> {
                showError(R.string.expired_error)
                return
            }
            is SecureStorage.PairingStatus.Paired -> {
                // Continue with sharing
            }
        }

        // Extract shared content
        val content = extractContent(intent)
        if (content == null) {
            showError(R.string.sent_error)
            return
        }

        // Show sending UI and perform send
        setContent {
            ShareToInboxTheme {
                SendingScreen(
                    content = content,
                    storage = storage,
                    onComplete = { success ->
                        if (success) {
                            Toast.makeText(this, R.string.sent_success, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, R.string.sent_error, Toast.LENGTH_SHORT).show()
                        }
                        finish()
                    }
                )
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
                        // Read image, compress, and base64 encode
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
     *
     * ntfy.sh has limits, so we compress to stay under ~400KB
     */
    private fun encodeImage(uri: Uri): String? {
        return try {
            // Read image bytes
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
}

@Composable
private fun SendingScreen(
    content: String,
    storage: SecureStorage,
    onComplete: (Boolean) -> Unit
) {
    var isSending by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // Trigger send on composition
    LaunchedEffect(Unit) {
        scope.launch {
            val secret = storage.getSecret()
            if (secret == null) {
                onComplete(false)
                return@launch
            }

            val result = InboxSender.send(
                content = content,
                secret = secret,
                server = storage.getServer(),
                windowSeconds = storage.getWindowSeconds()
            )

            // Secret variable goes out of scope - evaporated
            isSending = false
            onComplete(result is InboxSender.SendResult.Success)
        }
    }

    // Minimal UI - just a loading indicator
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
