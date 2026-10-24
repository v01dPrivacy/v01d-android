package com.voidprivacy.app.ui.lock

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * App lock manager: PIN storage, verification, lock state.
 * PIN is stored as salted SHA-256 hash in EncryptedSharedPreferences.
 * Cooldown is persisted to survive app restart.
 */
object LockManager {
    private const val PREFS_FILE = "void_lock_encrypted"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_LOCK_TIMEOUT = "lock_timeout_ms"
    private const val KEY_COOLDOWN_UNTIL = "cooldown_until"

    var isLocked by mutableStateOf(true)
        private set
    var attempts by mutableIntStateOf(0)
        private set
    var cooldownUntil by mutableLongStateOf(0L)
        private set

    private const val MAX_ATTEMPTS = 3
    private const val COOLDOWN_MS = 30_000L

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

    fun hasPin(context: Context): Boolean {
        return prefs(context).contains(KEY_PIN_HASH)
    }

    fun setPin(context: Context, pin: String) {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        prefs(context).edit()
            .putString(KEY_PIN_HASH, hashPin(pin, saltHex))
            .putString(KEY_PIN_SALT, saltHex)
            .apply()
        isLocked = false
        attempts = 0
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        // Load persisted cooldown
        val persistedCooldown = prefs(context).getLong(KEY_COOLDOWN_UNTIL, 0L)
        if (persistedCooldown > cooldownUntil) cooldownUntil = persistedCooldown
        if (System.currentTimeMillis() < cooldownUntil) return false

        val stored = prefs(context).getString(KEY_PIN_HASH, null) ?: return false
        val salt = prefs(context).getString(KEY_PIN_SALT, "") ?: ""
        return if (hashPin(pin, salt) == stored) {
            isLocked = false
            attempts = 0
            prefs(context).edit().putLong(KEY_COOLDOWN_UNTIL, 0L).apply()
            true
        } else {
            attempts++
            if (attempts >= MAX_ATTEMPTS) {
                cooldownUntil = System.currentTimeMillis() + COOLDOWN_MS
                prefs(context).edit().putLong(KEY_COOLDOWN_UNTIL, cooldownUntil).apply()
                attempts = 0
            }
            false
        }
    }

    fun lock() {
        isLocked = true
    }

    fun unlock() {
        isLocked = false
        attempts = 0
    }

    fun getTimeout(context: Context): Long {
        return prefs(context).getLong(KEY_LOCK_TIMEOUT, 60_000L)
    }

    fun setTimeout(context: Context, ms: Long) {
        prefs(context).edit().putLong(KEY_LOCK_TIMEOUT, ms).apply()
    }

    fun removePin(context: Context) {
        prefs(context).edit().clear().apply()
        isLocked = false
    }

    private fun hashPin(pin: String, saltHex: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(saltHex.toByteArray(Charsets.UTF_8))
        return digest.digest(pin.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
