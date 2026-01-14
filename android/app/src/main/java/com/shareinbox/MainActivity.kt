package com.shareinbox

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shareinbox.storage.SecureStorage
import com.shareinbox.ui.theme.ShareToInboxTheme
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main activity showing pairing status
 *
 * - If not paired: Shows setup prompt
 * - If paired: Shows status and expiration
 * - If expired: Shows re-pair prompt
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ShareToInboxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onSetupClick = {
                            startActivity(Intent(this, SetupActivity::class.java))
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh UI when returning from setup
        setContent {
            ShareToInboxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onSetupClick = {
                            startActivity(Intent(this, SetupActivity::class.java))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MainScreen(onSetupClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val storage = remember { SecureStorage(context) }
    val status = remember { storage.getStatusInfo() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Share to Inbox",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Secure sharing to your AI",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        when (status) {
            is SecureStorage.PairingStatus.NotPaired -> {
                NotPairedContent(onSetupClick)
            }
            is SecureStorage.PairingStatus.Expired -> {
                ExpiredContent(onSetupClick)
            }
            is SecureStorage.PairingStatus.Paired -> {
                PairedContent(status, onSetupClick)
            }
        }
    }
}

@Composable
private fun NotPairedContent(onSetupClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Not Paired",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Scan the QR code from your computer to start sharing.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onSetupClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scan QR Code")
            }
        }
    }
}

@Composable
private fun ExpiredContent(onSetupClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Pairing Expired",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Your pairing has expired. Scan a new QR code from your computer to re-pair.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onSetupClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scan QR Code")
            }
        }
    }
}

@Composable
private fun PairedContent(
    status: SecureStorage.PairingStatus.Paired,
    onSetupClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val expiryDate = remember { Date(status.expiresAt * 1000) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Paired",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Expires: ${dateFormat.format(expiryDate)}",
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Text(
                text = "${status.daysRemaining} days remaining",
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Share anything using the Android share menu and it will appear in your AI inbox.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Re-pair option
    OutlinedButton(
        onClick = onSetupClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Re-pair with New QR")
    }

    Spacer(modifier = Modifier.height(48.dp))

    // How to use section
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "How to Share",
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("1. Select any text, link, or content")
            Text("2. Tap Share")
            Text("3. Select \"Share to Inbox\"")
            Text("4. Ask your AI \"check my inbox\"")
        }
    }
}
