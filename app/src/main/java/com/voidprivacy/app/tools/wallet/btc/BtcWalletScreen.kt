package com.voidprivacy.app.tools.wallet.btc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.voidprivacy.app.R
import com.voidprivacy.app.ui.components.QrCode
import com.voidprivacy.app.ui.theme.VoidAmber
import com.voidprivacy.app.ui.theme.VoidGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet
import org.bitcoinj.params.MainNetParams
import java.security.SecureRandom

/**
 * Offline BTC wallet: BIP-39 seed generation + receive address.
 * Inspired by BoldWallet's seedless approach, but using standard BIP-39
 * for MVP (TSS threshold signatures are a phase-2 goal).
 * All key generation happens on-device. No network calls.
 */
object BtcWalletEngine {
    data class WalletInfo(
        val mnemonic: String,
        val receiveAddress: String,
    )

    fun createWallet(params: org.bitcoinj.core.NetworkParameters = MainNetParams.get()): WalletInfo {
        val entropy = ByteArray(16) // 128-bit → 12 words
        SecureRandom().nextBytes(entropy)
        val mnemonic = MnemonicCode.INSTANCE.toMnemonic(entropy)
        val phrase = mnemonic.joinToString(" ")

        val seed = DeterministicSeed(phrase, null, "", 0L)
        val wallet = Wallet.fromSeed(params, seed)
        val address = wallet.currentReceiveAddress()

        return WalletInfo(
            mnemonic = phrase,
            receiveAddress = address.toString(),
        )
    }

