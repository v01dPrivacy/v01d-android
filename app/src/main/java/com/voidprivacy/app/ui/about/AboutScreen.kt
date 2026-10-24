package com.voidprivacy.app.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voidprivacy.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
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
            Text("v01d", style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.about_version, com.voidprivacy.app.BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyLarge)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.about_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.about_links), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    LinkRow(stringResource(R.string.about_link_source)) { openUrl("https://github.com/v01dPrivacy/v01d-android") }
                    LinkRow(stringResource(R.string.about_link_license)) { openUrl("https://www.gnu.org/licenses/gpl-3.0.html") }
                    LinkRow(stringResource(R.string.about_link_tor)) { openUrl("https://torproject.org") }
                    LinkRow(stringResource(R.string.about_link_grapheneos)) { openUrl("https://grapheneos.org") }
                    LinkRow(stringResource(R.string.about_link_guardian)) { openUrl("https://guardianproject.info") }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.about_built_with), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.about_built_bitcoinj), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.about_built_zxing), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.about_built_tor_android), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.about_built_sqlcipher), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.about_built_room), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.about_built_security_crypto), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.about_built_biometric), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.about_built_compose), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    )
}
