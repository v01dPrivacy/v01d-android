package com.voidprivacy.app.tools.emailalias

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.voidprivacy.app.R
import com.voidprivacy.app.ui.theme.VoidGreen
import com.voidprivacy.app.ui.theme.VoidRed
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256 email alias generator (offline / own-domain mode).
 * Inspired by email-alias-core: generates deterministic, verifiable aliases
 * from a secret key + site name. No server needed — fully offline.
 */
object AliasGenerator {
    fun generate(secretKey: String, siteName: String, domain: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hash = mac.doFinal(siteName.lowercase().trim().toByteArray(Charsets.UTF_8))
        // Take first 10 bytes → base32-like encoding for readability
        val alias = hash.take(10).joinToString("") { b ->
            val v = b.toInt() and 0xFF
            ALPHABET[v % 32].toString() + ALPHABET[(v / 32) % 32].toString()
        }.take(12)
        return "$alias@$domain"
    }

    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz234567"
}

/**
 * Email Aliases screen — two modes:
 *
 *  1. addy.io (default): REAL aliases that forward to the user's mailbox.
 *     One-time setup: free addy.io account + API key. Then tap → working alias.
 *  2. Manual/offline (advanced): the original deterministic generator for
 *     people who own a catch-all domain, with the 3-step in-app explainer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailAliasScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var connected by remember { mutableStateOf(AddyAliasManager.isConnected(context)) }
    var showManual by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.alias_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (connected) {
                AddyConnectedSection(
                    onDisconnected = { connected = false },
                )
            } else {
                AddySetupSection(
                    onConnected = { connected = true },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Manual / offline generator (advanced) ──
            TextButton(onClick = { showManual = !showManual }) {
                Text(
                    (if (showManual) "▾ " else "▸ ") + stringResource(R.string.alias_manual_mode),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showManual) {
                ManualGeneratorSection()
            }
        }
    }
}

// ─── Mode 1: addy.io ───────────────────────────────────────────────

@Composable
private fun AddySetupSection(onConnected: () -> Unit) {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf("") }
    var connecting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Text(stringResource(R.string.alias_addy_setup_title), style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.alias_addy_setup_desc),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedButton(
        onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://app.addy.io/register")))
            } catch (_: Exception) {
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.alias_addy_create_account))
    }
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = apiKey,
        onValueChange = { apiKey = it; error = "" },
        label = { Text(stringResource(R.string.alias_addy_api_key)) },
        placeholder = { Text(stringResource(R.string.alias_addy_api_key_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    if (error.isNotEmpty()) {
        Text(error, style = MaterialTheme.typography.bodySmall, color = VoidRed, modifier = Modifier.padding(top = 4.dp))
    }
    Spacer(modifier = Modifier.height(8.dp))
    Button(
        onClick = {
            if (apiKey.isBlank()) return@Button
            connecting = true
            error = ""
            Thread {
                val result = AddyAliasManager.connect(context, apiKey)
                Handler(Looper.getMainLooper()).post {
                    connecting = false
                    result.onSuccess {
                        onConnected()
                    }.onFailure { e ->
                        error = context.getString(R.string.alias_addy_connect_failed, e.message ?: "")
                    }
                }
            }.start()
        },
        enabled = apiKey.isNotBlank() && !connecting,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (connecting) {
            CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(stringResource(R.string.alias_addy_connect))
    }
}

@Composable
private fun AddyConnectedSection(onDisconnected: () -> Unit) {
    val context = LocalContext.current
    var siteName by remember { mutableStateOf("") }
    var domains by remember { mutableStateOf<AddyAliasManager.AddyDomains?>(null) }
    var selectedDomain by remember { mutableStateOf("") }
    var createdAlias by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf("") }
    var recent by remember { mutableStateOf<List<AddyAliasManager.AddyAlias>>(emptyList()) }
    var refresh by remember { mutableStateOf(0) }

    // Load domains + recent aliases once
    remember(refresh) {
        Thread {
            val d = AddyAliasManager.getDomains(context)
            val list = AddyAliasManager.listAliases(context)
            Handler(Looper.getMainLooper()).post {
                domains = d
                if (selectedDomain.isBlank()) {
                    selectedDomain = d?.defaultDomain ?: d?.shared?.firstOrNull() ?: ""
                }
                recent = list
            }
        }.start()
        true
    }

    // Account row
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.alias_addy_connected), style = MaterialTheme.typography.titleSmall, color = VoidGreen)
                Text("addy.io", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = {
                AddyAliasManager.disconnect(context)
                onDisconnected()
            }) {
                Text(stringResource(R.string.alias_addy_disconnect), color = VoidRed)
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Create alias
    OutlinedTextField(
        value = siteName,
        onValueChange = { siteName = it },
        label = { Text(stringResource(R.string.alias_site_name)) },
        placeholder = { Text(stringResource(R.string.alias_site_name_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    // Domain chips (custom + shared)
    domains?.let { d ->
        val all = (listOf(d.defaultDomain) + d.custom + d.shared).filter { it.isNotBlank() }.distinct()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            all.take(3).forEach { dom ->
                FilterChip(
                    selected = selectedDomain == dom,
                    onClick = { selectedDomain = dom },
                    label = {
                        Text(
                            dom.substringBefore('.'),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
        }
        if (all.size > 3) {
            OutlinedTextField(
                value = selectedDomain,
                onValueChange = { selectedDomain = it },
                label = { Text(stringResource(R.string.alias_domain)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    Button(
        onClick = {
            if (siteName.isBlank() || selectedDomain.isBlank()) return@Button
            creating = true
            createError = ""
            Thread {
                val result = AddyAliasManager.createAlias(context, selectedDomain, siteName)
                Handler(Looper.getMainLooper()).post {
                    creating = false
                    result.onSuccess { email ->
                        createdAlias = email
                        siteName = ""
                        refresh++
                    }.onFailure { e ->
                        createError = e.message ?: "error"
                    }
                }
            }.start()
        },
        enabled = siteName.isNotBlank() && selectedDomain.isNotBlank() && !creating,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (creating) {
            CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(stringResource(R.string.alias_addy_create))
    }
    if (createError.isNotEmpty()) {
        Text(createError, style = MaterialTheme.typography.bodySmall, color = VoidRed, modifier = Modifier.padding(top = 4.dp))
    }

    createdAlias?.let { alias ->
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = VoidGreen.copy(alpha = 0.1f)),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.alias_your_alias), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(alias, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.alias_clipboard_label), alias))
                        Toast.makeText(context, context.getString(R.string.alias_copied), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.alias_copy))
                }
            }
        }
    }

    // Recent aliases
    if (recent.isNotEmpty()) {
        Spacer(modifier = Modifier.height(20.dp))
        Text(stringResource(R.string.alias_addy_recent), style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(4.dp))
        recent.forEach { a ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).clickable {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.alias_clipboard_label), a.email))
                        Toast.makeText(context, context.getString(R.string.alias_copied), Toast.LENGTH_SHORT).show()
                    }) {
                        Text(a.email, style = MaterialTheme.typography.bodySmall)
                        if (a.description.isNotBlank()) {
                            Text(
                                a.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = a.active,
                        onCheckedChange = { on ->
                            Thread {
                                AddyAliasManager.setAliasActive(context, a, on)
                            }.start()
                        },
                    )
                }
            }
        }
    }
}

// ─── Mode 2: manual / offline generator (advanced) ─────────────────

@Composable
private fun ManualGeneratorSection() {
    val context = LocalContext.current
    var secretKey by remember { mutableStateOf("") }
    var siteName by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf(com.voidprivacy.app.AppSettings.getAliasDomain(context)) }
    var alias by remember { mutableStateOf<String?>(null) }

    // How it actually works: 3 steps, plain language
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.alias_manual_how_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.alias_manual_how_steps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = secretKey,
        onValueChange = { secretKey = it },
        label = { Text(stringResource(R.string.alias_secret_key)) },
        placeholder = { Text(stringResource(R.string.alias_secret_key_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = siteName,
        onValueChange = { siteName = it },
        label = { Text(stringResource(R.string.alias_site_name)) },
        placeholder = { Text(stringResource(R.string.alias_site_name_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = domain,
        onValueChange = { domain = it },
        label = { Text(stringResource(R.string.alias_domain)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(20.dp))

    Button(
        onClick = {
            if (secretKey.isNotBlank() && siteName.isNotBlank() && domain.isNotBlank()) {
                alias = AliasGenerator.generate(secretKey, siteName, domain)
            }
        },
        enabled = secretKey.isNotBlank() && siteName.isNotBlank() && domain.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.alias_generate))
    }

    alias?.let { generated ->
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.alias_your_alias),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = generated,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.alias_clipboard_label), generated))
                Toast.makeText(context, context.getString(R.string.alias_copied), Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.alias_copy))
        }
    }
}
