package com.voidprivacy.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App settings persisted via SharedPreferences.
 * Theme is exposed as Compose state for instant recomposition.
 */
object AppSettings {
    private const val PREFS = "void_settings"
    private const val KEY_THEME = "theme" // system, light, dark
    private const val KEY_ELECTRUM_HOST = "electrum_host"
    private const val KEY_ELECTRUM_PORT = "electrum_port"
    private const val KEY_ALIAS_DOMAIN = "alias_domain"
    private const val KEY_NETWORK = "network" // mainnet, testnet
    private const val KEY_LANGUAGE = "language" // system, en, it

    // Observable theme state for Compose
    var themeState by mutableStateOf("system")
        private set

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun initTheme(context: Context) {
        themeState = prefs(context).getString(KEY_THEME, "system")!!
    }

    fun getTheme(ctx: Context) = themeState
    fun setTheme(ctx: Context, v: String) {
        themeState = v
        prefs(ctx).edit().putString(KEY_THEME, v).apply()
    }

    fun getElectrumHost(ctx: Context) = prefs(ctx).getString(KEY_ELECTRUM_HOST, "electrum.blockstream.info")!!
    fun setElectrumHost(ctx: Context, v: String) = prefs(ctx).edit().putString(KEY_ELECTRUM_HOST, v).apply()

    fun getElectrumPort(ctx: Context) = prefs(ctx).getInt(KEY_ELECTRUM_PORT, 50002)
    fun setElectrumPort(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_ELECTRUM_PORT, v).apply()

    fun getAliasDomain(ctx: Context) = prefs(ctx).getString(KEY_ALIAS_DOMAIN, "")!!
    fun setAliasDomain(ctx: Context, v: String) = prefs(ctx).edit().putString(KEY_ALIAS_DOMAIN, v).apply()

    fun getNetwork(ctx: Context) = prefs(ctx).getString(KEY_NETWORK, "mainnet")!!
    fun setNetwork(ctx: Context, v: String) = prefs(ctx).edit().putString(KEY_NETWORK, v).apply()
    fun isTestnet(ctx: Context) = getNetwork(ctx) == "testnet"

    fun getLanguage(ctx: Context) = prefs(ctx).getString(KEY_LANGUAGE, "system")!!
    fun setLanguage(ctx: Context, v: String) = prefs(ctx).edit().putString(KEY_LANGUAGE, v).apply()

    /** Wrap a context with the user-chosen app language (no-op for "system"). */
    fun wrapLocale(ctx: Context): Context {
        val lang = getLanguage(ctx)
        if (lang == "system") return ctx
        val base = android.content.res.Configuration(ctx.resources.configuration)
        base.setLocales(android.os.LocaleList(java.util.Locale.forLanguageTag(lang)))
        return ctx.createConfigurationContext(base)
    }
}
