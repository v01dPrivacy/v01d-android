package com.voidprivacy.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voidprivacy.app.R
import com.voidprivacy.app.navigation.Routes

data class ToolItem(
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val icon: ImageVector,
    val route: String?,
)

private val basics = listOf(
    ToolItem(
        title = R.string.dash_btc_wallet,
        subtitle = R.string.dash_btc_wallet_desc,
        icon = Icons.Outlined.AccountBalanceWallet,
        route = Routes.BTC_WALLET,
    ),
    ToolItem(
        title = R.string.dash_xmr_wallet,
        subtitle = R.string.dash_xmr_wallet_desc,
        icon = Icons.Outlined.Lock,
        route = Routes.XMR_WALLET,
    ),
    ToolItem(
        title = R.string.dash_secure_message,
        subtitle = R.string.dash_secure_message_desc,
        icon = Icons.Outlined.Forum,
        route = Routes.MESSAGE,
    ),
    ToolItem(
        title = R.string.dash_email_aliases,
        subtitle = R.string.dash_email_aliases_desc,
        icon = Icons.Outlined.Email,
        route = Routes.EMAIL_ALIAS,
    ),
    ToolItem(
        title = R.string.dash_privacy_score,
        subtitle = R.string.dash_privacy_score_desc,
        icon = Icons.Outlined.Shield,
        route = Routes.PRIVACY_SCORE,
    ),
    ToolItem(
        title = R.string.dash_tor_routing,
        subtitle = R.string.dash_tor_routing_desc,
        icon = Icons.Outlined.Router,
        route = Routes.TOR,
    ),
    ToolItem(
        title = R.string.dash_companion_apps,
        subtitle = R.string.dash_companion_apps_desc,
        icon = Icons.Outlined.Apps,
        route = Routes.COMPANION_APPS,
    ),
    ToolItem(
        title = R.string.dash_help,
        subtitle = R.string.dash_help_desc,
        icon = Icons.Outlined.MenuBook,
        route = Routes.HELP,
    ),
    ToolItem(
        title = R.string.dash_settings,
        subtitle = R.string.dash_settings_desc,
        icon = Icons.Outlined.Settings,
        route = Routes.SETTINGS,
    ),
    ToolItem(
        title = R.string.dash_about,
        subtitle = R.string.dash_about_desc,
        icon = Icons.Outlined.Info,
        route = Routes.ABOUT,
    ),
)

/** Expert-only tools — kept, but out of the way. */
private val advanced = listOf(
    ToolItem(
        title = R.string.dash_tx_check,
        subtitle = R.string.dash_tx_check_desc,
        icon = Icons.Outlined.Search,
        route = Routes.TX_CHECKER,
    ),
    ToolItem(
        title = R.string.dash_address_book,
        subtitle = R.string.dash_address_book_desc,
        icon = Icons.Outlined.Contacts,
        route = Routes.CONTACTS,
    ),
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "v01d",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                text = stringResource(R.string.dash_subtitle),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(2) }) {
                    Text(
                        text = stringResource(R.string.dash_section_basics),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(basics) { tool ->
                    ToolCard(
                        tool = tool,
                        onClick = { tool.route?.let { onNavigate(it) } },
                    )
                }
                item(span = { GridItemSpan(2) }) {
                    Text(
                        text = stringResource(R.string.dash_section_advanced),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(advanced) { tool ->
                    ToolCard(
                        tool = tool,
                        onClick = { tool.route?.let { onNavigate(it) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolCard(
    tool: ToolItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable(enabled = tool.route != null, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = stringResource(tool.title),
                modifier = Modifier.size(32.dp),
                tint = if (tool.route != null)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline,
            )
            Column {
                Text(
                    text = stringResource(tool.title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(tool.subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
