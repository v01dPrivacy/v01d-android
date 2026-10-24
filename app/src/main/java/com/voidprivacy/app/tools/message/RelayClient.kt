package com.voidprivacy.app.tools.message

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for the Void Message blind relay (Cloudflare Worker).
 *
 * Sends and polls opaque encrypted blobs keyed by routing token. The relay
 * never sees plaintext. When the in-app Tor VPN is enabled, this traffic is
 * routed through Tor automatically (all TCP goes through the TUN), giving IP
 * anonymity on top of the E2E encryption.
 */
object RelayClient {

    /** Queue an encrypted blob for a recipient's routing token. */
    fun send(relayUrl: String, token: String, blob: String): Boolean {
        val conn = open(relayUrl, "send", "POST")
        try {
            conn.doOutput = true
            val body = JSONObject().apply {
                put("token", token)
                put("blob", blob)
            }
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            val code = conn.responseCode
            return code in 200..299
        } finally {
            conn.disconnect()
        }
    }

    /** Fetch and clear all queued blobs for our routing token. */
    fun poll(relayUrl: String, token: String): List<String> {
        val conn = open(relayUrl, "poll?token=$token", "GET")
        try {
            val code = conn.responseCode
            if (code !in 200..299) return emptyList()
            val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val arr = JSONObject(body).optJSONArray("messages") ?: return emptyList()
            return (0 until arr.length()).map { arr.getString(it) }
        } finally {
            conn.disconnect()
        }
    }

    /** Liveness check. */
    fun healthy(relayUrl: String): Boolean {
        val conn = open(relayUrl, "health", "GET")
        try {
            return conn.responseCode in 200..299
        } catch (_: Exception) {
            return false
        } finally {
            conn.disconnect()
        }
    }

    /** Publish our public key bundle so offline peers can start a session with us. */
    fun publishBundle(relayUrl: String, token: String, bundleJson: String): Boolean {
        val conn = open(relayUrl, "bundle", "POST")
        try {
            conn.doOutput = true
            val body = JSONObject().apply {
                put("token", token)
                put("bundle", bundleJson)
            }
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            return conn.responseCode in 200..299
        } finally {
            conn.disconnect()
        }
    }

    /** Fetch a contact's public key bundle (null if they haven't published one). */
    fun fetchBundle(relayUrl: String, token: String): String? {
        val conn = open(relayUrl, "bundle?token=$token", "GET")
        try {
            if (conn.responseCode !in 200..299) return null
            val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            return JSONObject(body).optString("bundle").ifEmpty { null }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(relayUrl: String, path: String, method: String): HttpURLConnection {
        val base = relayUrl.trimEnd('/')
        val conn = URL("$base/$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        return conn
    }
}
