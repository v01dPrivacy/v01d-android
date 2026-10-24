package com.voidprivacy.app.tools.score

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voidprivacy.app.R
import com.voidprivacy.app.ui.theme.VoidAmber
import com.voidprivacy.app.ui.theme.VoidGreen
import com.voidprivacy.app.ui.theme.VoidRed
import java.io.File

data class CheckItem(
    val name: String,
    val passed: Boolean,
    val detail: String,
)

/**
 * Privacy Score: aggregates device-level privacy checks into one number.
 * All checks are local — no network calls.
 */
object PrivacyScorer {

    fun isGrapheneOS(): Boolean {
        return try {
            val brand = Build.BRAND.lowercase()
            val product = Build.PRODUCT.lowercase()
            brand.contains("graphene") || product.contains("graphene") ||
                File("/system/priv-app/GrapheneOS").exists() ||
                File("/system/etc/permissions/org.grapheneos").exists()
        } catch (_: Exception) {
            false
        }
    }

    fun runChecks(context: Context): List<CheckItem> {
        val checks = mutableListOf<CheckItem>()

        // 1. Verified Boot — read system property
        val verifiedBoot = try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", "ro.boot.verifiedbootstate"))
            process.inputStream.bufferedReader().readText().trim()
        } catch (_: Exception) { "" }
        checks.add(
            CheckItem(
                name = context.getString(R.string.score_check_verified_boot),
                passed = verifiedBoot == "green" || verifiedBoot.isEmpty(),
                detail = if (verifiedBoot.isNotEmpty()) context.getString(R.string.score_verified_boot_state, verifiedBoot) else context.getString(R.string.score_verified_boot_unknown),
            )
        )

        // 2. GrapheneOS detection
        val graphene = isGrapheneOS()
        checks.add(
            CheckItem(
                name = context.getString(R.string.score_check_graphene),
                passed = graphene,
                detail = if (graphene) context.getString(R.string.score_graphene_yes) else context.getString(R.string.score_graphene_no),
            )
        )

        // 3. SELinux enforcing
        val selinux = try {
            val process = Runtime.getRuntime().exec(arrayOf("getenforce"))
            process.inputStream.bufferedReader().readText().trim()
        } catch (_: Exception) { "unknown" }
        checks.add(
            CheckItem(
                name = context.getString(R.string.score_check_selinux),
                passed = selinux.equals("Enforcing", ignoreCase = true),
                detail = context.getString(R.string.score_selinux_status, selinux),
            )
        )

        // 4. File-Based Encryption
        checks.add(
            CheckItem(
                name = context.getString(R.string.score_check_encryption),
                passed = Build.VERSION.SDK_INT >= 24,
                detail = context.getString(R.string.score_encryption_detail, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
            )
        )

        // 5. Screen lock
        checks.add(
            CheckItem(
                name = context.getString(R.string.score_check_screen_lock),
                passed = true,
                detail = context.getString(R.string.score_screen_lock_detail),
            )
        )

        // 6. USB debugging — use ContentResolver
        val usbDebug = try {
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.ADB_ENABLED, 0) == 1
        } catch (_: Exception) { false }
        checks.add(
            CheckItem(
                name = context.getString(R.string.score_check_usb_debugging),
                passed = !usbDebug,
                detail = if (usbDebug) context.getString(R.string.score_usb_on) else context.getString(R.string.score_usb_off),
            )
        )

        // 7. VPN active
        val vpnActive = try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(network)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        } catch (_: Exception) { false }
        checks.add(
            CheckItem(
                name = context.getString(R.string.score_check_vpn),
                passed = vpnActive,
                detail = if (vpnActive) context.getString(R.string.score_vpn_on) else context.getString(R.string.score_vpn_off),
            )
        )

        // 8. Private DNS (DNS-over-TLS)
        val privateDns = try {
            Settings.Global.getString(context.contentResolver, "private_dns_mode")
        } catch (_: Exception) { null }
        checks.add(
            CheckItem(
                name = context.getString(R.string.score_check_private_dns),
                passed = privateDns == "hostname" || privateDns == "opportunistic",
                detail = when (privateDns) {
                    "hostname" -> context.getString(R.string.score_dns_strict)
                    "opportunistic" -> context.getString(R.string.score_dns_opportunistic)
                    else -> context.getString(R.string.score_dns_off)
                },
            )
        )

        return checks
    }

    fun calculateScore(checks: List<CheckItem>): Int {
        if (checks.isEmpty()) return 0
        val passed = checks.count { it.passed }
        return (passed * 100) / checks.size
    }

    // Score history
    private const val HISTORY_KEY = "score_history"

    fun saveScore(context: Context, score: Int) {
        val prefs = context.getSharedPreferences("void_score", Context.MODE_PRIVATE)
        val history = prefs.getString(HISTORY_KEY, "") ?: ""
        val entry = "${System.currentTimeMillis()}:$score"
        val updated = if (history.isEmpty()) entry else "$history,$entry"
        // Keep last 20 entries
        val trimmed = updated.split(",").takeLast(20).joinToString(",")
        prefs.edit().putString(HISTORY_KEY, trimmed).apply()
    }

    fun getHistory(context: Context): List<Pair<Long, Int>> {
        val prefs = context.getSharedPreferences("void_score", Context.MODE_PRIVATE)
        val history = prefs.getString(HISTORY_KEY, "") ?: ""
        if (history.isEmpty()) return emptyList()
        return history.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) {
                try { parts[0].toLong() to parts[1].toInt() } catch (_: Exception) { null }
            } else null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScoreScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val checks = remember { PrivacyScorer.runChecks(context) }
    val score = remember { PrivacyScorer.calculateScore(checks) }
    val history = remember { PrivacyScorer.getHistory(context) }

    // Save score on first composition
    LaunchedEffect(score) {
        PrivacyScorer.saveScore(context, score)
    }

    val scoreColor = when {
        score >= 80 -> VoidGreen
        score >= 50 -> VoidAmber
        else -> VoidRed
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.score_title)) },
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
            // Score circle
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = scoreColor.copy(alpha = 0.1f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "$score",
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor,
                    )
                    Text(
                        text = "/ 100",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when {
                            score >= 80 -> stringResource(R.string.score_posture_strong)
                            score >= 50 -> stringResource(R.string.score_posture_moderate)
                            else -> stringResource(R.string.score_posture_weak)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.score_device_checks),
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(12.dp))

            checks.forEach { check ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (check.passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (check.passed) VoidGreen else VoidAmber,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = check.name,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = check.detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.score_all_local),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Share score
            OutlinedButton(
                onClick = {
                    val summary = buildString {
                        append(context.getString(R.string.score_share_summary, score))
                        checks.forEach { c ->
                            append("${if (c.passed) "✅" else "⚠️"} ${c.name}\n")
                        }
                    }
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, summary)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.score_share_score)))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.score_share_score))
            }

            // History
            if (history.size > 1) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.score_history), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                history.takeLast(5).reversed().forEach { (ts, sc) ->
                    val date = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(ts))
                    Text(
                        text = stringResource(R.string.score_history_entry, date, sc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
