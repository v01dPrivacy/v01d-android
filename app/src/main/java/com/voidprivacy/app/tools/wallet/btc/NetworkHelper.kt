package com.voidprivacy.app.tools.wallet.btc

import android.content.Context
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params

/**
 * Returns the correct bitcoinj NetworkParameters based on the app setting.
 */
object NetworkHelper {
    fun getParams(context: Context): NetworkParameters {
        return if (com.voidprivacy.app.AppSettings.isTestnet(context)) {
            TestNet3Params.get()
        } else {
            MainNetParams.get()
        }
    }

    fun getDefaultElectrumHost(context: Context): String {
        return if (com.voidprivacy.app.AppSettings.isTestnet(context)) {
            "electrum.blockstream.info"
        } else {
            "electrum.blockstream.info"
        }
    }

    fun getDefaultElectrumPort(context: Context): Int {
        return if (com.voidprivacy.app.AppSettings.isTestnet(context)) 60002 else 50002
    }
}
