package com.voidprivacy.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.voidprivacy.app.navigation.VoidNavHost
import com.voidprivacy.app.ui.lock.LockManager
import com.voidprivacy.app.ui.lock.LockScreen
import com.voidprivacy.app.ui.lock.PinSetupScreen
import com.voidprivacy.app.ui.theme.VoidTheme

class MainActivity : FragmentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppSettings.wrapLocale(newBase))
    }

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Tor service checks on start */ }

    private var lastBackgrounded = 0L
    private var showSetup by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppSettings.initTheme(this)
        requestNotificationPermission()

        // Prevent screenshots/screen recording of wallet data (release only:
        // keeping debug screenshots possible for store assets)
        if (!BuildConfig.DEBUG) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        // Determine initial screen
        showSetup = !LockManager.hasPin(this)
        if (!LockManager.hasPin(this)) {
            // No PIN configured (user skipped setup): there is nothing to verify
            // against, so the app must never start in the locked state.
            LockManager.unlock()
        } else if (LockManager.isLocked) {
            // Stay locked until the user unlocks with PIN/biometric
        }

        setContent {
            VoidTheme {
                when {
                    showSetup -> PinSetupScreen(
                        onComplete = {
                            showSetup = false
                            // "Skip" leaves no PIN hash: verifyPin can never succeed,
                            // so we MUST unlock here or the user is locked out forever.
                            LockManager.unlock()
                        },
                    )
                    LockManager.isLocked -> LockScreen(
                        onUnlocked = { LockManager.unlock() }
                    )
                    else -> {
                        // Auto-start TX notifications if enabled
                        if (com.voidprivacy.app.tools.wallet.btc.TxNotificationService.isEnabled(this)) {
                            com.voidprivacy.app.tools.wallet.btc.TxNotificationService.start(this)
                        }
                        VoidNavHost()
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        lastBackgrounded = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        // Auto-lock if backgrounded longer than timeout
        if (LockManager.hasPin(this) && lastBackgrounded > 0) {
            val timeout = LockManager.getTimeout(this)
            if (System.currentTimeMillis() - lastBackgrounded > timeout) {
                LockManager.lock()
            }
        }
        lastBackgrounded = 0L
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
