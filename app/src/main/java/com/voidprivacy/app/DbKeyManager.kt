package com.voidprivacy.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Manages the database encryption key for SQLCipher.
 * Key is generated once on first launch and stored in EncryptedSharedPreferences
 * (protected by Android Keystore).
 */
object DbKeyManager {
    private const val PREFS_FILE = "void_db_key_encrypted"
    private const val KEY_DB_PASSPHRASE = "db_passphrase"

    private var cachedPrefs: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
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

    /**
     * Get or create the database passphrase.
     * Generates a random 256-bit key on first call, persists it encrypted.
     * Synchronized to prevent race conditions when both databases init concurrently.
     */
    @Synchronized
    fun getPassphrase(context: Context): ByteArray {
        val prefs = prefs(context)
        val existing = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (existing != null) {
            return existing.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }

        // Generate new 256-bit key
        val key = ByteArray(32)
        SecureRandom().nextBytes(key)
        val hex = key.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(KEY_DB_PASSPHRASE, hex).apply()
        return key
    }
}
