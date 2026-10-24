package com.voidprivacy.app.tools.message

import android.content.Context
import android.os.Environment
import org.json.JSONObject
import java.io.File

/**
 * Orchestrates v01d Message: identity, Signal sessions, the blind relay, and
 * local storage. Singleton holding the in-memory Signal state for the app's
 * lifetime.
 *
 * Addressing (per-relation rotating tokens — see [RelationToken]):
 *  - The STABLE token (SHA256 of pubkey) is a LOCAL id only: contact records,
 *    chat history keys and Signal addresses. It never touches the relay.
 *  - Relay addressing is per-relation and time-rotating: sender S puts a
 *    message in mailboxToken(S→R, epoch); recipient R polls that token (plus
 *    the previous epoch) for each known contact S. Key material comes from
 *    the QR exchange, so both sides derive the same tokens offline.
 *  - Envelopes are SEALED-SENDER: { type, msg } — no sender field. The
 *    recipient maps the mailbox token back to the contact.
 *
 * Bundles: the recipient publishes its PQXDH bundle under
 * bundleToken(S→R, epoch and epoch+1) for every known contact, throttled per
 * relation so the relay sees publish traffic sparingly.
 *
 * Session lifetime: ratchet state is in-memory; on process restart a fresh
 * session is bootstrapped from the peer's published bundle. This is
 * intentional (ephemeral, forward-secret) for a focused privacy tool.
 */
object MessageManager {

    private var sessionManager: SessionManager? = null
    private var myPub: ByteArray? = null

    /** Stable LOCAL identity id (contacts, history, Signal address) — never sent to the relay. */
    var myToken: String? = null
        private set

    /** Bundle republish throttle per relation: the relay shouldn't see frequent publishes. */
    private const val BUNDLE_THROTTLE_MS = 25 * 60 * 1000L

    /** Load/create identity and build the Signal engine. Call once at startup. */
    @Synchronized
    fun init(context: Context, displayName: String) {
        if (sessionManager != null) return
        val identity = IdentityManager.getOrCreateIdentity(context, displayName)
        val token = IdentityManager.routingToken(context) ?: return
        myToken = token
        myPub = identity.publicKey.serialize()
        sessionManager = SessionManager.create(identity, token)
    }

    val isReady: Boolean get() = sessionManager != null && myToken != null && myPub != null

    private fun contactByToken(context: Context, token: String): MsgContact? =
        MessageStorage.getContacts(context).firstOrNull { it.token == token }

    /**
     * Publish a fresh key bundle to the relay for EVERY contact relation
     * (current + next epoch), throttled per relation.
     */
    @Synchronized
    fun publishBundle(context: Context): Boolean {
        val sm = sessionManager ?: return false
        val pub = myPub ?: return false
        val relayUrl = MessageStorage.getRelayUrl(context)
        if (relayUrl.isEmpty()) return false

        val epoch = RelationToken.currentEpoch()
        var any = false
        for (contact in MessageStorage.getContacts(context)) {
            val last = MessageStorage.getLastBundlePublish(context, contact.token)
            if (System.currentTimeMillis() - last < BUNDLE_THROTTLE_MS) continue
            if (publishForRelation(sm, pub, relayUrl, contact.identityKey().serialize(), epoch)) {
                MessageStorage.setLastBundlePublish(context, contact.token, System.currentTimeMillis())
                any = true
            }
        }
        return any
    }

    /** Publish a bundle for a single relation immediately (contact just added — no throttle). */
    @Synchronized
    fun publishBundleForContact(context: Context, contactToken: String): Boolean {
        val sm = sessionManager ?: return false
        val pub = myPub ?: return false
        val contact = contactByToken(context, contactToken) ?: return false
        val relayUrl = MessageStorage.getRelayUrl(context)
        if (relayUrl.isEmpty()) return false

        val ok = publishForRelation(sm, pub, relayUrl, contact.identityKey().serialize(), RelationToken.currentEpoch())
        if (ok) MessageStorage.setLastBundlePublish(context, contact.token, System.currentTimeMillis())
        return ok
    }

    private fun publishForRelation(
        sm: SessionManager,
        myPub: ByteArray,
        relayUrl: String,
        contactPub: ByteArray,
        epoch: Long,
    ): Boolean {
        var ok = true
        for (e in longArrayOf(epoch, epoch + 1)) {
            val token = RelationToken.bundleToken(myPub, contactPub, e)
            ok = ok && RelayClient.publishBundle(relayUrl, token, sm.exportKeyBundle().toJson())
        }
        return ok
    }

    /**
     * Send a text message to a contact (by stable contact token).
     * Bootstraps a session from the contact's published bundle on first use.
     */
    @Synchronized
    fun send(context: Context, contactToken: String, text: String): Result<Unit> {
        val pre = preflight(context, contactToken) ?: return Result.failure(
            IllegalStateException(lastError ?: "Not ready"),
        )
        return transmit(pre, text.toByteArray())
            .onSuccess { MessageStorage.addMessage(context, contactToken, text, incoming = false) }
    }

    /**
     * Send a file to a contact. The file bytes travel THROUGH the E2E Signal
     * session (already forward-secret encrypted), so no separate crypto layer is
     * needed. Size-capped to fit the relay's blob limit.
     */
    @Synchronized
    fun sendFile(context: Context, contactToken: String, fileName: String, mime: String, bytes: ByteArray): Result<Unit> {
        if (bytes.size > MAX_FILE_SIZE) {
            return Result.failure(IllegalStateException("File too large (max ${MAX_FILE_SIZE / 1024} KB)"))
        }
        val pre = preflight(context, contactToken) ?: return Result.failure(
            IllegalStateException(lastError ?: "Not ready"),
        )
        val payload = JSONObject().apply {
            put("kind", "file")
            put("name", fileName)
            put("mime", mime)
            put("data", java.util.Base64.getEncoder().encodeToString(bytes))
        }.toString()
        return transmit(pre, payload.toByteArray())
            .onSuccess { MessageStorage.addMessage(context, contactToken, "\uD83D\uDCCE $fileName", incoming = false) }
    }

