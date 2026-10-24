package com.voidprivacy.app.tools.wallet.xmr

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted storage for XMR wallet seed, keys, and node config.
 */
object XmrWalletStorage {
    private const val PREFS_FILE = "void_xmr_wallet_encrypted"
    private const val KEY_SEED = "xmr_seed"
    private const val KEY_ADDRESS = "xmr_address"
    private const val KEY_NODE_URL = "xmr_node_url"
    private const val KEY_WALLET_RPC_URL = "xmr_wallet_rpc_url"
    private const val KEY_NETWORK = "xmr_network"

    private var cachedPrefs: SharedPreferences? = null

    data class XmrWalletInfo(
        val seed: String,
        val address: String,
        val network: Int = MoneroCrypto.MAINNET,
    )

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

    fun saveWallet(context: Context, seed: String, address: String, network: Int = MoneroCrypto.MAINNET) {
        getPrefs(context).edit()
            .putString(KEY_SEED, seed)
            .putString(KEY_ADDRESS, address)
            .putInt(KEY_NETWORK, network)
            .apply()
    }

    fun loadWallet(context: Context): XmrWalletInfo? {
        val prefs = getPrefs(context)
        val seed = prefs.getString(KEY_SEED, null) ?: return null
        val address = prefs.getString(KEY_ADDRESS, null) ?: return null
        val network = prefs.getInt(KEY_NETWORK, MoneroCrypto.MAINNET)
        return XmrWalletInfo(seed = seed, address = address, network = network)
    }

    fun hasWallet(context: Context): Boolean {
        return getPrefs(context).contains(KEY_SEED)
    }

    fun deleteWallet(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    // ─── Node config ───────────────────────────────────────────────

    fun saveNodeUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_NODE_URL, url).apply()
    }

    fun loadNodeUrl(context: Context): String {
        return getPrefs(context).getString(KEY_NODE_URL, "https://node.moneroworld.com:18089") ?: ""
    }

    fun saveWalletRpcUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_WALLET_RPC_URL, url).apply()
    }

    fun loadWalletRpcUrl(context: Context): String {
        return getPrefs(context).getString(KEY_WALLET_RPC_URL, "") ?: ""
    }
}
