package com.voidprivacy.app

import com.voidprivacy.app.tools.message.RelationToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the per-relation rotating relay tokens: determinism, direction
 * separation, epoch rotation, and epoch boundary math.
 */
class RelationTokenTest {

    private fun pub(seed: Int) = ByteArray(33) { ((it * seed + seed) % 256).toByte() }

    private val a = pub(1)
    private val b = pub(2)
    private val c = pub(3)

    // ─── Determinism ───────────────────────────────────────────────

    @Test
    fun `same inputs produce same tokens`() {
        assertEquals(
            RelationToken.mailboxToken(a, b, 100),
            RelationToken.mailboxToken(a, b, 100),
        )
        assertEquals(
            RelationToken.bundleToken(a, b, 100),
            RelationToken.bundleToken(a, b, 100),
        )
        assertEquals(
            RelationToken.relationKey(a, b).joinToString(""),
            RelationToken.relationKey(a, b).joinToString(""),
        )
    }

    // ─── Direction separation ─────────────────────────────────────

    @Test
    fun `a to b differs from b to a`() {
        assertNotEquals(
            RelationToken.mailboxToken(a, b, 100),
            RelationToken.mailboxToken(b, a, 100),
        )
        assertNotEquals(
            RelationToken.bundleToken(a, b, 100),
            RelationToken.bundleToken(b, a, 100),
        )
        assertNotEquals(
            RelationToken.relationKey(a, b).joinToString(""),
            RelationToken.relationKey(b, a).joinToString(""),
        )
    }

    @Test
    fun `different relations produce different tokens`() {
        val ab = RelationToken.mailboxToken(a, b, 100)
        val ac = RelationToken.mailboxToken(a, c, 100)
        val cb = RelationToken.mailboxToken(c, b, 100)
        assertNotEquals(ab, ac)
        assertNotEquals(ab, cb)
        assertNotEquals(ac, cb)
    }

    // ─── Epoch rotation ───────────────────────────────────────────

    @Test
    fun `token rotates with epoch`() {
        val e1 = RelationToken.mailboxToken(a, b, 100)
        val e2 = RelationToken.mailboxToken(a, b, 101)
        assertNotEquals(e1, e2)

        val b1 = RelationToken.bundleToken(a, b, 100)
        val b2 = RelationToken.bundleToken(a, b, 101)
        assertNotEquals(b1, b2)
    }

    @Test
    fun `mailbox and bundle tokens differ`() {
        assertNotEquals(
            RelationToken.mailboxToken(a, b, 100),
            RelationToken.bundleToken(a, b, 100),
        )
    }

    // ─── Format ───────────────────────────────────────────────────

    @Test
    fun `tokens are 64 hex chars`() {
        val t = RelationToken.mailboxToken(a, b, 7)
        assertEquals(64, t.length)
        assertTrue(t.all { it.isDigit() || it in 'a'..'f' })
    }

    // ─── Epoch math ───────────────────────────────────────────────

    @Test
    fun `epochAt boundaries`() {
        assertEquals(0L, RelationToken.epochAt(0L))
        assertEquals(0L, RelationToken.epochAt(86_399_999L))
        assertEquals(1L, RelationToken.epochAt(86_400_000L))
        // Fixed reference: 2025-08-12T00:00:00Z = 1754956800 s
        assertEquals(1754956800L / 86_400L, RelationToken.epochAt(1_754_956_800_000L))
    }

    @Test
    fun `currentEpoch is stable within a call`() {
        val e1 = RelationToken.currentEpoch()
        val e2 = RelationToken.currentEpoch()
        assertTrue(e2 >= e1)
    }
}
