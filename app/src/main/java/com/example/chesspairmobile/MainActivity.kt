package com.example.chesspairmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import com.example.chesspairmobile.data.preferences.AppSettings
import com.example.chesspairmobile.data.preferences.ThemeMode
import com.example.chesspairmobile.ui.screens.*
import com.example.chesspairmobile.ui.theme.ChessPairMobileTheme
import com.example.chesspairmobile.viewmodel.TournamentViewModel
import com.example.chesspairmobile.logic.tournamentRoundCount

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences = remember { AppPreferences(applicationContext) }
            val settings by preferences.settings.collectAsState(initial = AppSettings())
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
fun ChessPairApp(
    preferences: AppPreferences = AppPreferences(androidx.compose.ui.platform.LocalContext.current)
) {
    val viewModel: TournamentViewModel = viewModel()
    val tournaments by viewModel.tournaments.collectAsState()
    val activeId by viewModel.activeTournamentId.collectAsState()
    val players by viewModel.players.collectAsState()
    val teams by viewModel.teams.collectAsState()
    val matches by viewModel.matches.collectAsState()

    val settings by preferences.settings.collectAsState(initial = AppSettings())

    val activeTournament = tournaments.find { it.id == activeId }
    val plannedRounds = activeTournament?.let { t ->
        if (t.status != "SETUP") t.totalRounds else tournamentRoundCount(t.type,
            if (t.isTeamTournament) teams.count { Converters().toStringList(it.playerIds).isNotEmpty() } else players.count { it.active && !it.withdrawn },
            t.autoCalculateRounds, t.manualRoundOverride, t.totalRounds, t.swissExtraRound)
    } ?: 0

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isSettings = currentRoute == "settings"
    val inTournament = activeTournament != null && !isSettings && currentRoute != "home"
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbarHostState.showSnackbar(it) } }

    val navItems = listOf(
        NavItem("dashboard", "Overview", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
        NavItem("players", "Players", Icons.Outlined.People, Icons.Filled.People),
        NavItem("rounds", "Rounds", Icons.Outlined.GridView, Icons.Filled.GridView),
        NavItem("standings", "Standings", Icons.Outlined.Leaderboard, Icons.Filled.Leaderboard)
    )

    val allNavItems = navItems


    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (inTournament) {
                TopAppBar(
                    title = { Text(activeTournament!!.name, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigate("home") { popUpTo("home"); launchSingleTop = true } }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Tournaments")
                        }
                    },
                    actions = {
                        IconButton(onClick = { navController.navigate("settings") { launchSingleTop = true } }) {
                            Icon(Icons.Default.Settings, "Settings")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (inTournament) {
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
                            selected = currentRoute == item.route || (item.route == "players" && currentRoute == "teams"),
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
                    onSettings = { navController.navigate("settings") { launchSingleTop = true } },
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
                    players = players,
                    teams = teams,
                    matches = matches,
                    plannedRounds = plannedRounds,
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
                    isTeamTournament = activeTournament?.isTeamTournament == true,
                    onShowTeams = { navController.navigate("teams") { launchSingleTop = true } },
                    tournamentStatus = activeTournament?.status ?: "SETUP",
                    tournamentRound = activeTournament?.currentRound ?: 0,
                    tournamentTotalRounds = plannedRounds,
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
                    tournamentStatus = activeTournament?.status ?: "COMPLETED",
                    onShowPlayers = { navController.navigate("players") { popUpTo("players"); launchSingleTop = true } },
                    onAddTeam = { name -> viewModel.addTeam(name) },
                    onAssignPlayer = { teamId, playerId -> viewModel.assignPlayerToTeam(teamId, playerId) },
                    onMovePlayer = { teamId, playerId, offset -> viewModel.moveTeamPlayer(teamId, playerId, offset) },
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
                    teams = teams,
                    matches = matches,
                    plannedRounds = plannedRounds,
                    onStartTournament = { viewModel.startTournament() },
                    onGeneratePairings = { round -> viewModel.generatePairings(round) },
                    onUpdateResult = { matchId, result, correctionRound -> viewModel.updateMatchResult(matchId, result, correctionRound) },
                    onCompleteRound = {
                        viewModel.completeRound()
                        if (activeTournament?.currentRound == activeTournament?.totalRounds) {
                            navController.navigate("standings") { launchSingleTop = true }
                        }
                    },
                    onRollbackRound = { viewModel.rollbackRound() },
                    onSwapPlayers = { m1, w1, m2, w2 -> viewModel.swapPlayers(m1, w1, m2, w2) },
                    enableForfeitScoring = settings.enableForfeitScoring,
                    onAddForcedPairing = { w, b -> viewModel.addForcedPairing(w, b) },
                    onClearForcedPairings = { viewModel.clearForcedPairings() },
                    onBroadcast = {
                        viewModel.broadcastResults { ok, msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    }
                )
            }

            composable("standings") {
                StandingsScreen(
                    tournament = activeTournament,
                    players = players,
                    teams = teams,
                    matches = matches,
                    onExportCsv = { round, teamView -> viewModel.exportStandingsCsv(round, teamView) },
                    onUpdateTiebreakOrder = { order -> viewModel.updateTournamentSettings(tiebreakOrder = order) }
                )
            }

            composable("settings") {
                SettingsScreen(
                    preferences = preferences,
                    onBack = { navController.popBackStack() },
                    onThemeChanged = {}
                )
            }
        }
    }
}
