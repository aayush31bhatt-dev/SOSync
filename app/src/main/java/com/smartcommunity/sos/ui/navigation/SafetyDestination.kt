package com.smartcommunity.sos.ui.navigation

enum class SafetyDestination(
    val route: String,
    val title: String,
    val shortLabel: String,
    val description: String
) {
    Home(
        route = "home",
        title = "Home",
        shortLabel = "H",
        description = "Safety dashboard and quick actions"
    ),
    Map(
        route = "map",
        title = "Map",
        shortLabel = "M",
        description = "Area safety map and route guidance"
    ),
    Community(
        route = "community",
        title = "Community",
        shortLabel = "C",
        description = "Community alerts, reports, and nearby safety updates"
    ),
    Settings(
        route = "settings",
        title = "Settings",
        shortLabel = "S",
        description = "Privacy, contacts, and app preferences"
    )
}
