package com.voidprivacy.app.tools.wallet.btc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voidprivacy.app.R
import com.voidprivacy.app.ui.theme.VoidGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PsbtSignerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var psbtInput by remember { mutableStateOf("") }
    var psbtData by remember { mutableStateOf<PsbtParser.PsbtData?>(null) }
    var signResult by remember { mutableStateOf<PsbtSigner.SignResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.psbt_title)) },
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
            Text(
                text = stringResource(R.string.psbt_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = psbtInput,
                onValueChange = { psbtInput = it },
                label = { Text(stringResource(R.string.psbt_input_label)) },
                placeholder = { Text("cHNidP8BAH0CAAAAA…") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 10,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    error = null
                    psbtData = null
                    try {
                        psbtData = PsbtParser.parse(psbtInput)
                    } catch (e: Exception) {
                        error = e.message ?: context.getString(R.string.psbt_parse_failed)
                    }
                },
                enabled = psbtInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.psbt_parse_btn))
            }

            error?.let { err ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "⚠ $err",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            psbtData?.let { data ->
                Spacer(modifier = Modifier.height(24.dp))

                // Transaction summary
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.psbt_unsigned), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.psbt_txid, data.unsignedTx.txId), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.psbt_inputs, data.inputCount), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.psbt_outputs, data.outputCount), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(
                                R.string.psbt_witness,
                                stringResource(if (data.hasWitnessUtxos) R.string.psbt_yes else R.string.psbt_no),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (data.bip32Paths.isNotEmpty()) {
                            Text(stringResource(R.string.psbt_bip32, data.bip32Paths.size), style = MaterialTheme.typography.bodyMedium)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.psbt_outputs_header), style = MaterialTheme.typography.titleMedium)
                        data.unsignedTx.outputs.forEachIndexed { idx, out ->
                            val unknown = stringResource(R.string.psbt_unknown)
                            val nonstandard = stringResource(R.string.psbt_nonstandard)
                            val addr = try {
                                out.scriptPubKey.getToAddress(org.bitcoinj.params.MainNetParams.get())?.toString()
                                    ?: unknown
                            } catch (_: Exception) {
                                nonstandard
                            }
                            val btc = "%.8f".format(out.value.value / 100_000_000.0)
                            Text(
                                stringResource(R.string.psbt_output_line, idx, addr, btc),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sign with wallet
                val walletInfo = WalletStorage.loadWallet(context)
                if (walletInfo != null && data.hasWitnessUtxos) {
                    Button(
                        onClick = {
                            error = null
                            signResult = null
                            scope.launch {
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        PsbtSigner.sign(data, walletInfo.mnemonic)
                                    }
                                    signResult = result
                                } catch (e: Exception) {
                                    error = context.getString(R.string.psbt_sign_failed, e.message)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.psbt_sign_btn))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                signResult?.let { result ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = VoidGreen.copy(alpha = 0.1f),
                        ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.psbt_signed),
                                style = MaterialTheme.typography.titleMedium,
                                color = VoidGreen,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.psbt_signed_count, result.signedCount, result.totalInputs), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.psbt_txid, result.txid), style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = result.signedTxHex.take(80) + "…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Signed TX", result.signedTxHex))
                                    Toast.makeText(context, context.getString(R.string.psbt_hex_copied), Toast.LENGTH_SHORT).show()
                                },
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.psbt_copy_hex))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // TXID copy
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("TXID", data.unsignedTx.txId.toString()))
                        Toast.makeText(context, context.getString(R.string.send_txid_copied), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.send_copy_txid))
                }
            }
        }
    }
}
