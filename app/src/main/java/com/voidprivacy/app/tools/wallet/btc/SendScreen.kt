package com.voidprivacy.app.tools.wallet.btc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.voidprivacy.app.R
import com.voidprivacy.app.ui.theme.VoidGreen
import com.voidprivacy.app.ui.theme.VoidRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.TransactionOutput
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(onBack: () -> Unit, onPickContact: () -> Unit = {}, initialRecipient: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var recipient by remember { mutableStateOf(initialRecipient ?: "") }
    var amountBtc by remember { mutableStateOf("") }
    var feeSatVb by remember { mutableStateOf("10") }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var balance by remember { mutableStateOf<Long?>(null) }
    var feeEstimates by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) } // (label, sat/vB)
    var showManualFee by remember { mutableStateOf(false) }

    val walletInfo = WalletStorage.loadWallet(context)
    val electrumHost = com.voidprivacy.app.AppSettings.getElectrumHost(context)
    val electrumPort = com.voidprivacy.app.AppSettings.getElectrumPort(context)

    // QR scanner launcher
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { scanned ->
            // Parse BIP-21 URI: bitcoin:<address>?amount=<btc>&label=<label>
            val uri = if (scanned.startsWith("bitcoin:")) scanned.removePrefix("bitcoin:") else scanned
            val parts = uri.split("?")
            recipient = parts[0]
            if (parts.size > 1) {
                val params = parts[1].split("&").associate {
                    val kv = it.split("=", limit = 2)
                    kv[0] to (kv.getOrNull(1) ?: "")
                }
                params["amount"]?.let { amt ->
                    amountBtc = amt
                }
            }
        }
    }

    // Fetch balance once on first composition (+ live fee estimates + fiat rate)
    LaunchedEffect(walletInfo?.mnemonic) {
        val wi = walletInfo ?: return@LaunchedEffect
        loading = true
        try {
            val state = withContext(Dispatchers.IO) {
                UtxoManager.fetchWalletState(wi.mnemonic, electrumHost, electrumPort)
            }
            balance = state.balance
        } catch (e: Exception) {
            error = context.getString(R.string.send_failed_balance, e.message)
        } finally {
            loading = false
        }
        // Fee presets from real estimates: Slow ≈ 6 blocks, Normal ≈ 3, Fast ≈ next block.
        try {
            val est = withContext(Dispatchers.IO) {
                listOf(
                    context.getString(R.string.send_fee_slow) to estimateSatVb(electrumHost, electrumPort, 6),
                    context.getString(R.string.send_fee_normal) to estimateSatVb(electrumHost, electrumPort, 3),
                    context.getString(R.string.send_fee_fast) to estimateSatVb(electrumHost, electrumPort, 1),
                )
            }.filter { it.second > 0 }
            if (est.isNotEmpty()) {
                feeEstimates = est
                // Default: Normal (middle preset)
                est.getOrNull(1)?.let { feeSatVb = it.second.toString() }
            }
        } catch (_: Exception) {
            // keep the 10 sat/vB fallback
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.send_title)) },
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
            // Balance
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.wallet_balance), style = MaterialTheme.typography.titleMedium)
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                    } else {
                        Text(
                            text = if (balance != null) stringResource(R.string.wallet_amount_btc, formatBtc(balance ?: 0)) else "—",
                            style = MaterialTheme.typography.headlineMedium,
                            color = VoidGreen,
                        )
                        Text(
                            text = stringResource(R.string.send_satoshis, balance ?: 0),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        balance?.let { b ->
                            val eur = FiatPrice.satsToEurLabel(b)
                            if (eur.isNotEmpty()) {
                                Text(
                                    text = eur,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it },
                label = { Text(stringResource(R.string.send_recipient_label)) },
                placeholder = { Text("bc1q…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    val options = ScanOptions()
                    options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    options.setPrompt(context.getString(R.string.send_scan_prompt))
                    options.setBeepEnabled(false)
                    options.setOrientationLocked(true)
                    scanLauncher.launch(options)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.send_scan_btn))
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onPickContact,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.send_pick_contact))
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = amountBtc,
                onValueChange = { amountBtc = it },
                label = { Text(stringResource(R.string.send_amount_label)) },
                placeholder = { Text("0.001") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            amountBtc.toDoubleOrNull()?.let { amt ->
                val sats = (amt * 100_000_000).toLong()
                if (sats > 0) {
                    Text(
                        text = FiatPrice.satsToEurLabel(sats),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Fee presets from live Electrum estimates
            Text(stringResource(R.string.send_fee_title), style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            if (feeEstimates.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    feeEstimates.forEach { (label, satVb) ->
                        FilterChip(
                            selected = feeSatVb == satVb.toString(),
                            onClick = {
                                feeSatVb = satVb.toString()
                                showManualFee = false
                            },
                            label = { Text("$label · $satVb") },
                        )
                    }
                }
            }
            TextButton(onClick = { showManualFee = !showManualFee }) {
                Text(
                    stringResource(R.string.send_fee_manual),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (showManualFee) {
                OutlinedTextField(
                    value = feeSatVb,
                    onValueChange = { feeSatVb = it },
                    label = { Text(stringResource(R.string.send_fee_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    error = null
                    result = null
                    val wi = walletInfo ?: run { error = context.getString(R.string.send_no_wallet); return@Button }
                    val amount = try {
                        (amountBtc.toDouble() * 100_000_000).toLong()
                    } catch (_: Exception) {
                        error = context.getString(R.string.send_invalid_amount); return@Button
                    }
                    val fee = feeSatVb.toLongOrNull() ?: 10L

                    if (recipient.isBlank()) { error = context.getString(R.string.send_enter_recipient); return@Button }
                    if (amount <= 0) { error = context.getString(R.string.send_amount_positive); return@Button }

                    loading = true
                    scope.launch {
                        try {
                            val txid = withContext(Dispatchers.IO) {
                                buildSignBroadcast(wi.mnemonic, recipient, amount, fee, NetworkHelper.getParams(context), electrumHost, electrumPort, context)
                            }
                            result = txid
                        } catch (e: Exception) {
                            error = e.message ?: context.getString(R.string.send_failed_tx)
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading && walletInfo != null && recipient.isNotBlank() && amountBtc.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (loading) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                else Text(stringResource(R.string.send_btn))
            }

            error?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text("⚠ $it", color = VoidRed, style = MaterialTheme.typography.bodyMedium)
            }

            result?.let { txid ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = VoidGreen.copy(alpha = 0.1f)),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.send_broadcast), style = MaterialTheme.typography.titleMedium, color = VoidGreen)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.send_txid, txid), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("TXID", txid))
                            Toast.makeText(context, context.getString(R.string.send_txid_copied), Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.send_copy_txid))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.send_privacy_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/**
 * Build, sign, and broadcast a BTC transaction.
 * 1. Fetch UTXOs from Electrum
 * 2. Select coins (largest-first)
 * 3. Build tx with payment + change outputs
 * 4. Sign with wallet keys
 * 5. Broadcast via Electrum
 */
/**
 * Electrum fee estimate converted to sat/vB (ElectrumClient returns BTC/kB → ×100 000).
 * Returns 0 when the server answers with a sentinel (-1) or fails.
 */
private fun estimateSatVb(host: String, port: Int, blocks: Int): Long {
    return try {
        val btcPerKb = ElectrumClient.estimateFee(blocks, host, port)
        if (btcPerKb <= 0) 0L else Math.round(btcPerKb * 100_000.0)
    } catch (_: Exception) {
        0L
    }
}

private fun buildSignBroadcast(
    mnemonic: String,
    recipientAddr: String,
    amountSats: Long,
    feeSatVb: Long,
    params: org.bitcoinj.core.NetworkParameters,
    host: String = ElectrumClient.DEFAULT_HOST,
    port: Int = ElectrumClient.DEFAULT_PORT,
    context: Context? = null,
): String {
    fun err(res: Int) = context?.getString(res) ?: res.toString()

    // Validate recipient
    val toAddress = Address.fromString(params, recipientAddr)

    // Fetch UTXOs
    val state = UtxoManager.fetchWalletState(mnemonic, host, port)
    if (state.utxos.isEmpty()) throw RuntimeException(err(R.string.send_no_funds))

    // Coin selection
    val (selected, totalInput) = UtxoManager.selectCoins(state.utxos, amountSats, feeSatVb)
        ?: throw RuntimeException(err(R.string.send_insufficient))

    // Reconstruct wallet for signing
    val seed = DeterministicSeed(mnemonic, null, "", 0L)
    val wallet = Wallet.fromSeed(params, seed)

    // Build transaction
    val tx = Transaction(params)

    // Add inputs from selected UTXOs
    for (utxo in selected) {
        val hash = org.bitcoinj.core.Sha256Hash.wrap(utxo.txid)
        tx.addInput(hash, utxo.vout.toLong(), org.bitcoinj.script.ScriptBuilder.createEmpty())
    }

    // Payment output
    tx.addOutput(Coin.valueOf(amountSats), toAddress)

    // Change output (back to wallet)
    val estimatedFee = ((10.5 + 68.0 * selected.size + 31.0 * 2) * feeSatVb).toLong()
    val change = totalInput - amountSats - estimatedFee
    if (change > 546) { // dust threshold
        val changeAddr = wallet.freshAddress(org.bitcoinj.wallet.KeyChain.KeyPurpose.CHANGE)
        tx.addOutput(Coin.valueOf(change), changeAddr)
    }

    // Sign
    val request = SendRequest.forTx(tx)
    wallet.signTransaction(request)

    // Serialize and broadcast
    val hex = org.bitcoinj.core.Utils.HEX.encode(tx.bitcoinSerialize())
    return ElectrumClient.broadcast(hex, host, port)
}

private fun formatBtc(sats: Long): String {
    return "%.8f".format(sats / 100_000_000.0).trimEnd('0').trimEnd('.')
}
