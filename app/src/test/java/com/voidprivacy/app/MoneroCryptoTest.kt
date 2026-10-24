package com.voidprivacy.app

import com.voidprivacy.app.tools.wallet.xmr.MoneroCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for MoneroCrypto: Keccak-256, sc_reduce32, ed25519, Base58, address generation.
 */
class MoneroCryptoTest {

    // ─── Keccak-256 ────────────────────────────────────────────────

    @Test
    fun `keccak256 empty input produces correct hash`() {
        // Known Keccak-256 of empty string
        val hash = MoneroCrypto.keccak256(ByteArray(0))
        val hex = hash.joinToString("") { "%02x".format(it) }
        assertEquals("c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470", hex)
    }

    @Test
    fun `keccak256 hello produces correct hash`() {
        val hash = MoneroCrypto.keccak256("hello".toByteArray())
        val hex = hash.joinToString("") { "%02x".format(it) }
        assertEquals("1c8aff950685c2ed4bc3174f3472287b56d9517b9c948127319a09a7a36deac8", hex)
    }

    // ─── sc_reduce32 ───────────────────────────────────────────────

    @Test
    fun `scReduce32 reduces correctly`() {
        // L = 2^252 + 27742317777372353535851937790883648493
        // A value larger than L should be reduced
        val input = ByteArray(32) { 0xFF.toByte() } // 2^256 - 1
        val reduced = MoneroCrypto.scReduce32(input)
        val reducedBig = MoneroCrypto.leBytesToBigInt(reduced)
        assertTrue("Reduced value must be < L", reducedBig < MoneroCrypto.L)
    }

    @Test
    fun `scReduce32 of zero is zero`() {
        val input = ByteArray(32)
        val reduced = MoneroCrypto.scReduce32(input)
        assertEquals(0L, MoneroCrypto.leBytesToBigInt(reduced).toLong())
    }

    @Test
    fun `scReduce32 of one is one`() {
        val input = ByteArray(32)
        input[0] = 1
        val reduced = MoneroCrypto.scReduce32(input)
        assertEquals(1L, MoneroCrypto.leBytesToBigInt(reduced).toLong())
    }

    // ─── Ed25519 scalar mult base ──────────────────────────────────

    @Test
    fun `scalarMultBase of 1 gives base point`() {
        val scalar = ByteArray(32)
        scalar[0] = 1
        val pub = MoneroCrypto.scalarMultBase(scalar)
        assertEquals(32, pub.size)
        // Base point y = 4/5 mod p, encoded as little-endian with sign bit
        // Known base point encoding: 5866666666666666666666666666666666666666666666666666666666666666
        val hex = pub.joinToString("") { "%02x".format(it) }
        assertEquals("5866666666666666666666666666666666666666666666666666666666666666", hex)
    }

    @Test
    fun `scalarMultBase produces different keys for different scalars`() {
        val s1 = ByteArray(32); s1[0] = 1
        val s2 = ByteArray(32); s2[0] = 2
        val p1 = MoneroCrypto.scalarMultBase(s1)
        val p2 = MoneroCrypto.scalarMultBase(s2)
        assertNotEquals(p1.joinToString(""), p2.joinToString(""))
    }

    @Test
    fun `scalarMultBase output is 32 bytes`() {
        val scalar = ByteArray(32) { (it + 1).toByte() }
        val pub = MoneroCrypto.scalarMultBase(scalar)
        assertEquals(32, pub.size)
    }

    // ─── Byte helpers ──────────────────────────────────────────────

    @Test
    fun `leBytesToBigInt roundtrip`() {
        val original = ByteArray(32) { (it * 7).toByte() }
        val big = MoneroCrypto.leBytesToBigInt(original)
        val back = MoneroCrypto.bigIntTo32Le(big)
        assertEquals(original.joinToString(""), back.joinToString(""))
    }

    @Test
    fun `bigIntTo32Le of 256 is correct`() {
        val n = java.math.BigInteger.valueOf(256)
        val le = MoneroCrypto.bigIntTo32Le(n)
        assertEquals(0, le[0].toInt())
        assertEquals(1, le[1].toInt())
        for (i in 2 until 32) assertEquals(0, le[i].toInt())
    }

    // ─── Base58 ────────────────────────────────────────────────────

