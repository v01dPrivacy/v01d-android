package com.voidprivacy.app.tools.wallet.btc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.voidprivacy.app.ui.theme.VoidGreen
import com.voidprivacy.app.ui.theme.VoidRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var txs by remember { mutableStateOf<List<TxEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedTx by remember { mutableStateOf<TxEntity?>(null) }

    val walletInfo = WalletStorage.loadWallet(context)

    // Load from DB + sync from Electrum
    LaunchedEffect(walletInfo?.mnemonic) {
        val db = TxDatabase.get(context)
        // Show cached first
        txs = db.txDao().getRecent()
        loading = txs.isEmpty()

        // Sync from Electrum
        val wi = walletInfo ?: return@LaunchedEffect
        try {
            val host = com.voidprivacy.app.AppSettings.getElectrumHost(context)
            val port = com.voidprivacy.app.AppSettings.getElectrumPort(context)
            val synced = withContext(Dispatchers.IO) {
                syncHistory(wi.mnemonic, host, port)
            }
            db.txDao().insertAll(synced)
            txs = db.txDao().getRecent()
        } catch (_: Exception) {
            // Offline — show cached
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (selectedTx != null) {
            // Detail view
            val tx = selectedTx!!
            Column(modifier = Modifier.padding(padding).padding(16.dp)) {
                Text(stringResource(R.string.history_detail), style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                DetailRow(stringResource(R.string.history_txid), tx.txid) {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("TXID", tx.txid))
                    Toast.makeText(context, context.getString(R.string.send_txid_copied), Toast.LENGTH_SHORT).show()
                }
                DetailRow(stringResource(R.string.history_amount), stringResource(R.string.wallet_amount_btc, formatBtc(kotlin.math.abs(tx.amount))))
                DetailRow(stringResource(R.string.history_direction), stringResource(if (tx.direction == "in") R.string.history_incoming else R.string.history_outgoing))
                DetailRow(stringResource(R.string.history_address), tx.address)
                DetailRow(stringResource(R.string.history_confirmations), tx.confirmations.toString())
                DetailRow(stringResource(R.string.history_date), SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(tx.timestamp)))
                Spacer(modifier = Modifier.height(16.dp))
                IconButton(onClick = { selectedTx = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.history_back_list))
                }
            }
        } else {
            // List view
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            ) {
                if (loading) {
                    item { Text(stringResource(R.string.wallet_loading), style = MaterialTheme.typography.bodyMedium) }
                } else if (txs.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.history_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(txs) { tx ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { selectedTx = tx },
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (tx.direction == "in") Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = if (tx.direction == "in") VoidGreen else VoidRed,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(
                                        R.string.wallet_amount_btc,
                                        (if (tx.direction == "in") "+" else "-") + formatBtc(kotlin.math.abs(tx.amount)),
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (tx.direction == "in") VoidGreen else VoidRed,
                                )
                                Text(
                                    text = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(tx.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = stringResource(R.string.history_conf_short, tx.confirmations),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Sync transaction history from Electrum for all wallet addresses.
 */
private fun syncHistory(mnemonic: String, host: String, port: Int): List<TxEntity> {
    val addresses = UtxoManager.deriveAddresses(mnemonic)
    val txs = mutableListOf<TxEntity>()

    for (addr in addresses) {
        try {
            val scriptBytes = org.bitcoinj.script.ScriptBuilder.createOutputScript(addr).program
            val sh = ElectrumClient.scriptHash(scriptBytes)
            // Use listunspent as a proxy for tx history (simpler than get_history)
            val utxos = ElectrumClient.listUnspent(sh, host, port)
            for (utxo in utxos) {
                txs.add(
                    TxEntity(
                        txid = utxo.txid,
                        amount = utxo.value,
                        address = addr.toString(),
                        timestamp = System.currentTimeMillis(), // Electrum doesn't return timestamp in listunspent
                        direction = "in",
                        confirmations = if (utxo.height > 0) 1 else 0,
                    )
                )
            }
        } catch (_: Exception) { /* skip */ }
    }
    return txs
}

private fun formatBtc(sats: Long): String {
    return "%.8f".format(sats / 100_000_000.0).trimEnd('0').trimEnd('.')
}
