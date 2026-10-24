package com.voidprivacy.app.tools.message

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.voidprivacy.app.R
import com.voidprivacy.app.ui.components.QrCode
import com.voidprivacy.app.ui.theme.VoidAccent

/**
 * Add a contact by scanning their Void Message QR (identity pubkey + name),
 * and display our own QR for others to scan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var scannedName by remember { mutableStateOf("") }
    var scannedKey by remember { mutableStateOf<org.signal.libsignal.protocol.IdentityKey?>(null) }
    var myQr by remember { mutableStateOf(IdentityManager.exportQrPayload(context) ?: "") }
    var myFingerprint by remember { mutableStateOf(IdentityManager.fingerprint(context) ?: "") }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { payload ->
            val parsed = IdentityManager.parseQrPayload(payload)
            if (parsed != null) {
                scannedName = parsed.first
                scannedKey = parsed.second
            } else {
                Toast.makeText(context, context.getString(R.string.addcontact_invalid_qr), Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.addcontact_title)) },
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Scan a contact ──
            Text(stringResource(R.string.addcontact_scan_qr), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val options = ScanOptions().apply {
                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        setPrompt(context.getString(R.string.addcontact_scan_prompt))
                        setBeepEnabled(false)
                        setOrientationLocked(true)
                    }
                    scanLauncher.launch(options)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.addcontact_scan_button))
            }

            if (scannedKey != null) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = scannedName,
                    onValueChange = { scannedName = it },
                    label = { Text(stringResource(R.string.addcontact_contact_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val key = scannedKey ?: return@Button
                        val contact = MessageStorage.addContact(context, scannedName.ifBlank { context.getString(R.string.addcontact_unknown) }, key)
                        // Publish our bundle for this relation NOW so the new contact
                        // can start a session even before the periodic republish loop runs.
                        Thread { MessageManager.publishBundleForContact(context, contact.token) }.start()
                        Toast.makeText(context, context.getString(R.string.addcontact_added, contact.name), Toast.LENGTH_SHORT).show()
                        scannedKey = null
                        scannedName = ""
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.addcontact_save))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── My QR ──
            Text(stringResource(R.string.addcontact_your_qr), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = VoidAccent.copy(alpha = 0.08f)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (myQr.isNotEmpty()) {
                        QrCode(content = myQr, size = 220.dp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.addcontact_fingerprint_label),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    val fpWords = IdentityManager.fingerprintWords(context)
                    if (fpWords.isNotEmpty()) {
                        Text(
                            text = fpWords.joinToString(" · "),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.addcontact_fingerprint_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = myFingerprint.chunked(8).joinToString(" "),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
