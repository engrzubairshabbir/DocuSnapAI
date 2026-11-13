package com.docusnap.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.docusnap.ai.ui.screens.ConversationalSearchScreen
import com.docusnap.ai.ui.screens.SmartVaultScreen
import com.docusnap.ai.ui.screens.DocumentInspectorScreen
import com.docusnap.ai.ui.screens.SettingsScreen
import androidx.compose.material.icons.filled.WorkspacePremium
import com.docusnap.ai.i18n.AppStrings
import com.docusnap.ai.ui.screens.SubscriptionScreen
import com.docusnap.ai.ui.theme.AccentBlue
import com.docusnap.ai.ui.theme.BgDark
import com.docusnap.ai.ui.theme.BorderDark
import com.docusnap.ai.ui.theme.SurfaceDark
import com.docusnap.ai.ui.theme.TextMuted
import com.docusnap.ai.ui.theme.TextPrimary

sealed class Screen(val route: String, val titleKey: String) {
    data object Search : Screen("search", AppStrings.NAV_ASK_AI)
    data object Vault : Screen("vault", AppStrings.NAV_VAULT)
    data object Subscription : Screen("subscription", AppStrings.NAV_PREMIUM)
    data object Settings : Screen("settings", AppStrings.NAV_SETTINGS)
    data object Inspector : Screen("inspector/{docId}", "Inspector") {
        fun createRoute(docId: String) = "inspector/$docId"
    }
}

@Composable
fun DocuSnapNavHost(viewModel: DocuSnapViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentLang by viewModel.currentLanguage.collectAsState()

    val bottomNavItems = listOf(
        Screen.Search to Icons.Default.AutoAwesome,
        Screen.Vault to Icons.Default.Folder,
        Screen.Subscription to Icons.Default.WorkspacePremium,
        Screen.Settings to Icons.Default.Settings
    )

    val showBottomBar = currentRoute in listOf(
        Screen.Search.route,
        Screen.Vault.route,
        Screen.Subscription.route,
        Screen.Settings.route
    )

    Scaffold(
        containerColor = BgDark,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = SurfaceDark,
                    tonalElevation = 0.dp,
                    modifier = Modifier.background(SurfaceDark)
                ) {
                    bottomNavItems.forEach { (screen, icon) ->
                        val isSelected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = AppStrings.get(screen.titleKey, currentLang),
                                    tint = if (isSelected) AccentBlue else TextMuted
                                )
                            },
                            label = {
                                Text(
                                    text = AppStrings.get(screen.titleKey, currentLang),
                                    color = if (isSelected) AccentBlue else TextMuted,
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Search.route,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            composable(Screen.Search.route) {
                ConversationalSearchScreen(
                    viewModel = viewModel,
                    onDocumentClick = { docId ->
                        navController.navigate(Screen.Inspector.createRoute(docId))
                    },
                    onNavigateToSubscription = {
                        navController.navigate(Screen.Subscription.route)
                    }
                )
            }
            composable(Screen.Vault.route) {
                SmartVaultScreen(
                    viewModel = viewModel,
                    onDocumentClick = { docId ->
                        navController.navigate(Screen.Inspector.createRoute(docId))
                    },
                    onNavigateToSubscription = {
                        navController.navigate(Screen.Subscription.route)
                    }
                )
            }
            composable(Screen.Subscription.route) {
                SubscriptionScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToSubscription = {
                        navController.navigate(Screen.Subscription.route)
                    }
                )
            }
            composable(
                route = Screen.Inspector.route,
                arguments = listOf(navArgument("docId") { type = NavType.StringType })
            ) { backStackEntry ->
                val docId = backStackEntry.arguments?.getString("docId") ?: ""
                val context = androidx.compose.ui.platform.LocalContext.current
                val activity = context as? android.app.Activity

                DocumentInspectorScreen(
                    documentId = docId,
                    viewModel = viewModel,
                    onBack = {
                        activity?.let { act ->
                            com.docusnap.ai.ads.AdMobManager.showInterstitial(act, viewModel.billingManager) {
                                navController.popBackStack()
                            }
                        } ?: navController.popBackStack()
                    }
                )
            }
        }
    }
}

