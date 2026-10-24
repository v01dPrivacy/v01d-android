package com.voidprivacy.app.tools.tor

import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.voidprivacy.app.R
import com.voidprivacy.app.ui.theme.VoidAmber
import com.voidprivacy.app.ui.theme.VoidGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val isRunning = TorManager.isRunning
    val statusText = TorManager.statusText

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tor_title)) },
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
            // Tor toggle card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.tor_circuit),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isRunning) VoidGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = isRunning,
                        onCheckedChange = { enabled ->
                            if (enabled) TorManager.start(context)
                            else TorManager.stop(context)
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // VPN toggle card
            var vpnEnabled by remember { mutableStateOf(false) }
            val vpnLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    val vpnIntent = Intent(context, TorVpnService::class.java)
                    context.startForegroundService(vpnIntent)
                    vpnEnabled = true
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.tor_vpn_system_wide),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = if (vpnEnabled) stringResource(R.string.tor_vpn_active) else stringResource(R.string.tor_vpn_inactive),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (vpnEnabled) VoidGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (vpnEnabled) {
                            Text(
                                text = stringResource(R.string.tor_vpn_udp_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = VoidAmber,
                            )
                        }
                    }
                    Switch(
                        checked = vpnEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                if (!isRunning) {
                                    TorManager.start(context)
                                }
                                val prepareIntent = VpnService.prepare(context)
                                if (prepareIntent != null) {
                                    vpnLauncher.launch(prepareIntent)
                                } else {
                                    val vpnIntent = Intent(context, TorVpnService::class.java)
                                    context.startForegroundService(vpnIntent)
                                    vpnEnabled = true
                                }
                            } else {
                                val stopIntent = Intent(context, TorVpnService::class.java).apply { action = "STOP" }
                                context.startService(stopIntent)
                                vpnEnabled = false
                            }
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status info
            if (isRunning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = VoidGreen.copy(alpha = 0.1f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.tor_running),
                            style = MaterialTheme.typography.titleMedium,
                            color = VoidGreen,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.tor_running_description),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = VoidAmber.copy(alpha = 0.1f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.tor_stopped),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.tor_stopped_description),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.tor_how_it_works),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.tor_how_it_works_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.tor_powered_by),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
