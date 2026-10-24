package com.voidprivacy.app.tools.wallet.xmr

import org.bouncycastle.crypto.digests.KeccakDigest
import java.math.BigInteger

/**
 * Pure Kotlin Monero cryptography.
 * Implements seed → address derivation without the monero C++ library.
 *
 * Uses BouncyCastle (available via bitcoinj) for Keccak-256.
 * Ed25519 point arithmetic implemented with BigInteger (one-time address gen, speed irrelevant).
 *
 * Reference: https://monerodocs.org/cryptography/
 */
object MoneroCrypto {

    // ─── Ed25519 curve parameters ──────────────────────────────────

    /** Field prime p = 2^255 - 19 */
    private val P = BigInteger.TWO.pow(255).subtract(BigInteger.valueOf(19))

    /** Group order L = 2^252 + 27742317777372353535851937790883648493 */
    val L = BigInteger.TWO.pow(252).add(BigInteger("27742317777372353535851937790883648493"))

    /** Curve parameter d = -121665/121666 mod p */
    private val D = BigInteger.valueOf(-121665)
        .multiply(BigInteger.valueOf(121666).modInverse(P))
        .mod(P)

    /** Base point y = 4/5 mod p */
    private val By = BigInteger.valueOf(4)
        .multiply(BigInteger.valueOf(5).modInverse(P))
        .mod(P)

    /** Base point x (recovered from curve equation, positive root) */
    private val Bx = recoverX(By, false)

    // Network bytes
    const val MAINNET = 18  // 0x12
    const val TESTNET = 53  // 0x35
    const val STAGENET = 24 // 0x18

    // ─── Keccak-256 ────────────────────────────────────────────────

    /** Original Keccak-256 (NOT NIST SHA3-256). Monero uses the original. */
    fun keccak256(input: ByteArray): ByteArray {
        val digest = KeccakDigest(256)
        digest.update(input, 0, input.size)
        val out = ByteArray(32)
        digest.doFinal(out, 0)
        return out
    }

    // ─── Scalar reduction ──────────────────────────────────────────

    /** Reduce a 32-byte little-endian integer modulo L (ed25519 group order). */
    fun scReduce32(input: ByteArray): ByteArray {
        val n = leBytesToBigInt(input)
        val reduced = n.mod(L)
        return bigIntTo32Le(reduced)
    }

    // ─── Ed25519 point arithmetic ──────────────────────────────────

    private data class Point(val x: BigInteger, val y: BigInteger)

    private val IDENTITY = Point(BigInteger.ZERO, BigInteger.ONE)
    private val BASE = Point(Bx, By)

    /** Recover x from y on the curve -x² + y² = 1 + d·x²·y² */
    private fun recoverX(y: BigInteger, odd: Boolean): BigInteger {
        val y2 = y.multiply(y).mod(P)
        val u = y2.subtract(BigInteger.ONE).mod(P)
        val v = D.multiply(y2).add(BigInteger.ONE).mod(P)
        // x² = u/v mod p
        var x2 = u.multiply(v.modInverse(P)).mod(P)
        // x = x2^((p+3)/8) mod p (Tonelli-Shanks shortcut for p ≡ 5 mod 8)
        var x = x2.modPow(P.add(BigInteger.valueOf(3)).divide(BigInteger.valueOf(8)), P)
        if (x.multiply(x).mod(P) != x2) {
            // Multiply by sqrt(-1)
            val sqrtM1 = BigInteger.TWO.modPow(P.subtract(BigInteger.ONE).divide(BigInteger.valueOf(4)), P)
            x = x.multiply(sqrtM1).mod(P)
        }
        if (x.multiply(x).mod(P) != x2) throw IllegalArgumentException("No valid x for given y")
        if (x.testBit(0) != odd) x = P.subtract(x)
        return x
    }

    /** Point addition on twisted Edwards curve: -x² + y² = 1 + d·x²·y² */
    private fun pointAdd(a: Point, b: Point): Point {
        val x1y2 = a.x.multiply(b.y).mod(P)
        val x2y1 = b.x.multiply(a.y).mod(P)
        val y1y2 = a.y.multiply(b.y).mod(P)
        val x1x2 = a.x.multiply(b.x).mod(P)
        val dx1x2y1y2 = D.multiply(x1x2).mod(P).multiply(y1y2).mod(P)

        val x3 = x1y2.add(x2y1)
            .multiply(BigInteger.ONE.add(dx1x2y1y2).modInverse(P))
            .mod(P)
        val y3 = y1y2.add(x1x2)  // a = -1, so y1y2 - a·x1x2 = y1y2 + x1x2
            .multiply(BigInteger.ONE.subtract(dx1x2y1y2).mod(P).modInverse(P))
            .mod(P)
        return Point(x3, y3)
    }

