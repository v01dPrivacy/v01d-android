package com.voidprivacy.app.tools.message

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import org.signal.libsignal.protocol.IdentityKey
import java.security.MessageDigest

/** A saved messaging contact. */
data class MsgContact(
    val name: String,
    val pubKeyB64: String,
    val token: String, // routing token = SHA256(pubkey), also the Signal address name
) {
    fun identityKey(): IdentityKey =
        IdentityKey(java.util.Base64.getDecoder().decode(pubKeyB64))

    fun fingerprint(): String =
        java.util.Base64.getDecoder().decode(pubKeyB64)
            .joinToString("") { "%02x".format(it) }
}

/** A single chat message (post-decryption plaintext). filePath != null => file attachment. */
data class ChatMessage(
    val text: String,
    val incoming: Boolean,
    val timestamp: Long,
    val filePath: String? = null,
)

/**
 * Encrypted storage for messaging contacts and chat history.
 *
 * ponytail: JSON blobs in EncryptedSharedPreferences. Fine for a focused tool
 * with a handful of contacts and modest history; move to a SQLCipher Room DB if
 * contacts/history grow large.
 */
object MessageStorage {
    private const val PREFS_FILE = "void_message_data_encrypted"
    private const val KEY_CONTACTS = "contacts"
    private const val KEY_MSG_PREFIX = "msgs:"
    private const val KEY_RELAY_URL = "relay_url"
    private const val KEY_RELAY_MODE = "relay_mode"

    /** Deployed blind relay (Cloudflare Worker). Zero config for the user. */
    const val DEFAULT_RELAY_URL = "https://v01d-relay.bot-pawing596.workers.dev"

    /** Pre-rename default — users who saved it are migrated to "public" mode. */
    private const val LEGACY_DEFAULT_RELAY_URL = "https://void-message-relay.bot-pawing596.workers.dev"

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

    /** routing token = hex(SHA256(identity pubkey bytes)). */
    fun tokenFor(pubKey: IdentityKey): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(pubKey.serialize())
        return digest.joinToString("") { "%02x".format(it) }
    }

    // ─── Contacts ──────────────────────────────────────────────────

    fun addContact(context: Context, name: String, pubKey: IdentityKey): MsgContact {
        val contact = MsgContact(
            name = name,
            pubKeyB64 = java.util.Base64.getEncoder().encodeToString(pubKey.serialize()),
            token = tokenFor(pubKey),
        )
        val contacts = getContacts(context).toMutableList()
        contacts.removeAll { it.token == contact.token }
        contacts.add(contact)
        saveContacts(context, contacts)
        return contact
    }

    fun getContacts(context: Context): List<MsgContact> {
        val json = getPrefs(context).getString(KEY_CONTACTS, null) ?: return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            MsgContact(o.getString("name"), o.getString("pub"), o.getString("token"))
        }
    }

    fun getContactByToken(context: Context, token: String): MsgContact? =
        getContacts(context).firstOrNull { it.token == token }

    fun deleteContact(context: Context, token: String) {
        saveContacts(context, getContacts(context).filter { it.token != token })
        getPrefs(context).edit().remove(KEY_MSG_PREFIX + token).apply()
    }

    private fun saveContacts(context: Context, contacts: List<MsgContact>) {
        val arr = JSONArray()
        contacts.forEach { c ->
            arr.put(JSONObject().apply {
                put("name", c.name)
                put("pub", c.pubKeyB64)
                put("token", c.token)
            })
        }
        getPrefs(context).edit().putString(KEY_CONTACTS, arr.toString()).apply()
    }

    // ─── Messages ──────────────────────────────────────────────────

    fun addMessage(context: Context, token: String, text: String, incoming: Boolean, filePath: String? = null) {
        val msgs = getMessages(context, token).toMutableList()
        msgs.add(ChatMessage(text, incoming, System.currentTimeMillis(), filePath))
        val arr = JSONArray()
        msgs.forEach { m ->
            arr.put(JSONObject().apply {
                put("text", m.text)
                put("in", m.incoming)
                put("ts", m.timestamp)
                if (m.filePath != null) put("path", m.filePath)
            })
        }
        getPrefs(context).edit().putString(KEY_MSG_PREFIX + token, arr.toString()).apply()
    }

    fun getMessages(context: Context, token: String): List<ChatMessage> {
        val json = getPrefs(context).getString(KEY_MSG_PREFIX + token, null) ?: return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ChatMessage(
                o.getString("text"),
                o.getBoolean("in"),
                o.getLong("ts"),
                o.optString("path").ifEmpty { null },
            )
        }
    }

    fun clearMessages(context: Context, token: String) {
        getPrefs(context).edit().remove(KEY_MSG_PREFIX + token).apply()
    }

    // ─── Relay config ──────────────────────────────────────────────

    /** Last time a bundle was published for a relation (key = stable contact token). */
    fun getLastBundlePublish(context: Context, contactToken: String): Long =
        getPrefs(context).getString(KEY_PUB_PREFIX + contactToken, null)?.toLongOrNull() ?: 0L

    fun setLastBundlePublish(context: Context, contactToken: String, ts: Long) {
        getPrefs(context).edit().putString(KEY_PUB_PREFIX + contactToken, ts.toString()).apply()
    }

    private const val KEY_PUB_PREFIX = "pub:"

    /** "public" (zero config) or "custom" (self-hosted relay URL). */
    fun getRelayMode(context: Context): String {
        val prefs = getPrefs(context)
        prefs.getString(KEY_RELAY_MODE, null)?.let { return it }
        // Migration: a saved URL that differs from both defaults = user had a custom relay.
        val saved = prefs.getString(KEY_RELAY_URL, null)
        return if (saved != null && saved != DEFAULT_RELAY_URL && saved != LEGACY_DEFAULT_RELAY_URL) "custom" else "public"
    }

    fun setRelayMode(context: Context, mode: String, customUrl: String? = null) {
        val edit = getPrefs(context).edit().putString(KEY_RELAY_MODE, mode)
        if (mode == "custom" && !customUrl.isNullOrBlank()) {
            edit.putString(KEY_RELAY_URL, customUrl.trimEnd('/'))
        } else {
            edit.putString(KEY_RELAY_URL, DEFAULT_RELAY_URL)
        }
        edit.apply()
    }

    fun getRelayUrl(context: Context): String {
        val prefs = getPrefs(context)
        if (getRelayMode(context) == "custom") {
            return prefs.getString(KEY_RELAY_URL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_RELAY_URL
        }
        return DEFAULT_RELAY_URL
    }

    fun wipeAll(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
