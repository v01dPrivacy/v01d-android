package com.voidprivacy.app.tools.wallet.xmr

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Monero JSON-RPC client for daemon (monerod) and wallet RPC (monero-wallet-rpc).
 * Supports both HTTP and HTTPS.
 *
 * Daemon RPC: get_height, get_info
 * Wallet RPC: get_balance, get_address, get_transfers
 */
object MoneroRpcClient {

    data class NodeInfo(
        val height: Long,
        val network: String,
        val status: String,
    )

    data class Balance(
        val balance: Long,       // atomic units (1 XMR = 10^12)
        val unlockedBalance: Long,
    )

    data class Transfer(
        val txid: String,
        val amount: Long,
        val height: Long,
        val timestamp: Long,
        val isIncoming: Boolean,
        val confirmations: Long,
    )

    // Trust-all TLS for self-signed node certs (user's own infrastructure)
    private fun trustAllTls() {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(c: Array<X509Certificate>?, t: String?) {}
            override fun checkServerTrusted(c: Array<X509Certificate>?, t: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, trustAll, SecureRandom())
        HttpsURLConnection.setDefaultSSLSocketFactory(ctx.socketFactory)
        HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
    }

    private var tlsConfigured = false

    private fun rpcCall(nodeUrl: String, method: String, params: JSONObject = JSONObject()): JSONObject {
        if (!tlsConfigured && nodeUrl.startsWith("https")) {
            trustAllTls()
            tlsConfigured = true
        }

        val url = URL("$nodeUrl/json_rpc")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true

            val request = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", "0")
                put("method", method)
                put("params", params)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(request.toString()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            val json = JSONObject(body)
            if (json.has("error")) {
                val err = json.getJSONObject("error")
                throw RuntimeException("RPC error ${err.optInt("code")}: ${err.optString("message")}")
            }
            return json.optJSONObject("result") ?: JSONObject()
        } finally {
            conn.disconnect()
        }
    }

    // ─── Daemon RPC ────────────────────────────────────────────────

    /** Check daemon connectivity and get blockchain height. */
    fun getDaemonInfo(nodeUrl: String): NodeInfo {
        val result = rpcCall(nodeUrl, "get_info")
        return NodeInfo(
            height = result.optLong("height"),
            network = result.optString("nettype", "mainnet"),
            status = result.optString("status", "UNKNOWN"),
        )
    }

    // ─── Wallet RPC ────────────────────────────────────────────────

    /** Get wallet balance (requires monero-wallet-rpc with wallet loaded). */
    fun getBalance(walletRpcUrl: String): Balance {
        val result = rpcCall(walletRpcUrl, "get_balance")
        return Balance(
            balance = result.optLong("balance"),
            unlockedBalance = result.optLong("unlocked_balance"),
        )
    }

    /** Get wallet address. */
    fun getAddress(walletRpcUrl: String): String {
        val result = rpcCall(walletRpcUrl, "get_address")
        return result.optString("address")
    }

    /** Get transaction history. */
    fun getTransfers(walletRpcUrl: String, currentHeight: Long = 0): List<Transfer> {
        val params = JSONObject().apply {
            put("in", true)
            put("out", true)
            put("pending", false)
            put("failed", false)
            put("pool", false)
        }
        val result = rpcCall(walletRpcUrl, "get_transfers", params)
        val transfers = mutableListOf<Transfer>()

        for (key in listOf("in", "out")) {
            val arr = result.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) {
                val tx = arr.getJSONObject(i)
                transfers.add(
                    Transfer(
                        txid = tx.optString("txid"),
                        amount = tx.optLong("amount"),
                        height = tx.optLong("height"),
                        timestamp = tx.optLong("timestamp"),
                        isIncoming = key == "in",
                        confirmations = if (currentHeight > 0) currentHeight - tx.optLong("height") else 0,
                    )
                )
            }
        }
        return transfers.sortedByDescending { it.height }
    }

    /** Format atomic units to XMR string. */
    fun formatXmr(atomicUnits: Long): String {
        val xmr = atomicUnits / 1_000_000_000_000.0
        return String.format("%.12f", xmr).trimEnd('0').trimEnd('.')
    }
}
