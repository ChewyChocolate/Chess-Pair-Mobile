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
import com.example.chesspairmobile.data.models.*
import com.example.chesspairmobile.ui.screens.*
import com.example.chesspairmobile.ui.theme.ChessPairMobileTheme
import com.example.chesspairmobile.viewmodel.TournamentViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChessPairMobileTheme {
                ChessPairApp()
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

@Composable
fun ChessPairApp() {
    val viewModel: TournamentViewModel = viewModel()
    val tournaments by viewModel.tournaments.collectAsState()
    val activeId by viewModel.activeTournamentId.collectAsState()
    val players by viewModel.players.collectAsState()
    val teams by viewModel.teams.collectAsState()
    val matches by viewModel.matches.collectAsState()

    val activeTournament = tournaments.find { it.id == activeId }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isTvMode = currentRoute == "tv"

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

    var showTvDialog by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            if (activeTournament != null && !isTvMode) {
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
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
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
                    onCreateTournament = { name, type, isTeam, rounds, tc, pts, noClub, autoCalc, manualOverride, section, tiebreaks ->
                        viewModel.createTournament(name, type, isTeam, rounds, tc, pts, noClub, autoCalc, manualOverride, section, tiebreaks)
                            navController.navigate("dashboard") {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                    },
                    onDeleteTournament = { id -> viewModel.deleteTournament(id) }
                )
            }

            composable("dashboard") {
                DashboardScreen(
                    tournament = activeTournament,
                    playerCount = players.size,
                    teamCount = teams.size,
                    matchCount = matches.size
                )
            }

            composable("players") {
                PlayersScreen(
                    players = players,
                    tournamentStatus = activeTournament?.status ?: "SETUP",
                    tournamentRound = activeTournament?.currentRound ?: 0,
                    onAddPlayer = { name, rating, title, club ->
                        viewModel.addPlayer(name, rating, title, club)
                    },
                    onBulkAdd = { csv -> viewModel.bulkAddPlayers(csv) },
                    onRemovePlayer = { id -> viewModel.removePlayer(id) },
                    onWithdrawPlayer = { id -> viewModel.withdrawPlayer(id) },
                    onRejoinPlayer = { id -> viewModel.rejoinPlayer(id) },
                    onClearPlayers = { viewModel.clearPlayers() }
                )
            }

            composable("teams") {
                TeamsScreen(
                    teams = teams,
                    players = players,
                    onAddTeam = { name -> viewModel.addTeam(name) },
                    onAssignPlayer = { teamId, playerId ->
                        viewModel.assignPlayerToTeam(teamId, playerId)
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
                    onUpdateResult = { matchId, result ->
                        viewModel.updateMatchResult(matchId, result)
                    },
                    onCompleteRound = { viewModel.completeRound() },
                    onRollbackRound = { viewModel.rollbackRound() },
                    onSwapPlayers = { m1, w1, m2, w2 ->
                        viewModel.swapPlayers(m1, w1, m2, w2)
                    }
                )
            }

            composable("standings") {
                StandingsScreen(
                    tournament = activeTournament,
                    players = players,
                    matches = matches,
                    onExportCsv = { "" }
                )
            }

            composable("tv") {
                // TV mode is rendered outside NavHost for full-screen immersion
            }
        }
    }

    // TV Display button in top bar
    if (activeTournament != null && !isTvMode) {
        // TV mode accessible via direct navigation or intent
    }
}