    private data class Preflight(
        val sm: SessionManager,
        val relayUrl: String,
        val contactToken: String,
        val mailboxToken: String,
    )

    private var lastError: String? = null

    /** Validate state + ensure a session exists with the contact. */
    private fun preflight(context: Context, contactToken: String): Preflight? {
        val sm = sessionManager ?: run { lastError = "Not initialized"; return null }
        val pub = myPub ?: run { lastError = "No identity"; return null }
        val contact = contactByToken(context, contactToken) ?: run { lastError = "Unknown contact"; return null }
        val relayUrl = MessageStorage.getRelayUrl(context)
        if (relayUrl.isEmpty()) { lastError = "No relay configured"; return null }
        val contactPub = contact.identityKey().serialize()
        val epoch = RelationToken.currentEpoch()
        try {
            if (!sm.hasSession(contactToken)) {
                // The recipient publishes current + next epoch; try sensibly around ours.
                val bundleJson = RelayClient.fetchBundle(relayUrl, RelationToken.bundleToken(pub, contactPub, epoch))
                    ?: RelayClient.fetchBundle(relayUrl, RelationToken.bundleToken(pub, contactPub, epoch - 1))
                    ?: RelayClient.fetchBundle(relayUrl, RelationToken.bundleToken(pub, contactPub, epoch + 1))
                    ?: run { lastError = "Contact offline (no published bundle)"; return null }
                sm.initSession(contactToken, KeyBundle.fromJson(bundleJson))
            }
        } catch (e: Exception) {
            lastError = e.message; return null
        }
        val mbToken = RelationToken.mailboxToken(pub, contactPub, epoch)
        return Preflight(sm, relayUrl, contactToken, mbToken)
    }

    /** Encrypt plaintext and push the sealed envelope blob to the relay. */
    private fun transmit(pre: Preflight, plaintext: ByteArray): Result<Unit> {
        return try {
            val enc = pre.sm.encrypt(pre.contactToken, plaintext)
            // Sealed sender: no "from" — the recipient knows the relation from the mailbox token.
            val envelope = JSONObject().apply {
                put("type", enc.type)
                put("msg", java.util.Base64.getEncoder().encodeToString(enc.bytes))
            }.toString()
            if (!RelayClient.send(pre.relayUrl, pre.mailboxToken, envelope)) {
                Result.failure(IllegalStateException("Relay rejected message"))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Poll the relay for EVERY contact relation (current + previous epoch),
     * decrypt, and store. Returns the number of new messages decrypted.
     */
    @Synchronized
    fun pollAndDecrypt(context: Context): Int {
        val sm = sessionManager ?: return 0
        val pub = myPub ?: return 0
        val relayUrl = MessageStorage.getRelayUrl(context)
        if (relayUrl.isEmpty()) return 0

        val contacts = MessageStorage.getContacts(context)
        if (contacts.isEmpty()) return 0
        val epoch = RelationToken.currentEpoch()

        var count = 0
        for (contact in contacts) {
            val contactPub = contact.identityKey().serialize()
            val tokens = arrayOf(
                RelationToken.mailboxToken(pub, contactPub, epoch),
                RelationToken.mailboxToken(pub, contactPub, epoch - 1),
            )
            for (token in tokens) {
                val blobs = try {
                    RelayClient.poll(relayUrl, token)
                } catch (_: Exception) {
                    continue
                }
                for (blob in blobs) {
                    try {
                        val env = JSONObject(blob)
                        val type = env.getInt("type")
                        val cipher = java.util.Base64.getDecoder().decode(env.getString("msg"))
                        val plain = sm.decrypt(contact.token, EncryptedMessage(cipher, type))
                        handleDecrypted(context, contact.token, plain)
                        count++
                    } catch (_: Exception) {
                        // Skip undecryptable blobs (consumed prekey, spam).
                    }
                }
            }
        }
        return count
    }

    /** Route a decrypted payload to text or file handling. */
    private fun handleDecrypted(context: Context, contactToken: String, plain: ByteArray) {
        val s = String(plain)
        if (s.startsWith("{")) {
            try {
                val o = JSONObject(s)
                if (o.optString("kind") == "file") {
                    val name = o.optString("name", "file")
                    val data = java.util.Base64.getDecoder().decode(o.getString("data"))
                    val path = saveFile(context, name, data)
                    MessageStorage.addMessage(context, contactToken, "\uD83D\uDCCE $name", incoming = true, filePath = path)
                    return
                }
            } catch (_: Exception) {
                // Not a file payload — fall through to text.
            }
        }
        MessageStorage.addMessage(context, contactToken, s, incoming = true)
    }

    /** Save a received file to app-specific external storage (no permission needed). */
    private fun saveFile(context: Context, name: String, data: ByteArray): String? {
        return try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val file = File(dir, "void_${System.currentTimeMillis()}_$safeName")
            file.writeBytes(data)
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    /** True if a ratchet session exists with this peer. */
    fun hasSession(contactToken: String): Boolean =
        sessionManager?.hasSession(contactToken) == true

    /** Tear down (e.g. on secure wipe). */
    @Synchronized
    fun reset() {
        sessionManager = null
        myToken = null
        myPub = null
    }

    /** Max file size that fits the relay blob limit after base64 + envelope overhead. */
    const val MAX_FILE_SIZE = 256 * 1024
}