    /** Scalar multiplication: scalar * point (double-and-add) */
    private fun scalarMult(scalar: BigInteger, point: Point): Point {
        var result = IDENTITY
        var current = point
        var n = scalar
        while (n > BigInteger.ZERO) {
            if (n.testBit(0)) {
                result = pointAdd(result, current)
            }
            current = pointAdd(current, current)
            n = n.shiftRight(1)
        }
        return result
    }

    /** Compute public key = scalar * G (base point). Returns 32-byte compressed encoding. */
    fun scalarMultBase(scalarLe: ByteArray): ByteArray {
        val scalar = leBytesToBigInt(scalarLe)
        val point = scalarMult(scalar, BASE)
        return encodePoint(point)
    }

    /** Encode point as 32 bytes: little-endian y with sign of x in bit 255 */
    private fun encodePoint(point: Point): ByteArray {
        val yBytes = bigIntTo32Le(point.y)
        if (point.x.testBit(0)) {
            yBytes[31] = (yBytes[31].toInt() or 0x80).toByte()
        }
        return yBytes
    }

    // ─── Monero Base58 ─────────────────────────────────────────────

    private const val B58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    /** Encoded block sizes: for N input bytes, output this many Base58 chars */
    private val BLOCK_SIZES = intArrayOf(0, 2, 3, 5, 6, 7, 9, 10, 11)

