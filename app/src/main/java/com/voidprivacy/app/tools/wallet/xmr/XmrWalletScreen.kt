package com.voidprivacy.app.tools.wallet.xmr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.voidprivacy.app.R
import com.voidprivacy.app.ui.components.QrCode
import com.voidprivacy.app.ui.theme.VoidAmber
import com.voidprivacy.app.ui.theme.VoidGreen
import com.voidprivacy.app.ui.theme.VoidRed
import java.security.SecureRandom

/**
 * XMR "Seed & Receive" screen — plug and play by default:
 *  1. Create / import a seed (pure Kotlin crypto, offline)
 *  2. Receive: address + QR + copy + share (works fully offline)
 *  3. Balance & sending: handed off to Monerujo (dedicated wallet app)
 *
 * Everything expert-only (node check, monero-wallet-rpc balance, history)
 * lives under a collapsed "Advanced" card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XmrWalletScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var wallet by remember { mutableStateOf(XmrWalletStorage.loadWallet(context)) }
    var importSeed by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }
    var showSeed by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var importMode by remember { mutableStateOf("mnemonic") } // "mnemonic" or "hex"

    // Node state (advanced)
    var nodeUrl by remember { mutableStateOf(XmrWalletStorage.loadNodeUrl(context)) }
    var walletRpcUrl by remember { mutableStateOf(XmrWalletStorage.loadWalletRpcUrl(context)) }
    var showNodeConfig by remember { mutableStateOf(false) }
    var nodeStatus by remember { mutableStateOf("") }
    var nodeHeight by remember { mutableStateOf(0L) }
    var checkingNode by remember { mutableStateOf(false) }

    // Balance state (advanced, via wallet RPC)
    var balance by remember { mutableStateOf<Long?>(null) }
    var unlockedBalance by remember { mutableStateOf<Long?>(null) }
    var transfers by remember { mutableStateOf<List<MoneroRpcClient.Transfer>>(emptyList()) }
    var loadingBalance by remember { mutableStateOf(false) }
    var balanceError by remember { mutableStateOf("") }

    fun copyAddress(address: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("XMR Address", address))
        Toast.makeText(context, context.getString(R.string.xmr_address_copied), Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.xmr_title)) },
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
            if (wallet == null) {
                // ── No wallet: create (recommended) or import ──
                Text(stringResource(R.string.xmr_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.xmr_welcome),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        Thread {
                            try {
                                val seedBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
                                val words = MoneroCrypto.encodeMnemonic(seedBytes, loadWordList(context))
                                val seedStr = words.joinToString(" ")
                                val keys = MoneroCrypto.seedToKeys(seedBytes)
                                XmrWalletStorage.saveWallet(context, seedStr, keys.address)
                                Handler(Looper.getMainLooper()).post {
                                    wallet = XmrWalletStorage.XmrWalletInfo(seedStr, keys.address)
                                    showSeed = true // write it down right away
                                    Toast.makeText(context, context.getString(R.string.xmr_wallet_created), Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(context, context.getString(R.string.xmr_error, e.message ?: ""), Toast.LENGTH_LONG).show()
                                }
                            }
                        }.start()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.xmr_create_wallet))
                }
                Text(
                    text = stringResource(R.string.xmr_create_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (!showImport) {
                    OutlinedButton(
                        onClick = { showImport = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.xmr_import_seed))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.xmr_import_description),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { importMode = "mnemonic" },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.xmr_25_word_seed), style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { importMode = "hex" },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.xmr_hex_seed), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = importSeed,
                        onValueChange = { importSeed = it },
                        label = {
                            Text(if (importMode == "mnemonic") stringResource(R.string.xmr_25_word_seed_phrase) else stringResource(R.string.xmr_64_char_hex_seed))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = if (importMode == "mnemonic") 3 else 1,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            Thread {
                                try {
                                    val seedBytes: ByteArray
                                    val seedStr: String

                                    if (importMode == "hex") {
                                        val hex = importSeed.trim().lowercase()
                                        if (hex.length != 64) {
                                            Handler(Looper.getMainLooper()).post {
                                                Toast.makeText(context, context.getString(R.string.xmr_hex_seed_length), Toast.LENGTH_LONG).show()
                                            }
                                            return@Thread
                                        }
                                        seedBytes = MoneroCrypto.decodeHexSeed(hex)
                                        seedStr = hex
                                    } else {
                                        val words = importSeed.trim().lowercase().split(Regex("\\s+"))
                                        if (words.size != 25) {
                                            Handler(Looper.getMainLooper()).post {
                                                Toast.makeText(context, context.getString(R.string.xmr_seed_25_words, words.size), Toast.LENGTH_LONG).show()
                                            }
                                            return@Thread
                                        }
                                        val wordList = loadWordList(context)
                                        seedBytes = MoneroCrypto.decodeMnemonic(words, wordList)
                                        seedStr = words.joinToString(" ")
                                    }

                                    val keys = MoneroCrypto.seedToKeys(seedBytes)
                                    XmrWalletStorage.saveWallet(context, seedStr, keys.address)
                                    Handler(Looper.getMainLooper()).post {
                                        wallet = XmrWalletStorage.XmrWalletInfo(seedStr, keys.address)
                                        showImport = false
                                        importSeed = ""
                                        Toast.makeText(context, context.getString(R.string.xmr_wallet_imported), Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Handler(Looper.getMainLooper()).post {
                                        Toast.makeText(context, context.getString(R.string.xmr_error, e.message ?: ""), Toast.LENGTH_LONG).show()
                                    }
                                }
                            }.start()
                        },
                        enabled = importSeed.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.xmr_import_generate))
                    }
                }
            } else {
                // ── Wallet exists ──

                // HERO: Receive (offline, always works)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = VoidAmber.copy(alpha = 0.1f)),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.xmr_receive), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.xmr_receive_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        QrCode(content = wallet!!.address, size = 200.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = wallet!!.address,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            OutlinedButton(onClick = { copyAddress(wallet!!.address) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.xmr_copy))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, wallet!!.address)
                                }
                                context.startActivity(Intent.createChooser(send, context.getString(R.string.xmr_receive)))
                            }) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.xmr_share))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Balance & sending → Monerujo
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.xmr_balance_via_monerujo), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.xmr_monerujo_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row {
                            Button(onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("monerujo://pay?address=${wallet!!.address}"))
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, context.getString(R.string.xmr_monerujo_missing), Toast.LENGTH_LONG).show()
                                }
                            }) {
                                Text(stringResource(R.string.xmr_send_monerujo))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://monerujo.io")))
                                } catch (_: Exception) {
                                    Toast.makeText(context, context.getString(R.string.xmr_monerujo_missing), Toast.LENGTH_LONG).show()
                                }
                            }) {
                                Text(stringResource(R.string.xmr_install_monerujo))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Seed backup
                OutlinedButton(
                    onClick = { showSeed = !showSeed },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (showSeed) stringResource(R.string.xmr_hide_seed) else stringResource(R.string.xmr_show_seed_backup))
                }
                if (showSeed) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = VoidRed.copy(alpha = 0.1f)),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.xmr_seed_warning),
                                style = MaterialTheme.typography.labelSmall,
                                color = VoidRed,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = wallet!!.seed,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Advanced (collapsed by default) ──
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(
                        (if (showAdvanced) "▾ " else "▸ ") + stringResource(R.string.xmr_advanced),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (showAdvanced) {
                    // Node status
                    OutlinedButton(
                        onClick = {
                            checkingNode = true
                            nodeStatus = ""
                            Thread {
                                try {
                                    val info = MoneroRpcClient.getDaemonInfo(nodeUrl)
                                    nodeHeight = info.height
                                    nodeStatus = context.getString(R.string.xmr_node_connected, info.network, info.height)
                                } catch (e: Exception) {
                                    nodeStatus = context.getString(R.string.xmr_node_failed, e.message?.take(60) ?: "")
                                }
                                checkingNode = false
                            }.start()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.xmr_check_node))
                    }
                    if (checkingNode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.xmr_checking_node), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (nodeStatus.isNotEmpty()) {
                        Text(
                            text = nodeStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (nodeStatus.startsWith(stringResource(R.string.xmr_status_connected))) VoidGreen else VoidRed,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Balance via wallet RPC
                    OutlinedButton(
                        onClick = {
                            if (walletRpcUrl.isBlank()) {
                                balanceError = context.getString(R.string.xmr_configure_rpc_first)
                                showNodeConfig = true
                                return@OutlinedButton
                            }
                            loadingBalance = true
                            balanceError = ""
                            Thread {
                                try {
                                    val bal = MoneroRpcClient.getBalance(walletRpcUrl)
                                    val txs = MoneroRpcClient.getTransfers(walletRpcUrl, nodeHeight)
                                    Handler(Looper.getMainLooper()).post {
                                        balance = bal.balance
                                        unlockedBalance = bal.unlockedBalance
                                        transfers = txs
                                        loadingBalance = false
                                    }
                                } catch (e: Exception) {
                                    Handler(Looper.getMainLooper()).post {
                                        balanceError = e.message?.take(80) ?: context.getString(R.string.xmr_unknown_error)
                                        loadingBalance = false
                                    }
                                }
                            }.start()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.xmr_refresh_balance))
                    }
                    if (loadingBalance) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.xmr_loading), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (balanceError.isNotEmpty()) {
                        Text(balanceError, style = MaterialTheme.typography.bodySmall, color = VoidRed, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (balance != null) {
                        Text(
                            text = stringResource(R.string.xmr_amount_xmr, MoneroRpcClient.formatXmr(balance!!)),
                            style = MaterialTheme.typography.headlineMedium,
                            color = VoidAmber,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            text = stringResource(R.string.xmr_unlocked, MoneroRpcClient.formatXmr(unlockedBalance ?: 0)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    // Transaction history (RPC)
                    if (transfers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.xmr_transactions), style = MaterialTheme.typography.titleSmall)
                        transfers.take(20).forEach { tx ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column {
                                        Text(
                                            text = if (tx.isIncoming) stringResource(R.string.xmr_received) else stringResource(R.string.xmr_sent),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (tx.isIncoming) VoidGreen else VoidRed,
                                        )
                                        Text(
                                            text = stringResource(R.string.xmr_tx_meta, tx.height, tx.confirmations),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                    Text(
                                        text = stringResource(R.string.xmr_amount_xmr, MoneroRpcClient.formatXmr(tx.amount)),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Node config
                    OutlinedButton(
                        onClick = { showNodeConfig = !showNodeConfig },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (showNodeConfig) stringResource(R.string.xmr_hide_node_config) else stringResource(R.string.xmr_configure_nodes))
                    }

                    if (showNodeConfig) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = nodeUrl,
                            onValueChange = { nodeUrl = it },
                            label = { Text(stringResource(R.string.xmr_daemon_url)) },
                            placeholder = { Text("https://node.moneroworld.com:18089") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = walletRpcUrl,
                            onValueChange = { walletRpcUrl = it },
                            label = { Text(stringResource(R.string.xmr_wallet_rpc_url)) },
                            placeholder = { Text("http://127.0.0.1:18083") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                XmrWalletStorage.saveNodeUrl(context, nodeUrl)
                                XmrWalletStorage.saveWalletRpcUrl(context, walletRpcUrl)
                                Toast.makeText(context, context.getString(R.string.xmr_node_config_saved), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.xmr_save_node_config))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Delete wallet
                    OutlinedButton(
                        onClick = {
                            XmrWalletStorage.deleteWallet(context)
                            wallet = null
                            balance = null
                            transfers = emptyList()
                            showAdvanced = false
                            Toast.makeText(context, context.getString(R.string.xmr_wallet_deleted), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.xmr_delete_wallet), color = VoidRed)
                    }
                }
            }
        }
    }
}

/** Load the 1626-word Monero mnemonic word list from resources. */
private fun loadWordList(context: Context): List<String> {
    val res = context.resources.openRawResource(R.raw.monero_words)
    return res.bufferedReader().use { it.readLines() }.filter { it.isNotBlank() }
}
