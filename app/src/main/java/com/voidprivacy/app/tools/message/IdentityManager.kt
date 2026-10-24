package com.voidprivacy.app.tools.message

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.voidprivacy.app.R
import org.json.JSONObject
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair

/**
 * Void Message identity: a libsignal IdentityKeyPair (X25519).
 *
 * - Generated on-device, persisted encrypted (EncryptedSharedPreferences).
 * - routing_token = SHA256(identity_public_key) — stable address, no registration.
 * - Fingerprint = hex of the 33-byte compressed pubkey (out-of-band verification).
 * - QR payload = JSON { v, name, id } where id = base64(serialized IdentityKey).
 */
object IdentityManager {
    private const val PREFS_FILE = "void_message_identity_encrypted"
    private const val KEY_IDENTITY = "identity_keypair"
    private const val KEY_NAME = "display_name"

    private var cachedPrefs: SharedPreferences? = null
    private var cachedKeyPair: IdentityKeyPair? = null

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

    /** True if an identity already exists on this device. */
    fun hasIdentity(context: Context): Boolean =
        getPrefs(context).contains(KEY_IDENTITY)

    /** Load the existing identity, or null if none. */
    fun getIdentity(context: Context): IdentityKeyPair? {
        cachedKeyPair?.let { return it }
        val b64 = getPrefs(context).getString(KEY_IDENTITY, null) ?: return null
        return IdentityKeyPair(Base64.decode(b64, Base64.NO_WRAP)).also { cachedKeyPair = it }
    }

    /** Generate a new identity (or return the existing one) and persist it. */
    fun getOrCreateIdentity(context: Context, displayName: String): IdentityKeyPair {
        getIdentity(context)?.let { return it }
        val identityKeyPair = IdentityKeyPair.generate()
        getPrefs(context).edit()
            .putString(KEY_IDENTITY, Base64.encodeToString(identityKeyPair.serialize(), Base64.NO_WRAP))
            .putString(KEY_NAME, displayName)
            .apply()
        cachedKeyPair = identityKeyPair
        return identityKeyPair
    }

    /** Display name stored with the identity. */
    fun getDisplayName(context: Context): String =
        getPrefs(context).getString(KEY_NAME, "v01d User") ?: "v01d User"

    fun setDisplayName(context: Context, name: String) {
        getPrefs(context).edit().putString(KEY_NAME, name).apply()
    }

    /** 33-byte compressed public key. */
    fun publicKeyBytes(context: Context): ByteArray? =
        getIdentity(context)?.publicKey?.serialize()

    /** Hex fingerprint of the public key (for out-of-band verification). */
    fun fingerprint(context: Context): String? =
        publicKeyBytes(context)?.joinToString("") { "%02x".format(it) }

    /**
     * 6 memorable words derived from the fingerprint (vocabulary: the Monero
     * wordlist already bundled in the app). Two people who read the same words
     * aloud are assured no one tampered with the contact exchange.
     */
    fun fingerprintWords(context: Context, count: Int = 6): List<String> {
        val fp = fingerprint(context) ?: return emptyList()
        val words = context.resources.openRawResource(R.raw.monero_words)
            .bufferedReader().use { it.readLines() }.filter { it.isNotBlank() }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest("v01d-fingerprint:$fp".toByteArray())
        val out = mutableListOf<String>()
        var i = 0
        while (out.size < count && i + 1 < digest.size) {
            val idx = (((digest[i].toInt() and 0xFF) shl 8) or (digest[i + 1].toInt() and 0xFF)) % words.size
            out.add(words[idx])
            i += 2
        }
        return out
    }

    /**
     * routing_token = hex(SHA256(identity_public_key)).
     * This is the mailbox address on the relay — anyone with our pubkey can derive it.
     */
    fun routingToken(context: Context): String? {
        val pub = publicKeyBytes(context) ?: return null
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(pub)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** QR payload: JSON { v, name, id } — id is base64 of the serialized IdentityKey. */
    fun exportQrPayload(context: Context): String? {
        val id = getIdentity(context) ?: return null
        return JSONObject().apply {
            put("v", 1)
            put("name", getDisplayName(context))
            put("id", Base64.encodeToString(id.publicKey.serialize(), Base64.NO_WRAP))
        }.toString()
    }

    /**
     * Parse a scanned QR payload into (name, IdentityKey).
     * Returns null if the payload is not a valid Void Message contact QR.
     */
    fun parseQrPayload(payload: String): Pair<String, IdentityKey>? {
        return try {
            val json = JSONObject(payload)
            if (json.optInt("v") != 1) return null
            val name = json.optString("name", "Unknown")
            val idBytes = Base64.decode(json.getString("id"), Base64.NO_WRAP)
            name to IdentityKey(idBytes)
        } catch (_: Exception) {
            null
        }
    }

    /** Delete the identity and all key material. */
    fun deleteIdentity(context: Context) {
        getPrefs(context).edit().clear().apply()
        cachedKeyPair = null
    }
}
