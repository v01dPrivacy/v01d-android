package com.voidprivacy.app.tools.wallet.btc

import org.bitcoinj.core.Coin
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.TransactionOutput
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet

/**
 * Signs a PSBT's unsigned transaction using the wallet's BIP-39 seed.
 * Reconstructs the wallet, builds a signable transaction from witness UTXOs,
 * and signs all inputs that match the wallet's keys.
 */
object PsbtSigner {

    private val params = MainNetParams.get()

    data class SignResult(
        val signedTxHex: String,
        val signedCount: Int,
        val totalInputs: Int,
        val txid: String,
    )

    fun sign(psbt: PsbtParser.PsbtData, mnemonic: String): SignResult {
        val seed = DeterministicSeed(mnemonic, null, "", 0L)
        val wallet = Wallet.fromSeed(params, seed)

        // Build a new transaction with connected UTXOs so bitcoinj can sign
        val signTx = Transaction(params)

        // Add inputs from witness UTXOs (connected) or from the original outpoints
        val originalTx = psbt.unsignedTx
        for (i in originalTx.inputs.indices) {
            val utxoBytes = psbt.witnessUtxos[i]
            if (utxoBytes != null) {
                val utxo = parseWitnessUtxo(utxoBytes)
                signTx.addInput(utxo)
            } else {
                // No UTXO data — copy the original input (won't be signable without prev tx)
                val origInput = originalTx.inputs[i]
                signTx.addInput(origInput)
            }
        }

        // Copy outputs
        for (output in originalTx.outputs) {
            signTx.addOutput(output.value, output.scriptPubKey)
        }

        // Set locktime if present
        if (originalTx.lockTime > 0) {
            signTx.lockTime = originalTx.lockTime
        }

        // Sign
        val proposed = org.bitcoinj.wallet.SendRequest.forTx(signTx)
        try {
            wallet.signTransaction(proposed)
        } catch (_: Exception) {
            // Some inputs may not match our keys — partial sign is OK
        }

        // Count signed inputs
        var signedCount = 0
        for (input in signTx.inputs) {
            val hasSig = try {
                input.scriptSig != null && input.scriptSig.chunks.isNotEmpty()
            } catch (_: Exception) { false }
            val hasWitness = input.witness != null && input.witness.pushCount > 0
            if (hasSig || hasWitness) signedCount++
        }

        val hex = org.bitcoinj.core.Utils.HEX.encode(signTx.bitcoinSerialize())

        return SignResult(
            signedTxHex = hex,
            signedCount = signedCount,
            totalInputs = signTx.inputs.size,
            txid = signTx.txId.toString(),
        )
    }

    /**
     * Parse a PSBT witness UTXO (key 0x01 value):
     * amount (8 bytes LE) + scriptPubKey (compactSize + bytes)
     */
    private fun parseWitnessUtxo(bytes: ByteArray): TransactionOutput {
        require(bytes.size >= 9) { "Witness UTXO too short" }

        var amount = 0L
        for (i in 7 downTo 0) {
            amount = (amount shl 8) or (bytes[i].toLong() and 0xFF)
        }

        val scriptLen = bytes[8].toInt() and 0xFF
        require(bytes.size >= 9 + scriptLen) { "Witness UTXO script truncated" }
        val scriptBytes = bytes.copyOfRange(9, 9 + scriptLen)

        return TransactionOutput(params, null, Coin.valueOf(amount), scriptBytes)
    }
}
