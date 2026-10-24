package com.voidprivacy.app.tools.companion

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Share

data class CompanionApp(
    val name: String,
    val description: String,
    val icon: ImageVector,
    val packageName: String,
    val fdroidUrl: String,
    val githubUrl: String,
)

val companionApps = listOf(
    CompanionApp(
        name = "XMR Wallet",
        description = "Monerujo — Monero wallet",
        icon = Icons.Outlined.Lock,
        packageName = "com.m2049r.xmrwallet",
        fdroidUrl = "https://f-droid.monerujo.io/fdroid/repo",
        githubUrl = "https://github.com/m2049r/xmrwallet/releases",
    ),
    CompanionApp(
        name = "Auditor",
        description = "Hardware attestation & audit",
        icon = Icons.Outlined.Security,
        packageName = "app.attestation.auditor",
        fdroidUrl = "https://f-droid.org/packages/app.attestation.auditor/",
        githubUrl = "https://github.com/GrapheneOS/Auditor/releases",
    ),
    CompanionApp(
        name = "Profile Sharing",
        description = "Cross-profile file sharing",
        icon = Icons.Outlined.Share,
        packageName = "com.ventraldigital.interprofilesharing",
        fdroidUrl = "https://f-droid.org/packages/com.ventraldigital.interprofilesharing/",
        githubUrl = "https://github.com/VentralDigital/InterProfileSharing/releases",
    ),
)

object CompanionAppManager {
    fun isInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun launch(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (intent != null) {
            context.startActivity(intent)
            true
        } else {
            false
        }
    }

    fun openInstallPage(context: Context, app: CompanionApp) {
        // Try F-Droid first, fall back to GitHub
        val url = app.fdroidUrl
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }
}
