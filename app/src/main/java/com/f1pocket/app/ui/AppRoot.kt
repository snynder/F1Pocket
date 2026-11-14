package com.f1pocket.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.f1pocket.app.R
import com.f1pocket.app.data.AppSettings
import com.f1pocket.app.ui.screens.CircuitScreen
import com.f1pocket.app.ui.screens.CircuitsScreen
import com.f1pocket.app.ui.screens.CompareScreen
import com.f1pocket.app.ui.screens.HomeScreen
import com.f1pocket.app.ui.screens.LiveScreen
import com.f1pocket.app.ui.screens.MoreScreen
import com.f1pocket.app.ui.screens.RaceScreen
import com.f1pocket.app.ui.screens.RadioScreen
import com.f1pocket.app.ui.screens.RecordsScreen
import com.f1pocket.app.ui.screens.ReplayScreen
import com.f1pocket.app.ui.screens.SeasonScreen
import com.f1pocket.app.ui.screens.SettingsScreen
import com.f1pocket.app.ui.screens.SimulationScreen
import com.f1pocket.app.ui.screens.StandingsScreen
import com.f1pocket.app.ui.screens.StrategyScreen
import com.f1pocket.app.ui.screens.TelemetryScreen

object Routes {
    const val HOME = "home"
    const val STANDINGS = "standings"
    const val SEASON = "season"
    const val CIRCUITS = "circuits"
    const val MORE = "more"
    const val LIVE = "live"
    const val REPLAY = "replay"
    const val SETTINGS = "settings"
    const val RADIO = "radio"
    const val TELEMETRY = "telemetry"
    const val STRATEGY = "strategy"
    const val SIMULATION = "simulation"
    const val COMPARE = "compare"
    const val RECORDS = "records"
    fun circuit(id: String, year: Int? = null, round: Int? = null) = "circuit/$id?year=${year ?: -1}&round=${round ?: -1}"
    fun race(year: Int, round: Int) = "race/$year/$round"
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    Tab(Routes.STANDINGS, R.string.nav_standings, Icons.Filled.EmojiEvents),
    Tab(Routes.SEASON, R.string.nav_season, Icons.Filled.CalendarMonth),
    Tab(Routes.CIRCUITS, R.string.nav_circuits, Icons.Filled.Map),
    Tab(Routes.MORE, R.string.nav_more, Icons.Filled.Menu)
)

/** Which bottom tab a screen belongs to. */
private fun tabOf(route: String?): String = when {
    route == null -> Routes.HOME
    route.startsWith(Routes.STANDINGS) -> Routes.STANDINGS
    route.startsWith(Routes.SEASON) || route.startsWith("race/") -> Routes.SEASON
    route.startsWith(Routes.CIRCUITS) || route.startsWith("circuit/") -> Routes.CIRCUITS
    route in listOf(Routes.MORE, Routes.SETTINGS, Routes.RADIO, Routes.TELEMETRY, Routes.STRATEGY, Routes.SIMULATION, Routes.COMPARE, Routes.RECORDS) -> Routes.MORE
    else -> Routes.HOME
}

fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppRoot(settings: AppSettings) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = tabOf(entry?.destination?.route)
    val p = pal()
    Scaffold(
        containerColor = p.bg,
        bottomBar = {
            NavigationBar(containerColor = p.card) {
                TABS.forEach { t ->
                    NavigationBarItem(
                        selected = current == t.route,
                        onClick = { nav.goTab(t.route) },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.label), fontSize = 10.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = p.accentText, selectedTextColor = p.accentText,
                            unselectedIconColor = p.muted, unselectedTextColor = p.muted, indicatorColor = p.card2
                        )
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            NavHost(nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) { HomeScreen(nav, settings) }
                composable(Routes.STANDINGS) { StandingsScreen(nav) }
                composable(Routes.SEASON) { SeasonScreen(nav) }
                composable(Routes.CIRCUITS) { CircuitsScreen(nav) }
                composable(Routes.MORE) { MoreScreen(nav) }
                composable(Routes.LIVE) { LiveScreen(nav, settings) }
                composable(Routes.REPLAY) { ReplayScreen(nav) }
                composable(Routes.SETTINGS) { SettingsScreen(nav, settings) }
                composable(Routes.RADIO) { RadioScreen(nav, settings) }
                composable(Routes.TELEMETRY) { TelemetryScreen(nav) }
                composable(Routes.STRATEGY) { StrategyScreen(nav) }
                composable(Routes.SIMULATION) { SimulationScreen(nav) }
                composable(Routes.COMPARE) { CompareScreen(nav) }
                composable(Routes.RECORDS) { RecordsScreen(nav) }
                composable(
                    "circuit/{id}?year={year}&round={round}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.StringType },
                        navArgument("year") { type = NavType.IntType; defaultValue = -1 },
                        navArgument("round") { type = NavType.IntType; defaultValue = -1 }
                    )
                ) { e ->
                    CircuitScreen(
                        nav, e.arguments?.getString("id") ?: "",
                        e.arguments?.getInt("year")?.takeIf { it > 0 }, e.arguments?.getInt("round")?.takeIf { it > 0 }
                    )
                }
                composable(
                    "race/{year}/{round}",
                    arguments = listOf(navArgument("year") { type = NavType.IntType }, navArgument("round") { type = NavType.IntType })
                ) { e ->
                    RaceScreen(nav, e.arguments?.getInt("year") ?: 0, e.arguments?.getInt("round") ?: 0, settings)
                }
            }
        }
    }
}