    @Test
    fun `base58Encode produces valid characters`() {
        val input = ByteArray(69) { (it + 1).toByte() } // typical address payload size
        val encoded = MoneroCrypto.base58Encode(input)
        assertTrue(encoded.isNotEmpty())
        assertTrue(encoded.all { it in "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz" })
    }

    @Test
    fun `base58Encode of zeros starts with 1s`() {
        val input = ByteArray(8) // one full block of zeros
        val encoded = MoneroCrypto.base58Encode(input)
        // 8 zero bytes → big-endian 0 → "11111111111" (11 chars of '1')
        assertEquals("11111111111", encoded)
    }

    // ─── Address generation ────────────────────────────────────────

    @Test
    fun `seedToKeys produces valid address format`() {
        val seed = ByteArray(32) { (it + 1).toByte() }
        val keys = MoneroCrypto.seedToKeys(seed)

        // Address should start with '4' for mainnet
        assertTrue("Mainnet address starts with 4, got: ${keys.address.take(5)}", keys.address.startsWith("4"))
        // Address length should be 95 chars for standard Monero address
        assertEquals(95, keys.address.length)
    }

    @Test
    fun `seedToKeys produces different addresses for different seeds`() {
        val s1 = ByteArray(32) { 1 }
        val s2 = ByteArray(32) { 2 }
        val k1 = MoneroCrypto.seedToKeys(s1)
        val k2 = MoneroCrypto.seedToKeys(s2)
        assertNotEquals(k1.address, k2.address)
    }

    @Test
    fun `seedToKeys is deterministic`() {
        val seed = ByteArray(32) { (it * 3 + 7).toByte() }
        val k1 = MoneroCrypto.seedToKeys(seed)
        val k2 = MoneroCrypto.seedToKeys(seed)
        assertEquals(k1.address, k2.address)
        assertEquals(k1.spendPublic.joinToString(""), k2.spendPublic.joinToString(""))
        assertEquals(k1.viewPublic.joinToString(""), k2.viewPublic.joinToString(""))
    }

    @Test
    fun `testnet address starts with 9`() {
        val seed = ByteArray(32) { (it + 1).toByte() }
        val keys = MoneroCrypto.seedToKeys(seed, MoneroCrypto.TESTNET)
        assertTrue("Testnet address starts with 9, got: ${keys.address.take(5)}", keys.address.startsWith("9"))
    }

    @Test
    fun `spend and view keys are different`() {
        val seed = ByteArray(32) { (it + 1).toByte() }
        val keys = MoneroCrypto.seedToKeys(seed)
        assertNotEquals(
            keys.spendPublic.joinToString(""),
            keys.viewPublic.joinToString(""),
        )
    }

    // ─── Hex seed ──────────────────────────────────────────────────

    @Test
    fun `decodeHexSeed works`() {
        val hex = "0102030405060708091011121314151617181920212223242526272829303132"
        val seed = MoneroCrypto.decodeHexSeed(hex)
        assertEquals(32, seed.size)
        assertEquals(1, seed[0].toInt())
        assertEquals(0x32, seed[31].toInt())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decodeHexSeed rejects wrong length`() {
        MoneroCrypto.decodeHexSeed("0102")
    }

    // ─── Mnemonic encode (create wallet) ──────────────────────────

    private fun loadWordList(): List<String> {
        val f = java.io.File("src/main/res/raw/monero_words.txt")
        return f.bufferedReader().readLines().filter { it.isNotBlank() }
    }

    @Test
    fun `encodeMnemonic roundtrips with decodeMnemonic`() {
        val wordList = loadWordList()
        assertEquals(1626, wordList.size)
        val seed = ByteArray(32) { (it * 7 + 3).toByte() }
        val words = MoneroCrypto.encodeMnemonic(seed, wordList)
        assertEquals(25, words.size)
        val decoded = MoneroCrypto.decodeMnemonic(words, wordList)
        assertTrue("Roundtrip seed mismatch", decoded.contentEquals(seed))
    }

    @Test
    fun `encodeMnemonic produces valid address when decoded`() {
        val wordList = loadWordList()
        val seed = ByteArray(32) { (it * 11 + 5).toByte() }
        val words = MoneroCrypto.encodeMnemonic(seed, wordList)
        val decoded = MoneroCrypto.decodeMnemonic(words, wordList)
        val expected = MoneroCrypto.seedToKeys(seed).address
        val actual = MoneroCrypto.seedToKeys(decoded).address
        assertEquals(expected, actual)
    }
}
