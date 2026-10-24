package com.voidprivacy.app.tools.message

import android.widget.Toast
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.voidprivacy.app.R
import com.voidprivacy.app.ui.theme.VoidAccent
import com.voidprivacy.app.ui.theme.VoidDarkGray
import com.voidprivacy.app.ui.theme.VoidGreen
import com.voidprivacy.app.ui.theme.VoidRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Conversation with one contact. Polls the relay every 3s while open and
 * renders decrypted messages as chat bubbles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    contactToken: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val contact = remember(contactToken) { MessageStorage.getContactByToken(context, contactToken) }
    var input by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val messages = remember(refresh) { MessageStorage.getMessages(context, contactToken) }

    // File picker: send a file through the E2E session.
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        Thread {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@Thread
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                var name = context.getString(R.string.chat_default_file_name)
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) name = c.getString(idx)
                    }
                }
                val result = MessageManager.sendFile(context, contactToken, name, mime, bytes)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    result.onSuccess { refresh++ }.onFailure { e ->
                        Toast.makeText(context, e.message ?: context.getString(R.string.chat_send_failed), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, context.getString(R.string.chat_cant_read_file, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    // Poll for new messages while this chat is open (on IO — network).
    LaunchedEffect(contactToken) {
        while (true) {
            val n = withContext(Dispatchers.IO) { MessageManager.pollAndDecrypt(context) }
            if (n > 0) refresh++
            // Randomized interval: a fixed cadence is a timing fingerprint at the relay.
            delay(2500 + kotlin.random.Random.nextLong(3500))
        }
    }

    // Auto-scroll to bottom on new messages.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(contact?.name ?: stringResource(R.string.chat_unknown_contact))
                        Text(
                            text = contact?.fingerprint()?.take(16) ?: contactToken.take(16),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                if (error.isNotEmpty()) {
                    Text(error, style = MaterialTheme.typography.labelSmall, color = VoidRed, modifier = Modifier.padding(bottom = 4.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                        Icon(Icons.Default.AttachFile, contentDescription = stringResource(R.string.chat_attach_file), tint = VoidAccent)
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.chat_message_hint)) },
                        maxLines = 4,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val text = input.trim()
                            if (text.isEmpty()) return@IconButton
                            sending = true
                            error = ""
                            Thread {
                                val result = MessageManager.send(context, contactToken, text)
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    result.onSuccess {
                                        input = ""
                                        refresh++
                                    }.onFailure { e ->
                                        error = e.message ?: context.getString(R.string.chat_send_failed)
                                    }
                                    sending = false
                                }
                            }.start()
                        },
                        enabled = !sending && input.isNotBlank(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send), tint = VoidAccent)
                    }
                }
            }
        },
    ) { padding ->
        if (messages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.chat_no_messages),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(messages) { msg ->
                    MessageBubble(msg)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val context = LocalContext.current
    val isMe = !msg.incoming
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isMe) VoidAccent.copy(alpha = 0.25f) else VoidDarkGray)
                .then(
                    if (msg.filePath != null) Modifier.clickable { shareFile(context, msg.filePath) }
                    else Modifier,
                )
                .padding(10.dp),
        ) {
            Text(text = msg.text, style = MaterialTheme.typography.bodyMedium)
            if (msg.filePath != null) {
                Text(
                    text = stringResource(R.string.chat_tap_to_open),
                    style = MaterialTheme.typography.labelSmall,
                    color = VoidAccent,
                )
            }
            Text(
                text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(msg.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = if (isMe) VoidAccent else VoidGreen,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

private fun shareFile(context: android.content.Context, path: String?) {
    if (path == null) return
    try {
        val file = java.io.File(path)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.chat_share_file)))
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.chat_cant_open_file, e.message), Toast.LENGTH_SHORT).show()
    }
}
