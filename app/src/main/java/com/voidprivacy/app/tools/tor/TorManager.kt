package com.voidprivacy.app.tools.tor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import org.torproject.jni.TorService

/**
 * Wraps Guardian Project's TorService (org.torproject.jni).
 * Manages start/stop and exposes observable status for Compose.
 */
object TorManager {
    var isRunning by mutableStateOf(false)
        private set
    var statusText by mutableStateOf("Stopped")
        private set

    private var receiver: BroadcastReceiver? = null

    fun start(context: Context) {
        val appCtx = context.applicationContext
        registerReceiver(appCtx)
        val intent = Intent(appCtx, TorService::class.java)
        intent.action = TorService.ACTION_START
        appCtx.startForegroundService(intent)
        isRunning = true
        statusText = "Starting…"
    }

    fun stop(context: Context) {
        val appCtx = context.applicationContext
        val intent = Intent(appCtx, TorService::class.java)
        appCtx.stopService(intent)
        isRunning = false
        statusText = "Stopped"
        unregisterReceiver(appCtx)
    }

    private fun registerReceiver(context: Context) {
        if (receiver != null) return
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    TorService.ACTION_STATUS -> {
                        val status = intent.getStringExtra(TorService.EXTRA_STATUS)
                        statusText = when (status) {
                            TorService.STATUS_ON -> "Connected"
                            TorService.STATUS_STARTING -> "Starting…"
                            TorService.STATUS_STOPPING -> "Stopping…"
                            TorService.STATUS_OFF -> "Stopped"
                            else -> status ?: "Unknown"
                        }
                        isRunning = status == TorService.STATUS_ON || status == TorService.STATUS_STARTING
                    }
                    TorService.ACTION_ERROR -> {
                        statusText = "Error"
                        isRunning = false
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(TorService.ACTION_STATUS)
            addAction(TorService.ACTION_ERROR)
        }
        ContextCompat.registerReceiver(
            context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun unregisterReceiver(context: Context) {
        receiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        receiver = null
    }
}
