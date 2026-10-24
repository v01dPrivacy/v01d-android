package com.voidprivacy.app

import com.voidprivacy.app.tools.wallet.btc.ElectrumClient
import com.voidprivacy.app.tools.wallet.btc.UtxoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletLogicTest {

    private fun utxo(value: Long, txid: String = "aa".repeat(32)) =
        ElectrumClient.Utxo(txid = txid, vout = 0, value = value, height = 100)

    // === Coin Selection ===

    @Test
    fun `select single sufficient utxo`() {
        val utxos = listOf(utxo(100_000))
        val result = UtxoManager.selectCoins(utxos, 50_000, 10)
        assertNotNull(result)
        assertEquals(1, result!!.first.size)
        assertEquals(100_000, result.second)
    }

    @Test
    fun `select multiple utxos when one insufficient`() {
        val utxos = listOf(utxo(30_000), utxo(40_000), utxo(50_000))
        val result = UtxoManager.selectCoins(utxos, 80_000, 10)
        assertNotNull(result)
        assertTrue(result!!.second >= 80_000)
    }

    @Test
    fun `insufficient funds returns null`() {
        val utxos = listOf(utxo(10_000))
        val result = UtxoManager.selectCoins(utxos, 100_000, 10)
        assertNull(result)
    }

    @Test
    fun `largest first selection order`() {
        val utxos = listOf(utxo(10_000), utxo(50_000), utxo(30_000))
        val result = UtxoManager.selectCoins(utxos, 40_000, 1)
        assertNotNull(result)
        // Should pick the 50k utxo first (largest)
        assertEquals(50_000, result!!.first[0].value)
    }

    @Test
    fun `empty utxo list returns null`() {
        val result = UtxoManager.selectCoins(emptyList(), 10_000, 10)
        assertNull(result)
    }

    @Test
    fun `fee is accounted for in selection`() {
        // 50k sats, sending 49k with high fee should fail
        val utxos = listOf(utxo(50_000))
        val result = UtxoManager.selectCoins(utxos, 49_000, 100) // 100 sat/vB
        // Fee for 1in 2out ≈ (10.5 + 68 + 62) * 100 ≈ 14050 sats
        // 50000 < 49000 + 14050 → insufficient
        assertNull(result)
    }

    // === Script Hash ===

    @Test
    fun `scriptHash produces 64 char hex`() {
        val script = byteArrayOf(0x00, 0x14) + ByteArray(20) { 0x01 }
        val hash = ElectrumClient.scriptHash(script)
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `scriptHash is deterministic`() {
        val script = byteArrayOf(0x00, 0x14) + ByteArray(20) { 0xAB.toByte() }
        val h1 = ElectrumClient.scriptHash(script)
        val h2 = ElectrumClient.scriptHash(script)
        assertEquals(h1, h2)
    }

    @Test
    fun `scriptHash differs for different scripts`() {
        val s1 = byteArrayOf(0x00, 0x14) + ByteArray(20) { 0x01 }
        val s2 = byteArrayOf(0x00, 0x14) + ByteArray(20) { 0x02 }
        assertTrue(ElectrumClient.scriptHash(s1) != ElectrumClient.scriptHash(s2))
    }
}
