package com.voidprivacy.app

import com.voidprivacy.app.tools.wallet.btc.BtcWalletEngine
import org.bitcoinj.params.MainNetParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BtcWalletEngineTest {

    @Test
    fun `createWallet generates 12 word mnemonic`() {
        val wallet = BtcWalletEngine.createWallet()
        val words = wallet.mnemonic.split(" ")
        assertEquals(12, words.size)
    }

    @Test
    fun `createWallet generates valid address`() {
        val wallet = BtcWalletEngine.createWallet()
        assertTrue(wallet.receiveAddress.startsWith("1") || wallet.receiveAddress.startsWith("3") || wallet.receiveAddress.startsWith("bc1"))
    }

    @Test
    fun `createWallet generates unique wallets`() {
        val w1 = BtcWalletEngine.createWallet()
        val w2 = BtcWalletEngine.createWallet()
        assertNotEquals(w1.mnemonic, w2.mnemonic)
        assertNotEquals(w1.receiveAddress, w2.receiveAddress)
    }

    @Test
    fun `same seed produces same address`() {
        val w1 = BtcWalletEngine.createWallet()
        // Recreate wallet from same seed
        val seed = org.bitcoinj.wallet.DeterministicSeed(w1.mnemonic, null, "", 0L)
        val wallet = org.bitcoinj.wallet.Wallet.fromSeed(MainNetParams.get(), seed)
        assertEquals(w1.receiveAddress, wallet.currentReceiveAddress().toString())
    }

    @Test
    fun `exportWallet produces valid JSON`() {
        val wallet = BtcWalletEngine.createWallet()
        val json = BtcWalletEngine.exportWallet(wallet.mnemonic)
        assertTrue(json.contains("\"xpub\""))
        assertTrue(json.contains("\"addresses\""))
        assertTrue(json.contains("\"mainnet\""))
        assertTrue(json.contains(wallet.receiveAddress))
    }

    @Test
    fun `exportWallet contains 5 addresses`() {
        val wallet = BtcWalletEngine.createWallet()
        val json = BtcWalletEngine.exportWallet(wallet.mnemonic)
        val addressCount = json.split("\"").count { it.startsWith("1") || it.startsWith("3") || it.startsWith("bc1") }
        assertTrue(addressCount >= 5)
    }

    @Test
    fun `exportWallet does not contain private keys`() {
        val wallet = BtcWalletEngine.createWallet()
        val json = BtcWalletEngine.exportWallet(wallet.mnemonic)
        // xpub starts with "xpub" or "zpub", private keys start with "xprv" or "zprv"
        assertTrue(!json.contains("xprv") && !json.contains("zprv"))
        // Should not contain the mnemonic
        assertTrue(!json.contains(wallet.mnemonic))
    }
}
