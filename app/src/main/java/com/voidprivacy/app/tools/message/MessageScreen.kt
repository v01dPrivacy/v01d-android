package com.voidprivacy.app.tools.message

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.voidprivacy.app.R
import com.voidprivacy.app.ui.theme.VoidAccent
import com.voidprivacy.app.ui.theme.VoidAmber
import com.voidprivacy.app.ui.theme.VoidGreen
import com.voidprivacy.app.ui.theme.VoidRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Void Message hub: setup (display name + relay), contact list, and entry to
 * chats / add-contact / identity. Polls the relay while visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(
    onBack: () -> Unit,
    onOpenChat: (token: String) -> Unit,
    onAddContact: () -> Unit,
    onShowIdentity: () -> Unit,
) {
    val context = LocalContext.current
    var relayMode by remember { mutableStateOf(MessageStorage.getRelayMode(context)) }
    var customRelayUrl by remember {
        mutableStateOf(
            MessageStorage.getRelayUrl(context).takeIf { relayMode == "custom" } ?: ""
        )
    }
    var displayName by remember { mutableStateOf(IdentityManager.getDisplayName(context)) }
    var setupDone by remember { mutableStateOf(MessageManager.isReady && MessageStorage.getRelayUrl(context).isNotEmpty()) }
    var contacts by remember { mutableStateOf(MessageStorage.getContacts(context)) }
    var status by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }

    // Poll the relay every 5s while this screen is visible (on IO — network).
    // Also republish our bundle ~every 30s so a fresh one-time prekey is available.
    LaunchedEffect(setupDone) {
        if (!setupDone) return@LaunchedEffect
        var tick = 0
        while (true) {
            val n = withContext(Dispatchers.IO) { MessageManager.pollAndDecrypt(context) }
            if (n > 0) refresh++
            if (tick++ % 6 == 0) {
                withContext(Dispatchers.IO) { MessageManager.publishBundle(context) }
            }
            // Randomized interval: a fixed cadence is a timing fingerprint at the relay.
            delay(4500 + kotlin.random.Random.nextLong(4000))
        }
    }

    // Refresh contact list when polling found messages or on resume.
    LaunchedEffect(refresh) {
        contacts = MessageStorage.getContacts(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.msg_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (setupDone) {
                        IconButton(onClick = {
                            Thread {
                                val ok = MessageManager.publishBundle(context)
                                status = if (ok) context.getString(R.string.msg_bundle_published) else context.getString(R.string.msg_publish_failed)
                            }.start()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.msg_republish_bundle))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (setupDone) {
                androidx.compose.material3.FloatingActionButton(onClick = onAddContact) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.msg_add_contact_cd))
                }
            }
        },
    ) { padding ->
        if (!setupDone) {
            // ── Setup ──
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(R.string.msg_setup_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.msg_setup_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(stringResource(R.string.msg_display_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.msg_relay_choice), style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { relayMode = "public" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = relayMode == "public", onClick = { relayMode = "public" })
                    Column {
                        Text(stringResource(R.string.msg_relay_public), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.msg_relay_public_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { relayMode = "custom" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = relayMode == "custom", onClick = { relayMode = "custom" })
                    Column {
                        Text(stringResource(R.string.msg_relay_custom), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.msg_relay_custom_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (relayMode == "custom") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customRelayUrl,
                        onValueChange = { customRelayUrl = it },
                        label = { Text(stringResource(R.string.msg_relay_url)) },
                        placeholder = { Text("https://relay.example.workers.dev") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (relayMode == "custom" && customRelayUrl.isBlank()) {
                            Toast.makeText(context, context.getString(R.string.msg_enter_relay_url), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val defaultName = context.getString(R.string.msg_default_user)
                        IdentityManager.setDisplayName(context, displayName.ifBlank { defaultName })
                        MessageStorage.setRelayMode(context, relayMode, customRelayUrl)
                        MessageManager.init(context, displayName.ifBlank { defaultName })
                        Thread {
                            // Bundles are published per-relation now: at setup there are no
                            // contacts yet, so this is a no-op. AddContact publishes for the
                            // new relation immediately; the loop keeps them fresh.
                            MessageManager.publishBundle(context)
                            status = context.getString(R.string.msg_identity_created)
                            setupDone = true
                        }.start()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.msg_create_identity))
                }
                if (status.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(status, style = MaterialTheme.typography.bodySmall, color = VoidAccent)
                }
            }
        } else {
            // ── Contact list ──
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Metadata warning: without the Tor VPN the relay sees the real IP.
                if (!com.voidprivacy.app.tools.tor.TorVpnService.isVpnActive) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = VoidAmber.copy(alpha = 0.12f)),
                    ) {
                        Text(
                            text = stringResource(R.string.msg_ip_warning),
                            style = MaterialTheme.typography.labelSmall,
                            color = VoidAmber,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                // Identity card
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).clickable { onShowIdentity() },
                    colors = CardDefaults.cardColors(containerColor = VoidAccent.copy(alpha = 0.1f)),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = VoidAccent)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(IdentityManager.getDisplayName(context), style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = stringResource(R.string.msg_tap_for_identity),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }

                if (status.isNotEmpty()) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (status.contains("fail", ignoreCase = true)) VoidRed else VoidGreen,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                Text(
                    text = stringResource(R.string.msg_contacts),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )

                if (contacts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.msg_no_contacts),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(contacts, key = { it.token }) { contact ->
                            val msgs = MessageStorage.getMessages(context, contact.token)
                            val last = msgs.lastOrNull()
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clickable { onOpenChat(contact.token) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(contact.name, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = last?.let { (if (it.incoming) "↓ " else "↑ ") + it.text } ?: stringResource(R.string.msg_no_messages),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                        )
                                    }
                                    Text(
                                        text = contact.fingerprint().take(8),
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
