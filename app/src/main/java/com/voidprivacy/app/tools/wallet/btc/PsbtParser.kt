package com.voidprivacy.app.tools.wallet.btc

import android.util.Base64
import org.bitcoinj.core.Transaction
import org.bitcoinj.params.MainNetParams
import java.io.ByteArrayInputStream

/**
 * Minimal BIP-174 PSBT parser.
 * Extracts the unsigned transaction and input metadata for display/signing.
 * Handles base64 and hex input.
 */
object PsbtParser {

    private val params = MainNetParams.get()
    private val MAGIC = byteArrayOf(0x70, 0x73, 0x62, 0x74, 0xFF.toByte()) // "psbt" + 0xFF

    data class PsbtData(
        val unsignedTx: Transaction,
        val inputCount: Int,
        val outputCount: Int,
        val hasWitnessUtxos: Boolean,
        val bip32Paths: List<String>,
        val witnessUtxos: Map<Int, ByteArray>, // input index → raw witness UTXO bytes
    )

    fun parse(input: String): PsbtData {
        val bytes = decodeInput(input)
        require(bytes.size > 5) { "Input too short for PSBT" }

        // Verify magic
        require(bytes.copyOfRange(0, 5).contentEquals(MAGIC)) {
            "Not a valid PSBT (missing magic bytes)"
        }

        val stream = ByteArrayInputStream(bytes, 5, bytes.size - 5)

        // Parse global map — extract unsigned tx
        var unsignedTxBytes: ByteArray? = null
        val globalMap = parseMap(stream)
        for ((key, value) in globalMap) {
            if (key.isNotEmpty() && key[0] == 0x00.toByte()) {
                unsignedTxBytes = value
            }
        }
        requireNotNull(unsignedTxBytes) { "PSBT missing unsigned transaction" }

        val tx = Transaction(params, unsignedTxBytes)
        val inputCount = tx.inputs.size
        val outputCount = tx.outputs.size

        // Parse input maps
        var hasWitnessUtxos = false
        val bip32Paths = mutableListOf<String>()
        val witnessUtxos = mutableMapOf<Int, ByteArray>()
        repeat(inputCount) { idx ->
            val inputMap = parseMap(stream)
            for ((key, value) in inputMap) {
                if (key.isEmpty()) continue
                when (key[0].toInt() and 0xFF) {
                    0x01 -> {
                        hasWitnessUtxos = true
                        witnessUtxos[idx] = value // raw: amount(8) + scriptPubKey
                    }
                    0x06 -> { // BIP32 derivation
                        if (value.size >= 4) {
                            val fingerprint = value.copyOfRange(0, 4)
                                .joinToString("") { "%02x".format(it) }
                            bip32Paths.add("m/$fingerprint/…")
                        }
                    }
                }
            }
        }

        return PsbtData(
            unsignedTx = tx,
            inputCount = inputCount,
            outputCount = outputCount,
            hasWitnessUtxos = hasWitnessUtxos,
            bip32Paths = bip32Paths,
            witnessUtxos = witnessUtxos,
        )
    }

    private fun decodeInput(input: String): ByteArray {
        val trimmed = input.trim()
        val noWhitespace = trimmed.replace(Regex("\\s"), "")

        // If it looks like pure hex (even length, all hex chars), decode as hex first
        if (noWhitespace.length % 2 == 0 && noWhitespace.matches(Regex("^[a-fA-F0-9]+$"))) {
            try {
                val bytes = noWhitespace.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                if (bytes.size > 5 && bytes.copyOfRange(0, 5).contentEquals(MAGIC)) {
                    return bytes
                }
            } catch (_: Exception) { /* fall through to base64 */ }
        }

        // Try base64
        return try {
            val bytes = Base64.decode(trimmed, Base64.DEFAULT)
            if (bytes.size > 5 && bytes.copyOfRange(0, 5).contentEquals(MAGIC)) {
                bytes
            } else {
                // Base64 decoded but not a PSBT — try hex as last resort
                noWhitespace.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            }
        } catch (_: Exception) {
            // Base64 failed — try hex
            try {
                noWhitespace.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            } catch (_: Exception) {
                throw IllegalArgumentException("Cannot decode as base64 or hex")
            }
        }
    }

    /** Parse a PSBT key-value map. Returns list of (key, value) pairs. */
    private fun parseMap(stream: ByteArrayInputStream): List<Pair<ByteArray, ByteArray>> {
        val entries = mutableListOf<Pair<ByteArray, ByteArray>>()
        while (true) {
            val keyLen = readCompactSize(stream)
            if (keyLen == 0L) break // end of map
            val key = ByteArray(keyLen.toInt())
            stream.read(key)
            val valLen = readCompactSize(stream)
            val value = ByteArray(valLen.toInt())
            stream.read(value)
            entries.add(Pair(key, value))
        }
        return entries
    }

    /** Read a Bitcoin compact size integer. */
    private fun readCompactSize(stream: ByteArrayInputStream): Long {
        val first = stream.read()
        if (first == -1) return 0
        return when {
            first < 0xFD -> first.toLong()
            first == 0xFD -> {
                val b = ByteArray(2); stream.read(b)
                ((b[0].toInt() and 0xFF) or ((b[1].toInt() and 0xFF) shl 8)).toLong()
            }
            first == 0xFE -> {
                val b = ByteArray(4); stream.read(b)
                (b[0].toLong() and 0xFF) or ((b[1].toLong() and 0xFF) shl 8) or
                    ((b[2].toLong() and 0xFF) shl 16) or ((b[3].toLong() and 0xFF) shl 24)
            }
            else -> {
                val b = ByteArray(8); stream.read(b)
                var v = 0L
                for (i in 7 downTo 0) v = (v shl 8) or (b[i].toLong() and 0xFF)
                v
            }
        }
    }
}
