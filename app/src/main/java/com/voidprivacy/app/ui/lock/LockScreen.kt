package com.voidprivacy.app.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.voidprivacy.app.R
import com.voidprivacy.app.ui.theme.VoidRed

/**
 * Lock screen: PIN entry + optional biometric unlock.
 */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val inCooldown = System.currentTimeMillis() < LockManager.cooldownUntil

    val hasBiometric = BiometricManager.from(context)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    fun tryUnlock() {
        if (LockManager.verifyPin(context, pin)) {
            onUnlocked()
        } else {
            error = if (System.currentTimeMillis() < LockManager.cooldownUntil) {
                context.getString(R.string.lock_too_many_attempts)
            } else {
                context.getString(R.string.lock_wrong_pin, 3 - LockManager.attempts)
            }
            pin = ""
        }
    }

    fun showBiometric() {
        val activity = context as? FragmentActivity ?: return
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                LockManager.unlock()
                onUnlocked()
            }
            override fun onAuthenticationError(code: Int, msg: CharSequence) {
                error = msg.toString()
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.lock_biometric_title))
            .setSubtitle(context.getString(R.string.lock_biometric_subtitle))
            .setNegativeButtonText(context.getString(R.string.lock_biometric_use_pin))
            .build()
        prompt.authenticate(info)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6) pin = it },
            label = { Text(stringResource(R.string.pin_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            enabled = !inCooldown,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { tryUnlock() },
            enabled = pin.length >= 4 && !inCooldown,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.lock_unlock))
        }

        if (hasBiometric) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showBiometric() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.lock_use_fingerprint))
            }
        }

        error?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(it, color = VoidRed, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * First-launch PIN setup screen.
 */
@Composable
fun PinSetupScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            text = stringResource(R.string.setup_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6) pin = it },
            label = { Text(stringResource(R.string.setup_pin_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = confirm,
            onValueChange = { if (it.length <= 6) confirm = it },
            label = { Text(stringResource(R.string.setup_confirm_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                when {
                    pin.length < 4 -> error = context.getString(R.string.setup_pin_short)
                    pin != confirm -> error = context.getString(R.string.setup_pin_mismatch)
                    else -> {
                        LockManager.setPin(context, pin)
                        onComplete()
                    }
                }
            },
            enabled = pin.length >= 4 && confirm.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.setup_set_pin))
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { onComplete() }, // skip PIN setup
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.setup_skip))
        }

        error?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(it, color = VoidRed, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
