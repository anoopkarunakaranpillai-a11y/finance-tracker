package com.anoop.financetracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.anoop.financetracker.AppContainer
import com.anoop.financetracker.study.StudyHubScreen
import com.anoop.financetracker.study.StudyWebScreen
import com.anoop.financetracker.ui.screens.AccountDetailScreen
import com.anoop.financetracker.ui.screens.AccountEditScreen
import com.anoop.financetracker.ui.screens.AccountsScreen
import com.anoop.financetracker.ui.screens.CategoriesScreen
import com.anoop.financetracker.ui.screens.DashboardScreen
import com.anoop.financetracker.ui.screens.PlanScreen
import com.anoop.financetracker.ui.screens.ReportsScreen
import com.anoop.financetracker.ui.screens.SettingsScreen
import com.anoop.financetracker.ui.screens.TransactionsScreen
import com.anoop.financetracker.ui.screens.TxEditorScreen
import com.anoop.financetracker.ui.screens.WelcomeScreen

data class TopDest(val route: String, val label: String, val icon: ImageVector)

val TOP = listOf(
    TopDest("dashboard", "Dashboard", Icons.Filled.Home),
    TopDest("transactions", "Transactions", Icons.AutoMirrored.Filled.List),
    TopDest("plan", "Budgets", Icons.Filled.DateRange),
    TopDest("reports", "Reports", Icons.Filled.Info),
    TopDest("settings", "Settings", Icons.Filled.Settings),
)

object Routes {
    fun editTx(id: String? = null, type: String? = null, acct: String? = null) =
        "tx?id=${id ?: ""}&type=${type ?: ""}&acct=${acct ?: ""}"
    fun account(id: String) = "account/$id"
    fun editAccount(id: String? = null) = "accountEdit?id=${id ?: ""}"
    fun study(who: String) = "study/$who"
}

/** Root of the app UI. [container] is passed in so tests can supply an in-memory database. */
@Composable
fun AppRoot(container: AppContainer, nav: NavHostController = rememberNavController()) {
    val vm: AppViewModel = viewModel(factory = viewModelFactory { initializer { AppViewModel(container) } })
    val st by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm) {
        vm.messages.collect { msg ->
            val r = snack.showSnackbar(msg.text, actionLabel = msg.actionLabel, duration = if (msg.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) msg.action?.invoke()
        }
    }

    if (!st.loaded) {
        Box(Modifier.fillMaxSize())
        return
    }
    if (!st.settings.welcomeDone && st.accounts.isEmpty()) {
        Scaffold(snackbarHost = { SnackbarHost(snack) }) { p -> Box(Modifier.padding(p)) { WelcomeScreen(vm, st) } }
        return
    }

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "dashboard"
    val isTop = TOP.any { it.route == route }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val go: (String) -> Unit = { r ->
        nav.navigate(r) { popUpTo("dashboard") { saveState = true }; launchSingleTop = true; restoreState = true }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (isTop && !wide) NavigationBar {
                TOP.forEach { d ->
                    NavigationBarItem(selected = route == d.route, onClick = { go(d.route) },
                        icon = { Icon(d.icon, contentDescription = null) }, label = { Text(d.label, maxLines = 1) },
                        modifier = Modifier.testTag("nav-${d.route}"))
                }
            }
        },
        floatingActionButton = {
            if (route == "dashboard" || route == "transactions") ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.editTx()) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add") },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("fab-add"),
            )
        },
    ) { pad ->
        Row(Modifier.fillMaxSize().padding(pad)) {
            if (isTop && wide) NavigationRail {
                TOP.forEach { d ->
                    NavigationRailItem(selected = route == d.route, onClick = { go(d.route) },
                        icon = { Icon(d.icon, contentDescription = null) }, label = { Text(d.label) },
                        modifier = Modifier.testTag("nav-${d.route}"))
                }
            }
            Box(Modifier.weight(1f)) {
                NavHost(nav, startDestination = "dashboard") {
                    composable("dashboard") { DashboardScreen(st, nav) }
                    composable("transactions") { TransactionsScreen(st, vm, nav) }
                    composable("plan") { PlanScreen(st, vm) }
                    composable("reports") { ReportsScreen(st, vm) }
                    composable("settings") { SettingsScreen(st, vm, nav) }
                    composable(
                        "tx?id={id}&type={type}&acct={acct}",
                        arguments = listOf(
                            navArgument("id") { type = NavType.StringType; defaultValue = "" },
                            navArgument("type") { type = NavType.StringType; defaultValue = "" },
                            navArgument("acct") { type = NavType.StringType; defaultValue = "" },
                        ),
                    ) { e ->
                        val a = e.arguments
                        TxEditorScreen(container, st, vm, a?.getString("id").orEmpty().ifBlank { null },
                            a?.getString("type").orEmpty().ifBlank { null }, a?.getString("acct").orEmpty().ifBlank { null }) { nav.popBackStack() }
                    }
                    composable("accounts") { AccountsScreen(st, nav) }
                    composable("account/{id}") { e -> AccountDetailScreen(st, vm, nav, e.arguments?.getString("id").orEmpty()) }
                    composable("accountEdit?id={id}", arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "" })) { e ->
                        AccountEditScreen(st, vm, e.arguments?.getString("id").orEmpty().ifBlank { null }) { nav.popBackStack() }
                    }
                    composable("categories") { CategoriesScreen(st, vm) { nav.popBackStack() } }
                    composable("study") { StudyHubScreen(nav) }
                    composable("study/{who}") { e -> StudyWebScreen(e.arguments?.getString("who") ?: "navw") { nav.popBackStack() } }
                }
            }
        }
    }
}