    /** Monero Base58 encoding (8-byte block encoding, NOT Bitcoin Base58). */
    fun base58Encode(input: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < input.size) {
            val blockSize = minOf(8, input.size - i)
            val block = input.copyOfRange(i, i + blockSize)
            val encoded = encodeBlock(block)
            sb.append(encoded)
            i += blockSize
        }
        return sb.toString()
    }

    private fun encodeBlock(block: ByteArray): String {
        var num = leBytesToBigIntBigEndian(block)
        val targetLen = BLOCK_SIZES[block.size]
        val chars = CharArray(targetLen)
        for (j in targetLen - 1 downTo 0) {
            val divRem = num.divideAndRemainder(BigInteger.valueOf(58))
            chars[j] = B58_ALPHABET[divRem[1].toInt()]
            num = divRem[0]
        }
        return String(chars)
    }

    /** Decode a big-endian byte array to BigInteger (for Base58 blocks). */
    private fun leBytesToBigIntBigEndian(bytes: ByteArray): BigInteger {
        // Base58 blocks are treated as big-endian unsigned integers
        return BigInteger(1, bytes)
    }

    // ─── Monero mnemonic ───────────────────────────────────────────

    /**
     * Decode a 25-word Monero mnemonic to a 32-byte seed.
     * Words 1-24 encode 32 bytes (8 groups of 3 words → 4 bytes each).
     * Word 25 is a checksum.
     */
    fun decodeMnemonic(words: List<String>, wordList: List<String>): ByteArray {
        require(words.size == 25) { "Monero mnemonic must be 25 words, got ${words.size}" }
        val n = wordList.size // 1626

        val seed = ByteArray(32)
        for (i in 0 until 8) {
            val w1 = wordList.indexOf(words[i * 3])
            val w2 = wordList.indexOf(words[i * 3 + 1])
            val w3 = wordList.indexOf(words[i * 3 + 2])
            require(w1 >= 0 && w2 >= 0 && w3 >= 0) { "Invalid mnemonic word at position ${i * 3}" }

            // Reverse the encoding: n_val = w1 + N * ((w2 - w1) mod N) + N² * ((w3 - w2) mod N)
            val x = w1.toLong() +
                n.toLong() * (((w2 - w1) % n + n) % n).toLong() +
                n.toLong() * n.toLong() * (((w3 - w2) % n + n) % n).toLong()

            // Write as 4 bytes little-endian
            seed[i * 4] = (x and 0xFF).toByte()
            seed[i * 4 + 1] = ((x shr 8) and 0xFF).toByte()
            seed[i * 4 + 2] = ((x shr 16) and 0xFF).toByte()
            seed[i * 4 + 3] = ((x shr 24) and 0xFF).toByte()
        }

        // Verify checksum word (word 25)
        val checksumWord = computeChecksumWord(words.subList(0, 24), wordList)
        require(words[24] == checksumWord) {
            "Invalid checksum word: expected '$checksumWord', got '${words[24]}'"
        }

        return seed
    }

    /**
     * Encode a 32-byte seed as a 25-word Monero mnemonic (24 words + checksum).
     * Mirror of decodeMnemonic: 8 chunks of 4 bytes, 3 words each (Monero uses
     * cumulative differences: w0 = x % n, w1 = (x/n + w0) % n, w2 = (x/n² + w1) % n).
     */
    fun encodeMnemonic(seed: ByteArray, wordList: List<String>): List<String> {
        require(seed.size == 32) { "Seed must be 32 bytes, got ${seed.size}" }
        val n = wordList.size // 1626
        val words = ArrayList<String>(24)
        for (i in 0 until 8) {
            val x = (seed[i * 4].toLong() and 0xFF) or
                ((seed[i * 4 + 1].toLong() and 0xFF) shl 8) or
                ((seed[i * 4 + 2].toLong() and 0xFF) shl 16) or
                ((seed[i * 4 + 3].toLong() and 0xFF) shl 24)
            val w0 = (x % n).toInt()
            val w1 = ((x / n + w0) % n).toInt()
            val w2 = ((x / (n.toLong() * n) + w1) % n).toInt()
            words.add(wordList[w0])
            words.add(wordList[w1])
            words.add(wordList[w2])
        }
        words.add(computeChecksumWord(words, wordList))
        return words
    }

    /** Compute the 25th checksum word from the first 24 words. */
    fun computeChecksumWord(words24: List<String>, wordList: List<String>): String {
        // Checksum = first 4 letters of each word concatenated, then CRC32-like
        // Monero uses: keccak256 of the first 4 letters of each word, then mod 1626
        val prefix = words24.joinToString("") { it.take(4) }
        val hash = keccak256(prefix.toByteArray())
        val idx = leBytesToBigInt(hash.copyOfRange(0, 4)).mod(BigInteger.valueOf(wordList.size.toLong())).toInt()
        return wordList[idx]
    }

    /**
     * Decode a hex seed (64 hex chars = 32 bytes) directly.
     */
    fun decodeHexSeed(hex: String): ByteArray {
        require(hex.length == 64) { "Hex seed must be 64 characters" }
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    // ─── Address generation ────────────────────────────────────────

    data class MoneroKeys(
        val spendSecret: ByteArray,
        val spendPublic: ByteArray,
        val viewSecret: ByteArray,
        val viewPublic: ByteArray,
        val address: String,
    )

    /**
     * Derive Monero keys and address from a 32-byte seed.
     *
     * spend_secret = sc_reduce32(seed)
     * spend_public = spend_secret * G
     * view_secret  = sc_reduce32(keccak256(spend_secret))
     * view_public  = view_secret * G
     * address      = base58(network_byte || spend_public || view_public || checksum)
     */
    fun seedToKeys(seed: ByteArray, networkByte: Int = MAINNET): MoneroKeys {
        require(seed.size == 32) { "Seed must be 32 bytes" }

        val spendSecret = scReduce32(seed)
        val spendPublic = scalarMultBase(spendSecret)

        val viewSecret = scReduce32(keccak256(spendSecret))
        val viewPublic = scalarMultBase(viewSecret)

        val address = encodeAddress(networkByte, spendPublic, viewPublic)

        return MoneroKeys(spendSecret, spendPublic, viewSecret, viewPublic, address)
    }

    /** Encode a Monero address: base58(network_byte || spend_pub || view_pub || checksum) */
    fun encodeAddress(networkByte: Int, spendPublic: ByteArray, viewPublic: ByteArray): String {
        val payload = ByteArray(1 + 32 + 32)
        payload[0] = networkByte.toByte()
        System.arraycopy(spendPublic, 0, payload, 1, 32)
        System.arraycopy(viewPublic, 0, payload, 33, 32)

        val checksum = keccak256(payload).copyOfRange(0, 4)
        val full = payload + checksum
        return base58Encode(full)
    }

    // ─── Byte helpers ──────────────────────────────────────────────

    /** Convert little-endian bytes to BigInteger (unsigned). */
    fun leBytesToBigInt(bytes: ByteArray): BigInteger {
        val reversed = bytes.reversedArray()
        return BigInteger(1, reversed)
    }

    /** Convert BigInteger to 32-byte little-endian array. */
    fun bigIntTo32Le(n: BigInteger): ByteArray {
        val be = n.toByteArray()
        val result = ByteArray(32)
        // Copy big-endian bytes into result in reverse (little-endian)
        val src = if (be.size > 32) be.copyOfRange(be.size - 32, be.size) else be
        for (i in src.indices) {
            result[i] = src[src.size - 1 - i]
        }
        return result
    }
}
