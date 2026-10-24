package com.voidprivacy.app.tools.txchecker

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voidprivacy.app.R
import com.voidprivacy.app.ui.theme.VoidAmber
import com.voidprivacy.app.ui.theme.VoidGreen
import com.voidprivacy.app.ui.theme.VoidRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxCheckerScreen(onBack: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<TxPrivacyAnalyzer.AnalysisResult?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.txcheck_title)) },
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
                text = stringResource(R.string.txcheck_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(stringResource(R.string.txcheck_input_label)) },
                placeholder = { Text(stringResource(R.string.txcheck_input_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { result = TxPrivacyAnalyzer.analyzeInput(input) },
                enabled = input.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.txcheck_analyze))
            }

            result?.let { r ->
                Spacer(modifier = Modifier.height(24.dp))

                // TX summary
                r.txSummary?.let { summary ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.txcheck_transaction), style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(stringResource(R.string.txcheck_txid, summary.txid.take(16)), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.txcheck_inputs_outputs, summary.inputCount, summary.outputCount), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.txcheck_total_output, "%.8f".format(summary.totalOutput / 100_000_000.0)), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (r.score >= 0) {
                    // Score card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                r.score >= 80 -> VoidGreen.copy(alpha = 0.15f)
                                r.score >= 50 -> VoidAmber.copy(alpha = 0.15f)
                                else -> VoidRed.copy(alpha = 0.15f)
                            },
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${r.score}",
                                style = MaterialTheme.typography.headlineLarge,
                                color = when {
                                    r.score >= 80 -> VoidGreen
                                    r.score >= 50 -> VoidAmber
                                    else -> VoidRed
                                },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.txcheck_score_of),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Findings
                r.findings.forEach { finding ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when (finding.severity) {
                                        TxPrivacyAnalyzer.Severity.HIGH -> "⚠"
                                        TxPrivacyAnalyzer.Severity.MEDIUM -> "●"
                                        TxPrivacyAnalyzer.Severity.LOW -> "○"
                                    },
                                    color = when (finding.severity) {
                                        TxPrivacyAnalyzer.Severity.HIGH -> VoidRed
                                        TxPrivacyAnalyzer.Severity.MEDIUM -> VoidAmber
                                        TxPrivacyAnalyzer.Severity.LOW -> VoidGreen
                                    },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = finding.name,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = finding.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