    /**
     * Export wallet as JSON for external signers (Sparrow, Coldcard, Specter).
     * Contains xpub + first 5 receive addresses. No private keys.
     */
    fun exportWallet(mnemonic: String, params: org.bitcoinj.core.NetworkParameters = MainNetParams.get()): String {
        val seed = DeterministicSeed(mnemonic, null, "", 0L)
        val wallet = Wallet.fromSeed(params, seed)

        // Get the watching key (xpub) for the account
        val xpub = wallet.activeKeyChain.watchingKey.serializePubB58(params)

        // Derive first 5 receive addresses using wallet's built-in method
        val addresses = mutableListOf(wallet.currentReceiveAddress().toString())
        repeat(4) {
            addresses.add(wallet.freshReceiveAddress().toString())
        }

        val networkName = if (params is org.bitcoinj.params.TestNet3Params) "testnet" else "mainnet"

        return buildString {
            append("{\n")
            append("  \"label\": \"v01d Wallet\",\n")
            append("  \"network\": \"$networkName\",\n")
            append("  \"xpub\": \"$xpub\",\n")
            append("  \"addresses\": [\n")
            addresses.forEachIndexed { i, addr ->
                val comma = if (i < addresses.size - 1) "," else ""
                append("    \"$addr\"$comma\n")
            }
            append("  ]\n")
            append("}")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BtcWalletScreen(onBack: () -> Unit, onPsbt: () -> Unit = {}, onSend: () -> Unit = {}, onHistory: () -> Unit = {}) {
    val context = LocalContext.current
    var wallet by remember { mutableStateOf(WalletStorage.loadWallet(context)) }
    var showMnemonic by remember { mutableStateOf(false) }
    var importPhrase by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var verifyWords by remember { mutableStateOf<List<Pair<Int, String>>?>(null) }
    var verifyAnswers by remember { mutableStateOf("") }
    var balanceSats by remember { mutableStateOf<Long?>(null) }
    var balanceLoading by remember { mutableStateOf(false) }

    val network = com.voidprivacy.app.AppSettings.getNetwork(context)

    // Fetch balance when wallet exists
    LaunchedEffect(wallet?.mnemonic) {
        val w = wallet ?: return@LaunchedEffect
        balanceLoading = true
        try {
            val host = com.voidprivacy.app.AppSettings.getElectrumHost(context)
            val port = com.voidprivacy.app.AppSettings.getElectrumPort(context)
            val state = withContext(Dispatchers.IO) {
                UtxoManager.fetchWalletState(w.mnemonic, host, port)
            }
            balanceSats = state.balance
        } catch (_: Exception) {
            balanceSats = null // offline
        } finally {
            balanceLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wallet_title)) },
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
            // Network banner — always visible, no surprises about where money goes
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (network == "testnet") VoidAmber.copy(alpha = 0.15f) else VoidGreen.copy(alpha = 0.12f),
                ),
            ) {
                Text(
                    text = stringResource(
                        if (network == "testnet") R.string.wallet_banner_testnet else R.string.wallet_banner_mainnet,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (network == "testnet") VoidAmber else VoidGreen,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (wallet == null) {
                Text(
                    text = stringResource(R.string.wallet_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        try {
                            val w = BtcWalletEngine.createWallet(NetworkHelper.getParams(context))
                            WalletStorage.saveWallet(context, w.mnemonic, w.receiveAddress)
                            wallet = w
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.wallet_error, e.message), Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.wallet_generate))
                }
                Text(
                    text = stringResource(R.string.wallet_generate_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Import wallet
                if (!showImport) {
                    OutlinedButton(
                        onClick = { showImport = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.wallet_import_btn))
                    }
                } else {
                    OutlinedTextField(
                        value = importPhrase,
                        onValueChange = { importPhrase = it },
                        label = { Text(stringResource(R.string.wallet_seed_label)) },
                        placeholder = { Text(stringResource(R.string.wallet_seed_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            try {
                                val words = importPhrase.trim().lowercase().split(Regex("\\s+"))
                                require(words.size == 12) { context.getString(R.string.wallet_need_12) }
                                val phrase = words.joinToString(" ")
                                // Validate by trying to create a wallet
                                val seed = org.bitcoinj.wallet.DeterministicSeed(phrase, null, "", 0L)
                                val w = org.bitcoinj.wallet.Wallet.fromSeed(
                                    NetworkHelper.getParams(context), seed
                                )
                                val addr = w.currentReceiveAddress().toString()
                                WalletStorage.saveWallet(context, phrase, addr)
                                wallet = BtcWalletEngine.WalletInfo(mnemonic = phrase, receiveAddress = addr)
                                showImport = false
                                importPhrase = ""
                                Toast.makeText(context, context.getString(R.string.wallet_imported), Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.wallet_invalid_seed, e.message), Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = importPhrase.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.wallet_import))
                    }
                }
            } else {
                val w = wallet!!

                // ── HERO: Receive ──
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.wallet_receive_address), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.wallet_receive_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        QrCode(
                            content = "bitcoin:${w.receiveAddress}",
                            size = 200.dp,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = w.receiveAddress,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("BTC Address", w.receiveAddress))
                                    Toast.makeText(context, context.getString(R.string.wallet_address_copied), Toast.LENGTH_SHORT).show()
                                },
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.wallet_copy_address))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, w.receiveAddress)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(send, context.getString(R.string.wallet_share_address)))
                                },
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.wallet_share))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Balance
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.wallet_balance), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (balanceLoading) {
                            Text(stringResource(R.string.wallet_loading), style = MaterialTheme.typography.bodyMedium)
                        } else if (balanceSats != null) {
                            Text(
                                text = stringResource(
                                    R.string.wallet_amount_btc,
                                    "%.8f".format((balanceSats ?: 0) / 100_000_000.0).trimEnd('0').trimEnd('.'),
                                ),
                                style = MaterialTheme.typography.headlineMedium,
                                color = VoidGreen,
                            )
                            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = stringResource(R.string.wallet_sats, balanceSats ?: 0),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                balanceSats?.let { b ->
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
                        } else {
                            Text(stringResource(R.string.wallet_offline), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Primary actions ──
                Button(onClick = onSend, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.wallet_send_btn))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.wallet_history_btn))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Advanced (collapsed) ──
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(
                        (if (showAdvanced) "▾ " else "▸ ") + stringResource(R.string.wallet_advanced),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (showAdvanced) {
                    // PSBT signer
                    OutlinedButton(onClick = onPsbt, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.wallet_psbt_btn))
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Export xpub
                    OutlinedButton(
                        onClick = {
                            try {
                                val json = BtcWalletEngine.exportWallet(w.mnemonic, NetworkHelper.getParams(context))
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(android.content.Intent.EXTRA_TEXT, json)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(R.string.wallet_export_chooser)))
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, context.getString(R.string.wallet_export_failed, e.message), android.widget.Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.wallet_export_btn))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Seed phrase (hidden by default)
                    Text(
                        text = stringResource(R.string.wallet_recovery_seed),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (!showMnemonic) {
                        OutlinedButton(
                            onClick = {
                                showMnemonic = true
                                // Pick 3 random word positions for verification
                                val words = w.mnemonic.split(" ")
                                val positions = (0 until 12).shuffled().take(3).sorted()
                                verifyWords = positions.map { it to words[it] }
                                verifyAnswers = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.wallet_show_seed))
                        }
                        Text(
                            text = stringResource(R.string.wallet_seed_warning),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = w.mnemonic,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                // Verification: type 3 words
                                verifyWords?.let { vw ->
                                    Text(
                                        text = stringResource(R.string.wallet_verify_words, vw.joinToString(", #") { (pos, _) -> "${pos + 1}" }),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = verifyAnswers,
                                        onValueChange = { verifyAnswers = it },
                                        placeholder = { Text(stringResource(R.string.wallet_verify_placeholder)) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    val expected = vw.joinToString(" ") { (_, word) -> word }
                                    val answered = verifyAnswers.trim().lowercase().split(Regex("\\s+")).joinToString(" ")
                                    val verified = answered == expected

                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Seed", w.mnemonic))
                                            Toast.makeText(context, context.getString(R.string.wallet_seed_copied), Toast.LENGTH_LONG).show()
                                        },
                                        enabled = verified,
                                    ) {
                                        Text(stringResource(if (verified) R.string.wallet_copy_seed else R.string.wallet_verify_to_copy))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Reset
                    OutlinedButton(
                        onClick = {
                            WalletStorage.deleteWallet(context)
                            wallet = null
                            showMnemonic = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.wallet_delete_generate))
                    }
                }
            }
        }
    }
}
