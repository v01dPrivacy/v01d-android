package com.voidprivacy.app.tools.message

import org.json.JSONObject
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.util.KeyHelper

/**
 * A transportable PQXDH key bundle — everything a peer needs to establish a
 * Double Ratchet session with us. Serialized to JSON (base64 fields) for
 * exchange via the blind relay or QR code.
 *
 * Mirrors void-veil-p2p's KeyBundle concept.
 */
data class KeyBundle(
    val registrationId: Int,
    val deviceId: Int,
    val preKeyId: Int,
    val preKeyPublic: ByteArray,
    val signedPreKeyId: Int,
    val signedPreKeyPublic: ByteArray,
    val signedPreKeySignature: ByteArray,
    val identityKey: ByteArray,
    val kyberPreKeyId: Int,
    val kyberPreKeyPublic: ByteArray,
    val kyberPreKeySignature: ByteArray,
) {
    /** Rebuild a libsignal PreKeyBundle for SessionBuilder.process(). */
    fun toPreKeyBundle(): PreKeyBundle = PreKeyBundle(
        registrationId,
        deviceId,
        preKeyId,
        ECPublicKey(preKeyPublic),
        signedPreKeyId,
        ECPublicKey(signedPreKeyPublic),
        signedPreKeySignature,
        IdentityKey(identityKey),
        kyberPreKeyId,
        KEMPublicKey(kyberPreKeyPublic),
        kyberPreKeySignature,
    )

    fun toJson(): String = JSONObject().apply {
        put("registrationId", registrationId)
        put("deviceId", deviceId)
        put("preKeyId", preKeyId)
        put("preKeyPublic", b64(preKeyPublic))
        put("signedPreKeyId", signedPreKeyId)
        put("signedPreKeyPublic", b64(signedPreKeyPublic))
        put("signedPreKeySignature", b64(signedPreKeySignature))
        put("identityKey", b64(identityKey))
        put("kyberPreKeyId", kyberPreKeyId)
        put("kyberPreKeyPublic", b64(kyberPreKeyPublic))
        put("kyberPreKeySignature", b64(kyberPreKeySignature))
    }.toString()

    companion object {
        fun fromJson(json: String): KeyBundle {
            val o = JSONObject(json)
            return KeyBundle(
                registrationId = o.getInt("registrationId"),
                deviceId = o.getInt("deviceId"),
                preKeyId = o.getInt("preKeyId"),
                preKeyPublic = unb64(o.getString("preKeyPublic")),
                signedPreKeyId = o.getInt("signedPreKeyId"),
                signedPreKeyPublic = unb64(o.getString("signedPreKeyPublic")),
                signedPreKeySignature = unb64(o.getString("signedPreKeySignature")),
                identityKey = unb64(o.getString("identityKey")),
                kyberPreKeyId = o.getInt("kyberPreKeyId"),
                kyberPreKeyPublic = unb64(o.getString("kyberPreKeyPublic")),
                kyberPreKeySignature = unb64(o.getString("kyberPreKeySignature")),
            )
        }
    }
}

/** An encrypted message blob + its Signal message type (for transport). */
data class EncryptedMessage(val bytes: ByteArray, val type: Int)

/**
 * Per-identity Signal Protocol engine: generates PQXDH prekey material,
 * exports/imports KeyBundles, builds sessions, and encrypts/decrypts.
 *
 * Uses the explicit multi-store SessionBuilder/SessionCipher constructors so we
 * don't have to implement SenderKeyStore (groups are out of scope).
 *
 * @param localName stable name for our own SignalProtocolAddress (e.g. routing token).
 */
