package com.smartcommunity.sos.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.smartcommunity.sos.ui.auth.AccountSettingsScreen
import com.smartcommunity.sos.ui.community.CommunityScreen
import com.smartcommunity.sos.ui.dashboard.HomeDashboardScreen
import com.smartcommunity.sos.ui.map.MapScreen

@Composable
fun SafetyAppShell(
    currentUsername: String,
    onSignedOut: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            SafetyBottomBar(
                currentRoute = currentDestination?.route,
                onDestinationSelected = { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            NavHost(
                navController = navController,
                startDestination = SafetyDestination.Map.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(SafetyDestination.Home.route) {
                    HomeDashboardScreen(currentUsername = currentUsername)
                }
                composable(SafetyDestination.Map.route) {
                    MapScreen()
                }
                composable(SafetyDestination.Community.route) {
                    CommunityScreen(currentUsername = currentUsername)
                }
                composable(SafetyDestination.Settings.route) {
                    AccountSettingsScreen(
                        currentUsername = currentUsername,
                        onSignedOut = onSignedOut
                    )
                }
            }
        }
    }
}

@Composable
private fun SafetyBottomBar(
    currentRoute: String?,
    onDestinationSelected: (SafetyDestination) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF171717),
            border = BorderStroke(1.5.dp, Color(0x444F7DBD)),
            shadowElevation = 12.dp
        ) {
            NavigationBar(
                modifier = Modifier
                    .height(78.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                containerColor = Color.Transparent,
                tonalElevation = 0.dp
            ) {
                SafetyDestination.entries.forEach { destination ->
                    val selected = currentRoute == destination.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { onDestinationSelected(destination) },
                        icon = {
                            BottomNavChip(
                                destination = destination,
                                selected = selected
                            )
                        },
                        label = null,
                        alwaysShowLabel = false,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFF4F8FF),
                            unselectedIconColor = Color(0xFFB7C1CE),
                            selectedTextColor = Color(0xFFF4F8FF),
                            unselectedTextColor = Color(0xFFB7C1CE),
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavChip(
    destination: SafetyDestination,
    selected: Boolean
) {
    val icon = when (destination) {
        SafetyDestination.Home -> Icons.Filled.Home
        SafetyDestination.Map -> Icons.Filled.LocationOn
        SafetyDestination.Community -> Icons.Filled.Add
        SafetyDestination.Settings -> Icons.Filled.Settings
    }

    val chipColor = if (selected) Color(0xFF213447) else Color(0xFF222222)
    val borderColor = if (selected) Color(0xFF67A9FF) else Color(0x334A4A4A)
    val contentColor = if (selected) Color(0xFFF4F8FF) else Color(0xFFB9C4D2)

    Surface(
        modifier = Modifier
            .width(92.dp)
            .height(50.dp),
        shape = RoundedCornerShape(18.dp),
        color = chipColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = destination.title,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = destination.title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}
