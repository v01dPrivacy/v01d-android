package com.voidprivacy.app.tools.message

import java.security.MessageDigest

/**
 * Per-relation, time-rotating relay tokens.
 *
 * The relay only ever sees opaque tokens. Unlike the old stable
 * SHA256(pubkey) address, these tokens:
 *  - differ per RELATION (direction included): two people writing to the same
 *    recipient use different mailboxes, so the relay cannot tell that the
 *    recipients are the same;
 *  - rotate every EPOCH_SECONDS: a relationship edge observable at the relay
 *    dies with its epoch instead of living forever;
 *  - are derived OFFLINE by both sides from material exchanged in person
 *    (the identity keys) — no server round-trip, no registration.
 *
 * Direction: relationKey(senderPub, recipientPub) is asymmetric on purpose.
 * A→B and B→A are separate mailboxes; replies take the opposite direction.
 *
 * Epoch tolerance: senders use the current epoch; recipients ALSO poll the
 * previous one, so a message written just before a boundary is still picked
 * up just after.
 *
 * Sealed sender: envelopes carry NO sender field. The recipient maps the
 * mailbox token back to the contact they derived it from.
 */
object RelationToken {

    /** Rotation period: 24h. */
    const val EPOCH_SECONDS: Long = 86_400

    private const val RELATION_INFO = "v01d-rel1"
    private const val MAILBOX_INFO = "v01d-mb1"
    private const val BUNDLE_INFO = "v01d-bd1"

    /** Epoch index for a unix timestamp in millis. */
    fun epochAt(unixMs: Long): Long = Math.floorDiv(unixMs, 1000L * EPOCH_SECONDS)

    /** Current epoch index (device clock). */
    fun currentEpoch(): Long = epochAt(System.currentTimeMillis())

    /** Direction-specific shared secret for one (sender → recipient) relation. */
    fun relationKey(senderPub: ByteArray, recipientPub: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(RELATION_INFO.toByteArray())
        md.update(uint16(senderPub.size))
        md.update(senderPub)
        md.update(uint16(recipientPub.size))
        md.update(recipientPub)
        return md.digest()
    }

    /** Mailbox token: the SENDER sends here, the RECIPIENT polls it — for one epoch. */
    fun mailboxToken(senderPub: ByteArray, recipientPub: ByteArray, epoch: Long): String =
        derive(MAILBOX_INFO, relationKey(senderPub, recipientPub), epoch)

    /** Bundle token: the RECIPIENT publishes here, the SENDER fetches — for one epoch. */
    fun bundleToken(senderPub: ByteArray, recipientPub: ByteArray, epoch: Long): String =
        derive(BUNDLE_INFO, relationKey(senderPub, recipientPub), epoch)

    private fun derive(info: String, relationKey: ByteArray, epoch: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(info.toByteArray())
        md.update(relationKey)
        md.update(epochBe(epoch))
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun epochBe(epoch: Long): ByteArray = byteArrayOf(
        (epoch shr 56).toByte(), (epoch shr 48).toByte(), (epoch shr 40).toByte(), (epoch shr 32).toByte(),
        (epoch shr 24).toByte(), (epoch shr 16).toByte(), (epoch shr 8).toByte(), epoch.toByte(),
    )

    private fun uint16(v: Int): ByteArray = byteArrayOf((v shr 8).toByte(), v.toByte())
}
