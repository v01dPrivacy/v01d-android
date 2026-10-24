package com.voidprivacy.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.voidprivacy.app.ui.dashboard.DashboardScreen
import com.voidprivacy.app.tools.emailalias.EmailAliasScreen
import com.voidprivacy.app.tools.txchecker.TxCheckerScreen
import com.voidprivacy.app.tools.wallet.btc.BtcWalletScreen
import com.voidprivacy.app.tools.wallet.btc.PsbtSignerScreen
import com.voidprivacy.app.tools.wallet.btc.SendScreen
import com.voidprivacy.app.tools.wallet.btc.TxHistoryScreen
import com.voidprivacy.app.tools.score.PrivacyScoreScreen
import com.voidprivacy.app.tools.companion.CompanionAppsScreen
import com.voidprivacy.app.tools.tor.TorScreen
import com.voidprivacy.app.ui.settings.SettingsScreen
import com.voidprivacy.app.ui.about.AboutScreen
import com.voidprivacy.app.tools.contacts.ContactsScreen
import com.voidprivacy.app.tools.wallet.xmr.XmrWalletScreen
import com.voidprivacy.app.tools.message.MessageScreen
import com.voidprivacy.app.tools.message.ChatScreen
import com.voidprivacy.app.tools.message.AddContactScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val EMAIL_ALIAS = "email_alias"
    const val TX_CHECKER = "tx_checker"
    const val BTC_WALLET = "btc_wallet"
    const val PSBT_SIGNER = "psbt_signer"
    const val BTC_SEND = "btc_send"
    const val TX_HISTORY = "tx_history"
    const val PRIVACY_SCORE = "privacy_score"
    const val COMPANION_APPS = "companion_apps"
    const val TOR = "tor"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val HELP = "help"
    const val CONTACTS = "contacts"
    const val PICK_CONTACT = "pick_contact"
    const val XMR_WALLET = "xmr_wallet"
    const val MESSAGE = "message"
    const val MESSAGE_CHAT = "message_chat/{token}"
    const val MESSAGE_ADD_CONTACT = "message_add_contact"
}

@Composable
fun VoidNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD
    ) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable(Routes.EMAIL_ALIAS) {
            EmailAliasScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.HELP) {
            com.voidprivacy.app.ui.help.HelpScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TX_CHECKER) {
            TxCheckerScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.BTC_WALLET) {
            BtcWalletScreen(
                onBack = { navController.popBackStack() },
                onPsbt = { navController.navigate(Routes.PSBT_SIGNER) },
                onSend = { navController.navigate(Routes.BTC_SEND) },
                onHistory = { navController.navigate(Routes.TX_HISTORY) },
            )
        }
        composable(Routes.PSBT_SIGNER) {
            PsbtSignerScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.BTC_SEND) { backStackEntry ->
            val pickedAddress = backStackEntry.savedStateHandle.get<String>("picked_address")
            SendScreen(
                onBack = { navController.popBackStack() },
                onPickContact = {
                    backStackEntry.savedStateHandle.remove<String>("picked_address")
                    navController.navigate(Routes.PICK_CONTACT)
                },
                initialRecipient = pickedAddress,
            )
        }
        composable(Routes.TX_HISTORY) {
            TxHistoryScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PRIVACY_SCORE) {
            PrivacyScoreScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.COMPANION_APPS) {
            CompanionAppsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TOR) {
            TorScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CONTACTS) {
            ContactsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PICK_CONTACT) {
            ContactsScreen(
                onBack = { navController.popBackStack() },
                onPickContact = { address ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("picked_address", address)
                    navController.popBackStack()
                },
            )
        }
        composable(Routes.XMR_WALLET) {
            XmrWalletScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.MESSAGE) {
            MessageScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { token -> navController.navigate("message_chat/$token") },
                onAddContact = { navController.navigate(Routes.MESSAGE_ADD_CONTACT) },
                onShowIdentity = { navController.navigate(Routes.MESSAGE_ADD_CONTACT) },
            )
        }
        composable(Routes.MESSAGE_CHAT) { backStackEntry ->
            val token = backStackEntry.arguments?.getString("token") ?: ""
            ChatScreen(
                contactToken = token,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.MESSAGE_ADD_CONTACT) {
            AddContactScreen(onBack = { navController.popBackStack() })
        }
    }
}
