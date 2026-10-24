package com.voidprivacy.app.tools.wallet.btc

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * BTC→EUR exchange rate for plain-language amounts ("≈ €").
 * Public ticker, cached in memory for 5 minutes. Fails silently —
 * fiat is a nice-to-have, never a requirement.
 */
object FiatPrice {

    private const val TAG = "FiatPrice"
    private const val TICKER_URL = "https://blockchain.info/ticker"
    private const val CACHE_MS = 5 * 60 * 1000L

    @Volatile
    private var cachedRate: Double? = null
    @Volatile
    private var cachedAt: Long = 0

    /** Current EUR price of 1 BTC, or null if offline/error. */
    @Synchronized
    fun eurPerBtc(): Double? {
        val now = System.currentTimeMillis()
        if (cachedRate != null && now - cachedAt < CACHE_MS) return cachedRate
        try {
            val conn = URL(TICKER_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            try {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val eur = JSONObject(body).getJSONObject("EUR").getDouble("last")
                if (eur > 0) {
                    cachedRate = eur
                    cachedAt = now
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "fiat fetch failed: ${e.message}")
        }
        return cachedRate
    }

    /** Format sats as "≈ €12,34" or empty string when the rate is unavailable. */
    fun satsToEurLabel(sats: Long): String {
        val rate = eurPerBtc() ?: return ""
        val eur = sats / 100_000_000.0 * rate
        return "≈ €" + String.format(java.util.Locale.ITALY, "%,.2f", eur)
    }
}
