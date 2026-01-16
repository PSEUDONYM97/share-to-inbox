package com.shareinbox

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shareinbox.storage.SecureStorage
import com.shareinbox.ui.theme.ShareToInboxTheme
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main activity showing channel list and management
 *
 * Multi-channel support:
 * - Shows list of all paired channels
 * - Long-press to rename or delete
 * - Add button to pair new devices
 */
class MainActivity : ComponentActivity() {

    private lateinit var storage: SecureStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = SecureStorage(this)
        // Initialize share shortcuts for existing channels
        ChannelShortcuts.updateShortcuts(this)
        refreshContent()
    }

    override fun onResume() {
        super.onResume()
        refreshContent()
    }

    private fun refreshContent() {
        // Load channels fresh each time - don't let Compose cache stale data
        val channels = storage.getChannels()

        setContent {
            ShareToInboxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        initialChannels = channels,
                        storage = storage,
                        onAddChannel = {
                            startActivity(Intent(this, SetupActivity::class.java))
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainScreen(
    initialChannels: List<SecureStorage.Channel>,
    storage: SecureStorage,
    onAddChannel: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var channels by remember { mutableStateOf(initialChannels) }

    // Update channels when initialChannels changes (e.g., on resume)
    LaunchedEffect(initialChannels) {
        channels = initialChannels
    }

    // Dialog states
    var channelToRename by remember { mutableStateOf<SecureStorage.Channel?>(null) }
    var channelToDelete by remember { mutableStateOf<SecureStorage.Channel?>(null) }
    var newName by remember { mutableStateOf("") }

    // Refresh channels and shortcuts when needed
    fun refresh() {
        channels = storage.getChannels()
        ChannelShortcuts.updateShortcuts(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Header
        Text(
            text = "Share to Inbox",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Secure sharing to your AI",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (channels.isEmpty()) {
            // No channels - show setup prompt
            NoChannelsContent(onAddChannel)
        } else {
            // Channel list header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Channels",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                FilledTonalButton(
                    onClick = onAddChannel,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Channel list
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(channels, key = { it.name }) { channel ->
                    ChannelCard(
                        channel = channel,
                        onRename = {
                            channelToRename = channel
                            newName = channel.name
                        },
                        onDelete = { channelToDelete = channel }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // How to use
            HowToUseCard()
        }
    }

    // Rename dialog
    if (channelToRename != null) {
        AlertDialog(
            onDismissRequest = { channelToRename = null },
            title = { Text("Rename Channel") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Channel name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank() && newName != channelToRename?.name) {
                            storage.renameChannel(channelToRename!!.name, newName.trim())
                            refresh()
                        }
                        channelToRename = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { channelToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete confirmation dialog
    if (channelToDelete != null) {
        AlertDialog(
            onDismissRequest = { channelToDelete = null },
            title = { Text("Delete Channel?") },
            text = {
                Text("This will remove \"${channelToDelete?.name}\" and you'll need to re-pair to use it again.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        storage.removeChannel(channelToDelete!!.name)
                        refresh()
                        channelToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { channelToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelCard(
    channel: SecureStorage.Channel,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val expiryDate = remember(channel.expiresAt) { Date(channel.expiresAt) }
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { /* no-op */ },
                onLongClick = { showMenu = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
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
                    text = channel.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${channel.getDaysRemaining()} days left • Expires ${dateFormat.format(expiryDate)}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            showMenu = false
                            onRename()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NoChannelsContent(onAddChannel: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No Channels",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Scan a QR code from your computer to start sharing.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onAddChannel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Channel")
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    HowToUseCard()
}

@Composable
private fun HowToUseCard() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "How to Share",
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("1. Select any text, link, or image", fontSize = 14.sp)
            Text("2. Tap Share", fontSize = 14.sp)
            Text("3. Select \"Share to Inbox\"", fontSize = 14.sp)
            Text("4. Ask your AI \"check my inbox\"", fontSize = 14.sp)
        }
    }
}
