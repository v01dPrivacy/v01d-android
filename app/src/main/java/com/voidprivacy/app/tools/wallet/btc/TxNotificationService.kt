package com.voidprivacy.app.tools.wallet.btc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.voidprivacy.app.AppSettings
import com.voidprivacy.app.MainActivity
import com.voidprivacy.app.R

/**
 * Foreground service that polls Electrum for new incoming transactions.
 * Compares current UTXOs with last-known state and shows a notification
 * when new funds arrive.
 *
 * Polls every 30 seconds. Lightweight — only checks UTXO set changes.
 */
object TxNotificationService {

    private const val TAG = "TxNotify"
    private const val CHANNEL_ID = "tx_notifications"
    private const val PREFS = "void_tx_notify"
    private const val KEY_KNOWN_UTXOS = "known_utxos"
    private const val KEY_ENABLED = "enabled"
    private const val POLL_INTERVAL_MS = 30_000L

    @Volatile private var running = false
    private var pollThread: Thread? = null

    fun isEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) start(context) else stop()
    }

    fun start(context: Context) {
        if (running) return
        val appCtx = context.applicationContext

        // Check wallet exists
        val wallet = WalletStorage.loadWallet(appCtx) ?: run {
            Log.w(TAG, "No wallet — notifications disabled")
            return
        }

        running = true
        createChannel(appCtx)

        pollThread = Thread({
            Log.i(TAG, "TX notification polling started")
            // Initialize known UTXOs on first run (don't notify for existing balance)
            val known = loadKnownUtxos(appCtx)
            if (known.isEmpty()) {
                val current = fetchUtxoSet(appCtx, wallet.mnemonic)
                saveKnownUtxos(appCtx, current)
            }

            while (running) {
                try {
                    Thread.sleep(POLL_INTERVAL_MS)
                    if (!running) break

                    val current = fetchUtxoSet(appCtx, wallet.mnemonic)
                    val previous = loadKnownUtxos(appCtx)

                    // Find new UTXOs
                    val newUtxos = current - previous
                    if (newUtxos.isNotEmpty()) {
                        // Parse "txid:vout:value" strings
                        var totalNew = 0L
                        var firstTxid = ""
                        for (entry in newUtxos) {
                            val parts = entry.split(":")
                            if (parts.size >= 3) {
                                totalNew += parts[2].toLongOrNull() ?: 0
                                if (firstTxid.isEmpty()) firstTxid = parts[0]
                            }
                        }
                        showNotification(appCtx, totalNew, firstTxid)
                        saveKnownUtxos(appCtx, current)
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Poll error: ${e.message}")
                }
            }
            Log.i(TAG, "TX notification polling stopped")
        }, "TxNotify-Poller")
        pollThread?.start()
    }

    fun stop() {
        running = false
        pollThread?.interrupt()
        pollThread = null
    }

    private fun fetchUtxoSet(context: Context, mnemonic: String): Set<String> {
        val host = com.voidprivacy.app.AppSettings.getElectrumHost(context)
        val port = com.voidprivacy.app.AppSettings.getElectrumPort(context)
        val params = NetworkHelper.getParams(context)
        val addresses = UtxoManager.deriveAddresses(mnemonic, params = params)
        val utxoSet = mutableSetOf<String>()

        for (addr in addresses) {
            try {
                val scriptBytes = org.bitcoinj.script.ScriptBuilder.createOutputScript(addr).program
                val sh = ElectrumClient.scriptHash(scriptBytes)
                val utxos = ElectrumClient.listUnspent(sh, host, port)
                for (utxo in utxos) {
                    utxoSet.add("${utxo.txid}:${utxo.vout}:${utxo.value}")
                }
            } catch (_: Exception) { /* skip */ }
        }
        return utxoSet
    }

    private fun showNotification(context: Context, amountSats: Long, txid: String) {
        val btc = "%.8f".format(amountSats / 100_000_000.0).trimEnd('0').trimEnd('.')
        val localized = AppSettings.wrapLocale(context)
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(localized.getString(R.string.txnotif_title))
            .setContentText(localized.getString(R.string.txnotif_received, btc))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(localized.getString(R.string.txnotif_big_text, btc, txid)))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(txid.hashCode(), notification)
        Log.i(TAG, "Notification: received $btc BTC")
    }

    private fun createChannel(context: Context) {
        val localized = AppSettings.wrapLocale(context)
        val channel = NotificationChannel(
            CHANNEL_ID,
            localized.getString(R.string.txnotif_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = localized.getString(R.string.txnotif_channel_description)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun loadKnownUtxos(context: Context): Set<String> {
        val raw = prefs(context).getStringSet(KEY_KNOWN_UTXOS, emptySet()) ?: emptySet()
        return raw.toSet()
    }

    private fun saveKnownUtxos(context: Context, utxos: Set<String>) {
        prefs(context).edit().putStringSet(KEY_KNOWN_UTXOS, utxos).apply()
    }

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
