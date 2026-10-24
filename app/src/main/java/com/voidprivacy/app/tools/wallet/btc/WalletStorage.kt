package com.voidprivacy.app.tools.wallet.btc

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted on-device wallet storage.
 * Uses AndroidX Security Crypto (EncryptedSharedPreferences) —
 * keys are stored in the Android Keystore, never in plain text.
 */
object WalletStorage {
    private const val PREFS_FILE = "void_wallet_encrypted"
    private const val KEY_MNEMONIC = "btc_mnemonic"
    private const val KEY_ADDRESS = "btc_receive_address"
    private const val KEY_CREATED_AT = "btc_created_at"

    private var cachedPrefs: SharedPreferences? = null

    private fun getPrefs(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ).also { cachedPrefs = it }
    }

    fun saveWallet(context: Context, mnemonic: String, address: String) {
        getPrefs(context).edit()
            .putString(KEY_MNEMONIC, mnemonic)
            .putString(KEY_ADDRESS, address)
            .putLong(KEY_CREATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun loadWallet(context: Context): BtcWalletEngine.WalletInfo? {
        val prefs = getPrefs(context)
        val mnemonic = prefs.getString(KEY_MNEMONIC, null) ?: return null
        val address = prefs.getString(KEY_ADDRESS, null) ?: return null
        return BtcWalletEngine.WalletInfo(mnemonic = mnemonic, receiveAddress = address)
    }

    fun hasWallet(context: Context): Boolean {
        return getPrefs(context).contains(KEY_MNEMONIC)
    }

    fun deleteWallet(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
