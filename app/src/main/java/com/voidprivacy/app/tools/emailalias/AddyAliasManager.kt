package com.voidprivacy.app.tools.emailalias

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * addy.io API client — creates REAL email aliases that forward to the user's
 * real mailbox. Free plan: unlimited "standard" aliases on the username
 * subdomain; shared-domain aliases are limited. API works on the free tier.
 *
 * Auth: `Authorization: Bearer <api key>` (addy.io → Settings → API Key).
 * All state (key, domains) in EncryptedSharedPreferences.
 *
 * API reference: https://app.addy.io/docs/
 */
object AddyAliasManager {

    private const val BASE = "https://app.addy.io/api/v1"
    private const val PREFS_FILE = "addy_prefs_encrypted"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_DOMAINS_CACHE = "domains_cache_json"

    data class AddyAlias(
        val id: String,
        val email: String,
        val description: String,
        val active: Boolean,
    )

    data class AddyDomains(
        val defaultDomain: String,
        val shared: List<String>,
        val custom: List<String>,
    )

    // ─── Local encrypted state ─────────────────────────────────────

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getApiKey(context: Context): String =
        prefs(context).getString(KEY_API_KEY, "") ?: ""

    fun isConnected(context: Context): Boolean = getApiKey(context).isNotBlank()

    fun disconnect(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun setApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    // ─── API calls (blocking — call from a background thread) ──────

    private fun request(
        apiKey: String,
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
    ): JSONObject {
        val conn = (URL("$BASE$path")).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "v01d-android")
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }
                    .getOrElse { "HTTP $code" }
                throw RuntimeException(msg)
            }
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    /** Validate an API key. On success stores it and returns the account email. */
    fun connect(context: Context, apiKey: String): Result<String> {
        return try {
            val details = request(apiKey.trim(), "/api-token-details")
            val email = details.optString("email", "addy.io account")
            setApiKey(context, apiKey)
            Result.success(email)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Alias domains available to the account (cached in encrypted prefs). */
    fun getDomains(context: Context): AddyDomains? {
        val prefs = prefs(context)
        val cached = prefs.getString(KEY_DOMAINS_CACHE, null)
        val apiKey = getApiKey(context)
        if (apiKey.isBlank()) return null
        return try {
            val resp = request(apiKey, "/domain-options")
            val shared = resp.optJSONArray("alias_domains") ?: JSONArray()
            val custom = resp.optJSONArray("custom_domains") ?: JSONArray()
            val domains = AddyDomains(
                defaultDomain = resp.optString("default_alias_domain", ""),
                shared = (0 until shared.length()).map { shared.getString(it) },
                custom = (0 until custom.length()).map { custom.getString(it) },
            )
            prefs.edit().putString(KEY_DOMAINS_CACHE, JSONObject().apply {
                put("default", domains.defaultDomain)
                put("shared", JSONArray(domains.shared))
                put("custom", JSONArray(domains.custom))
            }.toString()).apply()
            domains
        } catch (e: Exception) {
            // Offline fallback: use the cache if we have one
            cached?.let { c ->
                runCatching {
                    val o = JSONObject(c)
                    val s = o.optJSONArray("shared") ?: JSONArray()
                    val cu = o.optJSONArray("custom") ?: JSONArray()
                    AddyDomains(
                        defaultDomain = o.optString("default", ""),
                        shared = (0 until s.length()).map { s.getString(it) },
                        custom = (0 until cu.length()).map { cu.getString(it) },
                    )
                }.getOrNull()
            }
        }
    }

    /** Create a real alias. `description` = the site/service name, shown in addy.io. */
    fun createAlias(context: Context, domain: String, siteName: String): Result<String> {
        val apiKey = getApiKey(context)
        if (apiKey.isBlank()) return Result.failure(IllegalStateException("Not connected"))
        return try {
            val resp = request(
                apiKey,
                "/aliases",
                method = "POST",
                body = JSONObject().apply {
                    put("domain", domain)
                    put("description", siteName.take(50))
                    put("format", "random")
                },
            )
            val email = resp.getJSONObject("data").optString("email")
            if (email.isBlank()) throw RuntimeException("No email in response")
            Result.success(email)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Most recent aliases (newest first). */
    fun listAliases(context: Context, limit: Int = 10): List<AddyAlias> {
        val apiKey = getApiKey(context)
        if (apiKey.isBlank()) return emptyList()
        return try {
            val resp = request(apiKey, "/aliases")
            val data = resp.optJSONArray("data") ?: JSONArray()
            (0 until minOf(limit, data.length())).map { i ->
                val o = data.getJSONObject(i)
                AddyAlias(
                    id = o.optString("id"),
                    email = o.optString("email"),
                    description = o.optString("description", ""),
                    active = o.optBoolean("active", true),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Toggle an alias on/off (a deactivated alias stops forwarding). */
    fun setAliasActive(context: Context, alias: AddyAlias, active: Boolean): Result<Unit> {
        val apiKey = getApiKey(context)
        if (apiKey.isBlank()) return Result.failure(IllegalStateException("Not connected"))
        return try {
            request(
                apiKey,
                "/aliases/${alias.id}",
                method = "PATCH",
                body = JSONObject().apply { put("active", active) },
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
