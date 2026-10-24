package com.voidprivacy.app

import com.voidprivacy.app.tools.message.KeyBundle
import com.voidprivacy.app.tools.message.SessionManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.message.CiphertextMessage

/**
 * End-to-end Signal Protocol (PQXDH + Double Ratchet) tests.
 * Runs on the JVM — no Android dependencies (in-memory stores, java.util.Base64).
 * Proves the crypto foundation of Void Message actually works.
 */
class SignalProtocolTest {

    @Test
    fun `alice and bob exchange messages bidirectionally`() {
        val alice = SessionManager.create(IdentityKeyPair.generate(), "alice")
        val bob = SessionManager.create(IdentityKeyPair.generate(), "bob")

        // Alice obtains Bob's bundle out-of-band and builds a session.
        alice.initSession("bob", bob.exportKeyBundle())

        // Alice -> Bob: first message is a PreKey message (X3DH/PQXDH handshake).
        val m1 = alice.encrypt("bob", "hello bob".toByteArray())
        assertEquals(CiphertextMessage.PREKEY_TYPE, m1.type)
        assertEquals("hello bob", String(bob.decrypt("alice", m1)))

        // Bob -> Alice: reply (Bob's session auto-established by decrypting m1).
        val m2 = bob.encrypt("alice", "hi alice".toByteArray())
        assertEquals("hi alice", String(alice.decrypt("bob", m2)))

        // Alice -> Bob: subsequent message uses the ratchet (Whisper type).
        val m3 = alice.encrypt("bob", "how are you?".toByteArray())
        assertEquals(CiphertextMessage.WHISPER_TYPE, m3.type)
        assertEquals("how are you?", String(bob.decrypt("alice", m3)))
    }

    @Test
    fun `many messages ratchet forward`() {
        val alice = SessionManager.create(IdentityKeyPair.generate(), "a")
        val bob = SessionManager.create(IdentityKeyPair.generate(), "b")
        alice.initSession("b", bob.exportKeyBundle())

        for (i in 1..10) {
            val msg = "message $i".toByteArray()
            assertEquals("message $i", String(bob.decrypt("a", alice.encrypt("b", msg))))
        }
    }

    @Test
    fun `ciphertext does not leak plaintext`() {
        val alice = SessionManager.create(IdentityKeyPair.generate(), "a")
        val bob = SessionManager.create(IdentityKeyPair.generate(), "b")
        alice.initSession("b", bob.exportKeyBundle())

        val secret = "the launch codes are 12345".toByteArray()
        val enc = alice.encrypt("b", secret)
        assertFalse("ciphertext == plaintext", enc.bytes.contentEquals(secret))
        assertFalse("plaintext substring in ciphertext", String(enc.bytes, Charsets.ISO_8859_1).contains("launch codes"))
    }

    @Test
    fun `keybundle survives json round trip and still works`() {
        val carol = SessionManager.create(IdentityKeyPair.generate(), "carol")
        val bundle = carol.exportKeyBundle()

        val restored = KeyBundle.fromJson(bundle.toJson())
        assertEquals(bundle.registrationId, restored.registrationId)
        assertEquals(bundle.deviceId, restored.deviceId)
        assertArrayEquals(bundle.identityKey, restored.identityKey)
        assertArrayEquals(bundle.preKeyPublic, restored.preKeyPublic)
        assertArrayEquals(bundle.signedPreKeySignature, restored.signedPreKeySignature)
        assertArrayEquals(bundle.kyberPreKeyPublic, restored.kyberPreKeyPublic)

        // The restored bundle must still establish a working session.
        val dave = SessionManager.create(IdentityKeyPair.generate(), "dave")
        dave.initSession("carol", restored)
        val enc = dave.encrypt("carol", "ping".toByteArray())
        assertEquals("ping", String(carol.decrypt("dave", enc)))
    }

    @Test
    fun `hasSession reflects session state`() {
        val alice = SessionManager.create(IdentityKeyPair.generate(), "alice")
        val bob = SessionManager.create(IdentityKeyPair.generate(), "bob")
        assertFalse(alice.hasSession("bob"))
        alice.initSession("bob", bob.exportKeyBundle())
        assertTrue(alice.hasSession("bob"))
    }
}
