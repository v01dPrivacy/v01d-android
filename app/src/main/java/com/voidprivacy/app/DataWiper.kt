package com.voidprivacy.app

import android.content.Context
import com.voidprivacy.app.tools.contacts.ContactsDatabase
import com.voidprivacy.app.tools.message.IdentityManager
import com.voidprivacy.app.tools.message.MessageManager
import com.voidprivacy.app.tools.message.MessageStorage
import com.voidprivacy.app.tools.wallet.btc.TxDatabase
import com.voidprivacy.app.tools.wallet.btc.WalletStorage
import com.voidprivacy.app.tools.wallet.xmr.XmrWalletStorage
import com.voidprivacy.app.ui.lock.LockManager
import java.io.File

/**
 * Securely wipes all Void app data.
 * Deletes wallets, databases, settings, score history, and app lock.
 */
object DataWiper {

    fun wipeAll(context: Context) {
        val appCtx = context.applicationContext

        // 1. Delete wallets
        WalletStorage.deleteWallet(appCtx)
        XmrWalletStorage.deleteWallet(appCtx)

        // 1b. Delete messaging identity + contacts/history (clears in-memory key caches too)
        IdentityManager.deleteIdentity(appCtx)
        MessageStorage.wipeAll(appCtx)
        MessageManager.reset()

        // 2. Delete databases
        appCtx.deleteDatabase("void_tx_history")
        appCtx.deleteDatabase("void_contacts")

        // 3. Delete score history
        appCtx.getSharedPreferences("void_score", Context.MODE_PRIVATE)
            .edit().clear().commit()

        // 4. Delete settings
        appCtx.getSharedPreferences("void_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()

        // 5. Delete app lock
        LockManager.removePin(appCtx)

        // 6. Delete DB encryption key
        appCtx.getSharedPreferences("void_db_key_encrypted", Context.MODE_PRIVATE)
            .edit().clear().commit()

        // 7. Best-effort: overwrite and delete any remaining shared prefs files
        val prefsDir = File(appCtx.applicationInfo.dataDir, "shared_prefs")
        prefsDir.listFiles()?.forEach { file ->
            try {
                // Overwrite with zeros before deleting
                val bytes = ByteArray(file.length().toInt())
                file.writeBytes(bytes)
                file.delete()
            } catch (_: Exception) {
                file.delete()
            }
        }

        // 8. Reset in-memory state
        AppSettings.initTheme(appCtx) // reset to defaults
    }
}
