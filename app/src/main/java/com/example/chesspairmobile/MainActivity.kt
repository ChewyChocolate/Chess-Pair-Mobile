package com.example.chesspairmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.unit.dp
import com.example.chesspairmobile.data.db.Converters
import com.example.chesspairmobile.data.models.*
import com.example.chesspairmobile.data.preferences.AppPreferences
import com.example.chesspairmobile.data.preferences.ThemeMode
import com.example.chesspairmobile.ui.screens.*
import com.example.chesspairmobile.ui.theme.ChessPairMobileTheme
import com.example.chesspairmobile.viewmodel.TournamentViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences = remember { AppPreferences(applicationContext) }
            val settings by preferences.settings.collectAsState(initial = AppPreferences(applicationContext).run { com.example.chesspairmobile.data.preferences.AppSettings() })
            val themeOverride = when (settings.themeMode) {
                ThemeMode.SYSTEM -> null
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            ChessPairMobileTheme(darkTheme = themeOverride) {
                ChessPairApp(preferences = preferences)
            }
        }
    }
}

data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessPairApp(preferences: AppPreferences = AppPreferences(androidx.compose.ui.platform.LocalContext.current)) {
    val viewModel: TournamentViewModel = viewModel()
    val tournaments by viewModel.tournaments.collectAsState()
    val activeId by viewModel.activeTournamentId.collectAsState()
    val players by viewModel.players.collectAsState()
    val teams by viewModel.teams.collectAsState()
    val matches by viewModel.matches.collectAsState()

    val settings by preferences.settings.collectAsState(initial = AppPreferences(androidx.compose.ui.platform.LocalContext.current).run { com.example.chesspairmobile.data.preferences.AppSettings() })

    val activeTournament = tournaments.find { it.id == activeId }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isTvMode = currentRoute == "tv"
    val isSettings = currentRoute == "settings"

    val navItems = listOf(
        NavItem("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
        NavItem("dashboard", "Dashboard", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
        NavItem("players", "Players", Icons.Outlined.People, Icons.Filled.People),
        NavItem("rounds", "Rounds", Icons.Outlined.GridView, Icons.Filled.GridView),
        NavItem("standings", "Standings", Icons.Outlined.Leaderboard, Icons.Filled.Leaderboard)
    )

    val teamNavItem = NavItem("teams", "Teams", Icons.Outlined.Group, Icons.Filled.Group)
    val allNavItems = if (activeTournament?.isTeamTournament == true) {
        navItems + teamNavItem
    } else navItems

    if (isTvMode) {
        ChessPairMobileTheme(darkTheme = true) {
            TvDisplayScreen(
                tournament = activeTournament,
                players = players,
                matches = matches,
                onBack = { navController.popBackStack() }
            )
        }
        return
    }

    var themeChangedKey by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            if (activeTournament != null && !isTvMode && !isSettings) {
                TopAppBar(
                    title = { Text(activeTournament!!.name, maxLines = 1) },
                    actions = {
                        IconButton(onClick = { navController.navigate("settings") { launchSingleTop = true } }) {
                            Icon(Icons.Default.Settings, "Settings")
                        }
                        IconButton(onClick = { navController.navigate("tv") { launchSingleTop = true } }) {
                            Icon(Icons.Default.Tv, "TV Display")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (activeTournament != null && !isTvMode && !isSettings) {
                NavigationBar {
                    allNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    if (currentRoute == item.route) item.selectedIcon else item.icon,
                                    item.label
                                )
                            },
                            label = { Text(item.label) },
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(
                    tournaments = tournaments,
                    activeId = activeId,
                    onSelectTournament = { id ->
                        viewModel.setActiveTournament(id)
                        navController.navigate("dashboard") {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onCreateTournament = { name, type, isTeam, rounds, tc, pts, noClub, autoCalc, manualOverride, section, tiebreaks, accelMode, swissExtra ->
                        viewModel.createTournament(name, type, isTeam, rounds, tc, pts, noClub, autoCalc, manualOverride, section, tiebreaks, accelMode, swissExtra)
                        navController.navigate("dashboard") {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onDeleteTournament = { id -> viewModel.deleteTournament(id) },
                    defaultPoints = Triple(settings.defaultWinPoints, settings.defaultDrawPoints, settings.defaultLossPoints),
                    defaultAvoidClubs = settings.defaultAvoidClubs,
                    defaultAcceleration = settings.defaultAcceleration,
                    defaultTiebreakOrder = settings.defaultTiebreakOrder
                )
            }

            composable("dashboard") {
                DashboardScreen(
                    tournament = activeTournament,
                    playerCount = players.size,
                    teamCount = teams.size,
                    matchCount = matches.size,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo("dashboard") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable("players") {
                PlayersScreen(
                    players = players,
                    tournamentStatus = activeTournament?.status ?: "SETUP",
                    tournamentRound = activeTournament?.currentRound ?: 0,
                    tournamentTotalRounds = activeTournament?.totalRounds ?: 0,
                    onAddPlayer = { name, rating, title, club -> viewModel.addPlayer(name, rating, title, club) },
                    onBulkAdd = { csv -> viewModel.bulkAddPlayers(csv) },
                    onRemovePlayer = { id -> viewModel.removePlayer(id) },
                    onWithdrawPlayer = { id -> viewModel.withdrawPlayer(id) },
                    onRejoinPlayer = { id -> viewModel.rejoinPlayer(id) },
                    onClearPlayers = { viewModel.clearPlayers() },
                    onUpdatePlayer = { player -> viewModel.updatePlayer(player) }
                )
            }

            composable("teams") {
                TeamsScreen(
                    teams = teams,
                    players = players,
                    onAddTeam = { name -> viewModel.addTeam(name) },
                    onAssignPlayer = { teamId, playerId -> viewModel.assignPlayerToTeam(teamId, playerId) },
                    onRemoveFromTeam = { teamId, playerId ->
                        val team = teams.find { it.id == teamId } ?: return@TeamsScreen
                        val ids = viewModel.run {
                            val conv = Converters()
                            conv.toStringList(team.playerIds).toMutableList().also { it.remove(playerId) }
                        }
                        viewModel.updateTeam(team.copy(playerIds = Converters().fromStringList(ids)))
                    }
                )
            }

            composable("rounds") {
                RoundsScreen(
                    tournament = activeTournament,
                    players = players,
                    matches = matches,
                    onStartTournament = { viewModel.startTournament() },
                    onGeneratePairings = { round -> viewModel.generatePairings(round) },
                    onUpdateResult = { matchId, result -> viewModel.updateMatchResult(matchId, result) },
                    onCompleteRound = { viewModel.completeRound() },
                    onRollbackRound = { viewModel.rollbackRound() },
                    onSwapPlayers = { m1, w1, m2, w2 -> viewModel.swapPlayers(m1, w1, m2, w2) },
                    enableForfeitScoring = settings.enableForfeitScoring
                )
            }

            composable("standings") {
                StandingsScreen(
                    tournament = activeTournament,
                    players = players,
                    matches = matches,
                    onExportCsv = { "" },
                    onUpdateTiebreakOrder = { order -> viewModel.updateTournamentSettings(tiebreakOrder = order) }
                )
            }

            composable("tv") {}

            composable("settings") {
                SettingsScreen(
                    preferences = preferences,
                    onBack = { navController.popBackStack() },
                    onThemeChanged = { themeChangedKey++ }
                )
            }
        }
    }
}
