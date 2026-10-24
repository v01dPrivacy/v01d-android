package com.voidprivacy.app.tools.message

import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.KyberPreKeyStore
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyStore
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SessionStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyStore

/**
 * In-memory implementation of the five libsignal store interfaces.
 *
 * Security property (intentional): prekeys, signed prekeys, Kyber prekeys and
 * ratchet session state live ONLY in RAM and never touch disk. Only the
 * long-term identity is persisted (encrypted, via IdentityManager). This gives
 * forward secrecy and matches the ephemeral design of void-veil-p2p.
 *
 * ponytail: in-memory maps keyed by address string. Fine for a focused 1-on-1
 * tool with a handful of contacts; move to SQLCipher-backed stores if the
 * contact count grows large or sessions must survive process death.
 *
 * Trust model: Trust On First Use (TOFU) — the first identity seen for an
 * address is pinned; a different identity later is flagged (not silently trusted).
 */
class SignalStores(
    private val identityKeyPair: IdentityKeyPair,
    private val registrationId: Int,
) : IdentityKeyStore, PreKeyStore, SignedPreKeyStore, KyberPreKeyStore, SessionStore {

    private val remoteIdentities = mutableMapOf<String, IdentityKey>()
    private val preKeys = mutableMapOf<Int, PreKeyRecord>()
    private val signedPreKeys = mutableMapOf<Int, SignedPreKeyRecord>()
    private val kyberPreKeys = mutableMapOf<Int, KyberPreKeyRecord>()
    private val sessions = mutableMapOf<String, SessionRecord>()

    private fun addrKey(address: SignalProtocolAddress): String =
        "${address.name}:${address.deviceId}"

    // ─── IdentityKeyStore ──────────────────────────────────────────

    override fun getIdentityKeyPair(): IdentityKeyPair = identityKeyPair

    override fun getLocalRegistrationId(): Int = registrationId

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): IdentityKeyStore.IdentityChange {
        val key = addrKey(address)
        val existing = remoteIdentities[key]
        return when {
            existing == null -> {
                remoteIdentities[key] = identityKey
                IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
            }
            existing == identityKey -> IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
            else -> {
                remoteIdentities[key] = identityKey
                IdentityKeyStore.IdentityChange.REPLACED_EXISTING
            }
        }
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction,
    ): Boolean {
        // TOFU: trust if nothing pinned yet, or the pinned key matches.
        val existing = remoteIdentities[addrKey(address)] ?: return true
        return existing == identityKey
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? =
        remoteIdentities[addrKey(address)]

    // ─── PreKeyStore ───────────────────────────────────────────────

    override fun loadPreKey(preKeyId: Int): PreKeyRecord =
        preKeys[preKeyId] ?: throw org.signal.libsignal.protocol.InvalidKeyIdException("No prekey $preKeyId")

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) { preKeys[preKeyId] = record }

    override fun containsPreKey(preKeyId: Int): Boolean = preKeys.containsKey(preKeyId)

    override fun removePreKey(preKeyId: Int) { preKeys.remove(preKeyId) }

    // ─── SignedPreKeyStore ─────────────────────────────────────────

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord =
        signedPreKeys[signedPreKeyId] ?: throw org.signal.libsignal.protocol.InvalidKeyIdException("No signed prekey $signedPreKeyId")

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> = signedPreKeys.values.toList()

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) { signedPreKeys[signedPreKeyId] = record }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean = signedPreKeys.containsKey(signedPreKeyId)

    override fun removeSignedPreKey(signedPreKeyId: Int) { signedPreKeys.remove(signedPreKeyId) }

    // ─── KyberPreKeyStore ──────────────────────────────────────────

    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord =
        kyberPreKeys[kyberPreKeyId] ?: throw org.signal.libsignal.protocol.InvalidKeyIdException("No kyber prekey $kyberPreKeyId")

    override fun loadKyberPreKeys(): List<KyberPreKeyRecord> = kyberPreKeys.values.toList()

    override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) { kyberPreKeys[kyberPreKeyId] = record }

    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean = kyberPreKeys.containsKey(kyberPreKeyId)

    override fun markKyberPreKeyUsed(kyberPreKeyId: Int, deviceId: Int, baseKey: ECPublicKey) {
        // One-time Kyber prekey: consumed on use.
        kyberPreKeys.remove(kyberPreKeyId)
    }

    // ─── SessionStore ──────────────────────────────────────────────

    override fun loadSession(address: SignalProtocolAddress): SessionRecord? =
        sessions[addrKey(address)]

    override fun loadExistingSessions(addresses: List<SignalProtocolAddress>): List<SessionRecord> =
        addresses.mapNotNull { sessions[addrKey(it)] }

    override fun getSubDeviceSessions(name: String): List<Int> =
        sessions.keys
            .mapNotNull { key ->
                val parts = key.split(":")
                if (parts.size == 2 && parts[0] == name) parts[1].toIntOrNull() else null
            }
            .filter { it != 1 }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        sessions[addrKey(address)] = record
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean =
        sessions.containsKey(addrKey(address))

    override fun deleteSession(address: SignalProtocolAddress) { sessions.remove(addrKey(address)) }

    override fun deleteAllSessions(name: String) {
        sessions.keys.removeAll { it.startsWith("$name:") }
    }
}
