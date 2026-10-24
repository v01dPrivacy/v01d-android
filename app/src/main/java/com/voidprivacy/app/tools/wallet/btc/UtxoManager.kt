package com.voidprivacy.app.tools.wallet.btc

import org.bitcoinj.core.Address
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet

/**
 * Manages wallet addresses and UTXOs via Electrum.
 * Derives BIP-84 addresses (m/84'/0'/0'/0/i) and checks them for funds.
 */
object UtxoManager {

    private val params = MainNetParams.get()
    private const val GAP_LIMIT = 5

    data class WalletState(
        val addresses: List<String>,
        val utxos: List<ElectrumClient.Utxo>,
        val balance: Long, // satoshis
        val serverInfo: String,
    )

    /**
     * Derive receive addresses from the wallet seed.
     * Uses bitcoinj's wallet to get the current receive addresses.
     */
    fun deriveAddresses(mnemonic: String, count: Int = GAP_LIMIT, params: org.bitcoinj.core.NetworkParameters = MainNetParams.get()): List<Address> {
        val seed = DeterministicSeed(mnemonic, null, "", 0L)
        val wallet = Wallet.fromSeed(params, seed)
        // Get current + fresh addresses
        val addresses = mutableListOf(wallet.currentReceiveAddress())
        repeat(count - 1) {
            addresses.add(wallet.freshReceiveAddress())
        }
        return addresses
    }

    /**
     * Fetch UTXOs and balance for all wallet addresses.
     * Runs network calls — call from a coroutine/background thread.
     */
    fun fetchWalletState(
        mnemonic: String,
        host: String = ElectrumClient.DEFAULT_HOST,
        port: Int = ElectrumClient.DEFAULT_PORT,
    ): WalletState {
        val addresses = deriveAddresses(mnemonic)
        val allUtxos = mutableListOf<ElectrumClient.Utxo>()
        var totalBalance = 0L

        for (addr in addresses) {
            try {
                val scriptBytes = org.bitcoinj.script.ScriptBuilder.createOutputScript(addr).program
                val sh = ElectrumClient.scriptHash(scriptBytes)
                val utxos = ElectrumClient.listUnspent(sh, host, port)
                allUtxos.addAll(utxos)
                totalBalance += utxos.sumOf { it.value }
            } catch (_: Exception) {
                // Skip addresses that fail to query
            }
        }

        val serverInfo = try {
            ElectrumClient.testConnection(host, port)
        } catch (_: Exception) { "unknown" }

        return WalletState(
            addresses = addresses.map { it.toString() },
            utxos = allUtxos,
            balance = totalBalance,
            serverInfo = serverInfo,
        )
    }

    /**
     * Simple largest-first coin selection.
     * Returns selected UTXOs + total input value, or null if insufficient funds.
     */
    fun selectCoins(
        utxos: List<ElectrumClient.Utxo>,
        targetAmount: Long, // satoshis
        feePerVByte: Long,
    ): Pair<List<ElectrumClient.Utxo>, Long>? {
        // Estimate tx size: ~10.5 + 68*inputs + 31*outputs vbytes (P2WPKH)
        val sorted = utxos.sortedByDescending { it.value }
        val selected = mutableListOf<ElectrumClient.Utxo>()
        var total = 0L

        for (utxo in sorted) {
            selected.add(utxo)
            total += utxo.value
            val estimatedVBytes = 10.5 + 68.0 * selected.size + 31.0 * 2 // 2 outputs: payment + change
            val fee = (estimatedVBytes * feePerVByte).toLong()
            if (total >= targetAmount + fee) {
                return Pair(selected, total)
            }
        }
        return null // insufficient funds
    }
}