class SessionManager(
    val stores: SignalStores,
    private val identityKeyPair: IdentityKeyPair,
    private val registrationId: Int,
    localName: String,
) {
    val localAddress = SignalProtocolAddress(localName, DEFAULT_DEVICE)

    private lateinit var signedPreKey: SignedPreKeyRecord
    private var nextPreKeyId = 1
    private var nextKyberPreKeyId = 1

    init {
        generateSignedPreKey()
    }

    /** Long-term signed prekey, generated once and reused across bundles. */
    private fun generateSignedPreKey() {
        val signedPair = ECKeyPair.generate()
        val signedSig = identityKeyPair.privateKey.calculateSignature(signedPair.publicKey.serialize())
        signedPreKey = SignedPreKeyRecord(SIGNED_PREKEY_ID, System.currentTimeMillis(), signedPair, signedSig)
        stores.storeSignedPreKey(SIGNED_PREKEY_ID, signedPreKey)
    }

    /**
     * Export a KeyBundle with FRESH one-time EC + Kyber prekeys.
     *
     * Each call mints new one-time prekeys (stored so in-flight messages still
     * decrypt) so concurrent senders don't collide on a consumed prekey. The
     * signed prekey is long-term and reused. Callers republish this to the relay
     * regularly so offline peers can always start a session.
     */
    fun exportKeyBundle(): KeyBundle {
        // Fresh one-time EC prekey
        val preKeyPair = ECKeyPair.generate()
        val preKeyId = nextPreKeyId++
        stores.storePreKey(preKeyId, PreKeyRecord(preKeyId, preKeyPair))

        // Fresh Kyber (post-quantum) prekey, signed by identity key
        val kemPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val kemSig = identityKeyPair.privateKey.calculateSignature(kemPair.publicKey.serialize())
        val kyberId = nextKyberPreKeyId++
        stores.storeKyberPreKey(kyberId, KyberPreKeyRecord(kyberId, System.currentTimeMillis(), kemPair, kemSig))

        return KeyBundle(
            registrationId = registrationId,
            deviceId = DEFAULT_DEVICE,
            preKeyId = preKeyId,
            preKeyPublic = preKeyPair.publicKey.serialize(),
            signedPreKeyId = signedPreKey.id,
            signedPreKeyPublic = signedPreKey.keyPair.publicKey.serialize(),
            signedPreKeySignature = signedPreKey.signature,
            identityKey = identityKeyPair.publicKey.serialize(),
            kyberPreKeyId = kyberId,
            kyberPreKeyPublic = kemPair.publicKey.serialize(),
            kyberPreKeySignature = kemSig,
        )
    }

    /** Establish a session with a remote peer from their KeyBundle (X3DH/PQXDH). */
    fun initSession(remoteName: String, bundle: KeyBundle) {
        val remoteAddress = SignalProtocolAddress(remoteName, DEFAULT_DEVICE)
        // NOTE: SessionBuilder takes (remoteAddress, localAddress) — the reverse
        // order of SessionCipher's (localAddress, remoteAddress). libsignal quirk.
        val builder = SessionBuilder(
            stores, stores, stores, stores,
            remoteAddress, localAddress,
        )
        builder.process(bundle.toPreKeyBundle())
    }

    /** True if a ratchet session already exists with this peer. */
    fun hasSession(remoteName: String): Boolean =
        stores.containsSession(SignalProtocolAddress(remoteName, DEFAULT_DEVICE))

    /** Encrypt a plaintext for a peer. First message is PREKEY_TYPE, later WHISPER_TYPE. */
    fun encrypt(remoteName: String, plaintext: ByteArray): EncryptedMessage {
        val cipher = cipherFor(remoteName)
        val msg = cipher.encrypt(plaintext)
        return EncryptedMessage(msg.serialize(), msg.type)
    }

    /** Decrypt a message from a peer. */
    fun decrypt(remoteName: String, message: EncryptedMessage): ByteArray {
        val cipher = cipherFor(remoteName)
        return if (message.type == CiphertextMessage.PREKEY_TYPE) {
            cipher.decrypt(PreKeySignalMessage(message.bytes))
        } else {
            cipher.decrypt(SignalMessage(message.bytes))
        }
    }

    private fun cipherFor(remoteName: String): SessionCipher = SessionCipher(
        stores, stores, stores, stores, stores,
        localAddress, SignalProtocolAddress(remoteName, DEFAULT_DEVICE),
    )

    companion object {
        const val DEFAULT_DEVICE = 1
        private const val SIGNED_PREKEY_ID = 1

        /** Create a SessionManager from an IdentityKeyPair with a fresh registration id. */
        fun create(identityKeyPair: IdentityKeyPair, localName: String): SessionManager {
            val registrationId = KeyHelper.generateRegistrationId(false)
            val stores = SignalStores(identityKeyPair, registrationId)
            return SessionManager(stores, identityKeyPair, registrationId, localName)
        }
    }
}

// Base64 via java.util.Base64 — available on Android API 26+ (minSdk 31) and in JVM tests.
private fun b64(bytes: ByteArray): String =
    java.util.Base64.getEncoder().encodeToString(bytes)

private fun unb64(s: String): ByteArray =
    java.util.Base64.getDecoder().decode(s)
