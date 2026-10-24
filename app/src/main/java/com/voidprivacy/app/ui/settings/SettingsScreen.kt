package com.voidprivacy.app.ui.settings

import android.app.Activity
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import com.voidprivacy.app.AppSettings
import com.voidprivacy.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var theme by remember { mutableStateOf(AppSettings.getTheme(context)) }
    var language by remember { mutableStateOf(AppSettings.getLanguage(context)) }
    var electrumHost by remember { mutableStateOf(AppSettings.getElectrumHost(context)) }
    var electrumPort by remember { mutableStateOf(AppSettings.getElectrumPort(context).toString()) }
    var network by remember { mutableStateOf(AppSettings.getNetwork(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            // Theme
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                listOf(
                    "system" to stringResource(R.string.theme_system),
                    "light" to stringResource(R.string.theme_light),
                    "dark" to stringResource(R.string.theme_dark),
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = theme == value,
                        onClick = {
                            theme = value
                            AppSettings.setTheme(context, value)
                        },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Language
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                listOf(
                    "system" to stringResource(R.string.theme_system),
                    "en" to stringResource(R.string.lang_english),
                    "it" to stringResource(R.string.lang_italian),
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = language == value,
                        onClick = {
                            language = value
                            AppSettings.setLanguage(context, value)
                            (context as? Activity)?.recreate()
                        },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bitcoin server (advanced)
            Text(stringResource(R.string.settings_electrum), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_electrum_desc),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = electrumHost,
                onValueChange = {
                    electrumHost = it
                    AppSettings.setElectrumHost(context, it)
                },
                label = { Text(stringResource(R.string.settings_host)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = electrumPort,
                onValueChange = {
                    electrumPort = it
                    it.toIntOrNull()?.let { p -> AppSettings.setElectrumPort(context, p) }
                },
                label = { Text(stringResource(R.string.settings_port)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(24.dp))


            // Network
            var pendingNetwork by remember { mutableStateOf<String?>(null) }
            Text(stringResource(R.string.settings_network), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                listOf(
                    "mainnet" to stringResource(R.string.network_mainnet),
                    "testnet" to stringResource(R.string.network_testnet),
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = network == value,
                        onClick = {
                            if (value != network) pendingNetwork = value
                        },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            pendingNetwork?.let { target ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { pendingNetwork = null },
                    title = { Text(stringResource(R.string.settings_network_confirm_title)) },
                    text = {
                        Text(
                            stringResource(
                                if (target == "mainnet") R.string.settings_network_confirm_mainnet
                                else R.string.settings_network_confirm_testnet,
                            )
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            network = target
                            AppSettings.setNetwork(context, target)
                            pendingNetwork = null
                        }) { Text(stringResource(R.string.settings_network_confirm_yes)) }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { pendingNetwork = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }
            if (network == "testnet") {
                Text(
                    text = stringResource(R.string.settings_testnet_warning),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // TX Notifications
            var txNotifyEnabled by remember {
                mutableStateOf(com.voidprivacy.app.tools.wallet.btc.TxNotificationService.isEnabled(context))
            }
            Text(stringResource(R.string.settings_notifications), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_tx_alerts), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(R.string.settings_tx_alerts_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = txNotifyEnabled,
                    onCheckedChange = { enabled ->
                        txNotifyEnabled = enabled
                        com.voidprivacy.app.tools.wallet.btc.TxNotificationService.setEnabled(context, enabled)
                    },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.settings_stored_locally),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Danger zone: wipe all data
            var showWipeConfirm by remember { mutableStateOf(false) }
            var wipeText by remember { mutableStateOf("") }

            Text(
                stringResource(R.string.settings_danger_zone),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (!showWipeConfirm) {
                OutlinedButton(
                    onClick = { showWipeConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_wipe_all), color = MaterialTheme.colorScheme.error)
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.settings_wipe_list),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = wipeText,
                            onValueChange = { wipeText = it },
                            label = { Text(stringResource(R.string.settings_wipe_confirm_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            Button(
                                onClick = {
                                    com.voidprivacy.app.DataWiper.wipeAll(context)
                                    // Restart app
                                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                    intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                    (context as? Activity)?.finishAffinity()
                                },
                                enabled = wipeText == "WIPE",
                            ) {
                                Text(stringResource(R.string.settings_wipe_everything))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = { showWipeConfirm = false; wipeText = "" }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }
                }
            }
        }
    }
}
