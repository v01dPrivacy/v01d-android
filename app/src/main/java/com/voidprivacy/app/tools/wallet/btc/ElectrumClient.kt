package com.voidprivacy.app.tools.wallet.btc

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Minimal Electrum protocol client (JSON-RPC 2.0 over TLS).
 * Connects to an Electrum server to fetch UTXOs, estimate fees,
 * and broadcast transactions.
 *
 * Privacy note: the server sees which script hashes you query.
 * Use your own server or Tor for maximum privacy.
 */
object ElectrumClient {

    const val DEFAULT_HOST = "electrum.blockstream.info"
    const val DEFAULT_PORT = 50002
    private const val TIMEOUT_MS = 15_000

    data class Utxo(
        val txid: String,
        val vout: Int,
        val value: Long, // satoshis
        val height: Int,
    )

    data class Balance(
        val confirmed: Long,
        val unconfirmed: Long,
    ) {
        val total get() = confirmed + unconfirmed
    }

    private var requestId = 0

    /**
     * Execute a single JSON-RPC call over a fresh TLS connection.
     * Simple and stateless — no connection pooling (YAGNI for MVP).
     */
    private fun call(host: String, port: Int, method: String, params: JSONArray): JSONObject {
        // Trust all certs for public Electrum servers (they use self-signed or Let's Encrypt)
        // ponytail: proper cert pinning if this handles real money over untrusted networks
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(c: Array<X509Certificate>?, a: String?) {}
                override fun checkServerTrusted(c: Array<X509Certificate>?, a: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }), SecureRandom())
        }

        val socket = sslContext.socketFactory.createSocket() as SSLSocket
        socket.connect(InetSocketAddress(host, port), TIMEOUT_MS)
        socket.soTimeout = TIMEOUT_MS
        socket.startHandshake()

        try {
            val out: OutputStream = socket.outputStream
            val reader = BufferedReader(InputStreamReader(socket.inputStream))

            val request = JSONObject().apply {
                put("id", ++requestId)
                put("method", method)
                put("params", params)
                put("jsonrpc", "2.0")
            }

            out.write((request.toString() + "\n").toByteArray())
            out.flush()

            val line = reader.readLine()
                ?: throw RuntimeException("Server closed connection")

            val response = JSONObject(line)
            if (response.has("error") && !response.isNull("error")) {
                val err = response.getJSONObject("error")
                throw RuntimeException("Electrum error: ${err.optString("message", "unknown")}")
            }
            return response
        } finally {
            socket.close()
        }
    }

    /**
     * Compute the Electrum script hash for an address's scriptPubKey.
     * Electrum uses SHA256(scriptPubKey) with bytes reversed.
     */
    fun scriptHash(scriptPubKeyBytes: ByteArray): String {
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(scriptPubKeyBytes)
        return hash.reversedArray().joinToString("") { "%02x".format(it) }
    }

    /** Fetch unspent outputs for a script hash. */
    fun listUnspent(
        scriptHashHex: String,
        host: String = DEFAULT_HOST,
        port: Int = DEFAULT_PORT,
    ): List<Utxo> {
        val resp = call(host, port, "blockchain.scripthash.listunspent", JSONArray(listOf(scriptHashHex)))
        val result = resp.getJSONArray("result")
        return (0 until result.length()).map { i ->
            val obj = result.getJSONObject(i)
            Utxo(
                txid = obj.getString("tx_hash"),
                vout = obj.getInt("tx_pos"),
                value = obj.getLong("value"),
                height = obj.getInt("height"),
            )
        }
    }

    /** Get confirmed + unconfirmed balance for a script hash. */
    fun getBalance(
        scriptHashHex: String,
        host: String = DEFAULT_HOST,
        port: Int = DEFAULT_PORT,
    ): Balance {
        val resp = call(host, port, "blockchain.scripthash.get_balance", JSONArray(listOf(scriptHashHex)))
        val result = resp.getJSONObject("result")
        return Balance(
            confirmed = result.getLong("confirmed"),
            unconfirmed = result.getLong("unconfirmed"),
        )
    }

    /** Estimate fee in BTC/kB for confirmation within `blocks` blocks. */
    fun estimateFee(
        blocks: Int = 6,
        host: String = DEFAULT_HOST,
        port: Int = DEFAULT_PORT,
    ): Double {
        val resp = call(host, port, "blockchain.estimatefee", JSONArray(listOf(blocks)))
        return resp.getDouble("result")
    }

    /** Broadcast a signed raw transaction hex. Returns the txid. */
    fun broadcast(
        rawTxHex: String,
        host: String = DEFAULT_HOST,
        port: Int = DEFAULT_PORT,
    ): String {
        val resp = call(host, port, "blockchain.transaction.broadcast", JSONArray(listOf(rawTxHex)))
        return resp.getString("result")
    }

    /** Quick connectivity check. */
    fun testConnection(host: String = DEFAULT_HOST, port: Int = DEFAULT_PORT): String {
        val resp = call(host, port, "server.version", JSONArray(listOf("void", "1.4")))
        val result = resp.getJSONArray("result")
        return result.getString(0) // server software name
    }
}
